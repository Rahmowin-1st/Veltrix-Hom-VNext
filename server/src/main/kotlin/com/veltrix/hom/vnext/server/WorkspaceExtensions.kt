package com.veltrix.hom.vnext.server

import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import com.veltrix.hom.vnext.core.*

class WorkspaceExtensionRepository(private val db:Database) {
    private val templates=mapOf(
        "CUSTOM" to ProjectTemplateResponse("CUSTOM", listOf("CHAT","SOURCES","NOTES","GOALS"), emptyList(), emptyList()),
        "LANGUAGE_EXAM" to ProjectTemplateResponse("LANGUAGE_EXAM", listOf("CHAT","SOURCES","TESTS","QUIZZES","PRACTICE","FLASHCARDS","MISTAKES"), listOf("Define target level","Complete baseline assessment","Build a weekly practice loop"), listOf("Correct language errors","Prefer target-level vocabulary","Do not reveal practice answers immediately")),
        "SCHOOL_SUBJECT" to ProjectTemplateResponse("SCHOOL_SUBJECT", listOf("SOURCES","NOTES","TESTS","PRACTICE","FLASHCARDS"), listOf("Add syllabus or textbook sources","Complete baseline test"), listOf("Explain from first principles","Use source citations where available")),
        "EXAM_PREPARATION" to ProjectTemplateResponse("EXAM_PREPARATION", listOf("TESTS","QUIZZES","MISTAKES","PRACTICE","FLASHCARDS"), listOf("Set exam date","Complete diagnostic test","Review recurring mistakes"), listOf("Prioritize exam-style reasoning","Track weak topics")),
        "RESEARCH" to ProjectTemplateResponse("RESEARCH", listOf("SOURCES","CHAT","NOTES","GOALS"), listOf("Define research question","Collect primary sources"), listOf("Prefer citations","Separate evidence from inference")),
        "PERSONAL_SKILL" to ProjectTemplateResponse("PERSONAL_SKILL", listOf("GOALS","PRACTICE","NOTES","CHAT"), listOf("Define observable skill outcome"), listOf("Prefer actionable practice")),
        "COMPETITION" to ProjectTemplateResponse("COMPETITION", listOf("GOALS","TESTS","PRACTICE","MISTAKES"), listOf("Define competition target","Run baseline assessment"), listOf("Use competition constraints and timing"))
    )
    fun templates():List<ProjectTemplateResponse> = templates.values.toList()

    fun updateGoal(accountId:String,projectId:String,goalId:String,req:UpdateGoalRequest):GoalResponse=db.tx{c->
        owned(c,"project",accountId,projectId)
        val current=goal(c,accountId,projectId,goalId)
        if(current.revision!=req.expectedRevision)throw conflict("Goal revision conflict")
        val title=req.title?.trim()?.also{if(it.isEmpty()||it.length>200)throw validation("Invalid goal title")} ?: current.title
        val desc=req.description?.take(4000) ?: current.description
        val priority=req.priority?.coerceIn(-100,100) ?: current.priority
        val progress=req.progress?.also{if(it !in 0.0..1.0)throw validation("Goal progress must be 0..1")}
        val target=req.targetDate?.let{runCatching{OffsetDateTime.parse(it)}.getOrElse{throw validation("targetDate must be ISO-8601 timestamp")}}
        c.prepareStatement("UPDATE goal SET title=?,description=?,priority=?,target_date=COALESCE(?,target_date),progress=COALESCE(?,progress),updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND project_id=?::uuid AND revision=? AND deleted_at IS NULL RETURNING *").use{ps->
            ps.setString(1,title);ps.setString(2,desc);ps.setInt(3,priority);ps.setObject(4,target);ps.setObject(5,progress);ps.setString(6,goalId);ps.setString(7,accountId);ps.setString(8,projectId);ps.setLong(9,req.expectedRevision)
            ps.executeQuery().use{rs->if(!rs.next())throw conflict("Goal update conflict");mapGoal(rs)}
        }
    }
    fun deleteGoal(accountId:String,projectId:String,goalId:String,expectedRevision:Long){db.tx{c->owned(c,"project",accountId,projectId);val n=c.prepareStatement("UPDATE goal SET deleted_at=now(),updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND project_id=?::uuid AND revision=? AND deleted_at IS NULL").use{ps->ps.setString(1,goalId);ps.setString(2,accountId);ps.setString(3,projectId);ps.setLong(4,expectedRevision);ps.executeUpdate()};if(n!=1)throw conflict("Goal delete conflict")}}

