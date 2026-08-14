package com.veltrix.hom.vnext.core

import java.time.Instant
import kotlin.math.floor
import kotlin.math.min

enum class StudentSignalType {
    IDENTITY, PREFERENCE, LEARNING, FRICTION, PROJECT, PERFORMANCE, INTEREST, MISTAKE, GOAL, RECENT_CONTEXT
}
enum class StudentSignalStatus { ACTIVE, CONFIRMED, REJECTED, ARCHIVED, SUPERSEDED }
enum class RecommendationReason { GOAL, MISTAKE, PERFORMANCE, DUE_REVIEW, PROJECT_FOCUS, MAP, SOURCE, RECENT_CONTEXT }
enum class MapUnitStage { MISSION, PRACTICE, CHALLENGE, FINAL_CHECK }
enum class UniversalCommandKind {
    OPEN_PROJECT, SHOW_MISTAKES, CONTINUE_SOURCE, CREATE_FLASHCARDS, START_PRACTICE, START_TEST, CALCULATE, TRANSLATE, SEARCH, AI_INTERPRET
}
enum class StoreCategory {
    AVATARS, AVATAR_EFFECTS, FRAMES, PROFILE_BACKGROUNDS, PROFILE_THEMES, CHAT_ENVIRONMENTS, PROJECT_THEMES,
    MAP_COSMETICS, ANIMATIONS, EFFECTS, BADGES, NAMEPLATES, SOUND_PACKS, REACTION_PACKS
}
enum class FrontendSemanticEventType {
    LEVEL_UP, XP_GRANTED, COINS_GRANTED, COINS_SPENT, REWARD_CLAIMED, ACHIEVEMENT_UNLOCKED, ITEM_ACQUIRED,
    AVATAR_UNLOCKED, AVATAR_EQUIPPED, PROJECT_PROGRESS_CHANGED, GOAL_COMPLETED, SOURCE_READY, TEST_COMPLETED,
    QUIZ_COMPLETED, FLASHCARD_SESSION_COMPLETED, MISTAKE_RESOLVED, MEMORY_UPDATED, MAP_UNLOCKED, UNIT_REVEALED,
    UNIT_COMPLETED, SEASON_STARTED, SEASON_COMPLETED
}

data class StudentSignalEvidenceRef(
    val kind: String,
    val objectId: String,
    val observedAt: Instant = Instant.now(),
)

data class StudentSignal(
    val id: String = newId("signal"),
    val accountId: String,
    val projectId: String? = null,
    val type: StudentSignalType,
    val value: String,
    val confidence: Double,
    val evidence: List<StudentSignalEvidenceRef>,
    val source: String,
    val status: StudentSignalStatus = StudentSignalStatus.ACTIVE,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = createdAt,
    val lastConfirmedAt: Instant? = null,
    val reviewAfter: Instant? = null,
    val supersedes: String? = null,
    val supersededBy: String? = null,
    val revision: Long = 1,
)

data class PersonalizationRecommendation(
    val id: String = newId("rec"),
    val action: String,
    val targetId: String? = null,
    val projectId: String? = null,
    val reason: RecommendationReason,
    val evidenceIds: List<String>,
    val confidence: Double,
    val generatedAt: Instant = Instant.now(),
    val expiresAt: Instant,
)

data class HomePriorityCandidate(
    val key: String,
    val basePriority: Int,
    val projectPriority: Int = 0,
    val dueSoon: Boolean = false,
    val active: Boolean = true,
    val evidenceConfidence: Double = 1.0,
)

data class HomePriorityDecision(val orderedKeys: List<String>, val scores: Map<String, Int>)
data class HomeInsight(val code: String, val textKey: String, val numericValue: Long? = null, val evidenceIds: List<String> = emptyList())

data class UniversalCommandResolution(
    val kind: UniversalCommandKind,
    val deterministic: Boolean,
    val requiresConfirmation: Boolean,
    val query: String? = null,
    val targetHint: String? = null,
)

