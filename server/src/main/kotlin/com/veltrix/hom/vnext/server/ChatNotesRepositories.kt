package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.*
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

class ChatRepository(private val db: Database) {
    fun create(accountId: String, req: CreateConversationRequest): ConversationResponse = db.tx { c ->
        val scope = runCatching { ConversationScope.valueOf(req.scope) }.getOrElse { throw validation("Invalid conversation scope") }
        val mode = runCatching { LearningMode.valueOf(req.learningMode) }.getOrElse { throw validation("Invalid learning mode") }
        if (scope == ConversationScope.PROJECT) {
            val projectId = req.projectId ?: throw validation("PROJECT scope requires projectId")
            requireProject(c, accountId, projectId)
        } else if (req.projectId != null) requireProject(c, accountId, req.projectId)
        val title = req.title.trim().ifBlank { "New chat" }.take(180)
        c.prepareStatement("INSERT INTO conversation(account_id,project_id,scope,title,learning_mode,memory_enabled,project_memory_enabled) VALUES (?::uuid,?::uuid,?,?,?,?,?) RETURNING *").use { ps ->
            ps.setString(1, accountId); ps.setString(2, req.projectId); ps.setString(3, scope.name); ps.setString(4, title); ps.setString(5, mode.name); ps.setBoolean(6, req.memoryEnabled); ps.setBoolean(7, req.projectMemoryEnabled)
            ps.executeQuery().use { rs -> rs.next(); mapConversation(rs) }
        }
    }

    fun get(accountId: String, conversationId: String): ConversationResponse = db.tx { c -> requireConversation(c, accountId, conversationId) }

    fun update(accountId:String,conversationId:String,req:UpdateConversationRequest):ConversationResponse=db.tx{c->
        val current=requireConversation(c,accountId,conversationId)
        if(current.revision!=req.expectedRevision)throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Conversation revision conflict"))
        val mode=req.learningMode?.let{runCatching{LearningMode.valueOf(it)}.getOrElse{throw validation("Invalid learning mode")}}?.name ?: current.learningMode
        val title=req.title?.trim()?.also{if(it.isEmpty()||it.length>180)throw validation("Invalid conversation title")} ?: current.title
        c.prepareStatement("UPDATE conversation SET title=?,pinned=?,archived=?,learning_mode=?,memory_enabled=?,project_memory_enabled=?,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? RETURNING *").use{ps->
            ps.setString(1,title);ps.setBoolean(2,req.pinned?:current.pinned);ps.setBoolean(3,req.archived?:current.archived);ps.setString(4,mode);ps.setBoolean(5,req.memoryEnabled?:current.memoryEnabled);ps.setBoolean(6,req.projectMemoryEnabled?:current.projectMemoryEnabled);ps.setString(7,conversationId);ps.setString(8,accountId);ps.setLong(9,req.expectedRevision)
            ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Conversation revision conflict"));mapConversation(rs)}
        }
    }

