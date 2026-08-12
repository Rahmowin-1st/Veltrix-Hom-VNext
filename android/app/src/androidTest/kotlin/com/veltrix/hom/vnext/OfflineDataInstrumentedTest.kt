package com.veltrix.hom.vnext

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

private const val OFFLINE_ACCOUNT = "11111111-1111-1111-1111-111111111111"
private const val OFFLINE_PROJECT = "22222222-2222-2222-2222-222222222222"
private const val OFFLINE_ATTEMPT = "33333333-3333-3333-3333-333333333333"
private const val OFFLINE_CARD = "44444444-4444-4444-4444-444444444444"

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class OfflineDataInstrumentedTest {
    private fun db() = VeltrixLocalDatabase.get(InstrumentationRegistry.getInstrumentation().targetContext)

    @Test fun aSeedOfflineDurableState() = runBlocking {
        val db = db()
        val now = System.currentTimeMillis()
        db.profiles().upsert(LocalProfileEntity(OFFLINE_ACCOUNT, "Offline User", "en", "UTC", true, true, 3, now))
        db.projects().upsert(LocalProjectEntity(OFFLINE_PROJECT, OFFLINE_ACCOUNT, "Cached Project", "offline mission", "ACTIVE", 10, now, 4, "SYNCED"))
        db.notes().upsert(LocalNoteEntity("55555555-5555-5555-5555-555555555555", OFFLINE_ACCOUNT, OFFLINE_PROJECT, "Offline Note", "edited without network", now, 2, "PENDING"))
        db.flashcards().upsert(LocalFlashcardScheduleEntity(OFFLINE_CARD, OFFLINE_ACCOUNT, 6, 2.45, 3, 0, now + 86_400_000L, now, "PENDING"))
        db.assessments().upsertAttempt(LocalAssessmentAttemptEntity(OFFLINE_ATTEMPT, OFFLINE_ACCOUNT, "66666666-6666-6666-6666-666666666666", OFFLINE_PROJECT, "IN_PROGRESS", now, now, null, 2, "PENDING"))
        db.assessments().upsertAnswer(LocalAssessmentAnswerEntity(OFFLINE_ATTEMPT, "77777777-7777-7777-7777-777777777777", OFFLINE_ACCOUNT, "[\"answer-b\"]", now, "PENDING"))
        db.sync().enqueue(LocalSyncMutationEntity("88888888-8888-8888-8888-888888888888", OFFLINE_ACCOUNT, "NOTE", "55555555-5555-5555-5555-555555555555", "UPSERT", 1, "offline-note-edit-v1", "{}", now, 0, "PENDING"))
        val duplicate = db.sync().enqueue(LocalSyncMutationEntity("99999999-9999-9999-9999-999999999999", OFFLINE_ACCOUNT, "NOTE", "55555555-5555-5555-5555-555555555555", "UPSERT", 1, "offline-note-edit-v1", "{}", now + 1, 0, "PENDING"))
        assertEquals(-1L, duplicate)
        assertEquals(1, db.sync().nextBatch(OFFLINE_ACCOUNT, 10).count { it.idempotencyKey == "offline-note-edit-v1" })
    }

    @Test fun zVerifyOfflineDurableStateAfterProcessRestart() = runBlocking {
        val db = db()
        assertEquals("Offline User", db.profiles().get(OFFLINE_ACCOUNT)?.displayName)
        assertEquals("Cached Project", db.projects().get(OFFLINE_PROJECT)?.title)
        assertNotNull(db.assessments().attempt(OFFLINE_ATTEMPT))
        assertEquals("answer-b", db.assessments().answers(OFFLINE_ATTEMPT).single().answerPayload.removePrefix("[\"").removeSuffix("\"]"))
        assertTrue(db.sync().nextBatch(OFFLINE_ACCOUNT, 10).any { it.idempotencyKey == "offline-note-edit-v1" })
    }
}
