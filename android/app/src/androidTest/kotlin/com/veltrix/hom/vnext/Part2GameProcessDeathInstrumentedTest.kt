package com.veltrix.hom.vnext

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class Part2GameProcessDeathInstrumentedTest {
    private val name="veltrix-part2-process-death.db"
    private val account="ci-part2-process-death"

    @Test fun aSeedAuthoritativeSnapshot()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(name)
        val db=Part2GameCacheDatabase.open(context,name)
        Part2GameLocalStore(db).save(account,"PROFILE","{\"level\":9,\"coinBalance\":321,\"revision\":12}",12)
        db.close()
    }

    @Test fun zVerifySnapshotAfterFreshInstrumentationProcess()=runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val db=Part2GameCacheDatabase.open(context,name)
        val row=Part2GameLocalStore(db).load(account,"PROFILE")
        assertNotNull(row)
        assertEquals(12,row!!.serverRevision)
        assertTrue(row.payload.contains("\"coinBalance\":321"))
        db.close()
    }
}
