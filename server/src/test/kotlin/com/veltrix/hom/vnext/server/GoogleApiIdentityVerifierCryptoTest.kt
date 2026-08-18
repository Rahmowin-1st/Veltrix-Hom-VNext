package com.veltrix.hom.vnext.server

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken
import com.google.api.client.json.gson.GsonFactory
import com.google.api.client.json.webtoken.JsonWebSignature
import com.google.api.client.testing.http.MockHttpTransport
import com.google.api.client.testing.http.MockLowLevelHttpResponse
import com.veltrix.hom.vnext.core.DomainException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64
import kotlin.test.*

class GoogleApiIdentityVerifierCryptoTest {
    private val clientId = "veltrix-test.apps.googleusercontent.com"
    private val nonce = "nonce-local-crypto-fixture-1234567890"
    private val keyId = "veltrix-google-test-key"

    @Test
    fun realGoogleVerifierAcceptsValidSignedFixtureAndRejectsSecurityMatrix() {
        val verifier = verifier()
        val valid = signedToken()
        val identity = verifier.verify(valid, nonce)
        assertEquals("google-fixture-subject", identity.subject)
        assertEquals("fixture@example.test", identity.email)
        assertTrue(identity.emailVerified)

        assertAuthCode("AUTH_GOOGLE_NONCE_MISMATCH") { verifier.verify(valid, "$nonce-wrong") }
        assertAuthCode("AUTH_GOOGLE_INVALID") { verifier.verify(signedToken(audience = "wrong-client.apps.googleusercontent.com"), nonce) }
        assertAuthCode("AUTH_GOOGLE_INVALID") { verifier.verify(signedToken(issuer = "https://attacker.example.test"), nonce) }
        assertAuthCode("AUTH_GOOGLE_INVALID") { verifier.verify(signedToken(expirationSeconds = (System.currentTimeMillis() / 1000L) - 3600L), nonce) }
        assertAuthCode("AUTH_GOOGLE_INVALID") { verifier.verify(signedToken(subject = null), nonce) }

        val tampered = valid.dropLast(1) + if (valid.last() == 'a') 'b' else 'a'
        assertAuthCode("AUTH_GOOGLE_INVALID") { verifier.verify(tampered, nonce) }
    }

    private fun verifier(): GoogleApiIdentityVerifier {
        val certificate = resource("/google-fixture/certificate.pem")
        val certJson = JsonObject(mapOf(keyId to JsonPrimitive(certificate))).toString()
        val response = MockLowLevelHttpResponse()
            .setStatusCode(200)
            .setContentType("application/json")
            .addHeader("Cache-Control", "public, max-age=3600")
            .setContent(certJson)
        val transport = MockHttpTransport.Builder().setLowLevelHttpResponse(response).build()
        return GoogleApiIdentityVerifier(setOf(clientId), transport, GsonFactory.getDefaultInstance())
    }

    private fun signedToken(
        audience: String = clientId,
        issuer: String = "https://accounts.google.com",
        expirationSeconds: Long = (System.currentTimeMillis() / 1000L) + 3600L,
        subject: String? = "google-fixture-subject",
    ): String {
        val now = System.currentTimeMillis() / 1000L
        val header = JsonWebSignature.Header().apply {
            algorithm = "RS256"
            keyId = this@GoogleApiIdentityVerifierCryptoTest.keyId
        }
        val payload = GoogleIdToken.Payload().apply {
            this.issuer = issuer
            this.audience = audience
            this.expirationTimeSeconds = expirationSeconds
            this.issuedAtTimeSeconds = now
            this.subject = subject
            this.email = "fixture@example.test"
            this.emailVerified = true
            this["name"] = "Fixture User"
            this["picture"] = "https://example.test/fixture.png"
            this["nonce"] = nonce
        }
        return JsonWebSignature.signUsingRsaSha256(privateKey(), GsonFactory.getDefaultInstance(), header, payload)
    }

    private fun privateKey(): PrivateKey {
        val pem = resource("/google-fixture/private-key-pkcs8.pem")
        val encoded = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace(Regex("\\s"), "")
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)))
    }

    private fun resource(path: String): String = requireNotNull(javaClass.getResource(path)) { "Missing test resource $path" }.readText()

    private fun assertAuthCode(code: String, block: () -> Unit) {
        val error = assertFailsWith<DomainException> { block() }
        assertEquals(code, error.error.code)
    }
}
