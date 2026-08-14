package com.veltrix.hom.vnext.core

/**
 * Part 2 completion primitives that deliberately keep AI/content generation away from
 * authoritative progression, currency, entitlement and unlock decisions.
 */
enum class MapContentSource { DETERMINISTIC, AI }

data class MapGenerationContext(
    val accountId: String,
    val profileInterests: Set<String> = emptySet(),
    val subjectSignals: Set<String> = emptySet(),
    val goalSignals: Set<String> = emptySet(),
    val maturity: MemoryMaturityState,
)

data class MapContentUnitDraft(
    val unitId: String,
    val ordinal: Int,
    val semanticKey: String,
    val titleKey: String,
    val eventTypes: Set<String>,
    val requiredCount: Int,
    val prerequisiteIds: Set<String> = emptySet(),
    val contentReference: String = "",
)

data class MapContentDraft(
    val provider: MapContentSource,
    val generationVersion: String,
    val units: List<MapContentUnitDraft>,
)

data class MapContentResolution(
    val draft: MapContentDraft,
    val provider: MapContentSource,
    val fallbackUsed: Boolean,
    val validationErrors: List<String>,
)

fun interface MapContentProvider {
    fun generate(context: MapGenerationContext): MapContentDraft
}

class DeterministicMapContentProvider : MapContentProvider {
    override fun generate(context: MapGenerationContext): MapContentDraft = MapContentDraft(
        provider = MapContentSource.DETERMINISTIC,
        generationVersion = VERSION,
        units = listOf(
            MapContentUnitDraft("foundation-u1",1,"START","map.unit.start",setOf("PROJECT_CREATED","NOTE_CREATED"),1),
            MapContentUnitDraft("foundation-u2",2,"LEARN","map.unit.learn",setOf("QUIZ_COMPLETED","TEST_COMPLETED"),1,setOf("foundation-u1")),
            MapContentUnitDraft("foundation-u3",3,"IMPROVE","map.unit.improve",setOf("PRACTICE_COMPLETED","MISTAKE_RESOLVED"),2,setOf("foundation-u2")),
            MapContentUnitDraft("foundation-u4",4,"KNOWLEDGE","map.unit.knowledge",setOf("SOURCE_ADDED","FLASHCARD_REVIEW_COMPLETED"),3,setOf("foundation-u3")),
            MapContentUnitDraft("foundation-u5",5,"SYNTHESIZE","map.unit.synthesize",setOf("MEANINGFUL_CHAT_SESSION","GOAL_COMPLETED"),3,setOf("foundation-u4")),
        ),
    )

    companion object { const val VERSION = "map-content-deterministic-v1" }
}

/** Adapter boundary only. Any provider output is untrusted until StructuredMapValidator accepts it. */
class AiMapContentProvider(private val generator: (MapGenerationContext) -> MapContentDraft) : MapContentProvider {
    override fun generate(context: MapGenerationContext): MapContentDraft = generator(context).copy(provider = MapContentSource.AI)
}

object StructuredMapValidator {
    private val allowedEvents = RewardPolicyV1.config.rules.keys
    private val idPattern = Regex("[a-z0-9][a-z0-9._-]{2,79}")

