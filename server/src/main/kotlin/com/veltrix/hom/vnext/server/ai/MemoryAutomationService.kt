package com.veltrix.hom.vnext.server.ai

import com.veltrix.hom.vnext.core.*
import com.veltrix.hom.vnext.server.Database
import java.io.Closeable
import java.sql.Connection
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Automatic Memory pipeline. It consumes durable post-response jobs so streaming completion
 * never waits for memory synthesis. Explicit user facts are deterministic/high-confidence;
 * inferred learning signals are lower-confidence and always evidence-backed.
 */
class MemoryAutomationService(private val db: Database, workerEnabled: Boolean = true) : Closeable {
    private val worker = Executors.newSingleThreadScheduledExecutor { r -> Thread(r,"veltrix-memory-worker").apply { isDaemon=true } }
    init { if (workerEnabled) worker.scheduleWithFixedDelay({ runCatching { processOne() } }, 1, 2, TimeUnit.SECONDS) }

    fun enqueuePostChat(accountId:String,conversationId:String,userMessageId:String,assistantMessageId:String) {
        db.tx { c ->
            c.prepareStatement("""INSERT INTO post_response_job(account_id,conversation_id,user_message_id,assistant_message_id,job_type)
                VALUES (?::uuid,?::uuid,?::uuid,?::uuid,'MEMORY_AND_SIGNALS') ON CONFLICT(assistant_message_id,job_type) DO NOTHING""").use { ps ->
                ps.setString(1,accountId);ps.setString(2,conversationId);ps.setString(3,userMessageId);ps.setString(4,assistantMessageId);ps.executeUpdate()
            }
        }
    }

    fun processPendingNow(max:Int=50):Int { var n=0; repeat(max.coerceIn(1,200)) { if(!processOne()) return n; n++ }; return n }

    fun captureLearningSignal(accountId:String, projectId:String?, topic:String, kind:String, value:Double, evidenceId:String) {
        if (!value.isFinite()) return
        val statement = when {
            kind.contains("ACCURACY",true) && value < .65 -> "Needs more practice with $topic"
            kind.contains("HINT",true) && value > .5 -> "Often benefits from hints in $topic"
            kind.contains("FLASHCARD",true) && value < .5 -> "Flashcard recall is weak in $topic"
            kind.contains("PRACTICE",true) && value > .8 -> "Practice performance is improving in $topic"
            else -> return
        }
        val scope = if(projectId==null) MemoryScope.ACCOUNT else MemoryScope.PROJECT
        createAndProcessCandidate(accountId,scope,projectId,MemoryCategory.LEARNING,statement,MemoryOrigin.SYSTEM_DERIVED,0.66,"LEARNING_SIGNAL",listOf(evidenceId))
    }

    private fun processOne():Boolean = processPostChat() || processLearningSignal() || processMeaningfulActivity()

