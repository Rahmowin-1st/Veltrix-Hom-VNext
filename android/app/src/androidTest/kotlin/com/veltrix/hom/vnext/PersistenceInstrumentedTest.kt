package com.veltrix.hom.vnext

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class PersistenceInstrumentedTest {
    private val accountId = "dev-account-local"
    private val projectId = "ci-persist-project"
    private val goalId = "ci-persist-goal"
    private val noteId = "ci-persist-note"

    private fun db(): VeltrixLocalDatabase {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return VeltrixLocalDatabase.get(context)
    }

    @Test fun aSeedProjectForProcessRestart() = runBlocking {
        val database = db()
        val now = System.currentTimeMillis()
        database.projects().upsert(LocalProjectEntity(projectId, accountId, "CI Persistent Project", "durability evidence", "ACTIVE", 1, now, 1, "PENDING"))
        database.goals().upsert(LocalGoalEntity(goalId, accountId, projectId, "CI Persistent Goal", "ACTIVE", 1, now, 1, "PENDING"))
        database.notes().upsert(LocalNoteEntity(noteId, accountId, projectId, "CI Persistent Note", "Durable note body", now, 1, "PENDING"))
        verify(database)
    }

    @Test fun zVerifyProjectAfterProcessRestart() = runBlocking {
        verify(db())
    }

    private suspend fun verify(database: VeltrixLocalDatabase) {
        assertNotNull(database.projects().get(projectId))
        assertTrue(database.goals().observeProject(projectId).first().any { it.id == goalId })
        assertTrue(database.notes().observe(accountId).first().any { it.id == noteId && it.projectId == projectId })
    }
}