    fun validate(draft: MapContentDraft): List<String> {
        val errors = mutableListOf<String>()
        if (draft.generationVersion.isBlank()) errors += "generationVersion required"
        if (draft.units.isEmpty() || draft.units.size > 32) errors += "unit count must be 1..32"
        val ids = draft.units.map { it.unitId }
        if (ids.toSet().size != ids.size) errors += "duplicate unitId"
        val ordinals = draft.units.map { it.ordinal }.sorted()
        if (ordinals != (1..draft.units.size).toList()) errors += "ordinals must be contiguous from 1"
        val known = ids.toSet()
        for (u in draft.units) {
            if (!idPattern.matches(u.unitId)) errors += "invalid unitId:${u.unitId}"
            if (u.semanticKey.isBlank() || u.titleKey.isBlank()) errors += "semantic/title key required:${u.unitId}"
            if (u.requiredCount !in 1..100) errors += "requiredCount out of range:${u.unitId}"
            if (u.eventTypes.isEmpty() || !allowedEvents.containsAll(u.eventTypes)) errors += "unsupported event type:${u.unitId}"
            if (u.unitId in u.prerequisiteIds || !known.containsAll(u.prerequisiteIds)) errors += "invalid prerequisite:${u.unitId}"
            if (u.ordinal > 1 && u.prerequisiteIds.isEmpty()) errors += "future unit must have prerequisite:${u.unitId}"
        }
        val byId = draft.units.associateBy { it.unitId }
        fun cycle(start: String): Boolean {
            val visiting = mutableSetOf<String>()
            val visited = mutableSetOf<String>()
            fun visit(id: String): Boolean {
                if (id in visiting) return true
                if (!visited.add(id)) return false
                visiting += id
                for (p in byId[id]?.prerequisiteIds.orEmpty()) if (visit(p)) return true
                visiting -= id
                return false
            }
            return visit(start)
        }
        if (ids.any(::cycle)) errors += "dependency cycle"
        return errors.distinct()
    }
}

class MapContentOrchestrator(
    private val deterministic: MapContentProvider = DeterministicMapContentProvider(),
    private val ai: MapContentProvider? = null,
) {
    fun resolve(context: MapGenerationContext, allowAi: Boolean): MapContentResolution {
        if (allowAi && ai != null) {
            val proposed = runCatching { ai.generate(context) }.getOrNull()
            if (proposed != null) {
                val errors = StructuredMapValidator.validate(proposed)
                if (errors.isEmpty()) return MapContentResolution(proposed, MapContentSource.AI, false, emptyList())
                val fallback = deterministic.generate(context)
                check(StructuredMapValidator.validate(fallback).isEmpty())
                return MapContentResolution(fallback, MapContentSource.DETERMINISTIC, true, errors)
            }
        }
        val fallback = deterministic.generate(context)
        val errors = StructuredMapValidator.validate(fallback)
        check(errors.isEmpty()) { "Deterministic map template invalid: $errors" }
        return MapContentResolution(fallback, MapContentSource.DETERMINISTIC, allowAi && ai != null, emptyList())
    }
}

enum class ProgressionSimulationProfile(
    val effectiveDailyXp: Long,
    val rawAttemptedDailyXp: Long,
) {
    LIGHT(55,55),
    REGULAR(145,145),
    HIGH_ACTIVITY(260,260),
    ABUSIVE(450,1_800),
}

data class ProgressionSimulationPoint(
    val profile: ProgressionSimulationProfile,
    val days: Int,
    val rawAttemptedXp: Long,
    val effectiveXp: Long,
    val blockedOrSuppressedXp: Long,
    val level: Int,
)

object ProgressionSimulationEngine {
    val horizons = listOf(7,30,90,180,365)

    fun simulate(profile: ProgressionSimulationProfile, days: Int): ProgressionSimulationPoint {
        require(days > 0)
        val raw = Math.multiplyExact(profile.rawAttemptedDailyXp, days.toLong())
        val effectivePerDay = profile.effectiveDailyXp.coerceAtMost(RewardPolicyV1.config.dailyXpHardCap)
        val effective = Math.multiplyExact(effectivePerDay, days.toLong())
        return ProgressionSimulationPoint(profile, days, raw, effective, raw - effective, LevelCurveV1.levelForXp(effective))
    }

    fun matrix(): List<ProgressionSimulationPoint> = ProgressionSimulationProfile.entries.flatMap { p -> horizons.map { simulate(p,it) } }

    fun minimumDaysToLevel50(profile: ProgressionSimulationProfile): Long {
        val perDay = profile.effectiveDailyXp.coerceAtMost(RewardPolicyV1.config.dailyXpHardCap)
        val required = LevelCurveV1.thresholdForLevel(LevelCurveV1.MAX_LEVEL)
        return (required + perDay - 1) / perDay
    }
}
