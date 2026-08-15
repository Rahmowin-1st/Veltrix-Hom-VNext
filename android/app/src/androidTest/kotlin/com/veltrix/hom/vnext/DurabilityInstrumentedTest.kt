package com.veltrix.hom.vnext

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class DurabilityInstrumentedTest {
    private val accountId = "ci-durable-account"
    private val projectId = "ci-durable-project"
    private val goalId = "ci-durable-goal"
    private val noteId = "ci-durable-note"
    private val mutationId = "ci-durable-project-mutation"

    private fun db(): VeltrixLocalDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return VeltrixLocalDatabase.get(context)
    }

    @Test fun aSeedDurability() = runBlocking {
        val database = db()
        val now = System.currentTimeMillis()
        database.projects().upsert(LocalProjectEntity(projectId, accountId, "CI Durable Project", "durability evidence", "ACTIVE", 1, now, 0, "PENDING"))
        database.goals().upsert(LocalGoalEntity(goalId, accountId, projectId, "CI Durable Goal", "ACTIVE", 1, now, 1, "PENDING"))
        database.notes().upsert(LocalNoteEntity(noteId, accountId, projectId, "CI Durable Note", "Durable note body", now, 1, "PENDING"))
        database.sync().enqueue(
            LocalSyncMutationEntity(
                mutationId,
                accountId,
                "PROJECT",
                projectId,
                "UPSERT",
                null,
                "ci-project-create:$projectId",
                JSONObject().put("title", "CI Durable Project").put("purpose", "durability evidence").put("status", "ACTIVE").put("priority", 1).toString(),
                now,
                0,
                "PENDING",
            ),
        )
        verify(database)
    }

    @Test fun zVerifyDurability() = runBlocking {
        verify(db())
    }

    private suspend fun verify(database: VeltrixLocalDatabase) {
        val project = database.projects().get(projectId)
        assertNotNull(project)
        assertEquals(accountId, project!!.accountId)
        assertEquals("PENDING", project.syncState)
        assertTrue(database.projects().observe(accountId).first().any { it.id == projectId })
        assertTrue(database.goals().observeProject(projectId).first().any { it.id == goalId && it.title == "CI Durable Goal" })
        assertTrue(database.notes().observe(accountId).first().any { it.id == noteId && it.projectId == projectId && it.title == "CI Durable Note" })
        assertTrue(database.sync().nextBatch(accountId, 50).any { it.id == mutationId && it.entityType == "PROJECT" && it.entityId == projectId })
    }
}