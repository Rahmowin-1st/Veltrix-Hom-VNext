package com.veltrix.hom.vnext

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ServerIntegrationInstrumentedTest {
    @Test fun emulatorTalksToKtorAndSyncIsIdempotent() = runBlocking {
        val ctx=ApplicationProvider.getApplicationContext<android.content.Context>()
        val api=VeltrixApiClient()
        assertTrue(api.health())
        val s=UUID.randomUUID().toString().take(8)
        val session=api.register("android-$s@example.test","testing-password-12345","Android CI")
        SessionStore(ctx).save(LocalSession(session.accountId,session.token))
        val p=api.createProject(session.token,"Android Project $s","CI emulator -> Ktor")
        assertEquals(p.id,api.getProject(session.token,p.id).id)

        val entityId=UUID.randomUUID().toString();val mutationId=UUID.randomUUID().toString();val key="android-sync-$s-12345678"
        val first=api.syncProjectUpsert(session.token,mutationId,entityId,key,"Offline Sync Project $s")
        assertEquals("APPLIED",first.getString("status"))
        val replay=api.syncProjectUpsert(session.token,mutationId,entityId,key,"Offline Sync Project $s")
        assertEquals("APPLIED",replay.getString("status"))
        assertEquals("IDEMPOTENT_REPLAY",replay.getString("code"))
        assertEquals(entityId,api.getProject(session.token,entityId).id)
    }
}
