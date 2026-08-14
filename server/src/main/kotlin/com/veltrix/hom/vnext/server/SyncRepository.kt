package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.*
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.sql.Connection
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class SyncRepository(private val db:Database) {
    fun applyBatch(accountId:String,req:SyncBatchRequest):SyncBatchResponse {
        if(req.mutations.size>100)throw validation("Sync batch limit is 100")
        val seen=HashSet<String>()
        val results=req.mutations.map { m ->
            if(!seen.add(m.idempotencyKey)) SyncMutationResult(m.mutationId,"REJECTED",code="DUPLICATE_BATCH_KEY")
            else runCatching { applyOne(accountId,m) }.getOrElse { t ->
                val e=(t as? DomainException)?.error
                when(e?.category){
                    ErrorCategory.CONFLICT -> SyncMutationResult(m.mutationId,"CONFLICT",code=e.code)
                    ErrorCategory.TEMPORARY_UNAVAILABLE,ErrorCategory.NETWORK_UPSTREAM,ErrorCategory.DATABASE -> SyncMutationResult(m.mutationId,"RETRY",code=e.code,retryable=true)
                    else -> SyncMutationResult(m.mutationId,"REJECTED",code=e?.code?:"SYNC_FAILED",retryable=e?.retryable?:false)
                }
            }
        }
        return SyncBatchResponse(results,Instant.now().toString())
    }

    private fun applyOne(accountId:String,m:SyncMutationRequest):SyncMutationResult {
        if(m.idempotencyKey.length !in 8..180)throw validation("Invalid sync idempotency key")
        UUID.fromString(m.mutationId);UUID.fromString(m.entityId)
        val requestHash=hash(canonical(m))
        return db.tx { c ->
            val previous=c.prepareStatement("SELECT request_hash,response_body FROM idempotency_record WHERE account_id=?::uuid AND idempotency_key=? AND expires_at>now()").use{ps->ps.setString(1,accountId);ps.setString(2,m.idempotencyKey);ps.executeQuery().use{rs->if(rs.next())rs.getString(1) to rs.getString(2) else null}}
            if(previous!=null){if(previous.first!=requestHash)throw DomainException(DomainError("IDEMPOTENCY_CONFLICT",ErrorCategory.CONFLICT,"Idempotency key reused with different payload"));val revision=Regex("\\\"serverRevision\\\":([0-9]+)").find(previous.second?:"")?.groupValues?.get(1)?.toLongOrNull();return@tx SyncMutationResult(m.mutationId,"APPLIED",revision,"IDEMPOTENT_REPLAY")}
            val revision=when("${m.entityType.uppercase()}:${m.operation.uppercase()}"){
                "NOTE:UPSERT"->syncNote(c,accountId,m)
                "PROJECT:UPSERT"->syncProject(c,accountId,m)
                "GOAL:UPSERT"->syncGoal(c,accountId,m)
                "ASSESSMENT_ANSWER:UPSERT"->syncAssessmentAnswer(c,accountId,m)
                "FLASHCARD:REVIEW"->syncFlashcardReview(c,accountId,m)
                "CONTEXT_CARRY:UPSERT"->syncContextCarry(c,accountId,m)
                else->throw validation("Unsupported sync operation ${m.entityType}:${m.operation}")
            }
            val body="{\"status\":\"APPLIED\",\"serverRevision\":$revision}"
            c.prepareStatement("INSERT INTO idempotency_record(account_id,idempotency_key,operation,request_hash,response_status,response_body,expires_at) VALUES (?::uuid,?,?,?,?,?::jsonb,now()+interval '30 days')").use{ps->ps.setString(1,accountId);ps.setString(2,m.idempotencyKey);ps.setString(3,"${m.entityType}:${m.operation}");ps.setString(4,requestHash);ps.setInt(5,200);ps.setString(6,body);ps.executeUpdate()}
            SyncMutationResult(m.mutationId,"APPLIED",revision)
        }
    }
    private fun syncNote(c:Connection,a:String,m:SyncMutationRequest):Long {
        val title=m.payload["title"]?.trim()?.take(240)?.takeIf{it.isNotEmpty()}?:throw validation("Note title required")
        val body=m.payload["body"]?.take(1_000_000)?:""
        val project=m.payload["projectId"]?.takeIf{it.isNotBlank()};project?.let{owned(c,"project",a,it)}
        return if(m.expectedRevision==null){
            c.prepareStatement("INSERT INTO note(id,account_id,project_id,title,body) VALUES (?::uuid,?::uuid,?::uuid,?,?) RETURNING revision").use{ps->ps.setString(1,m.entityId);ps.setString(2,a);ps.setString(3,project);ps.setString(4,title);ps.setString(5,body);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
        } else c.prepareStatement("UPDATE note SET title=?,body=?,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL RETURNING revision").use{ps->ps.setString(1,title);ps.setString(2,body);ps.setString(3,m.entityId);ps.setString(4,a);ps.setLong(5,m.expectedRevision);ps.executeQuery().use{rs->if(!rs.next())throw conflict("Note sync revision conflict");rs.getLong(1)}}
    }
    private fun syncProject(c:Connection,a:String,m:SyncMutationRequest):Long {
        val title=m.payload["title"]?.trim()?.take(120)?.takeIf{it.isNotEmpty()}?:throw validation("Project title required")
        val purpose=m.payload["purpose"]?.take(2000);val status=m.payload["status"]?:"ACTIVE";if(status !in setOf("ACTIVE","PAUSED","COMPLETED","ARCHIVED"))throw validation("Invalid project status")
        val priority=m.payload["priority"]?.toIntOrNull()?.coerceIn(-100,100)?:0
        return if(m.expectedRevision==null)c.prepareStatement("INSERT INTO project(id,account_id,title,purpose,status,priority) VALUES (?::uuid,?::uuid,?,?,?::project_status,?) RETURNING revision").use{ps->ps.setString(1,m.entityId);ps.setString(2,a);ps.setString(3,title);ps.setString(4,purpose);ps.setString(5,status);ps.setInt(6,priority);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}} else c.prepareStatement("UPDATE project SET title=?,purpose=?,status=?::project_status,priority=?,updated_at=now(),last_active_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL RETURNING revision").use{ps->ps.setString(1,title);ps.setString(2,purpose);ps.setString(3,status);ps.setInt(4,priority);ps.setString(5,m.entityId);ps.setString(6,a);ps.setLong(7,m.expectedRevision);ps.executeQuery().use{rs->if(!rs.next())throw conflict("Project sync revision conflict");rs.getLong(1)}}
    }
    private fun syncGoal(c:Connection,a:String,m:SyncMutationRequest):Long {
        val project=m.payload["projectId"]?:throw validation("Goal projectId required");owned(c,"project",a,project)
        val title=m.payload["title"]?.trim()?.take(200)?.takeIf{it.isNotEmpty()}?:throw validation("Goal title required");val status=m.payload["status"]?:"ACTIVE";if(status !in setOf("ACTIVE","COMPLETED","PAUSED","CANCELLED","ARCHIVED"))throw validation("Invalid goal status")
        val priority=m.payload["priority"]?.toIntOrNull()?.coerceIn(-100,100)?:0
        return if(m.expectedRevision==null)c.prepareStatement("INSERT INTO goal(id,account_id,project_id,title,description,priority,status) VALUES (?::uuid,?::uuid,?::uuid,?,?,?,?::goal_status) RETURNING revision").use{ps->ps.setString(1,m.entityId);ps.setString(2,a);ps.setString(3,project);ps.setString(4,title);ps.setString(5,m.payload["description"]?.take(4000));ps.setInt(6,priority);ps.setString(7,status);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}} else c.prepareStatement("UPDATE goal SET title=?,description=?,priority=?,status=?::goal_status,completed_at=CASE WHEN ?='COMPLETED' THEN COALESCE(completed_at,now()) ELSE NULL END,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND project_id=?::uuid AND revision=? AND deleted_at IS NULL RETURNING revision").use{ps->ps.setString(1,title);ps.setString(2,m.payload["description"]?.take(4000));ps.setInt(3,priority);ps.setString(4,status);ps.setString(5,status);ps.setString(6,m.entityId);ps.setString(7,a);ps.setString(8,project);ps.setLong(9,m.expectedRevision);ps.executeQuery().use{rs->if(!rs.next())throw conflict("Goal sync revision conflict");rs.getLong(1)}}
    }
    private fun syncAssessmentAnswer(c:Connection,a:String,m:SyncMutationRequest):Long {
        val attempt=m.payload["attemptId"]?:throw validation("attemptId required");val question=m.payload["questionId"]?:throw validation("questionId required");val answer=m.payload["answerJson"]?:throw validation("answerJson required")
        c.prepareStatement("SELECT 1 FROM assessment_attempt WHERE id=?::uuid AND account_id=?::uuid AND state='IN_PROGRESS'").use{ps->ps.setString(1,attempt);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("ATTEMPT_ALREADY_SUBMITTED",ErrorCategory.CONFLICT,"Attempt not editable"))}}
        c.prepareStatement("INSERT INTO assessment_answer(attempt_id,question_id,account_id,answer) VALUES (?::uuid,?::uuid,?::uuid,?::jsonb) ON CONFLICT(attempt_id,question_id) DO UPDATE SET answer=excluded.answer,answered_at=now(),updated_at=now()").use{ps->ps.setString(1,attempt);ps.setString(2,question);ps.setString(3,a);ps.setString(4,answer);ps.executeUpdate()}
        return c.prepareStatement("UPDATE assessment_attempt SET last_active_at=now(),updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid RETURNING revision").use{ps->ps.setString(1,attempt);ps.setString(2,a);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
    }
    private fun syncFlashcardReview(c:Connection,a:String,m:SyncMutationRequest):Long {
        val rating=runCatching{ReviewRating.valueOf(m.payload["rating"]?.uppercase()?:"")}.getOrElse{throw validation("Invalid flashcard rating")}
        val pair=c.prepareStatement("SELECT interval_days,ease,repetitions,lapses,due_at,last_reviewed_at,revision FROM flashcard_schedule WHERE card_id=?::uuid AND account_id=?::uuid FOR UPDATE").use{ps->ps.setString(1,m.entityId);ps.setString(2,a);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("CARD_NOT_FOUND",ErrorCategory.NOT_FOUND,"Card not found"));FlashcardScheduleState(m.entityId,rs.getInt(1),rs.getDouble(2),rs.getInt(3),rs.getInt(4),rs.getObject(5,OffsetDateTime::class.java).toInstant(),rs.getObject(6,OffsetDateTime::class.java)?.toInstant()) to rs.getLong(7)}}
        if(m.expectedRevision!=null && pair.second!=m.expectedRevision)throw conflict("Flashcard schedule sync conflict")
        val next=FlashcardScheduler.review(pair.first,rating,Instant.now())
        return c.prepareStatement("UPDATE flashcard_schedule SET interval_days=?,ease=?,repetitions=?,lapses=?,due_at=?,last_reviewed_at=?,revision=revision+1 WHERE card_id=?::uuid AND account_id=?::uuid AND revision=? RETURNING revision").use{ps->ps.setInt(1,next.intervalDays);ps.setDouble(2,next.ease);ps.setInt(3,next.repetitions);ps.setInt(4,next.lapses);ps.setObject(5,OffsetDateTime.ofInstant(next.dueAt,ZoneOffset.UTC));ps.setObject(6,OffsetDateTime.ofInstant(next.lastReviewedAt?:Instant.now(),ZoneOffset.UTC));ps.setString(7,m.entityId);ps.setString(8,a);ps.setLong(9,pair.second);ps.executeQuery().use{rs->if(!rs.next())throw conflict("Flashcard schedule sync conflict");rs.getLong(1)}}
    }
    private fun syncContextCarry(c:Connection,a:String,m:SyncMutationRequest):Long {
        if(m.entityId!=a)throw validation("ContextCarry entityId must equal accountId")
        val project=m.payload["projectId"]?.takeIf{it.isNotBlank()};project?.let{owned(c,"project",a,it)}
        val conversation=m.payload["conversationId"]?.takeIf{it.isNotBlank()};conversation?.let{ownedScoped(c,"conversation",a,it)}
        val assessment=m.payload["assessmentId"]?.takeIf{it.isNotBlank()};assessment?.let{ownedScoped(c,"assessment",a,it)}
        val sourceIds=m.payload["sourceIdsJson"]?.takeIf{it.isNotBlank()}?:"[]"
        validateSourceIds(c,a,sourceIds)
        val topic=m.payload["topic"]?.take(500);val mode=(m.payload["learningMode"]?:"DEFAULT").take(80);val origin=(m.payload["origin"]?:"UNKNOWN").take(120);val ret=m.payload["returnDestination"]?.take(500)
        val active=c.prepareStatement("SELECT id,context_revision FROM context_carry_state WHERE account_id=?::uuid AND state='ACTIVE' FOR UPDATE").use{ps->ps.setString(1,a);ps.executeQuery().use{rs->if(rs.next())rs.getString(1) to rs.getLong(2) else null}}
        if(active==null){
            if((m.expectedRevision?:0)>0)throw conflict("ContextCarry sync revision conflict")
            return c.prepareStatement("INSERT INTO context_carry_state(account_id,project_id,source_ids,conversation_id,assessment_id,topic,learning_mode,origin,return_destination,context_revision,state) VALUES (?::uuid,?::uuid,?::jsonb,?::uuid,?::uuid,?,?,?,?,1,'ACTIVE') RETURNING context_revision").use{ps->ps.setString(1,a);ps.setString(2,project);ps.setString(3,sourceIds);ps.setString(4,conversation);ps.setString(5,assessment);ps.setString(6,topic);ps.setString(7,mode);ps.setString(8,origin);ps.setString(9,ret);ps.executeQuery().use{rs->rs.next();rs.getLong(1)}}
        }
        if(m.expectedRevision!=null && active.second!=m.expectedRevision)throw conflict("ContextCarry sync revision conflict")
        return c.prepareStatement("UPDATE context_carry_state SET project_id=?::uuid,source_ids=?::jsonb,conversation_id=?::uuid,assessment_id=?::uuid,topic=?,learning_mode=?,origin=?,return_destination=?,context_revision=context_revision+1,updated_at=now() WHERE id=?::uuid AND account_id=?::uuid AND context_revision=? RETURNING context_revision").use{ps->ps.setString(1,project);ps.setString(2,sourceIds);ps.setString(3,conversation);ps.setString(4,assessment);ps.setString(5,topic);ps.setString(6,mode);ps.setString(7,origin);ps.setString(8,ret);ps.setString(9,active.first);ps.setString(10,a);ps.setLong(11,active.second);ps.executeQuery().use{rs->if(!rs.next())throw conflict("ContextCarry sync revision conflict");rs.getLong(1)}}
    }
    private fun validateSourceIds(c:Connection,a:String,json:String){
        val invalid=runCatching{c.prepareStatement("SELECT count(*) FROM jsonb_array_elements_text(?::jsonb) j(id) LEFT JOIN source s ON s.id::text=j.id AND s.account_id=?::uuid AND s.deleted_at IS NULL WHERE s.id IS NULL").use{ps->ps.setString(1,json);ps.setString(2,a);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}}.getOrElse{throw validation("sourceIdsJson must be a JSON array of owned source UUIDs")}
        if(invalid>0)throw DomainException(DomainError("SOURCE_NOT_FOUND",ErrorCategory.NOT_FOUND,"ContextCarry source not found"))
    }
    private fun ownedScoped(c:Connection,t:String,a:String,id:String){require(t in setOf("conversation","assessment"));c.prepareStatement("SELECT 1 FROM $t WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("NOT_FOUND",ErrorCategory.NOT_FOUND,"Linked object not found"))}}}
    private fun owned(c:Connection,t:String,a:String,id:String){require(t=="project");c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}
    private fun conflict(msg:String)=DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,msg))
    private fun canonical(m:SyncMutationRequest)=listOf(m.mutationId,m.entityType,m.entityId,m.operation,m.expectedRevision?.toString().orEmpty(),m.idempotencyKey)+m.payload.toSortedMap().flatMap{listOf(it.key,it.value)}
    private fun hash(parts:List<String>):String=MessageDigest.getInstance("SHA-256").digest(parts.joinToString("\u0000").toByteArray(StandardCharsets.UTF_8)).joinToString(""){"%02x".format(it)}
}
