package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.*
import com.veltrix.hom.vnext.server.foundation.PasswordHasher
import com.veltrix.hom.vnext.server.foundation.SessionTokens
import java.sql.Connection
import java.sql.ResultSet
import java.time.Duration
import java.time.Instant
import java.util.Locale
import java.util.UUID

class AuthRepository(private val db: Database) {
    data class SessionPrincipal(val accountId: String, val sessionId: String, val expiresAt: Instant)

    fun register(req: RegisterRequest): SessionResponse {
        val login = normalizeLogin(req.login)
        validatePassword(req.password)
        require(req.displayName.trim().length in 1..80) { "displayName length must be 1..80" }
        val passwordHash = PasswordHasher.hash(req.password.toCharArray())
        val session = SessionTokens.generate()
        val expires = Instant.now().plus(Duration.ofDays(30))
        val accountId = db.tx { c ->
            val accountId = c.prepareStatement("INSERT INTO account DEFAULT VALUES RETURNING id").use { ps -> ps.executeQuery().use { rs -> rs.next(); rs.getObject(1, UUID::class.java).toString() } }
            c.prepareStatement("INSERT INTO account_credential(account_id,login_normalized,password_hash,password_algorithm) VALUES (?::uuid,?,?,?)").use { ps ->
                ps.setString(1, accountId); ps.setString(2, login); ps.setString(3, passwordHash); ps.setString(4, "PBKDF2-HMAC-SHA256-600000"); ps.executeUpdate()
            }
            c.prepareStatement("INSERT INTO user_profile(account_id,display_name,preferred_language,timezone) VALUES (?::uuid,?,?,?)").use { ps ->
                ps.setString(1, accountId); ps.setString(2, req.displayName.trim()); ps.setString(3, req.preferredLanguage.take(16)); ps.setString(4, req.timezone.take(64)); ps.executeUpdate()
            }
            c.prepareStatement("INSERT INTO device_session(account_id,refresh_token_hash,device_label,expires_at) VALUES (?::uuid,?,?,?)").use { ps ->
                ps.setString(1, accountId); ps.setString(2, session.storedHashHex); ps.setString(3, "register"); ps.setObject(4, java.time.OffsetDateTime.ofInstant(expires, java.time.ZoneOffset.UTC)); ps.executeUpdate()
            }
            accountId
        }
        return SessionResponse(session.clientToken, accountId, expires.toString())
    }

    fun login(req: LoginRequest): SessionResponse {
        val login = normalizeLogin(req.login)
        val record = db.tx { c ->
            c.prepareStatement("SELECT a.id, ac.password_hash FROM account a JOIN account_credential ac ON ac.account_id=a.id WHERE ac.login_normalized=? AND a.deleted_at IS NULL").use { ps ->
                ps.setString(1, login)
                ps.executeQuery().use { rs -> if (rs.next()) rs.getObject(1, UUID::class.java).toString() to rs.getString(2) else null }
            }
        } ?: throw DomainException(DomainError("AUTH_INVALID", ErrorCategory.AUTH, "Invalid credentials"))
        if (!PasswordHasher.verify(req.password.toCharArray(), record.second)) throw DomainException(DomainError("AUTH_INVALID", ErrorCategory.AUTH, "Invalid credentials"))
        val token = SessionTokens.generate()
        val expires = Instant.now().plus(Duration.ofDays(30))
        db.tx { c ->
            c.prepareStatement("INSERT INTO device_session(account_id,refresh_token_hash,device_label,expires_at) VALUES (?::uuid,?,?,?)").use { ps ->
                ps.setString(1, record.first); ps.setString(2, token.storedHashHex); ps.setString(3, req.deviceLabel?.take(120)); ps.setObject(4, java.time.OffsetDateTime.ofInstant(expires, java.time.ZoneOffset.UTC)); ps.executeUpdate()
            }
        }
        return SessionResponse(token.clientToken, record.first, expires.toString())
    }

    fun resolve(token: String): SessionPrincipal? {
        if (token.length !in 32..256) return null
        val hash = SessionTokens.hash(token)
        return db.tx { c ->
            c.prepareStatement("SELECT id,account_id,expires_at FROM device_session WHERE refresh_token_hash=? AND revoked_at IS NULL AND expires_at>now()").use { ps ->
                ps.setString(1, hash)
                ps.executeQuery().use { rs -> if (!rs.next()) null else SessionPrincipal(rs.getObject("account_id", UUID::class.java).toString(), rs.getObject("id", UUID::class.java).toString(), rs.getObject("expires_at", java.time.OffsetDateTime::class.java).toInstant()) }
            }
        }
    }

