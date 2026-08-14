package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.core.StudentSignalStatus
import com.veltrix.hom.vnext.core.StudentSignalType
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime
import java.util.UUID

internal class Part3StudentRepository(private val db:Database,private val memory:MemoryRepository){
    private val json=Json{ignoreUnknownKeys=false}
    private val signalTypes=StudentSignalType.entries.map{it.name}.toSet()
    private val relationshipTypes=setOf("RELATED","DERIVED_FROM","SUPPLEMENTS","CONTRADICTS","SAME_TOPIC","PROJECT_REFERENCE","USER_DEFINED")

    fun snapshot(accountId:String,projectId:String?,limit:Int):StudentModelSnapshotResponse{
        projectId?.let{db.tx{c->owned(c,"project",accountId,it)}}
        val maturity=memory.maturity(accountId).state
        val signals=db.tx{c->
            val sql="""SELECT * FROM student_signal WHERE account_id=?::uuid AND deleted_at IS NULL ${if(projectId!=null)"AND (project_id IS NULL OR project_id=?::uuid)" else "AND project_id IS NULL"} ORDER BY CASE WHEN source='EXPLICIT_USER' THEN 0 ELSE 1 END,confidence DESC,updated_at DESC LIMIT ?"""
            c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,accountId);if(projectId!=null)ps.setString(i++,projectId);ps.setInt(i,limit.coerceIn(1,500));ps.executeQuery().use{rs->buildList{while(rs.next())add(mapSignal(c,rs))}}}
        }
        return StudentModelSnapshotResponse(accountId,maturity,signals)
    }

    fun createExplicit(accountId:String,req:StudentSignalCreateRequest):StudentSignalResponse{
        val type=req.type.uppercase();if(type !in signalTypes)throw validation("Unsupported student signal type")
        validateJson(req.valueJson,"valueJson");req.projectId?.let{db.tx{c->owned(c,"project",accountId,it)}}
        rejectInsultingLabel(req.valueJson)
        return db.tx{c->
            val id=c.prepareStatement("""INSERT INTO student_signal(account_id,project_id,signal_type,structured_value,confidence,source,status,last_confirmed_at) VALUES(?::uuid,?::uuid,?,?::jsonb,1,'EXPLICIT_USER','CONFIRMED',now()) RETURNING id""").use{ps->ps.setString(1,accountId);ps.setString(2,req.projectId);ps.setString(3,type);ps.setString(4,req.valueJson);ps.executeQuery().use{rs->rs.next();rs.getObject(1,UUID::class.java).toString()}}
            req.evidence.forEach{insertEvidence(c,accountId,id,it)}
            emitMemoryUpdated(c,accountId,id,"CREATE",1)
            signal(c,accountId,id)
        }
    }

    fun createInferred(accountId:String,projectId:String?,typeRaw:String,valueJson:String,confidence:Double,evidence:List<StudentSignalEvidenceDto>,source:String):StudentSignalResponse{
        val type=typeRaw.uppercase();if(type !in signalTypes)throw validation("Unsupported student signal type")
        if(confidence !in 0.0..1.0)throw validation("confidence must be in [0,1]")
        if(evidence.isEmpty())throw validation("Inferred student signals require evidence")
        if(source.isBlank()||source=="EXPLICIT_USER")throw validation("Invalid inference source")
        validateJson(valueJson,"valueJson");rejectInsultingLabel(valueJson);projectId?.let{db.tx{c->owned(c,"project",accountId,it)}}
        return db.tx{c->
            val id=c.prepareStatement("""INSERT INTO student_signal(account_id,project_id,signal_type,structured_value,confidence,source,status) VALUES(?::uuid,?::uuid,?,?::jsonb,?,?,'ACTIVE') RETURNING id""").use{ps->ps.setString(1,accountId);ps.setString(2,projectId);ps.setString(3,type);ps.setString(4,valueJson);ps.setDouble(5,confidence);ps.setString(6,source.take(100));ps.executeQuery().use{rs->rs.next();rs.getObject(1,UUID::class.java).toString()}}
            evidence.forEach{insertEvidence(c,accountId,id,it)};emitMemoryUpdated(c,accountId,id,"INFER",1);signal(c,accountId,id)
        }
    }

    fun correct(accountId:String,id:String,req:StudentSignalCorrectionRequest):StudentSignalResponse{
        validateJson(req.valueJson,"valueJson");rejectInsultingLabel(req.valueJson)
        return db.tx{c->
            val current=signal(c,accountId,id);if(current.revision!=req.expectedRevision)throw conflict("Student signal revision conflict");if(current.status=="SUPERSEDED")throw conflict("Student signal already superseded")
            val replacement=c.prepareStatement("""INSERT INTO student_signal(account_id,project_id,signal_type,structured_value,confidence,source,status,last_confirmed_at,supersedes) VALUES(?::uuid,?::uuid,?,?::jsonb,1,'EXPLICIT_USER','CONFIRMED',now(),?::uuid) RETURNING id""").use{ps->ps.setString(1,accountId);ps.setString(2,current.projectId);ps.setString(3,current.type);ps.setString(4,req.valueJson);ps.setString(5,id);ps.executeQuery().use{rs->rs.next();rs.getObject(1,UUID::class.java).toString()}}
            c.prepareStatement("UPDATE student_signal SET status='SUPERSEDED',superseded_by=?::uuid,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL").use{ps->ps.setString(1,replacement);ps.setString(2,id);ps.setString(3,accountId);ps.setLong(4,req.expectedRevision);if(ps.executeUpdate()!=1)throw conflict("Student signal revision conflict")}
            insertEvidence(c,accountId,replacement,StudentSignalEvidenceDto("USER_CORRECTION",req.evidenceObjectId));emitMemoryUpdated(c,accountId,replacement,"CORRECT",1);signal(c,accountId,replacement)
        }
    }

    fun state(accountId:String,id:String,req:StudentSignalStateRequest):StudentSignalResponse{
        val target=req.status.uppercase();if(target !in setOf("CONFIRMED","REJECTED","ARCHIVED"))throw validation("Unsupported signal state")
        return db.tx{c->signal(c,accountId,id);c.prepareStatement("""UPDATE student_signal SET status=?,last_confirmed_at=CASE WHEN ?='CONFIRMED' THEN now() ELSE last_confirmed_at END,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL RETURNING *""").use{ps->ps.setString(1,target);ps.setString(2,target);ps.setString(3,id);ps.setString(4,accountId);ps.setLong(5,req.expectedRevision);ps.executeQuery().use{rs->if(!rs.next())throw conflict("Student signal revision conflict");val out=mapSignal(c,rs);emitMemoryUpdated(c,accountId,id,target,out.revision);out}}}
    }

    fun delete(accountId:String,id:String,expectedRevision:Long){db.tx{c->signal(c,accountId,id);c.prepareStatement("UPDATE student_signal SET deleted_at=now(),status='ARCHIVED',updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.setLong(3,expectedRevision);if(ps.executeUpdate()!=1)throw conflict("Student signal revision conflict")};emitMemoryUpdated(c,accountId,id,"DELETE",expectedRevision+1)}}

    fun recommendations(accountId:String,projectId:String?,limit:Int):List<PersonalizationRecommendationResponse>{
        projectId?.let{db.tx{c->owned(c,"project",accountId,it)}}
        return db.tx{c->
            val sql="""SELECT s.id,s.project_id,s.signal_type,s.confidence,COALESCE(jsonb_agg(DISTINCT e.object_id) FILTER(WHERE e.object_id IS NOT NULL),'[]'::jsonb)::text evidence FROM student_signal s LEFT JOIN student_signal_evidence e ON e.signal_id=s.id WHERE s.account_id=?::uuid AND s.deleted_at IS NULL AND s.status IN('ACTIVE','CONFIRMED') AND s.confidence>=0.55 ${if(projectId!=null)"AND (s.project_id IS NULL OR s.project_id=?::uuid)" else "AND s.project_id IS NULL"} GROUP BY s.id ORDER BY CASE WHEN s.source='EXPLICIT_USER' THEN 0 ELSE 1 END,s.confidence DESC,s.updated_at DESC LIMIT ?"""
            c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,accountId);if(projectId!=null)ps.setString(i++,projectId);ps.setInt(i,limit.coerceIn(1,10));ps.executeQuery().use{rs->buildList{val now=Instant.now();while(rs.next()){val type=rs.getString(3);val reason=when(type){"MISTAKE"->"MISTAKE";"PERFORMANCE"->"PERFORMANCE";"GOAL"->"GOAL";"PROJECT"->"PROJECT_FOCUS";"RECENT_CONTEXT"->"RECENT_CONTEXT";else->"DUE_REVIEW"};val action=when(reason){"MISTAKE"->"PRACTICE_WEAK_TOPIC";"PERFORMANCE"->"REVIEW_PERFORMANCE_TOPIC";"GOAL"->"CONTINUE_GOAL";"PROJECT_FOCUS"->"CONTINUE_PROJECT";"RECENT_CONTEXT"->"CONTINUE_RECENT_CONTEXT";else->"REVIEW_RELEVANT_MATERIAL"};val id=rs.getObject(1).toString();add(PersonalizationRecommendationResponse("signal:$id",action,"STUDENT_SIGNAL",id,rs.getObject(2)?.toString(),reason,decodeStrings(rs.getString(5)),rs.getDouble(4),now.toString(),now.plusSeconds(86400).toString(),"ACTIVE",1))}}}}
        }
    }

    fun getContext(accountId:String):ContextCarryResponse?=db.tx{c->context(c,accountId)}
    fun putContext(accountId:String,req:ContextCarryPutRequest):ContextCarryResponse=db.tx{c->
        req.projectId?.let{owned(c,"project",accountId,it)};req.conversationId?.let{owned(c,"conversation",accountId,it)};req.assessmentId?.let{owned(c,"assessment",accountId,it)};req.sourceIds.distinct().forEach{owned(c,"source",accountId,it)}
        val current=context(c,accountId)
        if(current==null){if(req.expectedRevision!=0L)throw conflict("ContextCarry revision conflict");c.prepareStatement("""INSERT INTO context_carry_state(account_id,project_id,source_ids,conversation_id,assessment_id,topic,learning_mode,origin,return_destination,context_revision,state) VALUES(?::uuid,?::uuid,?::jsonb,?::uuid,?::uuid,?,?,?,?,1,'ACTIVE') RETURNING *""").use{ps->ps.setString(1,accountId);ps.setString(2,req.projectId);ps.setString(3,json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),req.sourceIds.distinct()));ps.setString(4,req.conversationId);ps.setString(5,req.assessmentId);ps.setString(6,req.topic?.take(300));ps.setString(7,req.learningMode.take(80));ps.setString(8,req.origin?.take(120));ps.setString(9,req.returnDestination?.take(300));ps.executeQuery().use{rs->rs.next();mapContext(rs)}}}
        else{if(current.contextRevision!=req.expectedRevision)throw conflict("ContextCarry revision conflict");c.prepareStatement("""UPDATE context_carry_state SET project_id=?::uuid,source_ids=?::jsonb,conversation_id=?::uuid,assessment_id=?::uuid,topic=?,learning_mode=?,origin=?,return_destination=?,context_revision=context_revision+1,updated_at=now() WHERE id=?::uuid AND account_id=?::uuid AND context_revision=? RETURNING *""").use{ps->ps.setString(1,req.projectId);ps.setString(2,json.encodeToString(kotlinx.serialization.builtins.ListSerializer(kotlinx.serialization.serializer<String>()),req.sourceIds.distinct()));ps.setString(3,req.conversationId);ps.setString(4,req.assessmentId);ps.setString(5,req.topic?.take(300));ps.setString(6,req.learningMode.take(80));ps.setString(7,req.origin?.take(120));ps.setString(8,req.returnDestination?.take(300));ps.setString(9,current.id);ps.setString(10,accountId);ps.setLong(11,req.expectedRevision);ps.executeQuery().use{rs->if(!rs.next())throw conflict("ContextCarry revision conflict");mapContext(rs)}}}
    }

    fun relationships(accountId:String,sourceId:String):List<SourceRelationshipResponse>=db.tx{c->owned(c,"source",accountId,sourceId);c.prepareStatement("SELECT * FROM source_relationship WHERE account_id=?::uuid AND(from_source_id=?::uuid OR to_source_id=?::uuid) ORDER BY updated_at DESC").use{ps->ps.setString(1,accountId);ps.setString(2,sourceId);ps.setString(3,sourceId);ps.executeQuery().use{rs->buildList{while(rs.next())add(mapRelationship(rs))}}}}
    fun createRelationship(accountId:String,sourceId:String,req:SourceRelationshipCreateRequest):SourceRelationshipResponse=db.tx{c->
        owned(c,"source",accountId,sourceId);owned(c,"source",accountId,req.toSourceId);if(sourceId==req.toSourceId)throw validation("Source relationship cannot target itself")
        val type=req.type.uppercase();if(type !in relationshipTypes)throw validation("Unsupported source relationship type");val by=req.createdBy.uppercase();if(by !in setOf("USER","AI_SUGGESTION"))throw validation("createdBy must be USER or AI_SUGGESTION");validateJson(req.metadataJson,"metadataJson")
        c.prepareStatement("""INSERT INTO source_relationship(account_id,from_source_id,to_source_id,relationship_type,created_by,accepted,metadata) VALUES(?::uuid,?::uuid,?::uuid,?,?,?,?::jsonb) ON CONFLICT(account_id,from_source_id,to_source_id,relationship_type) DO UPDATE SET accepted=EXCLUDED.accepted,metadata=EXCLUDED.metadata,updated_at=now(),revision=source_relationship.revision+1 RETURNING *""").use{ps->ps.setString(1,accountId);ps.setString(2,sourceId);ps.setString(3,req.toSourceId);ps.setString(4,type);ps.setString(5,by);ps.setBoolean(6,if(by=="AI_SUGGESTION")false else req.accepted);ps.setString(7,req.metadataJson);ps.executeQuery().use{rs->rs.next();mapRelationship(rs)}}
    }

    private fun signal(c:Connection,a:String,id:String)=c.prepareStatement("SELECT * FROM student_signal WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{rs->if(!rs.next())throw notFound("Student signal not found");mapSignal(c,rs)}}
    private fun mapSignal(c:Connection,rs:ResultSet):StudentSignalResponse{val id=rs.getObject("id").toString();val ev=c.prepareStatement("SELECT evidence_kind,object_id,observed_at,metadata FROM student_signal_evidence WHERE signal_id=?::uuid ORDER BY observed_at").use{ps->ps.setString(1,id);ps.executeQuery().use{er->buildList{while(er.next())add(StudentSignalEvidenceDto(er.getString(1),er.getString(2),er.getObject(3,OffsetDateTime::class.java).toInstant().toString(),er.getString(4)))}}};return StudentSignalResponse(id,rs.getObject("project_id")?.toString(),rs.getString("signal_type"),rs.getString("structured_value"),rs.getDouble("confidence"),ev,rs.getString("source"),rs.getString("status"),rs.getObject("created_at",OffsetDateTime::class.java).toInstant().toString(),rs.getObject("updated_at",OffsetDateTime::class.java).toInstant().toString(),rs.getObject("last_confirmed_at",OffsetDateTime::class.java)?.toInstant()?.toString(),rs.getObject("review_after",OffsetDateTime::class.java)?.toInstant()?.toString(),rs.getObject("supersedes")?.toString(),rs.getObject("superseded_by")?.toString(),rs.getLong("revision"))}
    private fun insertEvidence(c:Connection,a:String,id:String,e:StudentSignalEvidenceDto){if(e.kind.isBlank()||e.objectId.isBlank())throw validation("Signal evidence requires kind and objectId");validateJson(e.metadataJson,"evidence metadata");c.prepareStatement("INSERT INTO student_signal_evidence(signal_id,account_id,evidence_kind,object_id,metadata)VALUES(?::uuid,?::uuid,?,?,?::jsonb)").use{ps->ps.setString(1,id);ps.setString(2,a);ps.setString(3,e.kind.take(100));ps.setString(4,e.objectId.take(300));ps.setString(5,e.metadataJson);ps.executeUpdate()}}
    private fun context(c:Connection,a:String):ContextCarryResponse?=c.prepareStatement("SELECT * FROM context_carry_state WHERE account_id=?::uuid AND state='ACTIVE' ORDER BY updated_at DESC LIMIT 1").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->if(rs.next())mapContext(rs)else null}}
    private fun mapContext(rs:ResultSet)=ContextCarryResponse(rs.getObject("id").toString(),rs.getObject("project_id")?.toString(),decodeStrings(rs.getString("source_ids")),rs.getObject("conversation_id")?.toString(),rs.getObject("assessment_id")?.toString(),rs.getString("topic"),rs.getString("learning_mode"),rs.getString("origin"),rs.getString("return_destination"),rs.getLong("context_revision"),rs.getString("state"),rs.getObject("updated_at",OffsetDateTime::class.java).toInstant().toString())
    private fun mapRelationship(rs:ResultSet)=SourceRelationshipResponse(rs.getObject("id").toString(),rs.getObject("from_source_id").toString(),rs.getObject("to_source_id").toString(),rs.getString("relationship_type"),rs.getString("created_by"),rs.getBoolean("accepted"),rs.getString("metadata"),rs.getLong("revision"),rs.getObject("updated_at",OffsetDateTime::class.java).toInstant().toString())
    private fun emitMemoryUpdated(c:Connection,a:String,id:String,op:String,revision:Long){c.prepareStatement("INSERT INTO frontend_semantic_event(account_id,event_type,entity_id,payload,revision,idempotency_key)VALUES(?::uuid,'MEMORY_UPDATED',?,jsonb_build_object('signalId',?,'operation',?),?,?)ON CONFLICT(account_id,idempotency_key)DO NOTHING").use{ps->ps.setString(1,a);ps.setString(2,id);ps.setString(3,id);ps.setString(4,op);ps.setLong(5,revision);ps.setString(6,"student-signal:$id:$op:$revision");ps.executeUpdate()}}
    private fun owned(c:Connection,table:String,a:String,id:String){if(table !in setOf("project","source","conversation","assessment"))error("Unsafe table");c.prepareStatement("SELECT 1 FROM $table WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw notFound("Owned object not found")}}}
    private fun decodeStrings(raw:String)=runCatching{json.decodeFromString<List<String>>(raw)}.getOrDefault(emptyList())
    private fun validateJson(raw:String,label:String){runCatching{json.parseToJsonElement(raw)}.getOrElse{throw validation("$label must be valid JSON")}}
    private fun rejectInsultingLabel(raw:String){val canonical=raw.lowercase();if(listOf("lazy","bad student","weak person").any{canonical.contains("\"$it\"")})throw validation("Permanent insulting labels are forbidden")}
    private fun conflict(m:String)=com.veltrix.hom.vnext.core.DomainException(com.veltrix.hom.vnext.core.DomainError("CONFLICT",ErrorCategory.CONFLICT,m))
    private fun notFound(m:String)=com.veltrix.hom.vnext.core.DomainException(com.veltrix.hom.vnext.core.DomainError("NOT_FOUND",ErrorCategory.NOT_FOUND,m))
}
