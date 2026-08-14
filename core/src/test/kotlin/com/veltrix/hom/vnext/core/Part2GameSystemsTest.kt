package com.veltrix.hom.vnext.core

import java.time.Instant
import kotlin.test.*

class Part2GameSystemsTest {
    @Test fun levelCurveIsMonotonicAndExactAtBoundaries() {
        var previous = -1L
        for (level in 1..50) {
            val threshold = LevelCurveV1.thresholdForLevel(level)
            assertTrue(threshold > previous)
            assertEquals(level, LevelCurveV1.levelForXp(threshold))
            if (level > 1) assertEquals(level - 1, LevelCurveV1.levelForXp(threshold - 1))
            previous = threshold
        }
        assertEquals(1, LevelCurveV1.levelForXp(0))
        assertEquals(50, LevelCurveV1.levelForXp(Long.MAX_VALUE))
    }

    @Test fun levelProgressFractionIsBounded() {
        for (xp in listOf(0L, 1L, 250L, 5_000L, 50_000L, 200_000L)) {
            val p = LevelCurveV1.progress(xp)
            assertTrue(p.progressFraction in 0.0..1.0)
            assertEquals(LevelCurveV1.VERSION, p.curveVersion)
        }
    }

    @Test fun trivialActivityNeverRewards() {
        val event = ActivityEvent(accountId="a",type="NAV_TAP",objectId="screen",idempotencyKey="abcdefgh")
        val d = RewardPolicyV1.decide(RewardEligibilityContext(event,true))
        assertFalse(d.eligible); assertEquals(RewardDecisionCode.NOT_MEANINGFUL,d.code)
    }

    @Test fun meaningfulActivityNeedsEvidenceAndObject() {
        val event = ActivityEvent(accountId="a",type="QUIZ_COMPLETED",objectId=null,idempotencyKey="abcdefgh")
        assertEquals(RewardDecisionCode.MISSING_EVIDENCE, RewardPolicyV1.decide(RewardEligibilityContext(event,true)).code)
        val event2 = event.copy(objectId="quiz-1")
        assertEquals(RewardDecisionCode.MISSING_EVIDENCE, RewardPolicyV1.decide(RewardEligibilityContext(event2,false)).code)
    }

    @Test fun duplicateSemanticObjectNeverRewards() {
        val event=ActivityEvent(accountId="a",type="GOAL_COMPLETED",objectId="g",idempotencyKey="abcdefgh")
        assertEquals(RewardDecisionCode.SEMANTIC_DUPLICATE,RewardPolicyV1.decide(RewardEligibilityContext(event,true,semanticDuplicate=true)).code)
    }

    @Test fun rewardDiminishesThenStopsAtCategoryCap() {
        val e=ActivityEvent(accountId="a",type="QUIZ_COMPLETED",objectId="q",idempotencyKey="abcdefgh")
        val first=RewardPolicyV1.decide(RewardEligibilityContext(e,true,sameTypeEligibleToday=0))
        val diminished=RewardPolicyV1.decide(RewardEligibilityContext(e,true,sameTypeEligibleToday=4))
        val stopped=RewardPolicyV1.decide(RewardEligibilityContext(e,true,sameTypeEligibleToday=7))
        assertTrue(first.eligible); assertEquals(45,first.xp)
        assertTrue(diminished.eligible); assertTrue(diminished.xp < first.xp)
        assertFalse(stopped.eligible); assertEquals(RewardDecisionCode.CATEGORY_HARD_CAP,stopped.code)
    }

    @Test fun dailyCapIsHard() {
        val e=ActivityEvent(accountId="a",type="TEST_COMPLETED",objectId="t",idempotencyKey="abcdefgh")
        assertEquals(RewardDecisionCode.DAILY_XP_CAP,RewardPolicyV1.decide(RewardEligibilityContext(e,true,xpGrantedToday=440)).code)
        assertEquals(RewardDecisionCode.DAILY_COIN_CAP,RewardPolicyV1.decide(RewardEligibilityContext(e,true,coinsGrantedToday=85)).code)
    }

    @Test fun mapRequiresBothGates() {
        assertFalse(PersonalMapEligibilityEngine.evaluate(4,MemoryMaturityState.STRONG).eligible)
        assertFalse(PersonalMapEligibilityEngine.evaluate(5,MemoryMaturityState.LEARNING).eligible)
        assertTrue(PersonalMapEligibilityEngine.evaluate(5,MemoryMaturityState.SUFFICIENT).eligible)
        assertTrue(PersonalMapEligibilityEngine.evaluate(50,MemoryMaturityState.STRONG).eligible)
    }

    @Test fun mapUnitSequenceHidesFutureUnits() {
        val u1=UnitDefinitionRule("u1",1)
        val u2=UnitDefinitionRule("u2",2,setOf("u1"))
        val u3=UnitDefinitionRule("u3",3,setOf("u2"))
        assertEquals(UnitProgressState.AVAILABLE,MapUnitSequenceEngine.stateFor(u1,emptySet()))
        assertEquals(UnitProgressState.HIDDEN,MapUnitSequenceEngine.stateFor(u2,emptySet()))
        assertEquals(UnitProgressState.AVAILABLE,MapUnitSequenceEngine.stateFor(u2,setOf("u1")))
        assertEquals(setOf("u2"),MapUnitSequenceEngine.newlyAvailable(listOf(u1,u2,u3),setOf("u1")))
    }

    @Test fun consistencyCountsQualifiedLocalDaysNotOpens() {
        val d1=Instant.parse("2026-08-10T12:00:00Z")
        val one=ConsistencyEngine.update(0,0,null,d1,"UTC")
        assertEquals(1,one.current)
        val same=ConsistencyEngine.update(one.current,one.longest,one.localDate,d1.plusSeconds(3600),"UTC")
        assertFalse(same.qualifiedNewDay); assertEquals(1,same.current)
        val next=ConsistencyEngine.update(same.current,same.longest,same.localDate,d1.plusSeconds(86_400),"UTC")
        assertEquals(2,next.current); assertEquals(2,next.longest)
        val gap=ConsistencyEngine.update(next.current,next.longest,next.localDate,d1.plusSeconds(3*86_400),"UTC")
        assertEquals(1,gap.current); assertEquals(2,gap.longest)
    }

    @Test fun seasonBoundariesAreExact() {
        val w=SeasonWindow("s",Instant.parse("2026-01-01T00:00:00Z"),Instant.parse("2027-01-01T00:00:00Z"))
        assertEquals(SeasonTimeState.PLANNED,SeasonTimeEngine.state(w,Instant.parse("2025-12-31T23:59:59Z")))
        assertEquals(SeasonTimeState.ACTIVE,SeasonTimeEngine.state(w,w.startAt))
        assertEquals(SeasonTimeState.CLOSED,SeasonTimeEngine.state(w,w.endAt))
    }

    @Test fun allCanonicalAvatarTiersExist() {
        assertEquals(setOf("NOOB","PRO","ELITE","SUPER","ULTRA","MAX","HYPERPRO","LEGENDARY"),AvatarTier.entries.map{it.name}.toSet())
    }
}
