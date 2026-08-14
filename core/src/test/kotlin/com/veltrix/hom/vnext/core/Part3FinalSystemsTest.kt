package com.veltrix.hom.vnext.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.time.Instant

class Part3FinalSystemsTest {
    @Test fun userCorrectionOutranksInferenceAndKeepsLineage() {
        val old=StudentSignal(accountId="a",type=StudentSignalType.INTEREST,value="Prefers physics",confidence=.7,evidence=listOf(StudentSignalEvidenceRef("CHAT","m1")),source="AI_INFERENCE")
        val (superseded,replacement)=StudentModelEngine.correct(old,"Prefers chemistry",StudentSignalEvidenceRef("USER_CORRECTION","m2"),Instant.parse("2026-08-14T00:00:00Z"))
        assertEquals(StudentSignalStatus.SUPERSEDED,superseded.status)
        assertEquals(old.id,replacement.supersedes)
        assertEquals(1.0,replacement.confidence)
        assertEquals("EXPLICIT_USER",replacement.source)
    }

    @Test fun insultingPermanentLabelsAreRejected() {
        assertFailsWith<IllegalArgumentException>{StudentModelEngine.validate(StudentSignal(accountId="a",type=StudentSignalType.FRICTION,value="lazy",confidence=.9,evidence=listOf(StudentSignalEvidenceRef("CHAT","m")),source="AI_INFERENCE"))}
    }

    @Test fun globalSignalsDoNotLeakProjectSignalsIntoUnrelatedProject() {
        val global=StudentSignal(accountId="a",type=StudentSignalType.PREFERENCE,value="concise",confidence=.8,evidence=listOf(StudentSignalEvidenceRef("USER","1")),source="EXPLICIT_USER")
        val p1=StudentSignal(accountId="a",projectId="p1",type=StudentSignalType.PROJECT,value="CEFR",confidence=.9,evidence=listOf(StudentSignalEvidenceRef("PROJECT","p1")),source="SYSTEM_DERIVED")
        val p2=StudentSignal(accountId="a",projectId="p2",type=StudentSignalType.PROJECT,value="Physics",confidence=.9,evidence=listOf(StudentSignalEvidenceRef("PROJECT","p2")),source="SYSTEM_DERIVED")
        assertEquals(setOf(global.id,p1.id),StudentModelEngine.approvedForPersonalization(listOf(global,p1,p2),"p1").map{it.id}.toSet())
        assertEquals(setOf(global.id),StudentModelEngine.approvedForPersonalization(listOf(global,p1,p2),null).map{it.id}.toSet())
    }

    @Test fun universalKnownCommandsAvoidAi() {
        assertEquals(UniversalCommandKind.SHOW_MISTAKES,UniversalCommandEngine.resolve("Show my mistakes").kind)
        assertTrue(UniversalCommandEngine.resolve("Calculate 2+2").deterministic)
        assertFalse(UniversalCommandEngine.resolve("Help me understand why this is hard").deterministic)
    }

    @Test fun level50RequiresLongTermQualifiedUse() {
        val huge=Long.MAX_VALUE
        assertTrue(LongTermLevelGate.effectiveLevel(huge,0)<50)
        assertTrue(LongTermLevelGate.effectiveLevel(huge,89)<50)
        assertEquals(50,LongTermLevelGate.effectiveLevel(huge,90))
    }

    @Test fun mapCheckpointReward20DoesNotAlsoGrantNoob() {
        assertEquals(AvatarTier.NOOB,MapRewardLadder.avatarTierForCompletedUnits(5))
        assertEquals(AvatarTier.NOOB,MapRewardLadder.avatarTierForCompletedUnits(15))
        assertEquals(AvatarTier.PRO,MapRewardLadder.avatarTierForCompletedUnits(20))
        assertEquals(null,MapRewardLadder.avatarTierForCompletedUnits(21))
    }

    @Test fun unitArcIsMissionPracticeChallengeFinalCheck() {
        assertEquals(MapUnitStage.MISSION,MapUnitArcEngine.next(null))
        assertEquals(MapUnitStage.PRACTICE,MapUnitArcEngine.next(MapUnitStage.MISSION))
        assertEquals(MapUnitStage.CHALLENGE,MapUnitArcEngine.next(MapUnitStage.PRACTICE))
        assertEquals(MapUnitStage.FINAL_CHECK,MapUnitArcEngine.next(MapUnitStage.CHALLENGE))
        assertEquals(null,MapUnitArcEngine.next(MapUnitStage.FINAL_CHECK))
    }

    @Test fun storeRejectsRandomPurchaseAndAiQuality() {
        assertFailsWith<IllegalArgumentException>{StorePolicy.requireDeterministicPurchase(mapOf("loot_box" to "true"))}
        assertFailsWith<IllegalArgumentException>{StorePolicy.requireDeterministicPurchase(mapOf("aiQualityBoost" to "true"))}
        StorePolicy.requireDeterministicPurchase(mapOf("exactItem" to "avatar-1"))
    }

    @Test fun homePriorityIsDeterministicAndEvidenceWeighted() {
        val r=HomePriorityEngine.rank(listOf(HomePriorityCandidate("a",10),HomePriorityCandidate("b",8,dueSoon=true,evidenceConfidence=.9)))
        assertEquals("b",r.orderedKeys.first())
    }
}
