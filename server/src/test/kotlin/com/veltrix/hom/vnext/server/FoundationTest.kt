package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.server.foundation.PasswordHasher
import com.veltrix.hom.vnext.server.foundation.SessionTokens
import kotlin.test.*
import java.time.Instant

class FoundationTest {
    @Test fun passwordHashIsSaltedAndVerifiable() {
        val p="correct horse battery staple".toCharArray()
        val a=PasswordHasher.hash(p);val b=PasswordHasher.hash(p)
        assertNotEquals(a,b);assertTrue(PasswordHasher.verify(p,a));assertFalse(PasswordHasher.verify("wrong password 123".toCharArray(),a))
    }
    @Test fun sessionTokenStoresOnlyHash() {
        val t=SessionTokens.generate();assertTrue(t.clientToken.length>=32);assertNotEquals(t.clientToken,t.storedHashHex);assertTrue(SessionTokens.matches(t.clientToken,t.storedHashHex))
    }
    @Test fun rateLimiterIsBoundedByWindow() {
        val r=RequestRateLimiter(maxRequests=2,windowSeconds=60)
        val t=Instant.ofEpochSecond(1000)
        assertTrue(r.allow("a",t));assertTrue(r.allow("a",t));assertFalse(r.allow("a",t));assertTrue(r.allow("a",t.plusSeconds(60)))
    }
}
