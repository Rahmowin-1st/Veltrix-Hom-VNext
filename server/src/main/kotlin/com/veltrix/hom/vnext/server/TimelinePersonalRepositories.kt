package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import java.time.OffsetDateTime
import java.util.UUID

class ActivityTimelineRepository(private val db: Database) {
    fun list(accountId:String,projectId:String?,limit:Int,offset:Int):List<ActivityEventResponse> = db.tx{c->
        if(projectId!=null) requireProject(c,accountId,projectId)
        val sql="SELECT event_id,event_type,occurred_at,project_id,object_id,meaningful FROM activity_event WHERE account_id=?::uuid"+(if(projectId!=null)" AND project_id=?::uuid" else "")+" ORDER BY occurred_at DESC,event_id DESC LIMIT ? OFFSET ?"
        c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,accountId);if(projectId!=null)ps.setString(i++,projectId);ps.setInt(i++,limit.coerceIn(1,200));ps.setInt(i,offset.coerceAtLeast(0));ps.executeQuery().use{rs->buildList{while(rs.next())add(ActivityEventResponse(rs.getObject(1,UUID::class.java).toString(),rs.getString(2),rs.getObject(3,OffsetDateTime::class.java).toInstant().toString(),rs.getObject(4,UUID::class.java)?.toString(),rs.getString(5),rs.getBoolean(6)))}}}
    }
    private fun requireProject(c:java.sql.Connection,a:String,id:String){c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}
}

class PersonalAggregatorRepository(private val db:Database,private val memory:MemoryRepository,private val timeline:ActivityTimelineRepository,private val game:Part2GameRepository){
    fun snapshot(accountId:String):PersonalSnapshotResponse=db.tx{c->
        val profile=c.prepareStatement("SELECT display_name,preferred_language,timezone FROM user_profile WHERE account_id=?::uuid").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("PROFILE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Profile not found"));Triple(rs.getString(1),rs.getString(2),rs.getString(3))}}
        fun count(sql:String):Int=c.prepareStatement(sql).use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}
        val activeProjects=count("SELECT count(*) FROM project WHERE account_id=?::uuid AND status='ACTIVE' AND deleted_at IS NULL")
        val assessments=count("SELECT count(*) FROM assessment_attempt WHERE account_id=?::uuid AND state='GRADED'")
        val mistakes=count("SELECT count(*) FROM mistake WHERE account_id=?::uuid AND status IN ('ACTIVE','IMPROVING','RECURRED') AND deleted_at IS NULL")
        val due=count("SELECT count(*) FROM flashcard_schedule WHERE account_id=?::uuid AND due_at<=now()")
        PersonalSnapshotResponse(accountId,profile.first,profile.second,profile.third,memory.maturity(accountId).state,activeProjects,assessments,mistakes,due,timeline.list(accountId,null,10,0),schemaVersion=2,game=game.profile(accountId))
    }
}

class ProjectInstructionRepository(private val db:Database){
    fun active(accountId:String,projectId:String):ProjectInstructionResponse?=db.tx{c->requireProject(c,accountId,projectId);c.prepareStatement("SELECT * FROM project_instruction WHERE account_id=?::uuid AND project_id=?::uuid AND active=true LIMIT 1").use{ps->ps.setString(1,accountId);ps.setString(2,projectId);ps.executeQuery().use{rs->if(rs.next())map(rs)else null}}}
    fun put(accountId:String,projectId:String,req:ProjectInstructionPutRequest):ProjectInstructionResponse=db.tx{c->
        requireProject(c,accountId,projectId)
        val body=req.body.trim();if(body.length>20_000)throw validation("Project instruction too large")
        val structured=req.structuredJson.trim().ifEmpty{"{}"};if(structured.length>16_384)throw validation("Structured instruction too large")
        val next=c.prepareStatement("SELECT COALESCE(max(version),0)+1 FROM project_instruction WHERE project_id=?::uuid").use{ps->ps.setString(1,projectId);ps.executeQuery().use{rs->rs.next();rs.getInt(1)}}
        c.prepareStatement("UPDATE project_instruction SET active=false,updated_at=now() WHERE project_id=?::uuid AND account_id=?::uuid AND active=true").use{ps->ps.setString(1,projectId);ps.setString(2,accountId);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO project_instruction(account_id,project_id,body,structured,version,active) VALUES (?::uuid,?::uuid,?,?::jsonb,?,true) RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,projectId);ps.setString(3,body);ps.setString(4,structured);ps.setInt(5,next);runCatching{ps.executeQuery()}.getOrElse{throw DomainException(DomainError("VALIDATION",ErrorCategory.VALIDATION,"structuredJson must be valid JSON"))}.use{rs->rs.next();map(rs)}}
    }
    fun reset(accountId:String,projectId:String){db.tx{c->requireProject(c,accountId,projectId);c.prepareStatement("UPDATE project_instruction SET active=false,updated_at=now() WHERE project_id=?::uuid AND account_id=?::uuid AND active=true").use{ps->ps.setString(1,projectId);ps.setString(2,accountId);ps.executeUpdate()}}}
    private fun requireProject(c:java.sql.Connection,a:String,id:String){c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,a);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}
    private fun map(rs:java.sql.ResultSet)=ProjectInstructionResponse(rs.getObject("id",UUID::class.java).toString(),rs.getObject("project_id",UUID::class.java).toString(),rs.getString("body"),rs.getString("structured"),rs.getInt("version"),rs.getBoolean("active"),rs.getObject("updated_at",OffsetDateTime::class.java).toInstant().toString())
}
