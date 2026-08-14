package com.veltrix.hom.vnext.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.pow
import kotlin.math.roundToLong

enum class AvatarTier { NOOB, PRO, ELITE, SUPER, ULTRA, MAX, HYPERPRO, LEGENDARY }
enum class MapUnlockState { LOCKED, ELIGIBLE, ACTIVE, COMPLETED }
enum class UnitProgressState { HIDDEN, LOCKED, AVAILABLE, IN_PROGRESS, COMPLETED, REWARD_GRANTED }
enum class RewardDecisionCode { ELIGIBLE, NOT_MEANINGFUL, UNSUPPORTED_EVENT, MISSING_EVIDENCE, SEMANTIC_DUPLICATE, CATEGORY_HARD_CAP, DAILY_XP_CAP, DAILY_COIN_CAP }

data class LevelProgress(
    val level: Int,
    val lifetimeXp: Long,
    val currentLevelXp: Long,
    val nextLevelRequiredXp: Long,
    val progressFraction: Double,
    val curveVersion: String,
)

data class XpRewardRule(
    val eventType: String,
    val baseXp: Long,
    val baseCoins: Long,
    val softDailyLimit: Int,
    val hardDailyLimit: Int,
    val requiresSemanticObject: Boolean = true,
)

data class RewardPolicyConfig(
    val version: String,
    val dailyXpHardCap: Long,
    val dailyCoinHardCap: Long,
    val dailyBonusXp: Long,
    val dailyBonusCoins: Long,
    val rules: Map<String, XpRewardRule>,
)

data class RewardEligibilityContext(
    val event: ActivityEvent,
    val semanticEvidenceValid: Boolean,
    val semanticDuplicate: Boolean = false,
    val sameTypeEligibleToday: Int = 0,
    val xpGrantedToday: Long = 0,
    val coinsGrantedToday: Long = 0,
)

data class RewardDecision(
    val eligible: Boolean,
    val code: RewardDecisionCode,
    val xp: Long = 0,
    val coins: Long = 0,
    val policyVersion: String,
    val multiplier: Double = 0.0,
)

object LevelCurveV1 {
    const val VERSION = "level-curve-v1"
    const val MAX_LEVEL = 50

    fun thresholdForLevel(level: Int): Long {
        require(level in 1..MAX_LEVEL)
        if (level == 1) return 0L
        val n = (level - 1).toDouble()
        return (100.0 * n.pow(1.85) + 150.0 * n).roundToLong()
    }

    fun levelForXp(lifetimeXp: Long): Int {
        require(lifetimeXp >= 0)
        var lo = 1
        var hi = MAX_LEVEL
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (thresholdForLevel(mid) <= lifetimeXp) lo = mid else hi = mid - 1
        }
        return lo
    }

    fun progress(lifetimeXp: Long): LevelProgress {
        val safeXp = lifetimeXp.coerceAtLeast(0)
        val level = levelForXp(safeXp)
        val floor = thresholdForLevel(level)
        if (level == MAX_LEVEL) return LevelProgress(level, safeXp, safeXp - floor, 0, 1.0, VERSION)
        val next = thresholdForLevel(level + 1)
        val span = next - floor
        val within = safeXp - floor
        return LevelProgress(level, safeXp, within, span, (within.toDouble() / span).coerceIn(0.0, 1.0), VERSION)
    }

    fun exportTsv(): String = buildString {
        appendLine("level\tcumulative_xp")
        for (level in 1..MAX_LEVEL) appendLine("$level\t${thresholdForLevel(level)}")
    }
}

object RewardPolicyV1 {
    const val VERSION = "reward-v1"
    val config = RewardPolicyConfig(
        version = VERSION,
        dailyXpHardCap = 450,
        dailyCoinHardCap = 90,
        dailyBonusXp = 20,
        dailyBonusCoins = 5,
        rules = listOf(
            XpRewardRule("PROJECT_CREATED", 25, 4, 1, 2),
            XpRewardRule("GOAL_COMPLETED", 45, 8, 4, 7),
            XpRewardRule("SOURCE_ADDED", 20, 3, 3, 5),
            XpRewardRule("TEST_COMPLETED", 55, 10, 2, 4),
            XpRewardRule("QUIZ_COMPLETED", 45, 8, 4, 7),
            XpRewardRule("PRACTICE_COMPLETED", 35, 6, 4, 7),
            XpRewardRule("FLASHCARD_REVIEW_COMPLETED", 15, 2, 5, 8),
            XpRewardRule("MISTAKE_RESOLVED", 40, 8, 4, 7),
            XpRewardRule("NOTE_CREATED", 10, 1, 3, 5),
            XpRewardRule("MEANINGFUL_CHAT_SESSION", 20, 3, 2, 4),
        ).associateBy { it.eventType },
    )

    private val explicitlyTrivial = setOf("APP_OPEN", "SCREEN_OPEN", "SCREEN_CLOSE", "NAV_TAP", "REFRESH", "API_RETRY", "SYNC_RETRY", "BACKGROUND_REFRESH")

