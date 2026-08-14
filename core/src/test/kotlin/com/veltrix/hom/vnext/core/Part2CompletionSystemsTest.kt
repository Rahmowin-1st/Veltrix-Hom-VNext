package com.veltrix.hom.vnext.core

import kotlin.test.*

class Part2CompletionSystemsTest {
    private val context = MapGenerationContext("account-test", maturity = MemoryMaturityState.SUFFICIENT)

    @Test fun deterministicMapTemplateIsStructurallyValidAndSequential() {
        val draft = DeterministicMapContentProvider().generate(context)
        assertTrue(StructuredMapValidator.validate(draft).isEmpty())
        assertEquals((1..5).toList(), draft.units.map { it.ordinal })
        assertEquals(setOf("foundation-u1"), draft.units[1].prerequisiteIds)
    }

    @Test fun invalidAiMapFallsBackWithoutGivingAiEconomicAuthority() {
        val ai = AiMapContentProvider {
            MapContentDraft(
                MapContentSource.AI,
                "ai-proposal-v1",
                listOf(MapContentUnitDraft("bad",2,"BAD","bad",setOf("APP_OPEN"),0)),
            )
        }
        val resolved = MapContentOrchestrator(ai = ai).resolve(context, allowAi = true)
        assertEquals(MapContentSource.DETERMINISTIC, resolved.provider)
        assertTrue(resolved.fallbackUsed)
        assertTrue(resolved.validationErrors.isNotEmpty())
        assertTrue(StructuredMapValidator.validate(resolved.draft).isEmpty())
    }

    @Test fun validAiMapCanSupplyContentShapeOnly() {
        val ai = AiMapContentProvider {
            MapContentDraft(
                MapContentSource.AI,
                "ai-proposal-v1",
                listOf(
                    MapContentUnitDraft("ai-unit-1",1,"DISCOVER","map.ai.discover",setOf("PROJECT_CREATED"),1),
                    MapContentUnitDraft("ai-unit-2",2,"PRACTICE","map.ai.practice",setOf("PRACTICE_COMPLETED"),2,setOf("ai-unit-1")),
                ),
            )
        }
        val resolved = MapContentOrchestrator(ai = ai).resolve(context, allowAi = true)
        assertEquals(MapContentSource.AI, resolved.provider)
        assertFalse(resolved.fallbackUsed)
        // MapContentUnitDraft intentionally has no XP, Coin, entitlement or unlock-truth fields.
        assertEquals(2, resolved.draft.units.size)
    }

    @Test fun progressionSimulationCoversRequiredHorizonsAndEnforcesCap() {
        val matrix = ProgressionSimulationEngine.matrix()
        assertEquals(4 * 5, matrix.size)
        assertEquals(setOf(7,30,90,180,365), matrix.map { it.days }.toSet())
        matrix.forEach {
            assertTrue(it.effectiveXp <= RewardPolicyV1.config.dailyXpHardCap * it.days)
            assertTrue(it.level in 1..50)
        }
        val abusive365 = matrix.single { it.profile == ProgressionSimulationProfile.ABUSIVE && it.days == 365 }
        assertTrue(abusive365.blockedOrSuppressedXp > 0)
        assertTrue(ProgressionSimulationEngine.minimumDaysToLevel50(ProgressionSimulationProfile.ABUSIVE) >= 300)
        assertTrue(ProgressionSimulationEngine.minimumDaysToLevel50(ProgressionSimulationProfile.REGULAR) > 365)
    }
}
