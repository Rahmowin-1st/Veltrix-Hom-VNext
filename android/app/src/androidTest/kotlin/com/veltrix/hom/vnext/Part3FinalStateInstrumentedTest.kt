package com.veltrix.hom.vnext

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class Part3FinalStateInstrumentedTest {
    @Test fun finalSnapshotsContextAndSemanticEventsSurviveRoomReopen() {
        runBlocking {
            val context=ApplicationProvider.getApplicationContext<Context>()
            val name="part3-final-${UUID.randomUUID()}.db"
            context.deleteDatabase(name)
            var db=Part3LocalDatabase.openForTest(context,name)
            val account=UUID.randomUUID().toString()
            db.snapshots().put(Part3SnapshotEntity(account,"HOME","GLOBAL","{\"accountId\":\"$account\",\"revision\":9}",9,System.currentTimeMillis()))
            db.contextCarry().put(Part3ContextCarryEntity(account,null,"[]",null,null,"mechanics","TUTOR","PROJECT",null,4,"PENDING",System.currentTimeMillis()))
            db.frontendEvents().insertAll(listOf(Part3FrontendEventEntity(account,"event-1","PROJECT_PROGRESS_CHANGED","project-1","project-1","{}",System.currentTimeMillis())))
            db.close()

            db=Part3LocalDatabase.openForTest(context,name)
            assertEquals(9,db.snapshots().get(account,"HOME")!!.serverRevision)
            assertEquals(4,db.contextCarry().get(account)!!.contextRevision)
            val event=db.frontendEvents().pending(account,10).single()
            assertEquals("PROJECT_PROGRESS_CHANGED",event.eventType)
            assertEquals(1,db.frontendEvents().consume(account,event.eventId,System.currentTimeMillis()))
            assertTrue(db.frontendEvents().pending(account,10).isEmpty())
            db.close();context.deleteDatabase(name)
        }
    }

    @Test fun typedPart3RepositoryReachesRealHomePersonalWorkspaceCommandAndSearch() {
        runBlocking {
            val context=ApplicationProvider.getApplicationContext<Context>()
            val api=VeltrixApiClient()
            assertTrue(api.health())
            val suffix=UUID.randomUUID().toString().take(8)
            val session=api.register("android-part3-$suffix@example.test","testing-password-12345","Android Part3")
            val project=api.createProject(session.token,"Physics $suffix","Mechanics project")
            val repo=Part3AndroidRepository(context,Part3RemoteDataSource(api))

            val home=repo.home(session,forceRefresh=true)
            assertNotNull(home.value);assertEquals(session.accountId,home.value!!.accountId);assertEquals(DataFreshness.FRESH,home.freshness)
            val personal=repo.personal(session,forceRefresh=true)
            assertNotNull(personal.value);assertEquals(session.accountId,personal.value!!.accountId)
            val workspace=repo.projectWorkspace(session,project.id,forceRefresh=true)
            assertNotNull(workspace.value);assertEquals(project.id,workspace.value!!.projectId)

            val command=repo.resolveCommand(session,"Open my Physics $suffix project")
            assertEquals("OPEN_PROJECT",command.kind);assertTrue(command.deterministic)
            val results=repo.search(session,"Physics",null)
            assertTrue(results.any{it.type=="PROJECT" && it.id==project.id && it.deepLink.startsWith("veltrix://project/")})
        }
    }
}
