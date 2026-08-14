package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.server.foundation.PasswordHasher
import java.time.OffsetDateTime
import java.time.ZoneOffset

class SettingsRepository(private val db: Database) {
    fun list(accountId: String, category: String? = null): List<SettingResponse> = db.tx { c ->
        val sql = if (category == null)
            "SELECT category,setting_key,setting_value::text,revision,updated_at FROM user_setting WHERE account_id=?::uuid ORDER BY category,setting_key"
        else
            "SELECT category,setting_key,setting_value::text,revision,updated_at FROM user_setting WHERE account_id=?::uuid AND category=? ORDER BY setting_key"
        c.prepareStatement(sql).use { ps ->
            ps.setString(1, accountId)
            if (category != null) ps.setString(2, category.trim().uppercase().take(40))
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(SettingResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getObject(5, OffsetDateTime::class.java).toInstant().toString())) } }
        }
    }

    fun put(accountId: String, req: SettingPutRequest): SettingResponse = db.tx { c ->
        val category = req.category.trim().uppercase().also { require(it in allowedCategories) { "Unsupported settings category" } }
        val key = req.key.trim().also { require(it.matches(Regex("[A-Za-z0-9_.-]{1,80}"))) { "Invalid setting key" } }
        val value = req.jsonValue.trim().also { require(it.length in 1..16_384) { "Setting value too large" } }
        c.prepareStatement("""
            INSERT INTO user_setting(account_id,category,setting_key,setting_value)
            VALUES (?::uuid,?,?,?::jsonb)
            ON CONFLICT(account_id,category,setting_key) DO UPDATE
            SET setting_value=EXCLUDED.setting_value,updated_at=now(),revision=user_setting.revision+1
            RETURNING category,setting_key,setting_value::text,revision,updated_at
        """.trimIndent()).use { ps ->
            ps.setString(1, accountId); ps.setString(2, category); ps.setString(3, key); ps.setString(4, value)
            runCatching { ps.executeQuery() }.getOrElse { throw DomainException(DomainError("VALIDATION", ErrorCategory.VALIDATION, "setting jsonValue must be valid JSON")) }.use { rs ->
                rs.next(); SettingResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getObject(5, OffsetDateTime::class.java).toInstant().toString())
            }
        }
    }

    companion object {
        val allowedCategories = setOf("ACCOUNT", "AI", "MEMORY", "LEARNING", "NOTIFICATIONS", "DATA", "ACCESSIBILITY", "PRIVACY", "PROJECT_DEFAULTS")
    }
}

class NotificationRepository(private val db: Database) {
    fun preferences(accountId: String): List<NotificationPreferenceResponse> = db.tx { c ->
        c.prepareStatement("SELECT category,enabled,quiet_hours::text,timezone,revision,updated_at FROM notification_preference WHERE account_id=?::uuid ORDER BY category").use { ps ->
            ps.setString(1, accountId); ps.executeQuery().use { rs -> buildList { while (rs.next()) add(NotificationPreferenceResponse(rs.getString(1),rs.getBoolean(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getObject(6,OffsetDateTime::class.java).toInstant().toString())) } }
        }
    }

    fun putPreference(accountId: String, req: NotificationPreferencePutRequest): NotificationPreferenceResponse = db.tx { c ->
        val category=req.category.trim().uppercase().also { require(it in allowedCategories) { "Unsupported notification category" } }
        val quiet=req.quietHoursJson.trim().ifEmpty { "{}" }.also { require(it.length <= 2048) }
        val timezone=req.timezone.trim().take(64).ifEmpty { "UTC" }
        c.prepareStatement("""
            INSERT INTO notification_preference(account_id,category,enabled,quiet_hours,timezone)
            VALUES (?::uuid,?,?,?::jsonb,?)
            ON CONFLICT(account_id,category) DO UPDATE SET enabled=EXCLUDED.enabled,quiet_hours=EXCLUDED.quiet_hours,timezone=EXCLUDED.timezone,updated_at=now(),revision=notification_preference.revision+1
            RETURNING category,enabled,quiet_hours::text,timezone,revision,updated_at
        """.trimIndent()).use { ps ->
            ps.setString(1,accountId);ps.setString(2,category);ps.setBoolean(3,req.enabled);ps.setString(4,quiet);ps.setString(5,timezone)
            runCatching { ps.executeQuery() }.getOrElse { throw DomainException(DomainError("VALIDATION",ErrorCategory.VALIDATION,"quietHoursJson must be valid JSON")) }.use { rs -> rs.next(); NotificationPreferenceResponse(rs.getString(1),rs.getBoolean(2),rs.getString(3),rs.getString(4),rs.getLong(5),rs.getObject(6,OffsetDateTime::class.java).toInstant().toString()) }
        }
    }

    fun listIntents(accountId:String,limit:Int):List<NotificationIntentResponse> = db.tx { c ->
        c.prepareStatement("SELECT id,project_id,category,payload::text,scheduled_for,status,created_at FROM notification_intent WHERE account_id=?::uuid ORDER BY created_at DESC LIMIT ?").use { ps ->
            ps.setString(1,accountId);ps.setInt(2,limit.coerceIn(1,200));ps.executeQuery().use { rs -> buildList { while(rs.next()) add(NotificationIntentResponse(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getObject(5,OffsetDateTime::class.java)?.toInstant()?.toString(),rs.getString(6),rs.getObject(7,OffsetDateTime::class.java).toInstant().toString())) } }
        }
    }

    companion object { val allowedCategories=setOf("PROCESSING_COMPLETE","PROJECT_REMINDER","ASSESSMENT_REMINDER","FLASHCARD_DUE","SYSTEM_NOTICE","ACCOUNT_SECURITY") }
}

class AccountDataRepository(private val db: Database) {
    fun export(accountId:String):AccountExportResponse = db.tx { c ->
        val counts=linkedMapOf<String,Long>()
        for ((name,table) in countTables) {
            c.prepareStatement("SELECT count(*) FROM $table WHERE account_id=?::uuid").use { ps -> ps.setString(1,accountId);ps.executeQuery().use { rs -> rs.next(); counts[name]=rs.getLong(1) } }
        }
        val profile=c.prepareStatement("SELECT display_name,preferred_language,timezone,onboarding_complete,memory_enabled FROM user_profile WHERE account_id=?::uuid").use { ps -> ps.setString(1,accountId);ps.executeQuery().use { rs -> if(!rs.next()) throw DomainException(DomainError("PROFILE_NOT_FOUND",ErrorCategory.NOT_FOUND,"Profile not found")); AccountExportProfile(rs.getString(1),rs.getString(2),rs.getString(3),rs.getBoolean(4),rs.getBoolean(5)) } }
        AccountExportResponse(accountId,OffsetDateTime.now(ZoneOffset.UTC).toInstant().toString(),profile,counts)
    }