data class WorldContinuityIds(
    val avatarEntityId: String,
    val accountProgressEntityId: String,
    val coinBalanceEntityId: String,
    val projectEntityId: String? = null,
    val mapEntityId: String? = null,
    val seasonEntityId: String? = null,
)

data class AvatarCatalogContract(
    val avatarId: String,
    val permanentName: String,
    val tier: AvatarTier,
    val assetKey: String,
    val rarityOrder: Int,
    val animationCapabilities: Set<String>,
    val behaviorCapabilities: Set<String>,
    val previewCapabilities: Set<String>,
)

object StudentModelEngine {
    private val forbiddenCharacterLabels = setOf("lazy", "bad student", "weak person")

    fun validate(signal: StudentSignal): StudentSignal {
        require(signal.value.isNotBlank()) { "Student signal value cannot be blank" }
        require(signal.value.length <= 2_000) { "Student signal value is too large" }
        require(signal.confidence in 0.0..1.0) { "Student signal confidence must be in [0,1]" }
        require(signal.evidence.isNotEmpty() || signal.source == "EXPLICIT_USER") { "Inferred student signals require evidence" }
        val canonical = signal.value.trim().lowercase()
        require(forbiddenCharacterLabels.none { canonical == it || canonical.startsWith("$it ") }) {
            "Permanent insulting labels are forbidden"
        }
        return signal.copy(value = signal.value.trim())
    }

    fun correct(previous: StudentSignal, correctedValue: String, evidence: StudentSignalEvidenceRef, now: Instant = Instant.now()): Pair<StudentSignal, StudentSignal> {
        require(correctedValue.isNotBlank())
        val replacement = validate(previous.copy(
            id = newId("signal"),
            value = correctedValue,
            confidence = 1.0,
            evidence = listOf(evidence),
            source = "EXPLICIT_USER",
            status = StudentSignalStatus.CONFIRMED,
            createdAt = now,
            updatedAt = now,
            lastConfirmedAt = now,
            supersedes = previous.id,
            supersededBy = null,
            revision = 1,
        ))
        return previous.copy(status = StudentSignalStatus.SUPERSEDED, supersededBy = replacement.id, updatedAt = now, revision = previous.revision + 1) to replacement
    }

    fun approvedForPersonalization(signals: List<StudentSignal>, projectId: String? = null): List<StudentSignal> =
        signals.filter { it.status in setOf(StudentSignalStatus.ACTIVE, StudentSignalStatus.CONFIRMED) }
            .filter { it.projectId == null || it.projectId == projectId }
            .filter { it.confidence >= 0.55 || it.source == "EXPLICIT_USER" }
            .sortedWith(compareByDescending<StudentSignal> { it.source == "EXPLICIT_USER" }.thenByDescending { it.confidence }.thenByDescending { it.updatedAt })
}

object PersonalizationEngine {
    fun recommend(signals: List<StudentSignal>, projectId: String?, now: Instant, max: Int = 5): List<PersonalizationRecommendation> {
        val approved = StudentModelEngine.approvedForPersonalization(signals, projectId)
        return approved.take(max.coerceIn(1, 10)).map { signal ->
            val reason = when (signal.type) {
                StudentSignalType.MISTAKE -> RecommendationReason.MISTAKE
                StudentSignalType.PERFORMANCE -> RecommendationReason.PERFORMANCE
                StudentSignalType.GOAL -> RecommendationReason.GOAL
                StudentSignalType.PROJECT -> RecommendationReason.PROJECT_FOCUS
                StudentSignalType.RECENT_CONTEXT -> RecommendationReason.RECENT_CONTEXT
                else -> RecommendationReason.DUE_REVIEW
            }
            PersonalizationRecommendation(
                action = when (reason) {
                    RecommendationReason.MISTAKE -> "PRACTICE_WEAK_TOPIC"
                    RecommendationReason.PERFORMANCE -> "REVIEW_PERFORMANCE_TOPIC"
                    RecommendationReason.GOAL -> "CONTINUE_GOAL"
                    RecommendationReason.PROJECT_FOCUS -> "CONTINUE_PROJECT"
                    RecommendationReason.RECENT_CONTEXT -> "CONTINUE_RECENT_CONTEXT"
                    else -> "REVIEW_RELEVANT_MATERIAL"
                },
                projectId = signal.projectId,
                reason = reason,
                evidenceIds = signal.evidence.map { it.objectId },
                confidence = signal.confidence,
                generatedAt = now,
                expiresAt = now.plusSeconds(24 * 3600),
            )
        }
    }
}