    fun rotate(token: String): SessionResponse {
        val principal = resolve(token) ?: throw DomainException(DomainError("AUTH_EXPIRED", ErrorCategory.AUTH, "Session is invalid or expired"))
        val next = SessionTokens.generate()
        val expires = Instant.now().plus(Duration.ofDays(30))
        db.tx { c ->
            c.prepareStatement("UPDATE device_session SET refresh_token_hash=?,last_seen_at=now(),expires_at=? WHERE id=?::uuid AND account_id=?::uuid").use { ps ->
                ps.setString(1, next.storedHashHex); ps.setObject(2, java.time.OffsetDateTime.ofInstant(expires, java.time.ZoneOffset.UTC)); ps.setString(3, principal.sessionId); ps.setString(4, principal.accountId)
                if (ps.executeUpdate()!=1) throw DomainException(DomainError("AUTH_EXPIRED", ErrorCategory.AUTH, "Session rotation failed"))
            }
        }
        return SessionResponse(next.clientToken, principal.accountId, expires.toString())
    }

    fun signOut(token: String) {
        val hash = SessionTokens.hash(token)
        db.tx { c -> c.prepareStatement("UPDATE device_session SET revoked_at=now() WHERE refresh_token_hash=? AND revoked_at IS NULL").use { ps -> ps.setString(1, hash); ps.executeUpdate() } }
    }

    private fun normalizeLogin(value: String): String {
        val out = value.trim().lowercase(Locale.ROOT)
        require(out.length in 3..254 && !out.any { it.isWhitespace() }) { "Invalid login" }
        return out
    }
    private fun validatePassword(value: String) { require(value.length in 12..1024) { "Password must be 12..1024 characters" } }
}

class ProfileRepository(private val db: Database) {
    fun get(accountId: String): ProfileResponse = db.tx { c ->
        c.prepareStatement("SELECT display_name,username,preferred_language,timezone,onboarding_complete,memory_enabled,revision FROM user_profile WHERE account_id=?::uuid").use { ps ->
            ps.setString(1, accountId); ps.executeQuery().use { rs ->
                if (!rs.next()) throw DomainException(DomainError("PROFILE_NOT_FOUND", ErrorCategory.NOT_FOUND, "Profile not found"))
                ProfileResponse(accountId, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6), rs.getLong(7))
            }
        }
    }

    fun update(accountId: String, req: UpdateProfileRequest): ProfileResponse = db.tx { c ->
        val current = getInConnection(c, accountId)
        if (current.revision != req.expectedRevision) throw DomainException(DomainError("CONFLICT", ErrorCategory.CONFLICT, "Profile revision conflict"))
        val display = req.displayName?.trim()?.also { require(it.length in 1..80) } ?: current.displayName
        val lang = req.preferredLanguage?.trim()?.take(16) ?: current.preferredLanguage
        val tz = req.timezone?.trim()?.take(64) ?: current.timezone
        c.prepareStatement("UPDATE user_profile SET display_name=?,preferred_language=?,timezone=?,onboarding_complete=?,memory_enabled=?,updated_at=now(),revision=revision+1 WHERE account_id=?::uuid AND revision=?").use { ps ->
            ps.setString(1, display); ps.setString(2, lang); ps.setString(3, tz); ps.setBoolean(4, req.onboardingComplete ?: current.onboardingComplete); ps.setBoolean(5, req.memoryEnabled ?: current.memoryEnabled); ps.setString(6, accountId); ps.setLong(7, req.expectedRevision)
            if (ps.executeUpdate()!=1) throw DomainException(DomainError("CONFLICT", ErrorCategory.CONFLICT, "Profile update conflict"))
        }
        getInConnection(c, accountId)
    }

    private fun getInConnection(c: Connection, accountId: String): ProfileResponse = c.prepareStatement("SELECT display_name,username,preferred_language,timezone,onboarding_complete,memory_enabled,revision FROM user_profile WHERE account_id=?::uuid").use { ps ->
        ps.setString(1, accountId); ps.executeQuery().use { rs ->
            if(!rs.next()) throw DomainException(DomainError("PROFILE_NOT_FOUND", ErrorCategory.NOT_FOUND, "Profile not found"))
            ProfileResponse(accountId, rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getBoolean(6), rs.getLong(7))
        }
    }
}

class ProjectRepository(private val db: Database) {
    fun create(accountId: String, req: CreateProjectRequest): ProjectResponse {
        val title=req.title.trim(); require(title.length in 1..120)
        return db.tx { c ->
            val created=c.prepareStatement("INSERT INTO project(account_id,title,purpose,template_type,priority) VALUES (?::uuid,?,?,?,?) RETURNING *").use { ps ->
                ps.setString(1,accountId); ps.setString(2,title); ps.setString(3,req.purpose?.trim()?.take(1000)); ps.setString(4,req.template.take(40)); ps.setInt(5,req.priority.coerceIn(-100,100)); ps.executeQuery().use { rs -> rs.next(); mapProject(rs, null) }
            }
            val meaningful=(created.purpose?.trim()?.length ?: 0)>=8
            insertActivity(c,accountId,"PROJECT_CREATED",created.id,created.id,"project-created:${created.id}",meaningful)
            created
        }
    }

