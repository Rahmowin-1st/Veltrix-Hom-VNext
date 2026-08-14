package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.core.UniversalCommandEngine
import com.veltrix.hom.vnext.core.UniversalCommandKind
import kotlinx.serialization.json.Json
import java.sql.Connection
import java.time.Instant
import java.time.OffsetDateTime

internal class Part3ExperienceRepository(
    private val db: Database,
    private val projects: ProjectRepository,
) {
    private val json = Json { ignoreUnknownKeys = false }

    fun resolveCommand(accountId: String, req: UniversalCommandRequest): UniversalCommandResponse {
        db.tx { c ->
            req.projectId?.let { owned(c, "project", accountId, it) }
            req.sourceIds.distinct().forEach { owned(c, "source", accountId, it) }
            req.conversationId?.let { owned(c, "conversation", accountId, it) }
        }
        val resolution = UniversalCommandEngine.resolve(req.text)
        var targetId: String? = null
        var deepLink: String? = null
        if (resolution.kind == UniversalCommandKind.OPEN_PROJECT && !resolution.targetHint.isNullOrBlank()) {
            targetId = db.tx { c ->
                c.prepareStatement(
                    "SELECT id FROM project WHERE account_id=?::uuid AND deleted_at IS NULL AND lower(title)=lower(?) ORDER BY last_active_at DESC LIMIT 1"
                ).use { ps ->
                    ps.setString(1, accountId)
                    ps.setString(2, resolution.targetHint)
                    ps.executeQuery().use { rs -> if (rs.next()) rs.getObject(1).toString() else null }
                }
            }
            deepLink = targetId?.let { "veltrix://project/$it" }
        } else {
            deepLink = when (resolution.kind) {
                UniversalCommandKind.SHOW_MISTAKES -> "veltrix://mistakes"
                UniversalCommandKind.CONTINUE_SOURCE -> req.sourceIds.firstOrNull()?.let { "veltrix://source/$it" }
                UniversalCommandKind.CREATE_FLASHCARDS -> "veltrix://flashcards/create"
                UniversalCommandKind.START_PRACTICE -> "veltrix://practice/create"
                UniversalCommandKind.START_TEST -> "veltrix://assessment/create?kind=TEST"
                UniversalCommandKind.CALCULATE -> "veltrix://calculator"
                UniversalCommandKind.TRANSLATE -> "veltrix://translate"
                UniversalCommandKind.SEARCH -> "veltrix://search"
                else -> null
            }
        }
        return UniversalCommandResponse(
            kind = resolution.kind.name,
            deterministic = resolution.deterministic,
            requiresConfirmation = resolution.requiresConfirmation,
            targetId = targetId,
            deepLink = deepLink,
            query = resolution.query,
            projectId = req.projectId,
            sourceIds = req.sourceIds.distinct(),
            diagnostics = mapOf("scopeValidated" to "true", "aiRequired" to (!resolution.deterministic).toString()),
        )
    }

    fun templates(): List<ProjectTemplateDefinitionResponse> = db.tx { c ->
        c.prepareStatement(
            "SELECT * FROM project_template_definition WHERE state='ACTIVE' ORDER BY template_id,version DESC"
        ).use { ps ->
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            ProjectTemplateDefinitionResponse(
                                templateId = rs.getString("template_id"),
                                version = rs.getInt("version"),
                                titleKey = rs.getString("title_key"),
                                defaultLearningMode = rs.getString("default_learning_mode"),
                                moduleSeedJson = rs.getString("module_seed"),
                                goalSuggestionsJson = rs.getString("goal_suggestions"),
                                sourcePolicyJson = rs.getString("source_policy"),
                                analyticsConfigJson = rs.getString("analytics_config"),
                                state = rs.getString("state"),
                            )
                        )
                    }
                }
            }
        }
    }

    fun customizeProject(accountId: String, id: String, req: ProjectCustomizationPutRequest): ProjectResponse {
        db.tx { c ->
            owned(c, "project", accountId, id)
            listOf(
                req.defaultSourcePolicyJson,
                req.moduleEnablementJson,
                req.moduleOrderJson,
                req.layoutPriorityJson,
                req.pinnedModulesJson,
                req.customQuickActionsJson,
            ).filterNotNull().forEach(::validJson)
            c.prepareStatement(
                """UPDATE project SET
                    description=COALESCE(?,description),icon_key=COALESCE(?,icon_key),cover_asset_key=COALESCE(?,cover_asset_key),
                    accent_token=COALESCE(?,accent_token),subject_type=COALESCE(?,subject_type),
                    default_source_policy=COALESCE(?::jsonb,default_source_policy),module_enablement=COALESCE(?::jsonb,module_enablement),
                    module_order=COALESCE(?::jsonb,module_order),layout_priority=COALESCE(?::jsonb,layout_priority),
                    pinned_modules=COALESCE(?::jsonb,pinned_modules),custom_quick_actions=COALESCE(?::jsonb,custom_quick_actions),
                    revision=revision+1,updated_at=now(),last_active_at=now()
                    WHERE id=?::uuid AND account_id=?::uuid AND revision=? AND deleted_at IS NULL"""
            ).use { ps ->
                ps.setString(1, req.description?.take(4000)); ps.setString(2, req.iconKey?.take(120))
                ps.setString(3, req.coverAssetKey?.take(240)); ps.setString(4, req.accentToken?.take(120))
                ps.setString(5, req.subjectType?.take(120)); ps.setString(6, req.defaultSourcePolicyJson)
                ps.setString(7, req.moduleEnablementJson); ps.setString(8, req.moduleOrderJson)
                ps.setString(9, req.layoutPriorityJson); ps.setString(10, req.pinnedModulesJson)
                ps.setString(11, req.customQuickActionsJson); ps.setString(12, id); ps.setString(13, accountId)
                ps.setLong(14, req.expectedRevision)
                if (ps.executeUpdate() != 1) throw conflict("Project revision conflict")
            }
            c.prepareStatement(
                "INSERT INTO frontend_semantic_event(account_id,event_type,entity_id,payload,revision,idempotency_key) VALUES(?::uuid,'PROJECT_PROGRESS_CHANGED',?,jsonb_build_object('projectId',?,'operation','CUSTOMIZE'),?,?) ON CONFLICT(account_id,idempotency_key) DO NOTHING"
            ).use { ps ->
                ps.setString(1, accountId); ps.setString(2, id); ps.setString(3, id)
                ps.setLong(4, req.expectedRevision + 1); ps.setString(5, "project-customize:$id:${req.expectedRevision + 1}")
                ps.executeUpdate()
            }
        }
        return projects.get(accountId, id)
    }

    fun mapStages(accountId: String, unitId: String): List<MapUnitStageResponse> = db.tx { c ->
        c.prepareStatement(
            """SELECT s.unit_id,s.stage,s.ordinal,s.completion_criteria,s.content_reference
               FROM map_unit_stage s
               WHERE s.unit_id=? AND EXISTS(
                 SELECT 1 FROM personal_map pm JOIN map_unit_progress p ON p.personal_map_id=pm.id AND p.account_id=pm.account_id
                 WHERE pm.account_id=?::uuid AND p.unit_id=s.unit_id)
               ORDER BY s.ordinal"""
        ).use { ps ->
            ps.setString(1, unitId); ps.setString(2, accountId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(MapUnitStageResponse(rs.getString(1), rs.getString(2), rs.getInt(3), rs.getString(4), rs.getString(5)))
                }
            }
        }
    }

    fun seasonHistory(accountId: String, limit: Int): List<SeasonHistoryResponse> = db.tx { c ->
        c.prepareStatement(
            """SELECT s.season_id,s.version,s.state,s.start_at,s.end_at,s.identity_metadata,
                      COALESCE(p.units_completed,0),COALESCE(p.xp_earned,0),COALESCE(p.coins_earned,0)
               FROM season_progress p JOIN season_definition s ON s.season_id=p.season_id AND s.version=p.season_version
               WHERE p.account_id=?::uuid ORDER BY s.start_at DESC LIMIT ?"""
        ).use { ps ->
            ps.setString(1, accountId); ps.setInt(2, limit.coerceIn(1, 100))
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            SeasonHistoryResponse(
                                rs.getString(1), rs.getInt(2), rs.getString(3),
                                rs.getObject(4, OffsetDateTime::class.java).toInstant().toString(),
                                rs.getObject(5, OffsetDateTime::class.java).toInstant().toString(),
                                rs.getString(6), rs.getInt(7), rs.getLong(8), rs.getLong(9),
                            )
                        )
                    }
                }
            }
        }
    }

    fun avatarCatalog(accountId: String): List<AvatarCatalogFinalResponse> = db.tx { c ->
        c.prepareStatement(
            """SELECT a.*,o.item_id IS NOT NULL AS owned,e.avatar_id=a.avatar_id AS equipped
               FROM avatar_catalog a
               LEFT JOIN inventory_ownership o ON o.account_id=?::uuid AND o.item_id=a.avatar_id
               LEFT JOIN equipped_avatar e ON e.account_id=?::uuid
               WHERE a.active=true ORDER BY a.rarity_order,a.permanent_name"""
        ).use { ps ->
            ps.setString(1, accountId); ps.setString(2, accountId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val storePrice = if (rs.getObject("store_price") == null) null else rs.getLong("store_price")
                        add(
                            AvatarCatalogFinalResponse(
                                avatarId = rs.getString("avatar_id"), permanentName = rs.getString("permanent_name"),
                                assetKey = rs.getString("asset_key"), tier = rs.getString("tier"), rarityOrder = rs.getInt("rarity_order"),
                                identityMetadataJson = rs.getString("identity_metadata"), animationCapabilitiesJson = rs.getString("animation_capabilities"),
                                behaviorCapabilitiesJson = rs.getString("behavior_capabilities"), previewCapabilitiesJson = rs.getString("preview_capabilities"),
                                owned = rs.getBoolean("owned"), equipped = rs.getBoolean("equipped"), storePrice = storePrice,
                                catalogVersion = rs.getString("catalog_version"),
                            )
                        )
                    }
                }
            }
        }
    }

    fun frontendEvents(accountId: String, limit: Int, offset: Int): List<FrontendSemanticEventResponse> = db.tx { c ->
        c.prepareStatement(
            "SELECT * FROM frontend_semantic_event WHERE account_id=?::uuid ORDER BY occurred_at DESC,event_id DESC LIMIT ? OFFSET ?"
        ).use { ps ->
            ps.setString(1, accountId); ps.setInt(2, limit.coerceIn(1, 200)); ps.setInt(3, offset.coerceAtLeast(0))
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            FrontendSemanticEventResponse(
                                eventId = rs.getObject("event_id").toString(), type = rs.getString("event_type"),
                                entityId = rs.getString("entity_id"), payloadJson = rs.getString("payload"),
                                occurredAt = rs.getObject("occurred_at", OffsetDateTime::class.java).toInstant().toString(),
                                revision = rs.getLong("revision"), idempotencyKey = rs.getString("idempotency_key"),
                            )
                        )
                    }
                }
            }
        }
    }

    fun timeline(
        accountId: String,
        projectId: String?,
        type: String?,
        from: String?,
        to: String?,
        query: String?,
        limit: Int,
        offset: Int,
    ): ActivityTimelineQueryResponse = db.tx { c ->
        projectId?.let { owned(c, "project", accountId, it) }
        val fromInstant = from?.let(::parseInstant)
        val toInstant = to?.let(::parseInstant)
        val normalizedQuery = query?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        val sql = StringBuilder("SELECT event_id,event_type,occurred_at,project_id,object_id,meaningful FROM activity_event WHERE account_id=?::uuid")
        if (projectId != null) sql.append(" AND project_id=?::uuid")
        if (type != null) sql.append(" AND event_type=?")
        if (fromInstant != null) sql.append(" AND occurred_at>=?::timestamptz")
        if (toInstant != null) sql.append(" AND occurred_at<=?::timestamptz")
        if (normalizedQuery != null) sql.append(" AND(lower(event_type) LIKE lower(?) OR lower(COALESCE(object_id,'')) LIKE lower(?) OR lower(metadata::text) LIKE lower(?))")
        sql.append(" ORDER BY occurred_at DESC,event_id DESC LIMIT ? OFFSET ?")
        val cap = limit.coerceIn(1, 200)
        val items = c.prepareStatement(sql.toString()).use { ps ->
            var index = 1
            ps.setString(index++, accountId)
            if (projectId != null) ps.setString(index++, projectId)
            if (type != null) ps.setString(index++, type.take(100))
            if (fromInstant != null) ps.setString(index++, fromInstant.toString())
            if (toInstant != null) ps.setString(index++, toInstant.toString())
            if (normalizedQuery != null) {
                val like = "%$normalizedQuery%"
                ps.setString(index++, like); ps.setString(index++, like); ps.setString(index++, like)
            }
            ps.setInt(index++, cap + 1); ps.setInt(index, offset.coerceAtLeast(0))
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        val eventType = rs.getString(2)
                        val objectId = rs.getString(5)
                        add(
                            ActivityTimelineItemResponse(
                                eventId = rs.getObject(1).toString(), type = eventType,
                                occurredAt = rs.getObject(3, OffsetDateTime::class.java).toInstant().toString(),
                                projectId = rs.getObject(4)?.toString(), objectId = objectId,
                                meaningful = rs.getBoolean(6), deepLink = deepLink(eventType, objectId),
                            )
                        )
                    }
                }
            }
        }
        ActivityTimelineQueryResponse(items.take(cap), if (items.size > cap) offset + cap else null)
    }

    private fun owned(c: Connection, table: String, accountId: String, id: String) {
        require(table in setOf("project", "source", "conversation"))
        c.prepareStatement("SELECT 1 FROM $table WHERE id=?::uuid AND account_id=?::uuid AND deleted_at IS NULL").use { ps ->
            ps.setString(1, id); ps.setString(2, accountId)
            ps.executeQuery().use { if (!it.next()) throw notFound("Owned object not found") }
        }
    }

    private fun validJson(raw: String) {
        runCatching { json.parseToJsonElement(raw) }.getOrElse { throw validation("Project customization must be valid JSON") }
    }

    private fun parseInstant(raw: String): Instant = runCatching { Instant.parse(raw) }.getOrElse { throw validation("Invalid ISO-8601 instant") }

    private fun deepLink(type: String, objectId: String?): String? = when (type) {
        "PROJECT_CREATED", "PROJECT_UPDATED", "GOAL_COMPLETED" -> objectId?.let { "veltrix://project/$it" }
        "SOURCE_ADDED", "SOURCE_READY" -> objectId?.let { "veltrix://source/$it" }
        "TEST_COMPLETED", "QUIZ_COMPLETED" -> objectId?.let { "veltrix://assessment/$it" }
        "MISTAKE_RESOLVED" -> objectId?.let { "veltrix://mistake/$it" }
        else -> objectId?.let { "veltrix://activity/$it" }
    }

    private fun conflict(message: String) = com.veltrix.hom.vnext.core.DomainException(
        com.veltrix.hom.vnext.core.DomainError("CONFLICT", ErrorCategory.CONFLICT, message)
    )

    private fun notFound(message: String) = com.veltrix.hom.vnext.core.DomainException(
        com.veltrix.hom.vnext.core.DomainError("NOT_FOUND", ErrorCategory.NOT_FOUND, message)
    )
}
