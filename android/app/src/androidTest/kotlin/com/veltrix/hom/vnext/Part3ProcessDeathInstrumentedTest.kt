package com.veltrix.hom.vnext

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Part3ProcessDeathInstrumentedTest {
    private val name="veltrix-part3-process-death.db"
    private val account="ci-part3-process-death"

    @Test fun aSeedPart3State()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)
        val db=Part3LocalDatabase.openForTest(context,name)
        db.snapshots().put(Part3SnapshotEntity(account,"HOME","GLOBAL","{\"accountId\":\"$account\",\"effectiveLevel\":11,\"coins\":777,\"revision\":21}",21,System.currentTimeMillis()))
        db.contextCarry().put(Part3ContextCarryEntity(account,null,"[]",null,null,"mechanics","TUTOR","PROJECT","veltrix://project/demo",8,"PENDING",System.currentTimeMillis()))
        db.frontendEvents().insertAll(listOf(Part3FrontendEventEntity(account,"process-event-1","PROJECT_PROGRESS_CHANGED","demo","demo","{\"progress\":42}",System.currentTimeMillis())))
        db.close()
    }

    @Test fun zVerifyPart3StateAfterFreshInstrumentationProcess()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Part3LocalDatabase.openForTest(context,name)
        val snapshot=db.snapshots().get(account,"HOME")
        assertNotNull(snapshot);assertEquals(21,snapshot!!.serverRevision);assertTrue(snapshot.payload.contains("\"coins\":777"))
        val carry=db.contextCarry().get(account)
        assertNotNull(carry);assertEquals(8,carry!!.contextRevision);assertEquals("PENDING",carry.syncState);assertEquals("mechanics",carry.topic)
        val events=db.frontendEvents().pending(account,10)
        assertEquals(1,events.size);assertEquals("process-event-1",events.single().eventId)
        db.close()
    }
}