    fun list(accountId:String, limit:Int=50, offset:Int=0):List<ProjectResponse> = db.tx { c ->
        c.prepareStatement("SELECT p.*, (SELECT body FROM project_instruction pi WHERE pi.project_id=p.id AND pi.active=true LIMIT 1) ai_instruction FROM project p WHERE p.account_id=?::uuid AND p.deleted_at IS NULL ORDER BY (status='ACTIVE') DESC,priority DESC,last_active_at DESC LIMIT ? OFFSET ?").use { ps ->
            ps.setString(1,accountId); ps.setInt(2,limit.coerceIn(1,100)); ps.setInt(3,offset.coerceAtLeast(0)); ps.executeQuery().use { rs -> buildList { while(rs.next()) add(mapProject(rs,rs.getString("ai_instruction"))) } }
        }
    }

    fun get(accountId:String,id:String):ProjectResponse = db.tx { c -> getInConnection(c,accountId,id) }

    fun update(accountId:String,id:String,req:UpdateProjectRequest):ProjectResponse = db.tx { c ->
        val current=getInConnection(c,accountId,id)
        if(current.revision!=req.expectedRevision) throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Project revision conflict"))
        val status=req.status ?: current.status
        require(status in setOf("ACTIVE","PAUSED","COMPLETED","ARCHIVED"))
        c.prepareStatement("UPDATE project SET title=?,purpose=?,status=?::project_status,priority=?,updated_at=now(),last_active_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL").use { ps ->
            ps.setString(1,req.title?.trim()?.takeIf{it.isNotEmpty()} ?: current.title); ps.setString(2,req.purpose ?: current.purpose); ps.setString(3,status); ps.setInt(4,req.priority ?: current.priority); ps.setString(5,id); ps.setString(6,accountId); ps.setLong(7,req.expectedRevision)
            if(ps.executeUpdate()!=1) throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Project update conflict"))
        }
        if(req.aiInstruction!=null) setInstruction(c,accountId,id,req.aiInstruction)
        getInConnection(c,accountId,id)
    }

    fun createGoal(accountId:String,projectId:String,req:CreateGoalRequest):GoalResponse = db.tx { c ->
        requireProjectOwnership(c,accountId,projectId)
        val title=req.title.trim(); require(title.length in 1..200)
        c.prepareStatement("INSERT INTO goal(account_id,project_id,title,description,priority) VALUES (?::uuid,?::uuid,?,?,?) RETURNING *").use { ps -> ps.setString(1,accountId); ps.setString(2,projectId); ps.setString(3,title); ps.setString(4,req.description?.take(2000)); ps.setInt(5,req.priority.coerceIn(-100,100)); ps.executeQuery().use { rs -> rs.next(); mapGoal(rs) } }
    }

    fun listGoals(accountId:String,projectId:String):List<GoalResponse> = db.tx { c ->
        requireProjectOwnership(c,accountId,projectId)
        c.prepareStatement("SELECT * FROM goal WHERE account_id=?::uuid AND project_id=?::uuid AND deleted_at IS NULL ORDER BY (status='ACTIVE') DESC,priority DESC,updated_at DESC").use { ps -> ps.setString(1,accountId); ps.setString(2,projectId); ps.executeQuery().use { rs -> buildList { while(rs.next()) add(mapGoal(rs)) } } }
    }