    fun delete(accountId:String,conversationId:String,expectedRevision:Long){db.tx{c->
        requireConversation(c,accountId,conversationId)
        c.prepareStatement("UPDATE conversation SET deleted_at=now(),updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL").use{ps->ps.setString(1,conversationId);ps.setString(2,accountId);ps.setLong(3,expectedRevision);if(ps.executeUpdate()!=1)throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Conversation revision conflict"))}
    }}

    fun editUserMessageBranch(accountId:String,conversationId:String,messageId:String,req:EditMessageRequest):MessageResponse=db.tx{c->
        requireConversation(c,accountId,conversationId)
        val original=c.prepareStatement("SELECT parent_message_id,role FROM conversation_message WHERE id=?::uuid AND account_id=?::uuid AND conversation_id=?::uuid").use{ps->ps.setString(1,messageId);ps.setString(2,accountId);ps.setString(3,conversationId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("MESSAGE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Message not found"));rs.getObject(1)?.toString() to rs.getString(2)}}
        if(original.second!="USER")throw DomainException(DomainError("MESSAGE_EDIT_NOT_ALLOWED",ErrorCategory.CONFLICT,"Only user messages can be edited into a new branch"))
        val text=req.text.trim();if(text.isEmpty()||text.length>100_000)throw validation("Message must be 1..100000 chars")
        if(req.idempotencyKey.length !in 8..180)throw validation("Invalid idempotency key")
        val existing=c.prepareStatement("SELECT * FROM conversation_message WHERE account_id=?::uuid AND idempotency_key=?").use{ps->ps.setString(1,accountId);ps.setString(2,req.idempotencyKey);ps.executeQuery().use{rs->if(rs.next())mapMessage(rs)else null}}
        existing ?: c.prepareStatement("INSERT INTO conversation_message(account_id,conversation_id,parent_message_id,branch_key,role,state,content,idempotency_key,final_marker) VALUES (?::uuid,?::uuid,?::uuid,?,'USER','QUEUED',?,?,false) RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,conversationId);ps.setString(3,original.first);ps.setString(4,"edit:$messageId");ps.setString(5,text);ps.setString(6,req.idempotencyKey);ps.executeQuery().use{rs->rs.next();mapMessage(rs)}}
    }

    fun list(accountId: String, projectId: String? = null, limit: Int = 50, offset: Int = 0): List<ConversationResponse> = db.tx { c ->
        if (projectId != null) requireProject(c, accountId, projectId)
        val sql = buildString {
            append("SELECT * FROM conversation WHERE account_id=?::uuid AND deleted_at IS NULL")
            if (projectId != null) append(" AND project_id=?::uuid")
            append(" ORDER BY pinned DESC,updated_at DESC,id LIMIT ? OFFSET ?")
        }
        c.prepareStatement(sql).use { ps ->
            var i=1; ps.setString(i++, accountId); if (projectId != null) ps.setString(i++, projectId); ps.setInt(i++, limit.coerceIn(1,100)); ps.setInt(i, offset.coerceAtLeast(0))
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(mapConversation(rs)) } }
        }
    }

    fun messages(accountId: String, conversationId: String, limit: Int = 100, before: String? = null): List<MessageResponse> = db.tx { c ->
        requireConversation(c, accountId, conversationId)
        val sql = if (before == null)
            "SELECT * FROM conversation_message WHERE account_id=?::uuid AND conversation_id=?::uuid ORDER BY created_at DESC,id DESC LIMIT ?"
        else
            "SELECT * FROM conversation_message WHERE account_id=?::uuid AND conversation_id=?::uuid AND created_at < (SELECT created_at FROM conversation_message WHERE id=?::uuid AND account_id=?::uuid) ORDER BY created_at DESC,id DESC LIMIT ?"
        c.prepareStatement(sql).use { ps ->
            var i=1; ps.setString(i++,accountId); ps.setString(i++,conversationId); if(before!=null){ps.setString(i++,before);ps.setString(i++,accountId)};ps.setInt(i,limit.coerceIn(1,200))
            ps.executeQuery().use { rs -> buildList { while(rs.next()) add(mapMessage(rs)) }.reversed() }
        }
    }

    fun enqueueUserMessage(accountId: String, conversationId: String, req: SendMessageRequest): MessageResponse = db.tx { c ->
        requireConversation(c, accountId, conversationId)
        val text = req.text.trim(); if(text.isEmpty() || text.length > 100_000) throw validation("Message must be 1..100000 chars")
        if(req.idempotencyKey.length !in 8..180) throw validation("Invalid idempotency key")
        req.parentMessageId?.let { requireMessage(c, accountId, conversationId, it) }
        val existing = c.prepareStatement("SELECT * FROM conversation_message WHERE account_id=?::uuid AND idempotency_key=?").use { ps -> ps.setString(1,accountId);ps.setString(2,req.idempotencyKey);ps.executeQuery().use{rs->if(rs.next()) mapMessage(rs) else null} }
        if(existing != null) return@tx existing
        c.prepareStatement("INSERT INTO conversation_message(account_id,conversation_id,parent_message_id,branch_key,role,state,content,idempotency_key,final_marker) VALUES (?::uuid,?::uuid,?::uuid,?,'USER','QUEUED',?,?,false) RETURNING *").use { ps ->
            ps.setString(1,accountId);ps.setString(2,conversationId);ps.setString(3,req.parentMessageId);ps.setString(4,req.branchKey?.take(100));ps.setString(5,text);ps.setString(6,req.idempotencyKey);ps.executeQuery().use{rs->rs.next();mapMessage(rs)}
        }.also { c.prepareStatement("UPDATE conversation SET updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,conversationId);ps.setString(2,accountId);ps.executeUpdate()} }
    }

    fun markUserSending(accountId: String, messageId: String): MessageResponse = transition(accountId,messageId,MessageState.SENDING)
    fun markUserCompleted(accountId: String, messageId: String): MessageResponse = transition(accountId,messageId,MessageState.COMPLETED, final=true)
    fun markUserFailed(accountId: String, messageId: String): MessageResponse = transition(accountId,messageId,MessageState.FAILED)

    fun createAssistantStreaming(accountId:String, conversationId:String, parentMessageId:String, idempotencyKey:String):MessageResponse = db.tx{c->
        requireConversation(c,accountId,conversationId);requireMessage(c,accountId,conversationId,parentMessageId)
        val existing=c.prepareStatement("SELECT * FROM conversation_message WHERE account_id=?::uuid AND idempotency_key=?").use{ps->ps.setString(1,accountId);ps.setString(2,idempotencyKey);ps.executeQuery().use{rs->if(rs.next())mapMessage(rs)else null}}
        existing ?: c.prepareStatement("INSERT INTO conversation_message(account_id,conversation_id,parent_message_id,role,state,content,idempotency_key,final_marker) VALUES (?::uuid,?::uuid,?::uuid,'ASSISTANT','STREAMING','',?,false) RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,conversationId);ps.setString(3,parentMessageId);ps.setString(4,idempotencyKey);ps.executeQuery().use{rs->rs.next();mapMessage(rs)}}
    }

    fun appendAssistantSegment(accountId:String,messageId:String,segment:String):MessageResponse = db.tx{c->
        if(segment.length>20_000) throw validation("Stream segment too large")
        c.prepareStatement("UPDATE conversation_message SET content=content||?,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND role='ASSISTANT' AND state='STREAMING' RETURNING *").use{ps->ps.setString(1,segment);ps.setString(2,messageId);ps.setString(3,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("MESSAGE_STATE",ErrorCategory.CONFLICT,"Assistant message is not streaming"));mapMessage(rs)}}
    }

    fun finishAssistant(accountId:String,messageId:String):MessageResponse = transition(accountId,messageId,MessageState.COMPLETED, final=true)
    fun cancelAssistant(accountId:String,messageId:String):MessageResponse = transition(accountId,messageId,MessageState.CANCELLED)
    fun failAssistant(accountId:String,messageId:String):MessageResponse = transition(accountId,messageId,MessageState.FAILED)

    private fun transition(accountId:String,messageId:String,target:MessageState,final:Boolean=false):MessageResponse = db.tx{c->
        val current=c.prepareStatement("SELECT * FROM conversation_message WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,messageId);ps.setString(2,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("MESSAGE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Message not found"));mapMessage(rs)}}
        ChatStateMachine.transition(
            ConversationMessage(
                id=current.id,
                accountId=accountId,
                conversationId=current.conversationId,
                parentMessageId=current.parentMessageId,
                role=MessageRole.valueOf(current.role),
                state=MessageState.valueOf(current.state),
                content=current.content,
                idempotencyKey="repository-transition",
                revision=current.revision,
            ),
            target,
        )
        c.prepareStatement("UPDATE conversation_message SET state=?,final_marker=?,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? RETURNING *").use{ps->ps.setString(1,target.name);ps.setBoolean(2,final);ps.setString(3,messageId);ps.setString(4,accountId);ps.setLong(5,current.revision);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Message revision conflict"));mapMessage(rs)}}
    }

    private fun requireConversation(c:Connection,accountId:String,id:String):ConversationResponse = c.prepareStatement("SELECT * FROM conversation WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("CONVERSATION_NOT_FOUND",ErrorCategory.NOT_FOUND,"Conversation not found"));mapConversation(rs)}}
    private fun requireMessage(c:Connection,accountId:String,conversationId:String,id:String){c.prepareStatement("SELECT 1 FROM conversation_message WHERE id=?::uuid AND account_id=?::uuid AND conversation_id=?::uuid").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.setString(3,conversationId);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("MESSAGE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Message not found"))}}}
    private fun requireProject(c:Connection,accountId:String,id:String){c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}
    private fun mapConversation(rs:ResultSet)=ConversationResponse(rs.uuid("id"),rs.uuidOrNull("project_id"),rs.getString("scope"),rs.getString("title"),rs.getString("learning_mode"),rs.getBoolean("memory_enabled"),rs.getBoolean("project_memory_enabled"),rs.getBoolean("pinned"),rs.getBoolean("archived"),rs.getLong("revision"),rs.instant("updated_at"))
    private fun mapMessage(rs:ResultSet)=MessageResponse(rs.uuid("id"),rs.uuid("conversation_id"),rs.uuidOrNull("parent_message_id"),rs.getString("branch_key"),rs.getString("role"),rs.getString("state"),rs.getString("content"),rs.getBoolean("final_marker"),rs.getLong("revision"),rs.instant("created_at"),rs.instant("updated_at"))
}

class NoteRepository(private val db:Database){
    fun create(accountId:String,req:CreateNoteRequest):NoteResponse=db.tx{c->validateLinks(c,accountId,req.projectId,req.sourceId,req.conversationId);val title=req.title.trim();if(title.isEmpty()||title.length>240)throw validation("Invalid note title");if(req.body.length>1_000_000)throw validation("Note too large");c.prepareStatement("INSERT INTO note(account_id,project_id,source_id,conversation_id,title,body) VALUES (?::uuid,?::uuid,?::uuid,?::uuid,?,?) RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,req.projectId);ps.setString(3,req.sourceId);ps.setString(4,req.conversationId);ps.setString(5,title);ps.setString(6,req.body);ps.executeQuery().use{rs->rs.next();map(rs)}}}
    fun list(accountId:String,projectId:String?=null,limit:Int=100): List<NoteResponse> = db.tx{c->if(projectId!=null)requireOwned(c,"project",accountId,projectId);val sql="SELECT * FROM note WHERE account_id=?::uuid AND deleted_at IS NULL"+(if(projectId!=null)" AND project_id=?::uuid" else "")+" ORDER BY pinned DESC,updated_at DESC LIMIT ?";c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,accountId);if(projectId!=null)ps.setString(i++,projectId);ps.setInt(i,limit.coerceIn(1,200));ps.executeQuery().use{rs->buildList{while(rs.next())add(map(rs))}}}}
    fun update(accountId:String,id:String,req:UpdateNoteRequest):NoteResponse=db.tx{c->val current=get(c,accountId,id);if(current.revision!=req.expectedRevision)throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Note revision conflict"));val title=req.title?.trim()?.also{if(it.isEmpty()||it.length>240)throw validation("Invalid note title")}?:current.title;val body=req.body?.also{if(it.length>1_000_000)throw validation("Note too large")}?:current.body;c.prepareStatement("UPDATE note SET title=?,body=?,pinned=?,archived=?,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? RETURNING *").use{ps->ps.setString(1,title);ps.setString(2,body);ps.setBoolean(3,req.pinned?:current.pinned);ps.setBoolean(4,req.archived?:current.archived);ps.setString(5,id);ps.setString(6,accountId);ps.setLong(7,req.expectedRevision);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Note update conflict"));map(rs)}}}
    private fun get(c:Connection,a:String,id:String)=c.prepareStatement("SELECT * FROM note WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("NOTE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Note not found"));map(rs)}}
    private fun validateLinks(c:Connection,a:String,p:String?,s:String?,conv:String?){p?.let{requireOwned(c,"project",a,it)};s?.let{requireOwned(c,"source",a,it)};conv?.let{requireOwned(c,"conversation",a,it)}}
    private fun requireOwned(c:Connection,table:String,a:String,id:String){require(table in setOf("project","source","conversation"));c.prepareStatement("SELECT 1 FROM $table WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("NOT_FOUND",ErrorCategory.NOT_FOUND,"Linked object not found"))}}}
    private fun map(rs:ResultSet)=NoteResponse(rs.uuid("id"),rs.uuidOrNull("project_id"),rs.uuidOrNull("source_id"),rs.uuidOrNull("conversation_id"),rs.getString("title"),rs.getString("body"),rs.getBoolean("pinned"),rs.getBoolean("archived"),rs.getLong("revision"),rs.instant("updated_at"))
}

internal fun validation(message:String)=DomainException(DomainError("VALIDATION",ErrorCategory.VALIDATION,message))
internal fun ResultSet.uuid(name:String)=getObject(name,UUID::class.java).toString()
internal fun ResultSet.uuidOrNull(name:String)=getObject(name,UUID::class.java)?.toString()
internal fun ResultSet.instant(name:String)=getObject(name,OffsetDateTime::class.java).toInstant().toString()