object HomePriorityEngine {
    fun rank(candidates: List<HomePriorityCandidate>): HomePriorityDecision {
        val scores = candidates.associate { c ->
            val score = if (!c.active) Int.MIN_VALUE else c.basePriority + c.projectPriority +
                (if (c.dueSoon) 20 else 0) + floor(c.evidenceConfidence.coerceIn(0.0, 1.0) * 10).toInt()
            c.key to score
        }
        val ordered = candidates.filter { it.active }.sortedWith(compareByDescending<HomePriorityCandidate> { scores.getValue(it.key) }.thenBy { it.key }).map { it.key }
        return HomePriorityDecision(ordered, scores)
    }
}

object HomeInsightEngine {
    fun deterministic(remainingXp: Long, unfinishedGoals: Int, activeMistakes: Int, mapState: String): List<HomeInsight> = buildList {
        if (remainingXp > 0) add(HomeInsight("XP_REMAINING", "home.insight.xp_remaining", remainingXp))
        if (unfinishedGoals > 0) add(HomeInsight("UNFINISHED_GOALS", "home.insight.unfinished_goals", unfinishedGoals.toLong()))
        if (activeMistakes > 0) add(HomeInsight("WEAK_REVIEW", "home.insight.active_mistakes", activeMistakes.toLong()))
        add(HomeInsight("MAP_STATUS", "home.insight.map.${mapState.lowercase()}"))
    }
}

object UniversalCommandEngine {
    fun resolve(raw: String): UniversalCommandResolution {
        val text = raw.trim()
        require(text.isNotBlank())
        val q = text.lowercase()
        return when {
            q.startsWith("open my ") && q.endsWith(" project") -> UniversalCommandResolution(
                UniversalCommandKind.OPEN_PROJECT,
                true,
                false,
                targetHint = q.removePrefix("open my ").removeSuffix(" project").trim(),
            )
            "show my mistakes" in q -> UniversalCommandResolution(UniversalCommandKind.SHOW_MISTAKES, true, false)
            q.startsWith("continue this source") || q.startsWith("continue source") -> UniversalCommandResolution(UniversalCommandKind.CONTINUE_SOURCE, true, false)
            "make flashcards" in q -> UniversalCommandResolution(UniversalCommandKind.CREATE_FLASHCARDS, true, false, query = text)
            q.startsWith("practice ") || "practice weak" in q -> UniversalCommandResolution(UniversalCommandKind.START_PRACTICE, true, false, query = text)
            q.startsWith("test me") -> UniversalCommandResolution(UniversalCommandKind.START_TEST, true, false, query = text)
            q.startsWith("calculate ") -> UniversalCommandResolution(UniversalCommandKind.CALCULATE, true, false, query = text.substringAfter(' '))
            q.startsWith("translate ") -> UniversalCommandResolution(UniversalCommandKind.TRANSLATE, true, false, query = text.substringAfter(' '))
            q.startsWith("search ") || q.startsWith("find ") -> UniversalCommandResolution(UniversalCommandKind.SEARCH, true, false, query = text.substringAfter(' '))
            else -> UniversalCommandResolution(UniversalCommandKind.AI_INTERPRET, false, false, query = text)
        }
    }
}

object LongTermLevelGate {
    const val VERSION = "long-term-level-gate-v1"
    const val MIN_QUALIFIED_DAYS_LEVEL_50 = 90