    fun transitionGoal(accountId:String,projectId:String,goalId:String,target:String,expectedRevision:Long):GoalResponse = db.tx { c ->
        requireProjectOwnership(c,accountId,projectId)
        val current=c.prepareStatement("SELECT * FROM goal WHERE id=?::uuid AND account_id=?::uuid AND project_id=?::uuid AND deleted_at IS NULL").use { ps -> ps.setString(1,goalId); ps.setString(2,accountId); ps.setString(3,projectId); ps.executeQuery().use { rs -> if(rs.next()) mapGoal(rs) else null } } ?: throw DomainException(DomainError("GOAL_NOT_FOUND",ErrorCategory.NOT_FOUND,"Goal not found"))
        if(current.revision!=expectedRevision) throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Goal revision conflict"))
        val core=Goal(id=current.id,accountId=accountId,projectId=projectId,title=current.title,description=current.description,priority=current.priority,status=GoalStatus.valueOf(current.status),completedAt=current.completedAt?.let(Instant::parse),revision=current.revision)
        val next=GoalEngine.transition(core,GoalStatus.valueOf(target))
        c.prepareStatement("UPDATE goal SET status=?::goal_status,completed_at=?,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=?").use { ps -> ps.setString(1,next.status.name); ps.setObject(2,next.completedAt?.let{java.time.OffsetDateTime.ofInstant(it,java.time.ZoneOffset.UTC)}); ps.setString(3,goalId); ps.setString(4,accountId); ps.setLong(5,expectedRevision); if(ps.executeUpdate()!=1) throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Goal transition conflict")) }
        val result=c.prepareStatement("SELECT * FROM goal WHERE id=?::uuid").use { ps -> ps.setString(1,goalId); ps.executeQuery().use {rs->rs.next();mapGoal(rs)} }
        if(result.status=="COMPLETED") insertActivity(c,accountId,"GOAL_COMPLETED",projectId,goalId,"goal-completed:$goalId:${result.revision}",true)
        result
    }

    private fun setInstruction(c:Connection,accountId:String,projectId:String,body:String){
        require(body.length<=8_000)
        c.prepareStatement("UPDATE project_instruction SET active=false,updated_at=now() WHERE project_id=?::uuid AND active=true").use {ps->ps.setString(1,projectId);ps.executeUpdate()}
        c.prepareStatement("INSERT INTO project_instruction(account_id,project_id,body,version) VALUES (?::uuid,?::uuid,?,COALESCE((SELECT max(version)+1 FROM project_instruction WHERE project_id=?::uuid),1))").use {ps->ps.setString(1,accountId);ps.setString(2,projectId);ps.setString(3,body);ps.setString(4,projectId);ps.executeUpdate()}
    }

    private fun getInConnection(c:Connection,accountId:String,id:String):ProjectResponse = c.prepareStatement("SELECT p.*, (SELECT body FROM project_instruction pi WHERE pi.project_id=p.id AND pi.active=true LIMIT 1) ai_instruction FROM project p WHERE p.id=?::uuid AND p.account_id=?::uuid AND p.deleted_at IS NULL").use {ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"));mapProject(rs,rs.getString("ai_instruction"))}}
    private fun requireProjectOwnership(c:Connection,accountId:String,id:String){ c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}} }
    private fun mapProject(rs:ResultSet, instruction:String?)=ProjectResponse(rs.getObject("id",UUID::class.java).toString(),rs.getString("title"),rs.getString("purpose"),rs.getString("template_type"),rs.getInt("priority"),rs.getString("status"),instruction,rs.getLong("revision"),rs.getObject("updated_at",java.time.OffsetDateTime::class.java).toInstant().toString(),rs.getObject("last_active_at",java.time.OffsetDateTime::class.java).toInstant().toString())
    private fun mapGoal(rs:ResultSet)=GoalResponse(rs.getObject("id",UUID::class.java).toString(),rs.getObject("project_id",UUID::class.java).toString(),rs.getString("title"),rs.getString("description"),rs.getInt("priority"),rs.getString("status"),rs.getLong("revision"),rs.getObject("completed_at",java.time.OffsetDateTime::class.java)?.toInstant()?.toString())
}

