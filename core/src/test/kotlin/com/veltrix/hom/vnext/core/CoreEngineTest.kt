package com.veltrix.hom.vnext.core

import kotlin.test.*
import java.time.Instant

class CoreEngineTest {
    @Test fun messageStateMachineRejectsIllegalCompletion() {
        val m=ConversationMessage(accountId=newId("a"),conversationId=newId("c"),role=MessageRole.USER,state=MessageState.QUEUED,content="x",idempotencyKey="12345678")
        assertFailsWith<DomainException>{ChatStateMachine.transition(m,MessageState.COMPLETED)}
        assertEquals(MessageState.SENDING,ChatStateMachine.transition(m,MessageState.SENDING).state)
    }
    @Test fun memoryCorrectionPreservesAuditAndProjectIsolation() {
        val a=newId("a");val p=newId("p")
        val old=MemoryItem(accountId=a,scope=MemoryScope.PROJECT,scopeId=p,category=MemoryCategory.PREFERENCE,statement="British English",origin=MemoryOrigin.EXPLICIT_USER,confidence=1.0,evidence=listOf(MemoryEvidence(kind="CHAT",objectId="1")))
        val corrected=MemoryEngine.correct(listOf(old),old.id,old.copy(id=newId("m"),statement="American English"))
        assertEquals(MemoryStatus.USER_CORRECTED,corrected.first{it.id==old.id}.status)
        assertTrue(MemoryEngine.rank(corrected,a,p,"English").any{it.statement=="American English"})
        assertTrue(MemoryEngine.rank(corrected,a,newId("other"),"English").none{it.scope==MemoryScope.PROJECT})
    }
    @Test fun deterministicScoringAndScheduler() {
        val a=newId("a");val q=Question(prompt="2+2",type="NUMERIC",expectedAnswers=listOf("4"),numericTolerance=0.0)
        val assessment=Assessment(accountId=a,kind="QUIZ",title="Math",questions=listOf(q))
        val result=AssessmentEngine.score(assessment,mapOf(q.id to AttemptAnswer(q.id,listOf("4"))))
        assertEquals(100.0,result.score)
        val next=FlashcardScheduler.review(FlashcardScheduleState(newId("card")),ReviewRating.GOOD,Instant.parse("2026-08-11T00:00:00Z"))
        assertEquals(1,next.intervalDays);assertEquals(1,next.repetitions)
    }
    @Test fun deterministicToolsNeverNeedAi() {
        val registry=ToolRegistry()
        assertEquals("14",registry.invoke("calculator.basic",mapOf("expression" to "2+3*4")).values["result"])
        assertFailsWith<DomainException>{registry.invoke("calculator.basic",mapOf("expression" to "1/0"))}
    }
    @Test fun meaningfulActivityRejectsNavigationSpam() {
        assertFalse(MeaningfulActivityClassifier.isMeaningful(ActivityEvent(accountId=newId("a"),type="NAV_TAP",idempotencyKey="nav")))
        assertTrue(MeaningfulActivityClassifier.isMeaningful(ActivityEvent(accountId=newId("a"),type="QUIZ_COMPLETED",metadata=mapOf("accuracy" to "0.8"),idempotencyKey="quiz")))
    }
}