    fun decide(context: RewardEligibilityContext): RewardDecision {
        val event = context.event
        if (!MeaningfulActivityClassifier.isMeaningful(event) || event.type in explicitlyTrivial)
            return RewardDecision(false, RewardDecisionCode.NOT_MEANINGFUL, policyVersion = VERSION)
        val rule = config.rules[event.type]
            ?: return RewardDecision(false, RewardDecisionCode.UNSUPPORTED_EVENT, policyVersion = VERSION)
        if (!context.semanticEvidenceValid || (rule.requiresSemanticObject && event.objectId.isNullOrBlank()))
            return RewardDecision(false, RewardDecisionCode.MISSING_EVIDENCE, policyVersion = VERSION)
        if (context.semanticDuplicate)
            return RewardDecision(false, RewardDecisionCode.SEMANTIC_DUPLICATE, policyVersion = VERSION)
        if (context.sameTypeEligibleToday >= rule.hardDailyLimit)
            return RewardDecision(false, RewardDecisionCode.CATEGORY_HARD_CAP, policyVersion = VERSION)

        val multiplier = if (context.sameTypeEligibleToday < rule.softDailyLimit) 1.0 else 0.5
        val xp = (rule.baseXp * multiplier).roundToLong()
        val coins = (rule.baseCoins * multiplier).roundToLong()
        if (context.xpGrantedToday + xp > config.dailyXpHardCap)
            return RewardDecision(false, RewardDecisionCode.DAILY_XP_CAP, policyVersion = VERSION)
        if (context.coinsGrantedToday + coins > config.dailyCoinHardCap)
            return RewardDecision(false, RewardDecisionCode.DAILY_COIN_CAP, policyVersion = VERSION)
        return RewardDecision(true, RewardDecisionCode.ELIGIBLE, xp, coins, VERSION, multiplier)
    }
}

data class MapEligibility(
    val eligible: Boolean,
    val levelRequirement: Int = 5,
    val memoryRequirement: String = "SUFFICIENT_OR_STRONG",
    val levelSatisfied: Boolean,
    val memorySatisfied: Boolean,
    val unlockState: MapUnlockState,
)

object PersonalMapEligibilityEngine {
    fun evaluate(level: Int, maturity: MemoryMaturityState, alreadyActive: Boolean = false, completed: Boolean = false): MapEligibility {
        require(level in 1..50)
        val levelOk = level >= 5
        val memoryOk = maturity == MemoryMaturityState.SUFFICIENT || maturity == MemoryMaturityState.STRONG
        val eligible = levelOk && memoryOk
        val state = when {
            completed -> MapUnlockState.COMPLETED
            alreadyActive -> MapUnlockState.ACTIVE
            eligible -> MapUnlockState.ELIGIBLE
            else -> MapUnlockState.LOCKED
        }
        return MapEligibility(eligible, levelSatisfied = levelOk, memorySatisfied = memoryOk, unlockState = state)
    }
}

data class UnitDefinitionRule(val unitId: String, val ordinal: Int, val prerequisiteIds: Set<String> = emptySet())

object MapUnitSequenceEngine {
    fun stateFor(unit: UnitDefinitionRule, completedIds: Set<String>, startedIds: Set<String> = emptySet()): UnitProgressState {
        if (unit.unitId in completedIds) return UnitProgressState.COMPLETED
        if (!completedIds.containsAll(unit.prerequisiteIds)) return if (unit.ordinal == 1) UnitProgressState.LOCKED else UnitProgressState.HIDDEN
        return if (unit.unitId in startedIds) UnitProgressState.IN_PROGRESS else UnitProgressState.AVAILABLE
    }

    fun newlyAvailable(units: List<UnitDefinitionRule>, completedIds: Set<String>): Set<String> =
        units.filter { it.unitId !in completedIds && completedIds.containsAll(it.prerequisiteIds) }.map { it.unitId }.toSet()
}

data class ConsistencyUpdate(val current: Int, val longest: Int, val localDate: LocalDate, val qualifiedNewDay: Boolean)

object ConsistencyEngine {
    fun update(current: Int, longest: Int, lastDate: LocalDate?, eventAt: Instant, timezone: String): ConsistencyUpdate {
        require(current >= 0 && longest >= 0)
        val date = eventAt.atZone(ZoneId.of(timezone)).toLocalDate()
        if (lastDate == date) return ConsistencyUpdate(current, longest, date, false)
        val next = if (lastDate != null && lastDate.plusDays(1) == date) current + 1 else 1
        return ConsistencyUpdate(next, maxOf(longest, next), date, true)
    }
}

data class SeasonWindow(val seasonId: String, val startAt: Instant, val endAt: Instant)
enum class SeasonTimeState { PLANNED, ACTIVE, CLOSED }
object SeasonTimeEngine {
    fun state(window: SeasonWindow, now: Instant): SeasonTimeState = when {
        now < window.startAt -> SeasonTimeState.PLANNED
        now >= window.endAt -> SeasonTimeState.CLOSED
        else -> SeasonTimeState.ACTIVE
    }
}