class MemoryRepository(private val db: Database) {
    fun create(accountId:String, req:MemoryCreateRequest):MemoryResponse {
        require(req.confidence in 0.0..1.0)
        require(req.statement.trim().length in 1..2000)
        val scope=MemoryScope.valueOf(req.scope)
        val category=MemoryCategory.valueOf(req.category)
        val origin=MemoryOrigin.valueOf(req.origin)
        val scopeId=req.scopeId
        if(scope==MemoryScope.PROJECT && scopeId==null) throw DomainException(DomainError("VALIDATION",ErrorCategory.VALIDATION,"Project memory requires scopeId"))
        return db.tx { c ->
            if(scope==MemoryScope.PROJECT) requireProject(c,accountId,scopeId!!)
            val canonical=req.statement.trim().lowercase(Locale.ROOT).replace(Regex("\\s+")," ")
            val existing=c.prepareStatement("SELECT * FROM memory_item WHERE account_id=?::uuid AND scope=? AND scope_id IS NOT DISTINCT FROM ?::uuid AND category=? AND lower(regexp_replace(trim(canonical_statement),'\\s+',' ','g'))=? AND status='ACTIVE' LIMIT 1").use { ps ->
                ps.setString(1,accountId);ps.setString(2,scope.name);ps.setString(3,scopeId);ps.setString(4,category.name);ps.setString(5,canonical);ps.executeQuery().use{rs->if(rs.next()) mapMemory(rs) else null}
            }
            val memory=if(existing!=null){
                c.prepareStatement("UPDATE memory_item SET confidence=greatest(confidence,?),updated_at=now(),revision=revision+1 WHERE id=?::uuid RETURNING *").use{ps->ps.setDouble(1,req.confidence);ps.setString(2,existing.id);ps.executeQuery().use{rs->rs.next();mapMemory(rs)}}
            }else{
                c.prepareStatement("INSERT INTO memory_item(account_id,scope,scope_id,category,canonical_statement,origin,confidence,status) VALUES (?::uuid,?,?::uuid,?,?,?,?,'ACTIVE') RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,scope.name);ps.setString(3,scopeId);ps.setString(4,category.name);ps.setString(5,req.statement.trim());ps.setString(6,origin.name);ps.setDouble(7,req.confidence);ps.executeQuery().use{rs->rs.next();mapMemory(rs)}}
            }
            c.prepareStatement("INSERT INTO memory_evidence(memory_id,account_id,kind,object_id) VALUES (?::uuid,?::uuid,?,?) ON CONFLICT DO NOTHING").use{ps->ps.setString(1,memory.id);ps.setString(2,accountId);ps.setString(3,req.evidenceKind.take(80));ps.setString(4,req.evidenceObjectId.take(200));ps.executeUpdate()}
            memory
        }
    }

    fun list(accountId:String, projectId:String?=null, limit:Int=100):List<MemoryResponse> = db.tx { c ->
        val sql=if(projectId==null) "SELECT * FROM memory_item WHERE account_id=?::uuid AND scope<>'PROJECT' AND status<>'ARCHIVED' ORDER BY updated_at DESC LIMIT ?" else "SELECT * FROM memory_item WHERE account_id=?::uuid AND (scope<>'PROJECT' OR scope_id=?::uuid) AND status<>'ARCHIVED' ORDER BY updated_at DESC LIMIT ?"
        c.prepareStatement(sql).use{ps->ps.setString(1,accountId);if(projectId==null)ps.setInt(2,limit.coerceIn(1,500)) else {ps.setString(2,projectId);ps.setInt(3,limit.coerceIn(1,500))};ps.executeQuery().use{rs->buildList{while(rs.next())add(mapMemory(rs))}}}
    }

    fun correct(accountId:String, id:String, replacement:MemoryCreateRequest):MemoryResponse = db.tx { c ->
        val old=c.prepareStatement("SELECT * FROM memory_item WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{rs->if(rs.next())mapMemory(rs)else null}} ?: throw DomainException(DomainError("MEMORY_NOT_FOUND",ErrorCategory.NOT_FOUND,"Memory not found"))
        c.prepareStatement("UPDATE memory_item SET status='USER_CORRECTED',updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeUpdate()}
        val req=replacement.copy(scope=old.scope,scopeId=old.scopeId,category=old.category,origin=MemoryOrigin.EXPLICIT_USER.name,confidence=1.0,evidenceKind="USER_CORRECTION",evidenceObjectId=id)
        createInConnection(c,accountId,req)
    }

    fun maturity(accountId:String):MemoryMaturityResponse = db.tx { c ->
        val created=c.prepareStatement("SELECT created_at FROM account WHERE id=?::uuid").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("ACCOUNT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Account not found"));rs.getObject(1,java.time.OffsetDateTime::class.java).toInstant()}}
        val memories=c.prepareStatement("SELECT * FROM memory_item WHERE account_id=?::uuid AND status='ACTIVE' ORDER BY updated_at DESC LIMIT 1000").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->buildList{while(rs.next())add(mapCoreMemory(c,rs))}}}
        val signals=c.prepareStatement("SELECT * FROM learning_signal WHERE account_id=?::uuid ORDER BY observed_at DESC LIMIT 1000").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->buildList{while(rs.next())add(LearningSignal(id=rs.getObject("id",UUID::class.java).toString(),accountId=accountId,projectId=rs.getObject("project_id",UUID::class.java)?.toString(),topic=rs.getString("topic"),kind=rs.getString("kind"),value=rs.getDouble("signal_value"),confidence=rs.getDouble("confidence"),evidenceIds=emptyList(),observedAt=rs.getObject("observed_at",java.time.OffsetDateTime::class.java).toInstant()))}}}
        val projects=c.prepareStatement("SELECT * FROM project WHERE account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,accountId);ps.executeQuery().use{rs->buildList{while(rs.next())add(Project(id=rs.getObject("id",UUID::class.java).toString(),accountId=accountId,title=rs.getString("title"),status=ProjectStatus.valueOf(rs.getString("status")),createdAt=rs.getObject("created_at",java.time.OffsetDateTime::class.java).toInstant(),updatedAt=rs.getObject("updated_at",java.time.OffsetDateTime::class.java).toInstant(),lastActiveAt=rs.getObject("last_active_at",java.time.OffsetDateTime::class.java).toInstant()))}}}
        val m=MemoryEngine.maturity(created,memories,signals,projects)
        MemoryMaturityResponse(m.score,m.state.name)
    }

    fun retrieveCore(accountId:String,projectId:String?,query:String,limit:Int=12):List<MemoryItem> = db.tx{c->
        val rows=c.prepareStatement("SELECT * FROM memory_item WHERE account_id=?::uuid AND status='ACTIVE' AND (scope<>'PROJECT' OR scope_id=?::uuid) ORDER BY updated_at DESC LIMIT 500").use{ps->ps.setString(1,accountId);ps.setString(2,projectId);ps.executeQuery().use{rs->buildList{while(rs.next())add(mapCoreMemory(c,rs))}}}
        MemoryEngine.rank(rows,accountId,projectId,query,limit=limit)
    }

    private fun createInConnection(c:Connection,accountId:String,req:MemoryCreateRequest):MemoryResponse{
        val scope=MemoryScope.valueOf(req.scope);val category=MemoryCategory.valueOf(req.category);val origin=MemoryOrigin.valueOf(req.origin)
        val m=c.prepareStatement("INSERT INTO memory_item(account_id,scope,scope_id,category,canonical_statement,origin,confidence,status,last_confirmed_at) VALUES (?::uuid,?,?::uuid,?,?,?,?,'ACTIVE',now()) RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,scope.name);ps.setString(3,req.scopeId);ps.setString(4,category.name);ps.setString(5,req.statement.trim());ps.setString(6,origin.name);ps.setDouble(7,req.confidence);ps.executeQuery().use{rs->rs.next();mapMemory(rs)}}
        c.prepareStatement("INSERT INTO memory_evidence(memory_id,account_id,kind,object_id) VALUES (?::uuid,?::uuid,?,?)").use{ps->ps.setString(1,m.id);ps.setString(2,accountId);ps.setString(3,req.evidenceKind);ps.setString(4,req.evidenceObjectId);ps.executeUpdate()}
        return m
    }
    private fun requireProject(c:Connection,accountId:String,id:String){c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}}}
    private fun mapMemory(rs:ResultSet)=MemoryResponse(rs.getObject("id",UUID::class.java).toString(),rs.getString("scope"),rs.getObject("scope_id",UUID::class.java)?.toString(),rs.getString("category"),rs.getString("canonical_statement"),rs.getString("origin"),rs.getDouble("confidence"),rs.getString("status"),rs.getLong("revision"))
    private fun mapCoreMemory(c:Connection,rs:ResultSet):MemoryItem{
        val id=rs.getObject("id",UUID::class.java).toString()
        val evidence=c.prepareStatement("SELECT id,kind,object_id,observed_at FROM memory_evidence WHERE memory_id=?::uuid").use{ps->ps.setString(1,id);ps.executeQuery().use{er->buildList{while(er.next())add(MemoryEvidence(id=er.getObject("id",UUID::class.java).toString(),kind=er.getString("kind"),objectId=er.getString("object_id"),observedAt=er.getObject("observed_at",java.time.OffsetDateTime::class.java).toInstant()))}}}
        return MemoryItem(id=id,accountId=rs.getObject("account_id",UUID::class.java).toString(),scope=MemoryScope.valueOf(rs.getString("scope")),scopeId=rs.getObject("scope_id",UUID::class.java)?.toString(),category=MemoryCategory.valueOf(rs.getString("category")),statement=rs.getString("canonical_statement"),origin=MemoryOrigin.valueOf(rs.getString("origin")),confidence=rs.getDouble("confidence"),evidence=evidence,status=MemoryStatus.valueOf(rs.getString("status")),createdAt=rs.getObject("created_at",java.time.OffsetDateTime::class.java).toInstant(),updatedAt=rs.getObject("updated_at",java.time.OffsetDateTime::class.java).toInstant(),revision=rs.getLong("revision"))
    }
}

