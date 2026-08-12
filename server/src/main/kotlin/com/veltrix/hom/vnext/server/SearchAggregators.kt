package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.*
import java.sql.Connection
import java.sql.ResultSet

class GlobalSearchRepository(private val db:Database){
    fun search(accountId:String,req:SearchRequest):List<SearchResultResponse>{
        val q=req.query.trim();if(q.length<2) return emptyList();val limit=req.limit.coerceIn(1,100)
        return db.tx{c->
            req.projectId?.let{requireProject(c,accountId,it)}
            val raw=mutableListOf<TypedSearchResult>()
            searchTable(c,accountId,req.projectId,q,limit,"PROJECT","project","id","title","purpose","id",null,"veltrix://project/").let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"CHAT","conversation","id","title","title","id","project_id","veltrix://chat/").let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"SOURCE","source","id","title","title","id",null,"veltrix://source/").let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"NOTE","note","id","title","body","id","project_id","veltrix://note/").let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"ASSESSMENT","assessment","id","title","title","id","project_id","veltrix://assessment/").let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"FLASHCARD","flashcard","id","front","back","id","project_id","veltrix://flashcard/").let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"MISTAKE","mistake","id","topic","prompt","id","project_id","veltrix://mistake/").let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"GOAL","goal","id","title","description","id","project_id","veltrix://goal/").let(raw::addAll)
            raw.sortedWith(compareByDescending<TypedSearchResult>{it.score}.thenBy{it.type}.thenBy{it.id}).take(limit).map{SearchResultResponse(it.type,it.id,it.title,it.snippet,it.projectId,it.score,it.deepLink)}
        }
    }

    private fun searchTable(c:Connection,a:String,p:String?,q:String,limit:Int,type:String,table:String,idCol:String,titleCol:String,snippetCol:String,deepId:String,projectCol:String?,prefix:String):List<TypedSearchResult>{
        val allowed=setOf("project","conversation","source","note","assessment","flashcard","mistake","goal");require(table in allowed)
        val deleted=if(table in setOf("conversation","source","note","assessment","flashcard","mistake","goal","project"))" AND deleted_at IS NULL" else ""
        val projectFilter=if(p!=null && projectCol!=null)" AND $projectCol=?::uuid" else if(p!=null && table=="project")" AND id=?::uuid" else ""
        val sql="SELECT $idCol id,$titleCol title,coalesce($snippetCol,'') snippet"+(if(projectCol!=null) ",$projectCol project_id" else ",NULL::uuid project_id")+" FROM $table WHERE account_id=?::uuid $deleted $projectFilter AND (lower($titleCol) LIKE lower(?) OR lower(coalesce($snippetCol,'')) LIKE lower(?)) ORDER BY updated_at DESC NULLS LAST LIMIT ?"
        return c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,a);if(p!=null&&(projectCol!=null||table=="project"))ps.setString(i++,p);val like="%${q.take(200)}%";ps.setString(i++,like);ps.setString(i++,like);ps.setInt(i,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val title=rs.getString("title")?:"";val snip=rs.getString("snippet")?:"";val exact=if(title.equals(q,true))1.0 else if(title.startsWith(q,true))0.88 else 0.72;val id=rs.getObject("id").toString();add(TypedSearchResult(type,id,title,snip.take(240),rs.getObject("project_id")?.toString(),exact,prefix+rs.getObject(deepId).toString()))}}}}
    }
    private fun requireProject(c:Connection,a:String,id:String){c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}
}

class HomeAggregatorRepository(private val db:Database, private val projects:ProjectRepository, private val memory:MemoryRepository){
    fun snapshot(accountId:String):HomeSnapshotResponse{
        val profile=db.tx{c->c.prepareStatement("SELECT display_name,default_avatar_id FROM user_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("PROFILE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Profile not found"));rs.getString(1) to rs.getString(2)}}}
        val recent=projects.list(accountId,6,0)
        val maturity=memory.maturity(accountId)
        val unread=db.tx{c->c.prepareStatement("SELECT count(*) FROM notification_intent WHERE account_id=?::uuid AND status='PENDING' AND (scheduled_for IS NULL OR scheduled_for<=now())").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}}
        val focus=recent.firstOrNull{it.status=="ACTIVE"}?.title
        return HomeSnapshotResponse(accountId,profile.first,profile.second?:"default",recent,focus,maturity.state,"LOCKED_PART_2",unread,"SERVER_AUTHORITATIVE",1)
    }
}

class ProjectWorkspaceRepository(private val db:Database,private val projects:ProjectRepository,private val chats:ChatRepository,private val memory:MemoryRepository){
    fun snapshot(accountId:String,projectId:String):ProjectWorkspaceResponse{
        val project=projects.get(accountId,projectId)
        val goals=projects.listGoals(accountId,projectId)
        val recentChats=chats.list(accountId,projectId,8,0)
        val counts=db.tx{c->
            fun count(sql:String):Int=c.prepareStatement(sql).use{ps->ps.setString(1,accountId);ps.setString(2,projectId);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}
            intArrayOf(
                count("SELECT count(*) FROM source_project_link WHERE account_id=?::uuid AND project_id=?::uuid"),
                count("SELECT count(*) FROM note WHERE account_id=?::uuid AND project_id=?::uuid AND deleted_at IS NULL"),
                count("SELECT count(*) FROM assessment WHERE account_id=?::uuid AND project_id=?::uuid AND deleted_at IS NULL"),
                count("SELECT count(*) FROM flashcard WHERE account_id=?::uuid AND project_id=?::uuid AND deleted_at IS NULL"),
                count("SELECT count(*) FROM mistake WHERE account_id=?::uuid AND project_id=?::uuid AND deleted_at IS NULL"),
            )
        }
        return ProjectWorkspaceResponse(project,goals,recentChats,counts[0],counts[1],counts[2],counts[3],counts[4],memory.maturity(accountId).state,1)
    }
}

class ToolRepository(private val db:Database){
    fun invoke(accountId:String,req:ToolRequest):ToolResponse{
        req.projectId?.let{db.tx{c->requireOwned(c,"project",accountId,it)}}
        req.conversationId?.let{db.tx{c->requireOwned(c,"conversation",accountId,it)}}
        val result=ToolRegistry().invoke(req.toolId,req.input)
        db.tx{c->c.prepareStatement("INSERT INTO tool_invocation(account_id,conversation_id,project_id,tool_id,input_hash,result_summary,deterministic) VALUES (?::uuid,?::uuid,?::uuid,?,?,?::jsonb,true)").use{ps->ps.setString(1,accountId);ps.setString(2,req.conversationId);ps.setString(3,req.projectId);ps.setString(4,req.toolId);ps.setString(5,sha256(req.input.toSortedMap().toString()));ps.setString(6,mapJson(result.values));ps.executeUpdate()}}
        return ToolResponse(req.toolId,result.values,true)
    }
    private fun requireOwned(c:Connection,t:String,a:String,id:String){require(t in setOf("project","conversation"));c.prepareStatement("SELECT 1 FROM $t WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("NOT_FOUND",ErrorCategory.NOT_FOUND,"Linked object not found"))}}}
}

internal fun sha256(s:String):String=java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString(""){"%02x".format(it)}
internal fun mapJson(m:Map<String,String>):String="{"+m.entries.joinToString(","){(k,v)->"\"${escapeJson(k)}\":\"${escapeJson(v)}\""}+"}"
internal fun escapeJson(s:String)=s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n")