    fun maxLevelForQualifiedDays(days: Int): Int {
        require(days >= 0)
        return when {
            days >= 90 -> 50
            days >= 75 -> 49
            days >= 60 -> 48
            days >= 45 -> 46
            days >= 30 -> 44
            else -> 40
        }
    }

    fun effectiveLevel(lifetimeXp: Long, qualifiedActiveDays: Int): Int = min(LevelCurveV1.levelForXp(lifetimeXp), maxLevelForQualifiedDays(qualifiedActiveDays))
}

object MapUnitArcEngine {
    val stages = listOf(MapUnitStage.MISSION, MapUnitStage.PRACTICE, MapUnitStage.CHALLENGE, MapUnitStage.FINAL_CHECK)
    fun next(current: MapUnitStage?): MapUnitStage? = when (current) {
        null -> MapUnitStage.MISSION
        MapUnitStage.MISSION -> MapUnitStage.PRACTICE
        MapUnitStage.PRACTICE -> MapUnitStage.CHALLENGE
        MapUnitStage.CHALLENGE -> MapUnitStage.FINAL_CHECK
        MapUnitStage.FINAL_CHECK -> null
    }
}

object MapRewardLadder {
    fun avatarTierForCompletedUnits(completedUnits: Int): AvatarTier? {
        require(completedUnits >= 0)
        if (completedUnits == 0) return null
        if (completedUnits % 20 == 0) return AvatarTier.PRO
        if (completedUnits % 5 == 0) return AvatarTier.NOOB
        return null
    }
}

object AvatarCatalogPolicy {
    val requiredTierCounts: Map<AvatarTier, Int> = linkedMapOf(
        AvatarTier.NOOB to 40,
        AvatarTier.PRO to 30,
        AvatarTier.ELITE to 20,
        AvatarTier.SUPER to 15,
        AvatarTier.ULTRA to 12,
        AvatarTier.MAX to 10,
        AvatarTier.HYPERPRO to 5,
        AvatarTier.LEGENDARY to 3,
    )
    const val TOTAL = 135

    fun validate(items: List<AvatarCatalogContract>) {
        require(items.size == TOTAL) { "Avatar catalog must contain exactly $TOTAL avatars" }
        require(items.map { it.avatarId }.distinct().size == items.size) { "Avatar IDs must be unique" }
        require(items.map { it.permanentName }.distinct().size == items.size) { "Avatar permanent names must be unique" }
        require(items.groupingBy { it.tier }.eachCount() == requiredTierCounts) { "Avatar tier counts do not match canonical catalog" }
        items.filter { it.tier == AvatarTier.LEGENDARY }.forEach {
            require(setOf("ENTRANCE","IDLE","REACTION","CELEBRATION","PROFILE_INTERACTION","SPECIAL").all(it.animationCapabilities::contains))
        }
    }
}

object StorePolicy {
    val categories = StoreCategory.entries.toSet()
    private val randomPurchaseTokens = setOf("loot_box", "lootbox", "roulette", "mystery_pack", "random_purchase")
    fun validateItemType(type: String) = require(runCatching { StoreCategory.valueOf(type) }.isSuccess) { "Unsupported Store category" }
    fun requireDeterministicPurchase(metadata: Map<String, String>) {
        val keys = metadata.keys.map { it.lowercase() }.toSet()
        require(keys.intersect(randomPurchaseTokens).isEmpty()) { "Randomized purchase paths are forbidden" }
        require(metadata["aiQualityBoost"]?.toBooleanStrictOrNull() != true) { "Coins cannot buy AI quality" }
    }
}

object WorldContinuityEngine {
    fun ids(accountId: String, projectId: String? = null, mapId: String? = null, seasonId: String? = null) = WorldContinuityIds(
        avatarEntityId = "account:$accountId:avatar",
        accountProgressEntityId = "account:$accountId:progress",
        coinBalanceEntityId = "account:$accountId:coins",
        projectEntityId = projectId?.let { "project:$it" },
        mapEntityId = mapId?.let { "map:$it" },
        seasonEntityId = seasonId?.let { "season:$it" },
    )
}