class SourceRepository(private val db: Database) {

    fun list(accountId:String,limit:Int=100,offset:Int=0):List<SourceResponse> = db.tx{c->c.prepareStatement("SELECT * FROM source WHERE account_id=?::uuid AND deleted_at IS NULL ORDER BY pinned DESC,favorite DESC,updated_at DESC LIMIT ? OFFSET ?").use{ps->ps.setString(1,accountId);ps.setInt(2,limit.coerceIn(1,200));ps.setInt(3,offset.coerceAtLeast(0));ps.executeQuery().use{rs->buildList{while(rs.next())add(mapSource(rs))}}}}
    fun get(accountId:String,id:String):SourceResponse = db.tx{c->requireOwnedSource(c,accountId,id);c.prepareStatement("SELECT * FROM source WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{rs->rs.next();mapSource(rs)}}}
    fun update(accountId:String,id:String,req:UpdateSourceRequest):SourceResponse=db.tx{c->
        requireOwnedSource(c,accountId,id)
        val current=c.prepareStatement("SELECT title,favorite,pinned,archived_at,revision FROM source WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{rs->rs.next();arrayOf(rs.getString(1),rs.getBoolean(2),rs.getBoolean(3),rs.getObject(4),rs.getLong(5))}}
        val revision=current[4] as Long;if(revision!=req.expectedRevision)throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Source revision conflict"))
        val title=req.title?.trim()?.also{if(it.isEmpty()||it.length>240)throw validation("Invalid source title")} ?: current[0] as String
        val archived=req.archived ?: (current[3]!=null)
        c.prepareStatement("UPDATE source SET title=?,favorite=?,pinned=?,archived_at=CASE WHEN ? THEN COALESCE(archived_at,now()) ELSE NULL END,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? RETURNING *").use{ps->ps.setString(1,title);ps.setBoolean(2,req.favorite?:current[1] as Boolean);ps.setBoolean(3,req.pinned?:current[2] as Boolean);ps.setBoolean(4,archived);ps.setString(5,id);ps.setString(6,accountId);ps.setLong(7,revision);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Source revision conflict"));mapSource(rs)}}
    }
    fun delete(accountId:String,id:String,expectedRevision:Long){db.tx{c->requireOwnedSource(c,accountId,id);c.prepareStatement("UPDATE source SET deleted_at=now(),updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.setLong(3,expectedRevision);if(ps.executeUpdate()!=1)throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Source revision conflict"))}}}
    fun createMetadata(accountId:String,req:SourceCreateRequest):SourceResponse{
        require(req.title.trim().length in 1..240);require(req.contentHash.matches(Regex("[a-fA-F0-9]{32,128}")));require(req.sizeBytes>=0)
        return db.tx{c->
            val existing=c.prepareStatement("SELECT * FROM source WHERE account_id=?::uuid AND content_hash=? AND deleted_at IS NULL LIMIT 1").use{ps->ps.setString(1,accountId);ps.setString(2,req.contentHash.lowercase());ps.executeQuery().use{rs->if(rs.next())mapSource(rs)else null}}
            existing ?: c.prepareStatement("INSERT INTO source(account_id,title,source_type,mime_type,content_hash,size_bytes,state,processing_progress) VALUES (?::uuid,?,?,?,?,?,'UPLOADING',0) RETURNING *").use{ps->ps.setString(1,accountId);ps.setString(2,req.title.trim());ps.setString(3,req.type.take(40));ps.setString(4,req.mimeType.take(160));ps.setString(5,req.contentHash.lowercase());ps.setLong(6,req.sizeBytes);ps.executeQuery().use{rs->rs.next();mapSource(rs)}}
        }
    }

    fun ingestText(accountId:String, sourceId:String, text:String):SourceResponse{
        require(text.length in 1..5_000_000)
        val chunks=com.veltrix.hom.vnext.server.foundation.Chunker.chunk(accountId,sourceId,1,text)
        return db.tx{c->
            val owned=c.prepareStatement("SELECT * FROM source WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,sourceId);ps.setString(2,accountId);ps.executeQuery().use{rs->if(rs.next())mapSource(rs)else null}} ?: throw DomainException(DomainError("SOURCE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Source not found"))
            if(owned.mimeType !in setOf("text/plain","text/markdown")) throw DomainException(DomainError("SOURCE_UNSUPPORTED",ErrorCategory.SOURCE_PROCESSING,"Direct text ingestion only supports text/plain and text/markdown"))
            c.prepareStatement("INSERT INTO source_version(source_id,account_id,version,content_hash) VALUES (?::uuid,?::uuid,1,?) ON CONFLICT (source_id,version) DO NOTHING").use{ps->ps.setString(1,sourceId);ps.setString(2,accountId);ps.setString(3,owned.contentHash);ps.executeUpdate()}
            c.prepareStatement("DELETE FROM source_chunk WHERE source_id=?::uuid AND source_version=1").use{ps->ps.setString(1,sourceId);ps.executeUpdate()}
            c.prepareStatement("INSERT INTO source_chunk(id,account_id,source_id,source_version,offset_start,offset_end,chunk_text,text_hash) VALUES (?::uuid,?::uuid,?::uuid,1,?,?,?,?)").use{ps->
                for(ch in chunks){ps.setString(1,ch.id);ps.setString(2,accountId);ps.setString(3,sourceId);ps.setInt(4,ch.offsetStart);ps.setInt(5,ch.offsetEnd);ps.setString(6,ch.text);ps.setString(7,ch.textHash);ps.addBatch()};ps.executeBatch()
            }
            val updated=c.prepareStatement("UPDATE source SET state='PROCESSING',processing_progress=75,updated_at=now(),revision=revision+1 WHERE id=?::uuid RETURNING *").use{ps->ps.setString(1,sourceId);ps.executeQuery().use{rs->rs.next();mapSource(rs)}}
            insertActivity(c,accountId,"SOURCE_ADDED",null,sourceId,"source-added:$sourceId",true)
            updated
        }
    }

    fun ingestExtractedText(accountId:String, sourceId:String, text:String):SourceResponse {
        if(text.isBlank() || text.length > 10_000_000) throw DomainException(DomainError("SOURCE_PROCESSING_FAILED",ErrorCategory.SOURCE_PROCESSING,"Extracted text is empty or too large"))
        val chunks=com.veltrix.hom.vnext.server.foundation.Chunker.chunk(accountId,sourceId,1,text)
        return db.tx{c->
            val owned=c.prepareStatement("SELECT * FROM source WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,sourceId);ps.setString(2,accountId);ps.executeQuery().use{rs->if(rs.next())mapSource(rs)else null}} ?: throw DomainException(DomainError("SOURCE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Source not found"))
            c.prepareStatement("INSERT INTO source_version(source_id,account_id,version,content_hash,extraction_metadata) VALUES (?::uuid,?::uuid,1,?,?::jsonb) ON CONFLICT (source_id,version) DO UPDATE SET content_hash=excluded.content_hash,extraction_metadata=excluded.extraction_metadata").use{ps->ps.setString(1,sourceId);ps.setString(2,accountId);ps.setString(3,owned.contentHash);ps.setString(4,"{\"processor\":\"server-extraction\"}");ps.executeUpdate()}
            c.prepareStatement("DELETE FROM source_chunk WHERE source_id=?::uuid AND source_version=1").use{ps->ps.setString(1,sourceId);ps.executeUpdate()}
            c.prepareStatement("INSERT INTO source_chunk(id,account_id,source_id,source_version,offset_start,offset_end,chunk_text,text_hash) VALUES (?::uuid,?::uuid,?::uuid,1,?,?,?,?)").use{ps->for(ch in chunks){ps.setString(1,ch.id);ps.setString(2,accountId);ps.setString(3,sourceId);ps.setInt(4,ch.offsetStart);ps.setInt(5,ch.offsetEnd);ps.setString(6,ch.text);ps.setString(7,ch.textHash);ps.addBatch()};ps.executeBatch()}
            val updated=c.prepareStatement("UPDATE source SET state='PROCESSING',processing_progress=75,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid RETURNING *").use{ps->ps.setString(1,sourceId);ps.setString(2,accountId);ps.executeQuery().use{rs->rs.next();mapSource(rs)}}
            insertActivity(c,accountId,"SOURCE_ADDED",null,sourceId,"source-added:$sourceId",true)
            updated
        }
    }

    fun markProcessingFailure(accountId:String,sourceId:String,state:String,progress:Int=0):SourceResponse=db.tx{c->
        if(state !in setOf("FAILED","PARTIAL","UNSUPPORTED"))throw validation("Invalid source failure state")
        c.prepareStatement("UPDATE source SET state=?::source_state,processing_progress=?,updated_at=now(),revision=revision+1 WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL RETURNING *").use{ps->ps.setString(1,state);ps.setInt(2,progress.coerceIn(0,99));ps.setString(3,sourceId);ps.setString(4,accountId);ps.executeQuery().use{rs->if(!rs.next())throw DomainException(DomainError("SOURCE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Source not found"));mapSource(rs)}}
    }

    fun search(accountId:String,req:SourceSearchRequest):List<CitationResponse> = db.tx{c->
        val limit=req.limit.coerceIn(1,30)
        val sourceFilter=if(req.sourceIds.isEmpty())"" else " AND sc.source_id = ANY(?::uuid[])"
        val sql="SELECT sc.*, ts_rank_cd(sc.search_vector, plainto_tsquery('simple', ?)) rank FROM source_chunk sc JOIN source s ON s.id=sc.source_id WHERE sc.account_id=?::uuid AND s.deleted_at IS NULL $sourceFilter AND sc.search_vector @@ plainto_tsquery('simple', ?) ORDER BY rank DESC, sc.id LIMIT ?"
        c.prepareStatement(sql).use{ps->var i=1;ps.setString(i++,req.query);ps.setString(i++,accountId);if(req.sourceIds.isNotEmpty()){val arr=c.createArrayOf("uuid",req.sourceIds.map(UUID::fromString).toTypedArray());ps.setArray(i++,arr)};ps.setString(i++,req.query);ps.setInt(i,limit);ps.executeQuery().use{rs->buildList{while(rs.next())add(CitationResponse(rs.getObject("source_id",UUID::class.java).toString(),rs.getLong("source_version"),rs.getObject("id",UUID::class.java).toString(),rs.getObject("page") as? Int,rs.getString("section"),rs.getDouble("rank").coerceIn(0.0,1.0),rs.getString("text_hash"),rs.getString("chunk_text").take(420)))}}}
    }

    fun linkProject(accountId:String,sourceId:String,projectId:String){db.tx{c->requireOwnedSource(c,accountId,sourceId);c.prepareStatement("SELECT 1 FROM project WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,projectId);ps.setString(2,accountId);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("PROJECT_NOT_FOUND",ErrorCategory.NOT_FOUND,"Project not found"))}};c.prepareStatement("INSERT INTO source_project_link(account_id,source_id,project_id) VALUES (?::uuid,?::uuid,?::uuid) ON CONFLICT DO NOTHING").use{ps->ps.setString(1,accountId);ps.setString(2,sourceId);ps.setString(3,projectId);ps.executeUpdate()}}}
    fun unlinkProject(accountId:String,sourceId:String,projectId:String){db.tx{c->requireOwnedSource(c,accountId,sourceId);c.prepareStatement("DELETE FROM source_project_link WHERE account_id=?::uuid AND source_id=?::uuid AND project_id=?::uuid").use{ps->ps.setString(1,accountId);ps.setString(2,sourceId);ps.setString(3,projectId);ps.executeUpdate()}}}
    private fun requireOwnedSource(c:Connection,accountId:String,id:String){c.prepareStatement("SELECT 1 FROM source WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use{ps->ps.setString(1,id);ps.setString(2,accountId);ps.executeQuery().use{if(!it.next())throw DomainException(DomainError("SOURCE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Source not found"))}}}
    private fun mapSource(rs:ResultSet)=SourceResponse(rs.getObject("id",UUID::class.java).toString(),rs.getString("title"),rs.getString("source_type"),rs.getString("mime_type"),rs.getString("content_hash"),rs.getString("state"),rs.getInt("processing_progress"),rs.getLong("revision"))
}