    fun requestDeletion(accountId:String, req:AccountDeletionRequest) {
        if(req.confirmation != "DELETE") throw DomainException(DomainError("VALIDATION",ErrorCategory.VALIDATION,"Explicit DELETE confirmation is required"))
        require(req.password.length in 12..1024) { "Password required" }
        db.tx { c ->
            val hash=c.prepareStatement("SELECT password_hash FROM account_credential WHERE account_id=?::uuid").use { ps -> ps.setString(1,accountId);ps.executeQuery().use { rs -> if(rs.next()) rs.getString(1) else null } } ?: throw DomainException(DomainError("AUTH_INVALID",ErrorCategory.AUTH,"Re-authentication failed"))
            if(!PasswordHasher.verify(req.password.toCharArray(),hash)) throw DomainException(DomainError("AUTH_INVALID",ErrorCategory.AUTH,"Re-authentication failed"))
            c.prepareStatement("UPDATE account SET deleted_at=now(),updated_at=now(),revision=revision+1 WHERE id=?::uuid AND deleted_at IS NULL").use { ps -> ps.setString(1,accountId); if(ps.executeUpdate()!=1) throw DomainException(DomainError("CONFLICT",ErrorCategory.CONFLICT,"Account is already deleted")) }
            c.prepareStatement("UPDATE device_session SET revoked_at=COALESCE(revoked_at,now()) WHERE account_id=?::uuid").use { ps -> ps.setString(1,accountId);ps.executeUpdate() }
        }
    }

    companion object {
        val countTables=listOf(
            "projects" to "project",
            "conversations" to "conversation",
            "sources" to "source",
            "notes" to "note",
            "assessments" to "assessment",
            "practiceSessions" to "practice_session",
            "flashcards" to "flashcard",
            "mistakes" to "mistake",
            "memories" to "memory_item",
            "activityEvents" to "activity_event",
            // Part 2 account-owned state. Definition/catalog tables are intentionally excluded because they are global product data.
            "progressionProfiles" to "progression_profile",
            "xpLedger" to "xp_ledger",
            "coinAccounts" to "coin_account_projection",
            "coinLedger" to "coin_ledger",
            "rewardGrants" to "reward_grant",
            "rewardDecisions" to "reward_decision_log",
            "rewardQueue" to "activity_reward_queue",
            "dailyActivity" to "daily_activity_state",
            "consistencyState" to "consistency_state",
            "consistencyHistory" to "consistency_history",
            "achievementProgress" to "achievement_progress",
            "inventoryOwnership" to "inventory_ownership",
            "equippedAvatars" to "equipped_avatar",
            "storePurchases" to "store_purchase",
            "storeRefunds" to "store_refund",
            "personalMaps" to "personal_map",
            "mapGenerations" to "map_generation_record",
            "mapUnitProgress" to "map_unit_progress",
            "seasonProgress" to "season_progress",
            "gamingStatistics" to "gaming_statistics",
            "gameStateEvents" to "game_state_event",
        )
    }
}