    fun searchNotes(accountId:String,query:String,projectId:String?,limit:Int):List<NoteResponse> = db.tx{c->
        if(query.isBlank())return@tx emptyList();projectId?.let{owned(c,"project",accountId,it)}
        val sql="SELECT * FROM note WHERE account_id=?::uuid AND deleted_at IS NULL AND archived=false"+(if(projectId!=null)" AND project_id=?::uuid" else "")+" AND (title ILIKE ? OR body ILIKE ?) ORDER BY pinned DESC,updated_at DESC LIMIT ?"
        c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,accountId);if(projectId!=null)ps.setString(i++,projectId);val like="%${query.trim().take(120).replace("%","\\%").replace("_","\\_")}%";ps.setString(i++,like);ps.setString(i++,like);ps.setInt(i,limit.coerceIn(1,100));ps.executeQuery().use{rs->buildList{while(rs.next())add(mapNote(rs))}}}
    }
    fun deleteNote(accountId:String,id:String,expectedRevision:Long){db.tx{c->val n=c.prepareStatement("UPDATE note SET deleted_at=now(),updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.setLong(3,expectedRevision);ps.executeUpdate()};if(n!=1)throw conflict("Note delete conflict")}}

    fun updateCard(accountId:String,id:String,req:UpdateCardRequest):CardResponse=db.tx{c->
        val row=c.prepareStatement("SELECT f.*,s.interval_days,s.repetitions,s.lapses,s.due_at FROM flashcard f JOIN flashcard_schedule s ON s.card_id=f.id WHERE f.id=?::uuid AND f.account_id=?::uuid AND f.deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{rs->if(!rs.next())throw notFound("CARD_NOT_FOUND","Card not found");mapCard(rs)}}
        if(row.revision!=req.expectedRevision)throw conflict("Flashcard revision conflict")
        val front=req.front?.trim()?.also{if(it.isEmpty())throw validation("Card front required")}?:row.front
        val back=req.back?.trim()?.also{if(it.isEmpty())throw validation("Card back required")}?:row.back
        c.prepareStatement("UPDATE flashcard SET front=?,back=?,explanation=?,favorite=COALESCE(?,favorite),suspended=COALESCE(?,suspended),updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=?").use{ps->ps.setString(1,front);ps.setString(2,back);ps.setString(3,req.explanation?:row.explanation);ps.setObject(4,req.favorite);ps.setObject(5,req.suspended);ps.setString(6,id);ps.setString(7,accountId);ps.setLong(8,req.expectedRevision);if(ps.executeUpdate()!=1)throw conflict("Flashcard update conflict")}
        card(c,accountId,id)
    }
    fun resetCardSchedule(accountId:String,id:String):CardResponse=db.tx{c->card(c,accountId,id);c.prepareStatement("UPDATE flashcard_schedule SET interval_days=0,ease=2.5,repetitions=0,lapses=0,due_at=now(),last_reviewed_at=NULL,revision=revision+1 WHERE card_id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeUpdate()};card(c,accountId,id)}
    fun deckStats(accountId:String,deckId:String):DeckStatsResponse=db.tx{c->owned(c,"flashcard_deck",accountId,deckId);c.prepareStatement("SELECT count(*) total,count(*) FILTER(WHERE s.due_at<=now() AND f.suspended=false) due,count(*) FILTER(WHERE f.suspended) suspended,count(*) FILTER(WHERE s.last_reviewed_at IS NOT NULL) reviewed FROM flashcard f JOIN flashcard_schedule s ON s.card_id=f.id WHERE f.account_id=?::uuid AND f.deck_id=?::uuid AND f.deleted_at IS NULL").use{ps->ps.setString(1,accountId);ps.setString(2,deckId);ps.executeQuery().use{rs->rs.next();DeckStatsResponse(deckId,rs.getInt("total"),rs.getInt("due"),rs.getInt("suspended"),rs.getInt("reviewed"))}}}

    fun practiceFromMistake(accountId:String,mistakeId:String,req:MistakePracticeRequest):PracticeResponse=db.tx{c->
        if(req.idempotencyKey.length !in 8..180)throw validation("Invalid idempotency key")
        val m=mistake(c,accountId,mistakeId)
        val existing=c.prepareStatement("SELECT object_id FROM activity_event WHERE account_id=?::uuid AND idempotency_key=? LIMIT 1").use{ps->ps.setString(1,accountId);ps.setString(2,req.idempotencyKey);ps.executeQuery().use{rs->if(rs.next())rs.getString(1)else null}}
        if(existing!=null)return@tx practice(c,accountId,existing)
        val p=c.prepareStatement("INSERT INTO practice_session(account_id,project_id,focus_topic,target_mistake_id,state) VALUES (?::uuid,?::uuid,?,?::uuid,'IN_PROGRESS') RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,m.projectId);ps.setString(3,m.topic);ps.setString(4,mistakeId);ps.executeQuery().use{rs->rs.next();mapPractice(rs)}}
        insertActivity(c,accountId,"PRACTICE_STARTED_FROM_MISTAKE",m.projectId,p.id,req.idempotencyKey,true);p
    }
    fun flashcardFromMistake(accountId:String,mistakeId:String,req:MistakeFlashcardRequest):CardResponse=db.tx{c->
        if(req.idempotencyKey.length !in 8..180)throw validation("Invalid idempotency key");val m=mistake(c,accountId,mistakeId)
        val existing=c.prepareStatement("SELECT object_id FROM activity_event WHERE account_id=?::uuid AND idempotency_key=? LIMIT 1").use{ps->ps.setString(1,accountId);ps.setString(2,req.idempotencyKey);ps.executeQuery().use{rs->if(rs.next())rs.getString(1)else null}}
        if(existing!=null)return@tx card(c,accountId,existing)
        val deckId=req.deckId?.also{owned(c,"flashcard_deck",accountId,it)} ?: c.prepareStatement("INSERT INTO flashcard_deck(account_id,project_id,scope,title) VALUES (?::uuid,?::uuid,'PROJECT',?) RETURNING id").use{ps->ps.setString(1,accountId);ps.setString(2,m.projectId);ps.setString(3,req.deckTitle.trim().ifBlank{"Mistake Review"}.take(240));ps.executeQuery().use{rs->rs.next();rs.uuid("id")}}
        val cardId=c.prepareStatement("INSERT INTO flashcard(account_id,deck_id,project_id,front,back,explanation) VALUES (?::uuid,?::uuid,?::uuid,?,?,?) RETURNING id").use{ps->ps.setString(1,accountId);ps.setString(2,deckId);ps.setString(3,m.projectId);ps.setString(4,m.prompt);ps.setString(5,m.expectedAnswer?:"Review expected answer");ps.setString(6,"Created from mistake ${m.id}");ps.executeQuery().use{rs->rs.next();rs.uuid("id")}}
        c.prepareStatement("INSERT INTO flashcard_schedule(card_id,account_id) VALUES (?::uuid,?::uuid)").use{ps->ps.setString(1,cardId);ps.setString(2,accountId);ps.executeUpdate()};insertActivity(c,accountId,"FLASHCARD_CREATED_FROM_MISTAKE",m.projectId,cardId,req.idempotencyKey,true);card(c,accountId,cardId)
    }

    fun createCollection(accountId:String,req:SourceCollectionCreateRequest):SourceCollectionResponse=db.tx{c->req.parentId?.let{owned(c,"source_collection",accountId,it)};val title=req.title.trim();if(title.isEmpty()||title.length>180)throw validation("Invalid collection title");c.prepareStatement("INSERT INTO source_collection(account_id,parent_id,title) VALUES (?::uuid,?::uuid,?) RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,req.parentId);ps.setString(3,title);ps.executeQuery().use{rs->rs.next();mapCollection(rs)}}}
    fun listCollections(accountId:String):List<SourceCollectionResponse> = db.tx{c->c.prepareStatement("SELECT * FROM source_collection WHERE account_id=?::uuid AND deleted_at IS NULL ORDER BY title,id").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->buildList{while(rs.next())add(mapCollection(rs))}}}}
    fun annotate(accountId:String,sourceId:String,req:SourceAnnotationCreateRequest):SourceAnnotationResponse=db.tx{c->owned(c,"source",accountId,sourceId);req.chunkId?.let{ownedChunk(c,accountId,sourceId,it)};if(req.locatorJson.length>20_000)throw validation("Annotation locator too large");c.prepareStatement("INSERT INTO annotation(account_id,source_id,source_version,chunk_id,annotation_type,body,locator) VALUES (?::uuid,?::uuid,?,?::uuid,?,?,?::jsonb) RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,sourceId);ps.setLong(3,req.sourceVersion);ps.setString(4,req.chunkId);ps.setString(5,req.type.take(40));ps.setString(6,req.body?.take(50_000));ps.setString(7,req.locatorJson);ps.executeQuery().use{rs->rs.next();mapAnnotation(rs)}}}
    fun annotations(accountId:String,sourceId:String):List<SourceAnnotationResponse> = db.tx{c->owned(c,"source",accountId,sourceId);c.prepareStatement("SELECT * FROM annotation WHERE account_id=?::uuid AND source_id=?::uuid AND deleted_at IS NULL ORDER BY created_at").use{ps->ps.setString(1,accountId);ps.setString(2,sourceId);ps.executeQuery().use{rs->buildList{while(rs.next())add(mapAnnotation(rs))}}}}

    fun noteFromMessage(accountId:String,conversationId:String,messageId:String,req:ChatArtifactRequest):NoteResponse=db.tx{c->
        if(req.idempotencyKey.length !in 8..180)throw validation("Invalid idempotency key");owned(c,"conversation",accountId,conversationId)
        val msg=c.prepareStatement("SELECT m.content,c.project_id FROM conversation_message m JOIN conversation c ON c.id=m.conversation_id WHERE m.id=?::uuid AND m.account_id=?::uuid AND m.conversation_id=?::uuid").use{ps->ps.setString(1,messageId);ps.setString(2,accountId);ps.setString(3,conversationId);ps.executeQuery().use{rs->if(!rs.next())throw notFound("MESSAGE_NOT_FOUND","Message not found");rs.getString(1) to rs.uuidOrNull("project_id")}}
        val existing=c.prepareStatement("SELECT object_id FROM activity_event WHERE account_id=?::uuid AND idempotency_key=? LIMIT 1").use{ps->ps.setString(1,accountId);ps.setString(2,req.idempotencyKey);ps.executeQuery().use{rs->if(rs.next())rs.getString(1)else null}}
        if(existing!=null)return@tx note(c,accountId,existing)
        val n=c.prepareStatement("INSERT INTO note(account_id,project_id,conversation_id,title,body) VALUES (?::uuid,?::uuid,?::uuid,?,?) RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,msg.second);ps.setString(3,conversationId);ps.setString(4,req.title?.trim()?.takeIf{it.isNotEmpty()}?.take(240)?:"Chat answer");ps.setString(5,msg.first);ps.executeQuery().use{rs->rs.next();mapNote(rs)}}
        insertActivity(c,accountId,"NOTE_CREATED",n.projectId,n.id,req.idempotencyKey,true);n
    }

    private fun goal(c:Connection,a:String,p:String,id:String)=c.prepareStatement("SELECT * FROM goal WHERE id=?::uuid AND account_id=?::uuid AND project_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.setString(3,p);ps.executeQuery().use{rs->if(!rs.next())throw notFound("GOAL_NOT_FOUND","Goal not found");mapGoal(rs)}}
    private fun mistake(c:Connection,a:String,id:String)=c.prepareStatement("SELECT * FROM mistake WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{rs->if(!rs.next())throw notFound("MISTAKE_NOT_FOUND","Mistake not found");mapMistake(rs)}}
    private fun practice(c:Connection,a:String,id:String)=c.prepareStatement("SELECT * FROM practice_session WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{rs->if(!rs.next())throw notFound("PRACTICE_NOT_FOUND","Practice not found");mapPractice(rs)}}
    private fun card(c:Connection,a:String,id:String)=c.prepareStatement("SELECT f.*,s.interval_days,s.repetitions,s.lapses,s.due_at FROM flashcard f JOIN flashcard_schedule s ON s.card_id=f.id WHERE f.id=?::uuid AND f.account_id=?::uuid AND f.deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{rs->if(!rs.next())throw notFound("CARD_NOT_FOUND","Card not found");mapCard(rs)}}
    private fun note(c:Connection,a:String,id:String)=c.prepareStatement("SELECT * FROM note WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{rs->if(!rs.next())throw notFound("NOTE_NOT_FOUND","Note not found");mapNote(rs)}}
    private fun owned(c:Connection,t:String,a:String,id:String){require(t in setOf("project","source","conversation","flashcard_deck","source_collection"));c.prepareStatement("SELECT 1 FROM $t WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw notFound("NOT_FOUND","Object not found")}}}
    private fun ownedChunk(c:Connection,a:String,s:String,id:String){c.prepareStatement("SELECT 1 FROM source_chunk WHERE id=?::uuid AND account_id=?::uuid AND source_id=?::uuid").use{ps->ps.setString(1,id);ps.setString(2,a);ps.setString(3,s);ps.executeQuery().use{if(!it.next())throw notFound("CHUNK_NOT_FOUND","Source chunk not found")}}}
    private fun mapGoal(rs:ResultSet)=GoalResponse(rs.uuid("id"),rs.uuid("project_id"),rs.getString("title"),rs.getString("description"),rs.getInt("priority"),rs.getString("status"),rs.getLong("revision"),rs.getObject("completed_at",OffsetDateTime::class.java)?.toInstant()?.toString())
    private fun mapNote(rs:ResultSet)=NoteResponse(rs.uuid("id"),rs.uuidOrNull("project_id"),rs.uuidOrNull("source_id"),rs.uuidOrNull("conversation_id"),rs.getString("title"),rs.getString("body"),rs.getBoolean("pinned"),rs.getBoolean("archived"),rs.getLong("revision"),rs.instant("updated_at"))
    private fun mapCard(rs:ResultSet)=CardResponse(rs.uuid("id"),rs.uuid("deck_id"),rs.uuidOrNull("project_id"),rs.getString("front"),rs.getString("back"),rs.getString("explanation"),rs.instant("due_at"),rs.getInt("interval_days"),rs.getInt("repetitions"),rs.getInt("lapses"),rs.getLong("revision"))
    private fun mapMistake(rs:ResultSet)=MistakeResponse(rs.uuid("id"),rs.uuidOrNull("project_id"),rs.uuidOrNull("source_id"),rs.getString("topic"),rs.getString("prompt"),rs.getString("user_answer"),rs.getString("expected_answer"),rs.getInt("occurrence_count"),rs.getString("status"),rs.getLong("revision"))
    private fun mapPractice(rs:ResultSet)=PracticeResponse(rs.uuid("id"),rs.uuidOrNull("project_id"),rs.getString("focus_topic"),rs.uuidOrNull("target_mistake_id"),rs.getString("state"),rs.getLong("revision"))
    private fun mapCollection(rs:ResultSet)=SourceCollectionResponse(rs.uuid("id"),rs.uuidOrNull("parent_id"),rs.getString("title"),rs.getLong("revision"))
    private fun mapAnnotation(rs:ResultSet)=SourceAnnotationResponse(rs.uuid("id"),rs.uuid("source_id"),rs.getLong("source_version"),rs.uuidOrNull("chunk_id"),rs.getString("annotation_type"),rs.getString("body"),rs.getString("locator"),rs.getLong("revision"))
    private fun conflict(msg:String)=DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,msg))
    private fun notFound(code:String,msg:String)=DomainException(DomainError(code,ErrorCategory.NOT_FOUND,msg))
}
