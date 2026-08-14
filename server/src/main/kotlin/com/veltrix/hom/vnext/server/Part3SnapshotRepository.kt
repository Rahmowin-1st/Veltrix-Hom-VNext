package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.HomeInsightEngine
import com.veltrix.hom.vnext.core.HomePriorityCandidate
import com.veltrix.hom.vnext.core.HomePriorityEngine
import com.veltrix.hom.vnext.core.LevelCurveV1
import com.veltrix.hom.vnext.core.WorldContinuityEngine
import java.sql.Connection
import java.time.Instant
import java.time.OffsetDateTime

internal class Part3SnapshotRepository(
    private val db: Database,
    private val projects: ProjectRepository,
    private val chats: ChatRepository,
    private val memory: MemoryRepository,
    private val projectInstructions: ProjectInstructionRepository,
    private val student: Part3StudentRepository,
) {
    fun home(accountId: String): HomeSnapshotV3Response {
        val maturity = memory.maturity(accountId).state
        return db.tx { c ->
            val identity = c.prepareStatement(
                """SELECT p.display_name,p.username,
                          COALESCE(e.avatar_id,NULLIF(p.default_avatar_id,'default'),'avatar-noob-default'),
                          COALESCE(g.level,1),COALESCE(g.effective_level,1),COALESCE(g.lifetime_xp,0),
                          COALESCE(g.qualified_active_days,0),COALESCE(cp.balance,0),
                          COALESCE(cs.current_consistency,0),COALESCE(cs.longest_consistency,0),COALESCE(g.revision,0)
                   FROM user_profile p
                   LEFT JOIN progression_profile g ON g.account_id=p.account_id
                   LEFT JOIN coin_account_projection cp ON cp.account_id=p.account_id
                   LEFT JOIN consistency_state cs ON cs.account_id=p.account_id
                   LEFT JOIN equipped_avatar e ON e.account_id=p.account_id
                   WHERE p.account_id=?::uuid"""
            ).use { ps ->
                ps.setString(1, accountId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) throw notFound("Profile not found")
                    HomeIdentityRow(
                        displayName = rs.getString(1), username = rs.getString(2), avatarId = rs.getString(3),
                        xpLevel = rs.getInt(4), effectiveLevel = rs.getInt(5), lifetimeXp = rs.getLong(6),
                        qualifiedDays = rs.getInt(7), coins = rs.getLong(8), currentConsistency = rs.getInt(9),
                        longestConsistency = rs.getInt(10), revision = rs.getLong(11),
                    )
                }
            }
            val levelProgress = LevelCurveV1.progress(identity.lifetimeXp)
            val remainingXp = if (levelProgress.level >= LevelCurveV1.MAX_LEVEL) 0L
            else (levelProgress.nextLevelRequiredXp - levelProgress.currentLevelXp).coerceAtLeast(0L)
            val recentProjects = homeProjects(c, accountId, 6)
            val focus = recentProjects.firstOrNull()
            val recentAssessmentScore = c.prepareStatement(
                "SELECT score FROM assessment_attempt WHERE account_id=?::uuid AND state='GRADED' ORDER BY updated_at DESC LIMIT 1"
            ).use { ps ->
                ps.setString(1, accountId)
                ps.executeQuery().use { rs -> if (rs.next() && rs.getObject(1) != null) rs.getDouble(1) else null }
            }
            val reviewTopics = c.prepareStatement(
                """SELECT topic FROM mistake
                   WHERE account_id=?::uuid AND status IN('ACTIVE','IMPROVING','RECURRED') AND deleted_at IS NULL AND topic IS NOT NULL
                   GROUP BY topic ORDER BY count(*) DESC,max(last_seen_at) DESC LIMIT 5"""
            ).use { ps ->
                ps.setString(1, accountId)
                ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
            }
            val recentSources = homeSources(c, accountId, 6)
            val mapRow = c.prepareStatement(
                """SELECT pm.state,mup.unit_id,pm.id
                   FROM personal_map pm
                   LEFT JOIN map_unit_progress mup ON mup.personal_map_id=pm.id AND mup.account_id=pm.account_id AND mup.state IN('AVAILABLE','IN_PROGRESS')
                   WHERE pm.account_id=?::uuid ORDER BY pm.updated_at DESC,mup.unit_id LIMIT 1"""
            ).use { ps ->
                ps.setString(1, accountId)
                ps.executeQuery().use { rs ->
                    if (rs.next()) MapRow(rs.getString(1), rs.getString(2), rs.getObject(3)?.toString())
                    else MapRow("LOCKED", null, null)
                }
            }
            val seasonId = activeSeasonId(c)
            val achievementIndicators = count(c, "SELECT count(*) FROM achievement_progress WHERE account_id=?::uuid AND state IN('UNLOCKED','CLAIMED')", accountId)
            val unreadNotifications = count(c, "SELECT count(*) FROM notification_intent WHERE account_id=?::uuid AND status='PENDING' AND(scheduled_for IS NULL OR scheduled_for<=now())", accountId)
            val candidates = buildList {
                if (focus != null) add(HomePriorityCandidate("PROJECT_FOCUS", 80, focus.priority))
                if (reviewTopics.isNotEmpty()) add(HomePriorityCandidate("WEAK_REVIEW", 70, dueSoon = true))
                if (mapRow.state in setOf("ACTIVE", "ELIGIBLE")) add(HomePriorityCandidate("PERSONAL_MAP", 65))
                if (recentSources.isNotEmpty()) add(HomePriorityCandidate("RECENT_SOURCE", 45))
            }
            val priority = HomePriorityEngine.rank(candidates)
            val insights = HomeInsightEngine.deterministic(
                remainingXp = remainingXp,
                unfinishedGoals = activeGoals(c, accountId),
                activeMistakes = reviewTopics.size,
                mapState = mapRow.state,
            )
            val mapEligible = identity.effectiveLevel >= 5 && maturity in setOf("SUFFICIENT", "STRONG")
            val world = WorldContinuityEngine.ids(accountId, focus?.id, mapRow.mapId, seasonId)
            HomeSnapshotV3Response(
                accountId = accountId,
                displayName = identity.displayName,
                username = identity.username,
                avatarId = identity.avatarId,
                effectiveLevel = identity.effectiveLevel,
                xpLevel = identity.xpLevel,
                lifetimeXp = identity.lifetimeXp,
                currentLevelXp = levelProgress.currentLevelXp,
                nextLevelXp = levelProgress.nextLevelRequiredXp,
                remainingXp = remainingXp,
                coins = identity.coins,
                qualifiedActiveDays = identity.qualifiedDays,
                currentConsistency = identity.currentConsistency,
                longestConsistency = identity.longestConsistency,
                currentFocus = focus,
                recentProjects = recentProjects,
                recentAssessmentScore = recentAssessmentScore,
                reviewTopics = reviewTopics,
                recentSources = recentSources,
                quickActionEligibility = mapOf(
                    "PROJECT" to (focus != null),
                    "REVIEW" to reviewTopics.isNotEmpty(),
                    "MAP" to mapEligible,
                    "FLASHCARDS" to hasDue(c, accountId),
                ),
                memoryMaturity = maturity,
                mapEligibility = mapEligible,
                mapState = mapRow.state,
                currentMapUnit = mapRow.unitId,
                seasonId = seasonId,
                achievementIndicators = achievementIndicators,
                unreadNotifications = unreadNotifications,
                syncState = "SERVER_AUTHORITATIVE",
                priorities = HomePriorityResponse(priority.orderedKeys, priority.scores),
                insights = insights.map { HomeInsightResponse(it.code, it.textKey, it.numericValue, it.evidenceIds) },
                world = WorldContinuityResponse(
                    world.avatarEntityId, world.accountProgressEntityId, world.coinBalanceEntityId,
                    world.projectEntityId, world.mapEntityId, world.seasonEntityId,
                ),
                revision = identity.revision,
                generatedAt = Instant.now().toString(),
            )
        }
    }

    fun personal(accountId: String): PersonalSnapshotV3Response {
        val maturity = memory.maturity(accountId).state
        return db.tx { c ->
            val profile = c.prepareStatement(
                """SELECT p.display_name,p.username,p.preferred_language,p.timezone,
                          COALESCE(e.avatar_id,NULLIF(p.default_avatar_id,'default'),'avatar-noob-default'),
                          COALESCE(g.effective_level,1),COALESCE(g.level,1),COALESCE(g.lifetime_xp,0),COALESCE(cp.balance,0),
                          COALESCE(g.qualified_active_days,0),COALESCE(cs.current_consistency,0),COALESCE(cs.longest_consistency,0),COALESCE(g.revision,0)
                   FROM user_profile p
                   LEFT JOIN equipped_avatar e ON e.account_id=p.account_id
                   LEFT JOIN progression_profile g ON g.account_id=p.account_id
                   LEFT JOIN coin_account_projection cp ON cp.account_id=p.account_id
                   LEFT JOIN consistency_state cs ON cs.account_id=p.account_id
                   WHERE p.account_id=?::uuid"""
            ).use { ps ->
                ps.setString(1, accountId)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) throw notFound("Profile not found")
                    PersonalIdentityRow(
                        displayName = rs.getString(1), username = rs.getString(2), preferredLanguage = rs.getString(3), timezone = rs.getString(4),
                        avatarId = rs.getString(5), effectiveLevel = rs.getInt(6), xpLevel = rs.getInt(7), lifetimeXp = rs.getLong(8),
                        coins = rs.getLong(9), qualifiedDays = rs.getInt(10), currentConsistency = rs.getInt(11), longestConsistency = rs.getInt(12), revision = rs.getLong(13),
                    )
                }
            }
            val seasonId = activeSeasonId(c)
            val mapState = c.prepareStatement("SELECT state FROM personal_map WHERE account_id=?::uuid ORDER BY updated_at DESC LIMIT 1").use { ps ->
                ps.setString(1, accountId); ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else "LOCKED" }
            }
            val world = WorldContinuityEngine.ids(accountId, seasonId = seasonId)
            val memorySummary = StudentModelSummaryResponse(
                maturity = maturity,
                activeSignals = count(c, "SELECT count(*) FROM student_signal WHERE account_id=?::uuid AND status='ACTIVE' AND deleted_at IS NULL", accountId),
                confirmedSignals = count(c, "SELECT count(*) FROM student_signal WHERE account_id=?::uuid AND status='CONFIRMED' AND deleted_at IS NULL", accountId),
                projectSignals = count(c, "SELECT count(*) FROM student_signal WHERE account_id=?::uuid AND project_id IS NOT NULL AND deleted_at IS NULL", accountId),
                performanceSignals = count(c, "SELECT count(*) FROM student_signal WHERE account_id=?::uuid AND signal_type='PERFORMANCE' AND deleted_at IS NULL", accountId),
                mistakeSignals = count(c, "SELECT count(*) FROM student_signal WHERE account_id=?::uuid AND signal_type='MISTAKE' AND deleted_at IS NULL", accountId),
            )
            PersonalSnapshotV3Response(
                accountId = accountId,
                displayName = profile.displayName,
                username = profile.username,
                preferredLanguage = profile.preferredLanguage,
                timezone = profile.timezone,
                avatarId = profile.avatarId,
                effectiveLevel = profile.effectiveLevel,
                xpLevel = profile.xpLevel,
                lifetimeXp = profile.lifetimeXp,
                coins = profile.coins,
                qualifiedActiveDays = profile.qualifiedDays,
                memory = memorySummary,
                strengths = signalValues(c, accountId, "PERFORMANCE"),
                weaknesses = signalValues(c, accountId, "MISTAKE"),
                interests = signalValues(c, accountId, "INTEREST"),
                goals = signalValues(c, accountId, "GOAL"),
                activeProjects = count(c, "SELECT count(*) FROM project WHERE account_id=?::uuid AND status='ACTIVE' AND deleted_at IS NULL", accountId),
                completedAssessments = count(c, "SELECT count(*) FROM assessment_attempt WHERE account_id=?::uuid AND state='GRADED'", accountId),
                dueFlashcards = count(c, "SELECT count(*) FROM flashcard_schedule WHERE account_id=?::uuid AND due_at<=now()", accountId),
                activeMistakes = count(c, "SELECT count(*) FROM mistake WHERE account_id=?::uuid AND status IN('ACTIVE','IMPROVING','RECURRED') AND deleted_at IS NULL", accountId),
                mapState = mapState,
                seasonId = seasonId,
                achievements = count(c, "SELECT count(*) FROM achievement_progress WHERE account_id=?::uuid AND state IN('UNLOCKED','CLAIMED')", accountId),
                inventoryItems = count(c, "SELECT count(*) FROM inventory_ownership WHERE account_id=?::uuid", accountId),
                currentConsistency = profile.currentConsistency,
                longestConsistency = profile.longestConsistency,
                recentActivity = activity(c, accountId, 10),
                world = WorldContinuityResponse(
                    world.avatarEntityId, world.accountProgressEntityId, world.coinBalanceEntityId,
                    world.projectEntityId, world.mapEntityId, world.seasonEntityId,
                ),
                revision = profile.revision,
                generatedAt = Instant.now().toString(),
            )
        }
    }

    fun workspace(accountId: String, projectId: String): ProjectWorkspaceV3Response {
        val project = projects.get(accountId, projectId)
        val goals = projects.listGoals(accountId, projectId)
        val recent = chats.list(accountId, projectId, 8, 0)
        val instruction = projectInstructions.active(accountId, projectId)
        val recommendations = student.recommendations(accountId, projectId, 5)
        val context = student.getContext(accountId)?.takeIf { it.projectId == projectId }
        val counts = db.tx { c ->
            WorkspaceCounts(
                sourceCount = countProjectLink(c, accountId, projectId),
                noteCount = countProject(c, "note", accountId, projectId),
                assessmentCount = countProject(c, "assessment", accountId, projectId),
                flashcardCount = countProject(c, "flashcard", accountId, projectId),
                mistakeCount = countProject(c, "mistake", accountId, projectId),
                practiceCount = countProject(c, "practice_session", accountId, projectId, hasDeletedAt = false),
                projectMemorySignals = countProject(c, "student_signal", accountId, projectId),
                meaningfulEvents = c.prepareStatement("SELECT count(*) FROM activity_event WHERE account_id=?::uuid AND project_id=?::uuid AND meaningful=true").use { ps ->
                    ps.setString(1, accountId); ps.setString(2, projectId); ps.executeQuery().use { rs -> rs.next(); rs.getLong(1) }
                },
            )
        }
        val world = WorldContinuityEngine.ids(accountId, projectId)
        return ProjectWorkspaceV3Response(
            project = project,
            goals = goals,
            recentChats = recent,
            sourceCount = counts.sourceCount,
            noteCount = counts.noteCount,
            assessmentCount = counts.assessmentCount,
            flashcardCount = counts.flashcardCount,
            mistakeCount = counts.mistakeCount,
            practiceCount = counts.practiceCount,
            projectMemorySignals = counts.projectMemorySignals,
            meaningfulEvents = counts.meaningfulEvents,
            activeInstruction = instruction,
            recommendations = recommendations,
            contextCarry = context,
            world = WorldContinuityResponse(
                world.avatarEntityId, world.accountProgressEntityId, world.coinBalanceEntityId,
                world.projectEntityId, world.mapEntityId, world.seasonEntityId,
            ),
            revision = project.revision,
            generatedAt = Instant.now().toString(),
        )
    }

    private fun homeProjects(c: Connection, accountId: String, limit: Int): List<HomeProjectFocusResponse> =
        c.prepareStatement(
            """SELECT p.id,p.title,p.purpose,p.priority,p.revision,
                      (SELECT count(*) FROM goal g WHERE g.account_id=p.account_id AND g.project_id=p.id AND g.status IN('ACTIVE','PAUSED') AND g.deleted_at IS NULL),
                      (SELECT count(*) FROM activity_event e WHERE e.account_id=p.account_id AND e.project_id=p.id AND e.meaningful=true)
               FROM project p WHERE p.account_id=?::uuid AND p.status='ACTIVE' AND p.deleted_at IS NULL
               ORDER BY p.priority DESC,p.last_active_at DESC LIMIT ?"""
        ).use { ps ->
            ps.setString(1, accountId); ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(HomeProjectFocusResponse(rs.getObject(1).toString(), rs.getString(2), rs.getString(3), rs.getInt(4), rs.getInt(6), rs.getLong(7), rs.getLong(5)))
                    }
                }
            }
        }

    private fun homeSources(c: Connection, accountId: String, limit: Int): List<HomeSourceBriefResponse> =
        c.prepareStatement("SELECT id,title,state,pinned,favorite,revision FROM source WHERE account_id=?::uuid AND deleted_at IS NULL ORDER BY pinned DESC,favorite DESC,updated_at DESC LIMIT ?").use { ps ->
            ps.setString(1, accountId); ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(HomeSourceBriefResponse(rs.getObject(1).toString(), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getBoolean(5), rs.getLong(6)))
                }
            }
        }

    private fun activity(c: Connection, accountId: String, limit: Int): List<ActivityEventResponse> =
        c.prepareStatement("SELECT event_id,event_type,occurred_at,project_id,object_id,meaningful FROM activity_event WHERE account_id=?::uuid ORDER BY occurred_at DESC,event_id DESC LIMIT ?").use { ps ->
            ps.setString(1, accountId); ps.setInt(2, limit)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(ActivityEventResponse(rs.getObject(1).toString(), rs.getString(2), rs.getObject(3, OffsetDateTime::class.java).toInstant().toString(), rs.getObject(4)?.toString(), rs.getString(5), rs.getBoolean(6)))
                }
            }
        }

    private fun signalValues(c: Connection, accountId: String, type: String): List<String> =
        c.prepareStatement("SELECT structured_value::text FROM student_signal WHERE account_id=?::uuid AND signal_type=? AND status IN('ACTIVE','CONFIRMED') AND deleted_at IS NULL ORDER BY confidence DESC,updated_at DESC LIMIT 5").use { ps ->
            ps.setString(1, accountId); ps.setString(2, type)
            ps.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }

    private fun activeSeasonId(c: Connection): String? =
        c.prepareStatement("SELECT season_id FROM season_definition WHERE state='ACTIVE' AND start_at<=now() AND end_at>now() ORDER BY start_at DESC LIMIT 1").use { ps ->
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else null }
        }

    private fun activeGoals(c: Connection, accountId: String): Int =
        count(c, "SELECT count(*) FROM goal WHERE account_id=?::uuid AND status IN('ACTIVE','PAUSED') AND deleted_at IS NULL", accountId)

    private fun hasDue(c: Connection, accountId: String): Boolean =
        c.prepareStatement("SELECT EXISTS(SELECT 1 FROM flashcard_schedule WHERE account_id=?::uuid AND due_at<=now())").use { ps ->
            ps.setString(1, accountId); ps.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
        }

    private fun count(c: Connection, sql: String, accountId: String): Int = c.prepareStatement(sql).use { ps ->
        ps.setString(1, accountId); ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
    }

    private fun countProjectLink(c: Connection, accountId: String, projectId: String): Int =
        c.prepareStatement("SELECT count(*) FROM source_project_link WHERE account_id=?::uuid AND project_id=?::uuid").use { ps ->
            ps.setString(1, accountId); ps.setString(2, projectId); ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }

    private fun countProject(c: Connection, table: String, accountId: String, projectId: String, hasDeletedAt: Boolean = true): Int {
        require(table in setOf("note", "assessment", "flashcard", "mistake", "practice_session", "student_signal"))
        val deleted = if (hasDeletedAt) " AND deleted_at IS NULL" else ""
        return c.prepareStatement("SELECT count(*) FROM $table WHERE account_id=?::uuid AND project_id=?::uuid$deleted").use { ps ->
            ps.setString(1, accountId); ps.setString(2, projectId); ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    private fun notFound(message: String) = com.veltrix.hom.vnext.core.DomainException(
        com.veltrix.hom.vnext.core.DomainError("NOT_FOUND", com.veltrix.hom.vnext.core.ErrorCategory.NOT_FOUND, message)
    )

    private data class HomeIdentityRow(
        val displayName: String, val username: String?, val avatarId: String, val xpLevel: Int, val effectiveLevel: Int,
        val lifetimeXp: Long, val qualifiedDays: Int, val coins: Long, val currentConsistency: Int, val longestConsistency: Int, val revision: Long,
    )
    private data class PersonalIdentityRow(
        val displayName: String, val username: String?, val preferredLanguage: String, val timezone: String, val avatarId: String,
        val effectiveLevel: Int, val xpLevel: Int, val lifetimeXp: Long, val coins: Long, val qualifiedDays: Int,
        val currentConsistency: Int, val longestConsistency: Int, val revision: Long,
    )
    private data class MapRow(val state: String, val unitId: String?, val mapId: String?)
    private data class WorkspaceCounts(
        val sourceCount: Int, val noteCount: Int, val assessmentCount: Int, val flashcardCount: Int,
        val mistakeCount: Int, val practiceCount: Int, val projectMemorySignals: Int, val meaningfulEvents: Long,
    )
}