    private fun processPostChat():Boolean {
        val job = db.tx { c ->
            c.prepareStatement("SELECT id::text,account_id::text,conversation_id::text,user_message_id::text,assistant_message_id::text FROM post_response_job WHERE status='PENDING' AND available_at<=now() ORDER BY available_at,id LIMIT 1 FOR UPDATE SKIP LOCKED").use { ps ->
                ps.executeQuery().use { rs -> if(!rs.next()) null else Job(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5)) }
            }?.also { j -> c.prepareStatement("UPDATE post_response_job SET status='RUNNING',attempts=attempts+1,updated_at=now() WHERE id=?::uuid").use { ps -> ps.setString(1,j.id);ps.executeUpdate() } }
        } ?: return false
        try {
            val payload = db.tx { c ->
                val userText=c.prepareStatement("SELECT content FROM conversation_message WHERE id=?::uuid AND account_id=?::uuid AND role='USER'").use{ps->ps.setString(1,job.userMessageId);ps.setString(2,job.accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("MESSAGE_NOT_FOUND",ErrorCategory.NOT_FOUND,"User message missing"));rs.getString(1)}}
                val projectId=c.prepareStatement("SELECT project_id::text FROM conversation WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,job.conversationId);ps.setString(2,job.accountId);ps.executeQuery().use{rs->if(rs.next())rs.getString(1) else null}}
                userText to projectId
            }
            deriveExplicit(payload.first,payload.second).forEach { candidate ->
                createAndProcessCandidate(job.accountId,candidate.scope,candidate.scopeId,candidate.category,candidate.statement,candidate.origin,candidate.confidence,"CHAT_EXPLICIT",listOf(job.userMessageId))
            }
            db.tx { c -> c.prepareStatement("UPDATE post_response_job SET status='SUCCEEDED',updated_at=now() WHERE id=?::uuid").use{ps->ps.setString(1,job.id);ps.executeUpdate()} }
        } catch (e:Throwable) {
            db.tx { c -> c.prepareStatement("UPDATE post_response_job SET status=CASE WHEN attempts<3 THEN 'PENDING' ELSE 'FAILED' END,available_at=now()+(attempts*interval '10 seconds'),last_error_code=?,updated_at=now() WHERE id=?::uuid").use{ps->ps.setString(1,(e as? DomainException)?.error?.code ?: "INTERNAL");ps.setString(2,job.id);ps.executeUpdate()} }
        }
        return true
    }


    /** Consume learning evidence emitted by Quiz/Test/Practice/Flashcards without requiring manual Memory CRUD. */
    private fun processLearningSignal():Boolean {
        val signal = db.tx { c ->
            c.prepareStatement("""SELECT ls.id::text,ls.account_id::text,ls.project_id::text,ls.topic,ls.kind,ls.signal_value,ls.confidence
                FROM learning_signal ls
                WHERE NOT EXISTS (
                  SELECT 1 FROM memory_candidate mc
                  WHERE mc.account_id=ls.account_id AND mc.evidence_type='LEARNING_SIGNAL'
                    AND mc.evidence_ids @> jsonb_build_array(ls.id::text)
                )
                ORDER BY ls.observed_at,ls.id LIMIT 1""").use { ps -> ps.executeQuery().use { rs ->
                if(!rs.next()) null else LearningSignalJob(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getDouble(6),rs.getDouble(7))
            }}
        } ?: return false
        val normalizedKind=signal.kind.uppercase(Locale.ROOT)
        val statement = when {
            normalizedKind.contains("WEAK") || (normalizedKind.contains("ACCURACY") && signal.value < .65) -> "Needs more practice with ${signal.topic}"
            normalizedKind.contains("HINT") && signal.value > .5 -> "Often benefits from hints in ${signal.topic}"
            normalizedKind.contains("FLASHCARD") && signal.value < .5 -> "Flashcard recall is weak in ${signal.topic}"
            normalizedKind.contains("CORRECT") || normalizedKind.contains("IMPROV") || signal.value >= .8 -> "Performance is improving in ${signal.topic}"
            else -> "Learning evidence observed for ${signal.topic}"
        }
        val scope=if(signal.projectId==null)MemoryScope.ACCOUNT else MemoryScope.PROJECT
        val confidence=(signal.confidence.coerceIn(.2,.95)*0.8).coerceIn(.2,.76)
        createAndProcessCandidate(signal.accountId,scope,signal.projectId,MemoryCategory.LEARNING,statement,MemoryOrigin.SYSTEM_DERIVED,confidence,"LEARNING_SIGNAL",listOf(signal.id))
        return true
    }

    /** Meaningful project/source/goal activity becomes low-confidence evidence, never one Memory row per click. */
    private fun processMeaningfulActivity():Boolean {
        val event = db.tx { c ->
            c.prepareStatement("""SELECT ae.event_id::text,ae.account_id::text,ae.project_id::text,ae.event_type,coalesce(ae.object_id,'')
                FROM activity_event ae
                WHERE ae.meaningful=true AND ae.event_type IN ('PROJECT_CREATED','GOAL_COMPLETED','SOURCE_ADDED','TEST_COMPLETED','QUIZ_COMPLETED','PRACTICE_COMPLETED','FLASHCARD_REVIEW_COMPLETED','MISTAKE_RESOLVED','NOTE_CREATED','MEANINGFUL_CHAT_SESSION')
                  AND NOT EXISTS (SELECT 1 FROM memory_candidate mc WHERE mc.account_id=ae.account_id AND mc.evidence_type='ACTIVITY' AND mc.evidence_ids @> jsonb_build_array(ae.event_id::text))
                ORDER BY ae.occurred_at,ae.event_id LIMIT 1""").use { ps -> ps.executeQuery().use { rs ->
                if(!rs.next()) null else ActivityJob(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5))
            }}
        } ?: return false
        val statement=when(event.type){
            "PROJECT_CREATED" -> "Active focus includes a recently created Project"
            "GOAL_COMPLETED" -> "Completed a Project goal"
            "SOURCE_ADDED" -> "Uses saved Sources while learning"
            "TEST_COMPLETED","QUIZ_COMPLETED" -> "Regularly completes assessments"
            "PRACTICE_COMPLETED" -> "Uses targeted Practice sessions"
            "FLASHCARD_REVIEW_COMPLETED" -> "Uses spaced-repetition Flashcards"
            "MISTAKE_RESOLVED" -> "Resolves tracked learning mistakes"
            "NOTE_CREATED" -> "Captures learning notes"
            else -> "Has meaningful Chat learning activity"
        }
        val scope=if(event.projectId==null)MemoryScope.ACCOUNT else MemoryScope.PROJECT
        createAndProcessCandidate(event.accountId,scope,event.projectId,MemoryCategory.LEARNING,statement,MemoryOrigin.OBSERVED_BEHAVIOR,.42,"ACTIVITY",listOf(event.id))
        return true
    }

    private data class Candidate(val scope:MemoryScope,val scopeId:String?,val category:MemoryCategory,val statement:String,val origin:MemoryOrigin,val confidence:Double)
    private fun deriveExplicit(text:String, projectId:String?):List<Candidate> {
        val t=text.trim().replace(Regex("\\s+")," ")
        if(t.length !in 3..5_000) return emptyList()
        val out=mutableListOf<Candidate>()
        fun add(category:MemoryCategory,statement:String,projectScoped:Boolean=false,confidence:Double=.95){
            val clean=statement.trim().trimEnd('.','!','?').take(600);if(clean.length<3)return
            out += Candidate(if(projectScoped&&projectId!=null)MemoryScope.PROJECT else MemoryScope.ACCOUNT,if(projectScoped)projectId else null,category,clean,MemoryOrigin.EXPLICIT_USER,confidence)
        }
        val patterns=listOf(
            Triple(Regex("(?i)\\b(?:i prefer|please prefer)\\s+(.{3,300})"),MemoryCategory.PREFERENCE,false),
            Triple(Regex("(?i)\\b(?:i like|i enjoy)\\s+(.{3,300})"),MemoryCategory.INTEREST,false),
            Triple(Regex("(?i)\\b(?:my goal is|i want to learn|i am trying to)\\s+(.{3,300})"),MemoryCategory.GOAL,true),
            Triple(Regex("(?i)\\b(?:i struggle with|i find .* difficult|i am weak at)\\s+(.{3,300})"),MemoryCategory.WEAKNESS,true),
            Triple(Regex("(?i)\\b(?:answer me|explain)\\s+(.{3,220})"),MemoryCategory.FORMAT_PREFERENCE,false),
        )
        patterns.forEach { (r,c,p) -> r.find(t)?.groupValues?.getOrNull(1)?.let{add(c,it,p)} }
        return out.distinctBy{canonical(it.statement)}.take(4)
    }

    private fun createAndProcessCandidate(accountId:String,scope:MemoryScope,scopeId:String?,category:MemoryCategory,statement:String,origin:MemoryOrigin,confidence:Double,evidenceType:String,evidenceIds:List<String>) {
        val canonical=canonical(statement);if(canonical.isBlank())return
        db.tx { c ->
            if(scope==MemoryScope.PROJECT){ if(scopeId==null) return@tx; requireOwned(c,"project",accountId,scopeId) }
            val evidenceJson=evidenceIds.joinToString(prefix="[",postfix="]"){"\"${it.replace("\"","_")}\""}
            val candidateId=c.prepareStatement("""INSERT INTO memory_candidate(account_id,scope,scope_id,category,statement,canonical_statement,origin,confidence,evidence_type,evidence_ids)
                VALUES (?::uuid,?,?::uuid,?,?,?,?,?,?,?::jsonb)
                ON CONFLICT(account_id,scope,scope_id,category,canonical_statement,evidence_type) DO UPDATE SET confidence=greatest(memory_candidate.confidence,excluded.confidence),evidence_ids=excluded.evidence_ids,observed_at=now(),status='PENDING'
                RETURNING id::text""").use{ps->ps.setString(1,accountId);ps.setString(2,scope.name);ps.setString(3,scopeId);ps.setString(4,category.name);ps.setString(5,statement);ps.setString(6,canonical);ps.setString(7,origin.name);ps.setDouble(8,confidence.coerceIn(.05,1.0));ps.setString(9,evidenceType);ps.setString(10,evidenceJson);ps.executeQuery().use{rs->rs.next();rs.getString(1)}}
            processCandidate(c,candidateId,accountId,scope,scopeId,category,statement,canonical,origin,confidence,evidenceType,evidenceIds)
        }
    }

    private fun processCandidate(c:Connection,candidateId:String,accountId:String,scope:MemoryScope,scopeId:String?,category:MemoryCategory,statement:String,canonical:String,origin:MemoryOrigin,confidence:Double,evidenceType:String,evidenceIds:List<String>) {
        val corrected=c.prepareStatement("SELECT id::text,canonical_statement FROM memory_item WHERE account_id=?::uuid AND scope=? AND scope_id IS NOT DISTINCT FROM ?::uuid AND category=? AND status='USER_CORRECTED' ORDER BY updated_at DESC LIMIT 1").use{ps->ps.setString(1,accountId);ps.setString(2,scope.name);ps.setString(3,scopeId);ps.setString(4,category.name);ps.executeQuery().use{rs->if(rs.next())rs.getString(1) to rs.getString(2) else null}}
        if(corrected!=null && origin!=MemoryOrigin.EXPLICIT_USER && canonical(corrected.second)!=canonical){ markCandidate(c,candidateId,"REJECTED");return }
        val existing=c.prepareStatement("SELECT id::text,status,confidence FROM memory_item WHERE account_id=?::uuid AND scope=? AND scope_id IS NOT DISTINCT FROM ?::uuid AND category=? AND lower(regexp_replace(trim(canonical_statement),'\\s+',' ','g'))=? AND status IN ('ACTIVE','UNCERTAIN','USER_CORRECTED') LIMIT 1").use{ps->ps.setString(1,accountId);ps.setString(2,scope.name);ps.setString(3,scopeId);ps.setString(4,category.name);ps.setString(5,canonical);ps.executeQuery().use{rs->if(rs.next())Triple(rs.getString(1),rs.getString(2),rs.getDouble(3))else null}}
        val memoryId = if(existing!=null){
            c.prepareStatement("UPDATE memory_item SET confidence=greatest(confidence,?),last_confirmed_at=now(),updated_at=now(),revision=revision+1 WHERE id=?::uuid RETURNING id::text").use{ps->ps.setDouble(1,confidence);ps.setString(2,existing.first);ps.executeQuery().use{rs->rs.next();rs.getString(1)}}
        } else {
            val status=if(confidence>=.7 || origin==MemoryOrigin.EXPLICIT_USER) "ACTIVE" else "UNCERTAIN"
            c.prepareStatement("INSERT INTO memory_item(account_id,scope,scope_id,category,canonical_statement,origin,confidence,status,last_confirmed_at) VALUES (?::uuid,?,?::uuid,?,?,?,?,?::memory_status,now()) RETURNING id::text").use{ps->ps.setString(1,accountId);ps.setString(2,scope.name);ps.setString(3,scopeId);ps.setString(4,category.name);ps.setString(5,statement.take(2_000));ps.setString(6,origin.name);ps.setDouble(7,confidence);ps.setString(8,status);ps.executeQuery().use{rs->rs.next();rs.getString(1)}}
        }
        evidenceIds.distinct().take(20).forEach { evidence -> c.prepareStatement("INSERT INTO memory_evidence(memory_id,account_id,kind,object_id) VALUES (?::uuid,?::uuid,?,?) ON CONFLICT DO NOTHING").use{ps->ps.setString(1,memoryId);ps.setString(2,accountId);ps.setString(3,evidenceType.take(80));ps.setString(4,evidence.take(200));ps.executeUpdate()} }
        markCandidate(c,candidateId,if(confidence>=.7)"ACCEPTED" else "UNCERTAIN")
        c.prepareStatement("INSERT INTO activity_event(account_id,event_type,project_id,object_id,idempotency_key,meaningful) VALUES (?::uuid,'MEMORY_UPDATED',?::uuid,?,?,false) ON CONFLICT(account_id,idempotency_key) DO NOTHING").use{ps->ps.setString(1,accountId);ps.setString(2,scopeId);ps.setString(3,memoryId);ps.setString(4,"memory-candidate:$candidateId");ps.executeUpdate()}
    }
    private fun markCandidate(c:Connection,id:String,status:String){c.prepareStatement("UPDATE memory_candidate SET status=?,processed_at=now() WHERE id=?::uuid").use{ps->ps.setString(1,status);ps.setString(2,id);ps.executeUpdate()}}
    private fun requireOwned(c:Connection,table:String,accountId:String,id:String){require(table=="project");c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}
    private fun canonical(s:String)=s.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+")," ").trim().replace(Regex("\\s+")," ").take(800)
    override fun close(){worker.shutdownNow()}
    private data class Job(val id:String,val accountId:String,val conversationId:String,val userMessageId:String,val assistantMessageId:String)
    private data class LearningSignalJob(val id:String,val accountId:String,val projectId:String?,val topic:String,val kind:String,val value:Double,val confidence:Double)
    private data class ActivityJob(val id:String,val accountId:String,val projectId:String?,val type:String,val objectId:String)
}
