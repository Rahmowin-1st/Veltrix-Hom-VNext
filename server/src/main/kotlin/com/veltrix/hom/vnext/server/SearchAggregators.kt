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
            searchChatMessages(c,accountId,req.projectId,q,limit).let(raw::addAll)
            searchSources(c,accountId,req.projectId,q,limit).let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"NOTE","note","id","title","body","id","project_id","veltrix://note/").let(raw::addAll)
            searchAssessments(c,accountId,req.projectId,q,limit).let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"FLASHCARD","flashcard","id","front","back","id","project_id","veltrix://flashcard/").let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"MISTAKE","mistake","id","topic","prompt","id","project_id","veltrix://mistake/").let(raw::addAll)
            searchTable(c,accountId,req.projectId,q,limit,"GOAL","goal","id","title","description","id","project_id","veltrix://goal/").let(raw::addAll)
            searchPractice(c,accountId,req.projectId,q,limit).let(raw::addAll)
            if(req.projectId==null){
                searchAchievements(c,accountId,q,limit).let(raw::addAll)
                searchMapUnits(c,accountId,q,limit).let(raw::addAll)
                searchInventory(c,accountId,q,limit).let(raw::addAll)
                searchStore(c,accountId,q,limit).let(raw::addAll)
            }
            raw.groupBy{it.type to it.id}.values.map{group->group.maxBy{it.score}}
                .sortedWith(compareByDescending<TypedSearchResult>{it.score}.thenBy{it.type}.thenBy{it.id})
                .take(limit).map{SearchResultResponse(it.type,it.id,it.title,it.snippet,it.projectId,it.score,it.deepLink)}
        }
    }

    private fun searchTable(c:Connection,a:String,p:String?,q:String,limit:Int,type:String,table:String,idCol:String,titleCol:String,snippetCol:String,deepId:String,projectCol:String?,prefix:String):List<TypedSearchResult>{
        val allowed=setOf("project","conversation","note","flashcard","mistake","goal");require(table in allowed)
        val deleted=" AND deleted_at IS NULL"
        val projectFilter=if(p!=null && projectCol!=null)" AND $projectCol=?::uuid" else if(p!=null && table=="project")" AND id=?::uuid" else ""
        val sql="SELECT $idCol id,$titleCol title,coalesce($snippetCol,'') snippet"+(if(projectCol!=null) ",$projectCol project_id" else ",NULL::uuid project_id")+" FROM $table WHERE account_id=?::uuid $deleted $projectFilter AND (lower($titleCol) LIKE lower(?) OR lower(coalesce($snippetCol,'')) LIKE lower(?)) ORDER BY updated_at DESC NULLS LAST LIMIT ?"
        return c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,a);if(p!=null&&(projectCol!=null||table=="project"))ps.setString(i++,p);val like="%${q.take(200)}%";ps.setString(i++,like);ps.setString(i++,like);ps.setInt(i,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val title=rs.getString("title")?:"";val snip=rs.getString("snippet")?:"";val exact=score(title,snip,q);val id=rs.getObject("id").toString();add(TypedSearchResult(type,id,title,snip.take(240),rs.getObject("project_id")?.toString(),exact,prefix+rs.getObject(deepId).toString()))}}}}
    }

    private fun searchChatMessages(c:Connection,a:String,p:String?,q:String,limit:Int):List<TypedSearchResult>{
        val sql="""SELECT c.id,c.title,m.content,c.project_id,m.updated_at FROM conversation_message m JOIN conversation c ON c.id=m.conversation_id AND c.account_id=m.account_id WHERE m.account_id=?::uuid AND c.deleted_at IS NULL AND m.state='FINAL' ${if(p!=null)"AND c.project_id=?::uuid" else ""} AND lower(m.content) LIKE lower(?) ORDER BY m.updated_at DESC LIMIT ?"""
        return c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,a);if(p!=null)ps.setString(i++,p);ps.setString(i++,"%${q.take(200)}%");ps.setInt(i,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val id=rs.getObject(1).toString();val title=rs.getString(2);val snippet=rs.getString(3);add(TypedSearchResult("CHAT",id,title,snippet.take(240),rs.getObject(4)?.toString(),score(title,snippet,q),"veltrix://chat/$id"))}}}}
    }

    private fun searchSources(c:Connection,a:String,p:String?,q:String,limit:Int):List<TypedSearchResult>{
        val sql=if(p==null) """SELECT s.id,s.title,coalesce(s.file_name,s.source_type) snippet,NULL::uuid project_id,s.updated_at FROM source s WHERE s.account_id=?::uuid AND s.deleted_at IS NULL AND (lower(s.title) LIKE lower(?) OR lower(coalesce(s.file_name,'')) LIKE lower(?) OR lower(s.metadata::text) LIKE lower(?)) ORDER BY s.updated_at DESC LIMIT ?"""
        else """SELECT DISTINCT s.id,s.title,coalesce(s.file_name,s.source_type) snippet,l.project_id,s.updated_at FROM source s JOIN source_project_link l ON l.source_id=s.id AND l.account_id=s.account_id WHERE s.account_id=?::uuid AND l.project_id=?::uuid AND s.deleted_at IS NULL AND (lower(s.title) LIKE lower(?) OR lower(coalesce(s.file_name,'')) LIKE lower(?) OR lower(s.metadata::text) LIKE lower(?)) ORDER BY s.updated_at DESC LIMIT ?"""
        return c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,a);if(p!=null)ps.setString(i++,p);val like="%${q.take(200)}%";repeat(3){ps.setString(i++,like)};ps.setInt(i,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val id=rs.getObject(1).toString();val title=rs.getString(2);val snippet=rs.getString(3);add(TypedSearchResult("SOURCE",id,title,snippet.take(240),rs.getObject(4)?.toString(),score(title,snippet,q),"veltrix://source/$id"))}}}}
    }

    private fun searchAssessments(c:Connection,a:String,p:String?,q:String,limit:Int):List<TypedSearchResult>{
        val sql="SELECT id,kind,title,project_id FROM assessment WHERE account_id=?::uuid AND deleted_at IS NULL ${if(p!=null)"AND project_id=?::uuid" else ""} AND (lower(title) LIKE lower(?) OR lower(config::text) LIKE lower(?)) ORDER BY updated_at DESC LIMIT ?"
        return c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,a);if(p!=null)ps.setString(i++,p);val like="%${q.take(200)}%";ps.setString(i++,like);ps.setString(i++,like);ps.setInt(i,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val id=rs.getObject(1).toString();val kind=rs.getString(2);val title=rs.getString(3);add(TypedSearchResult(kind,id,title,kind,rs.getObject(4)?.toString(),score(title,kind,q),"veltrix://assessment/$id"))}}}}
    }

    private fun searchPractice(c:Connection,a:String,p:String?,q:String,limit:Int):List<TypedSearchResult>{
        val sql="SELECT id,coalesce(focus_topic,'Practice session') title,state,project_id FROM practice_session WHERE account_id=?::uuid ${if(p!=null)"AND project_id=?::uuid" else ""} AND (lower(coalesce(focus_topic,'')) LIKE lower(?) OR lower(config::text) LIKE lower(?)) ORDER BY updated_at DESC LIMIT ?"
        return c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,a);if(p!=null)ps.setString(i++,p);val like="%${q.take(200)}%";ps.setString(i++,like);ps.setString(i++,like);ps.setInt(i,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val id=rs.getObject(1).toString();val title=rs.getString(2);val state=rs.getString(3);add(TypedSearchResult("PRACTICE",id,title,state,rs.getObject(4)?.toString(),score(title,state,q),"veltrix://practice/$id"))}}}}
    }

    private fun searchAchievements(c:Connection,a:String,q:String,limit:Int):List<TypedSearchResult>{
        val sql="""SELECT ap.achievement_id,ad.category,ap.state FROM achievement_progress ap JOIN achievement_definition ad ON ad.achievement_id=ap.achievement_id AND ad.version=ap.definition_version WHERE ap.account_id=?::uuid AND (lower(ap.achievement_id) LIKE lower(?) OR lower(ad.category) LIKE lower(?)) ORDER BY ap.updated_at DESC LIMIT ?"""
        return c.prepareStatement(sql).use{ps->ps.setString(1,a);val like="%${q.take(200)}%";ps.setString(2,like);ps.setString(3,like);ps.setInt(4,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val id=rs.getString(1);val category=rs.getString(2);val state=rs.getString(3);add(TypedSearchResult("ACHIEVEMENT",id,id,"$category • $state",null,score(id,category,q),"veltrix://achievement/$id"))}}}}
    }

    private fun searchMapUnits(c:Connection,a:String,q:String,limit:Int):List<TypedSearchResult>{
        val sql="""SELECT DISTINCT mu.unit_id,mu.title_key,mu.semantic_key FROM map_unit_progress up JOIN personal_map pm ON pm.id=up.personal_map_id AND pm.account_id=up.account_id JOIN map_unit mu ON mu.unit_id=up.unit_id AND mu.map_definition_id=pm.map_definition_id AND mu.map_version=pm.map_version WHERE up.account_id=?::uuid AND (lower(mu.title_key) LIKE lower(?) OR lower(mu.semantic_key) LIKE lower(?)) ORDER BY mu.unit_id LIMIT ?"""
        return c.prepareStatement(sql).use{ps->ps.setString(1,a);val like="%${q.take(200)}%";ps.setString(2,like);ps.setString(3,like);ps.setInt(4,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val id=rs.getString(1);val title=rs.getString(2);val semantic=rs.getString(3);add(TypedSearchResult("MAP_UNIT",id,title,semantic,null,score(title,semantic,q),"veltrix://map/unit/$id"))}}}}
    }

    private fun searchInventory(c:Connection,a:String,q:String,limit:Int):List<TypedSearchResult>{
        val sql="""SELECT io.item_id,ic.item_type,ic.metadata::text FROM inventory_ownership io JOIN inventory_catalog ic ON ic.item_id=io.item_id WHERE io.account_id=?::uuid AND (lower(io.item_id) LIKE lower(?) OR lower(ic.item_type) LIKE lower(?) OR lower(ic.metadata::text) LIKE lower(?)) ORDER BY io.acquired_at DESC LIMIT ?"""
        return c.prepareStatement(sql).use{ps->ps.setString(1,a);val like="%${q.take(200)}%";repeat(3){ps.setString(2+it,like)};ps.setInt(5,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val id=rs.getString(1);val type=rs.getString(2);val meta=rs.getString(3);add(TypedSearchResult("INVENTORY",id,id,"$type ${meta.take(180)}",null,score(id,type,q),"veltrix://inventory/$id"))}}}}
    }

    private fun searchStore(c:Connection,a:String,q:String,limit:Int):List<TypedSearchResult>{
        val sql="""SELECT si.item_id,coalesce(si.category,ic.item_type),coalesce(si.description,ic.metadata::text),si.price_coins FROM store_item si JOIN store_catalog sc ON sc.catalog_version=si.catalog_version AND sc.active=true JOIN inventory_catalog ic ON ic.item_id=si.item_id WHERE si.active=true AND (lower(si.item_id) LIKE lower(?) OR lower(coalesce(si.category,ic.item_type)) LIKE lower(?) OR lower(coalesce(si.description,ic.metadata::text)) LIKE lower(?)) ORDER BY si.price_coins,si.item_id LIMIT ?"""
        return c.prepareStatement(sql).use{ps->val like="%${q.take(200)}%";repeat(3){ps.setString(1+it,like)};ps.setInt(4,limit);ps.executeQuery().use{rs->buildList{while(rs.next()){val id=rs.getString(1);val category=rs.getString(2);val description=rs.getString(3);val price=rs.getLong(4);add(TypedSearchResult("STORE_ITEM",id,id,"$category • $price Coins • ${description.take(160)}",null,score(id,"$category $description",q),"veltrix://store/item/$id"))}}}}
    }

    private fun score(title:String,snippet:String,q:String):Double=when{title.equals(q,true)->1.0;title.startsWith(q,true)->0.92;snippet.contains(q,true)->0.78;else->0.68}
    private fun requireProject(c:Connection,a:String,id:String){c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}
}

class HomeAggregatorRepository(private val db:Database, private val projects:ProjectRepository, private val memory:MemoryRepository, private val game:Part2GameRepository){
    fun snapshot(accountId:String):HomeSnapshotResponse{
        val profile=db.tx{c->c.prepareStatement("SELECT display_name,default_avatar_id FROM user_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("PROFILE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Profile not found"));rs.getString(1) to rs.getString(2)}}}
        val recent=projects.list(accountId,6,0)
        val maturity=memory.maturity(accountId)
        val unread=db.tx{c->c.prepareStatement("SELECT count(*) FROM notification_intent WHERE account_id=?::uuid AND status='PENDING' AND (scheduled_for IS NULL OR scheduled_for<=now())").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}}
        val focus=recent.firstOrNull{it.status=="ACTIVE"}?.title
        val g=game.profile(accountId)
        return HomeSnapshotResponse(accountId,profile.first,g.equippedAvatar.avatarId,recent,focus,maturity.state,g.mapState,unread,"SERVER_AUTHORITATIVE",2,g.level,g.lifetimeXp,g.coinBalance,g.currentConsistency,g.currentSeason?.seasonId,g.revision)
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
