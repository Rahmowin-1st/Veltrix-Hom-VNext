package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.server.foundation.AiOperation
import com.veltrix.hom.vnext.server.foundation.AiProviderRequest
import java.sql.Connection
import java.util.UUID

/** Controlled AI artifact boundary: model output is schema-requested, validated, then persisted as DRAFT. */
class GeneratedArtifactService(private val db:Database, private val ai:AiExecutionService, private val environment:String) {
    fun create(accountId:String,req:GeneratedArtifactRequest):GeneratedArtifactResponse=db.tx{c->
        val type=req.type.uppercase();if(type !in setOf("QUIZ","TEST","FLASHCARDS","PRACTICE","GOAL_SUGGESTION"))throw validation("Unsupported artifact type")
        req.projectId?.let{owned(c,"project",accountId,it)};req.conversationId?.let{owned(c,"conversation",accountId,it)};req.sourceIds.forEach{owned(c,"source",accountId,it)}
        val existing=c.prepareStatement("SELECT * FROM generated_artifact WHERE account_id=?::uuid AND provenance->>'idempotencyKey'=? LIMIT 1").use{ps->ps.setString(1,accountId);ps.setString(2,req.idempotencyKey);ps.executeQuery().use{rs->if(rs.next())map(rs)else null}}
        if(existing!=null)return@tx existing
        val operation=when(type){"QUIZ"->AiOperation.QUIZ_DRAFT;"TEST"->AiOperation.TEST_DRAFT;"FLASHCARDS"->AiOperation.FLASHCARD_DRAFT;"PRACTICE"->AiOperation.PRACTICE_DRAFT;else->AiOperation.PROJECT_PLANNING}
        val schema="""{"type":"object","properties":{"title":{"type":"string"},"items":{"type":"array","items":{"type":"object","additionalProperties":true}}},"required":["title","items"],"additionalProperties":false}"""
        val providerRaw=ai.structured("artifact:${req.idempotencyKey}",AiProviderRequest(operation,req.prompt.take(40_000)),"veltrix_${type.lowercase()}_draft",schema)
        val payload=normalizePayload(type,req,providerRaw)
        validatePayload(type,payload)
        val title=(req.title?.trim()?.takeIf{it.isNotEmpty()} ?: "${type.lowercase().replaceFirstChar{it.uppercase()}} draft").take(240)
        val sources=jsonArray(req.sourceIds)
        val provenance="{\"idempotencyKey\":\"${escapeJson(req.idempotencyKey)}\",\"providerOutputHash\":\"${sha256(providerRaw)}\",\"sourceGrounded\":${req.sourceIds.isNotEmpty()}}"
        c.prepareStatement("INSERT INTO generated_artifact(account_id,project_id,conversation_id,source_ids,artifact_type,state,title,payload,provenance,created_by) VALUES (?::uuid,?::uuid,?::uuid,?::jsonb,?,'DRAFT',?,?::jsonb,?::jsonb,'AI') RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,req.projectId);ps.setString(3,req.conversationId);ps.setString(4,sources);ps.setString(5,type);ps.setString(6,title);ps.setString(7,payload);ps.setString(8,provenance);ps.executeQuery().use{rs->rs.next();map(rs)}}
    }
    fun get(accountId:String,id:String):GeneratedArtifactResponse=db.tx{c->get(c,accountId,id)}
    fun validateReady(accountId:String,id:String,req:ValidateArtifactRequest):GeneratedArtifactResponse=db.tx{c->val current=get(c,accountId,id);if(current.revision!=req.expectedRevision)throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Artifact revision conflict"));validatePayload(current.type,current.payloadJson);c.prepareStatement("UPDATE generated_artifact SET state='READY',updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? RETURNING *").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.setLong(3,req.expectedRevision);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Artifact revision conflict"));map(rs)}}}
    fun list(accountId:String,projectId:String?,limit:Int=100):List<GeneratedArtifactResponse> =db.tx{c->val sql="SELECT * FROM generated_artifact WHERE account_id=?::uuid"+(if(projectId!=null)" AND project_id=?::uuid" else "")+" ORDER BY created_at DESC LIMIT ?";c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,accountId);if(projectId!=null)ps.setString(i++,projectId);ps.setInt(i,limit.coerceIn(1,200));ps.executeQuery().use{rs->buildList{while(rs.next())add(map(rs))}}}}
    private fun normalizePayload(type:String,req:GeneratedArtifactRequest,raw:String):String {
        // Deterministic test provider proves orchestration but is deliberately not a real content model.
        if(environment=="test" && raw.contains("\"testOnly\":true")){
            val prompt=escapeJson(req.prompt.take(1_000));return when(type){
                "FLASHCARDS"->"{\"title\":\"Flashcards\",\"items\":[{\"front\":\"Key concept\",\"back\":\"$prompt\"}]}"
                "GOAL_SUGGESTION"->"{\"title\":\"Goal suggestions\",\"items\":[{\"title\":\"Review the requested topic\"}]}"
                else->"{\"title\":\"$type draft\",\"items\":[{\"prompt\":\"$prompt\",\"type\":\"SHORT_ANSWER\",\"expectedAnswers\":[\"reviewed\"]}]}"
            }
        }
        return raw.trim()
    }
    private fun validatePayload(type:String,json:String){if(json.length !in 2..1_000_000 || !json.trim().startsWith("{") || !json.trim().endsWith("}"))throw DomainException(DomainError("AI_OUTPUT_INVALID",ErrorCategory.AI_PROVIDER,"Generated artifact JSON is invalid"));if(!json.contains("\"title\"")||!json.contains("\"items\""))throw DomainException(DomainError("AI_OUTPUT_INVALID",ErrorCategory.AI_PROVIDER,"Generated artifact is missing required fields"));if(type=="FLASHCARDS"&&!json.contains("\"front\""))throw DomainException(DomainError("AI_OUTPUT_INVALID",ErrorCategory.AI_PROVIDER,"Flashcard draft missing card fields"))}
    private fun get(c:Connection,a:String,id:String)=c.prepareStatement("SELECT * FROM generated_artifact WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("ARTIFACT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Generated artifact not found"));map(rs)}}
    private fun owned(c:Connection,t:String,a:String,id:String){require(t in setOf("project","conversation","source"));val deleted=if(t=="conversation"||t=="project"||t=="source")" AND deleted_at IS NULL" else "";c.prepareStatement("SELECT 1 FROM $t WHERE id=?::uuid AND account_id=?::uuid$deleted").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("NOT_FOUND",ErrorCategory.NOT_FOUND,"Linked object not found"))}}}
    private fun map(rs:java.sql.ResultSet)=GeneratedArtifactResponse(rs.uuid("id"),rs.getString("artifact_type"),rs.getString("state"),rs.getString("title"),rs.uuidOrNull("project_id"),rs.uuidOrNull("conversation_id"),parseJsonStringArray(rs.getString("source_ids")),rs.getString("payload"),rs.getLong("revision"))
}
