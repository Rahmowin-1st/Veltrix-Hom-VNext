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
class Part2GameInstrumentedTest {
    @Test fun gameCacheSurvivesDatabaseReopenAndRejectsStaleRevision() {
        runBlocking {
            val context=ApplicationProvider.getApplicationContext<Context>()
            val name="part2-game-test-${UUID.randomUUID()}.db"
            context.deleteDatabase(name)
            var db=Part2GameCacheDatabase.open(context,name)
            var store=Part2GameLocalStore(db)
            val account="cache-account"
            store.save(account,"PROFILE","{\"revision\":4,\"level\":5}",4)
            db.close()

            db=Part2GameCacheDatabase.open(context,name)
            store=Part2GameLocalStore(db)
            val restored=store.load(account,"PROFILE")
            assertNotNull(restored)
            assertEquals(4,restored!!.serverRevision)
            store.save(account,"PROFILE","{\"revision\":3,\"level\":4}",3)
            val afterStale=store.load(account,"PROFILE")
            assertEquals(4,afterStale!!.serverRevision)
            assertTrue(afterStale.payload.contains("\"level\":5"))
            db.close()
            context.deleteDatabase(name)
        }
    }

    @Test fun androidClientReachesRealPart2ServerAndStoreIsNotPlaceholder() = runBlocking {
        val api=VeltrixApiClient()
        assertTrue(api.health())
        val suffix=UUID.randomUUID().toString().take(8)
        val session=api.register("android-part2-$suffix@example.test","testing-password-12345","Android Part2 CI")
        val client=Part2GameClient(api)
        val profile=client.profile(session)
        assertTrue(profile.payload.contains("\"level\":1"))
        val store=client.store(session)
        assertTrue(store.payload.contains("avatar-pro-focus"))
        assertFalse(store.payload.contains("PART2_NOT_AVAILABLE"))
        val map=client.personalMap(session)
        assertTrue(map.payload.contains("levelRequirement"))
        assertTrue(map.payload.contains("memoryRequirement"))
    }
}
