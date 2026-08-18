package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainException
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.test.*

class BackendFinalClosureIntegrationTest {
    private fun config(): ServerConfig? {
        val url = System.getenv("VELTRIX_TEST_DATABASE_URL") ?: return null
        return ServerConfig(
            environment = "test",
            databaseUrl = url,
            databaseUser = System.getenv("VELTRIX_TEST_DATABASE_USER") ?: "postgres",
            databasePassword = System.getenv("VELTRIX_TEST_DATABASE_PASSWORD") ?: "postgres",
            port = 8080,
            aiProvider = "disabled",
            aiApiKey = null,
            embeddingProvider = "disabled",
            workerEnabled = false,
            googleServerClientIds = setOf("veltrix-test.apps.googleusercontent.com"),
        )
    }

    private class FixtureVerifier(private val identities: Map<String, VerifiedGoogleIdentity>) : GoogleIdentityVerifier {
        override fun verify(idToken: String, expectedNonce: String): VerifiedGoogleIdentity {
            if (!expectedNonce.startsWith("nonce-")) throw DomainException(
                com.veltrix.hom.vnext.core.DomainError(
                    "AUTH_GOOGLE_NONCE_MISMATCH",
                    com.veltrix.hom.vnext.core.ErrorCategory.AUTH,
                    "Google identity nonce is invalid",
                ),
            )
            return identities[idToken] ?: throw DomainException(
                com.veltrix.hom.vnext.core.DomainError(
                    "AUTH_GOOGLE_INVALID",
                    com.veltrix.hom.vnext.core.ErrorCategory.AUTH,
                    "Google identity token is invalid or expired",
                ),
            )
        }
    }

    private fun identity(subject: String, email: String?, name: String = "Google User") = VerifiedGoogleIdentity(
        subject = subject,
        email = email,
        emailVerified = email != null,
        displayName = name,
        pictureUrl = "https://example.test/avatar.png",
        tokenExpiresAt = Instant.now().plusSeconds(3600),
    )

    @Test
    fun googleExchangeIsReplaySafeIsolatedAndPersistsOnlyServerSessionHash() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val suffix = UUID.randomUUID().toString().take(8)
            val tokenA = "google-token-a-$suffix-" + "x".repeat(32)
            val tokenB = "google-token-b-$suffix-" + "x".repeat(32)
            val verifier = FixtureVerifier(
                mapOf(
                    tokenA to identity("google-sub-a-$suffix", "ga-$suffix@example.test", "Google A"),
                    tokenB to identity("google-sub-b-$suffix", "gb-$suffix@example.test", "Google B"),
                ),
            )
            val federated = FederatedAuthRepository(db, verifier)
            val auth = AuthRepository(db)
            val profile = ProfileRepository(db)
            val projects = ProjectRepository(db)

            val first = federated.exchangeGoogle(GoogleIdentityExchangeRequest(tokenA, "nonce-$suffix-a-123456789"))
            assertTrue(first.created)
            assertEquals(first.session.accountId, auth.resolve(first.session.sessionToken)?.accountId)
            assertEquals("Google A", profile.get(first.session.accountId).displayName)

            // A consumed credential exchange is single-use even before session use.
            val replay = assertFailsWith<DomainException> {
                federated.exchangeGoogle(GoogleIdentityExchangeRequest(tokenA, "nonce-$suffix-a-123456789"))
            }
            assertEquals("AUTH_GOOGLE_REPLAY", replay.error.code)

            // A later fresh Google ID token for the same provider subject resolves the same Veltrix account.
            val freshTokenA = "google-token-a-fresh-$suffix-" + "y".repeat(32)
            val returning = FederatedAuthRepository(
                db,
                FixtureVerifier(mapOf(freshTokenA to identity("google-sub-a-$suffix", "ga-$suffix@example.test", "Google A Updated"))),
            ).exchangeGoogle(GoogleIdentityExchangeRequest(freshTokenA, "nonce-$suffix-a-fresh-123456"))
            assertFalse(returning.created)
            assertEquals(first.session.accountId, returning.session.accountId)

            val second = federated.exchangeGoogle(GoogleIdentityExchangeRequest(tokenB, "nonce-$suffix-b-123456789"))
            assertTrue(second.created)
            assertNotEquals(first.session.accountId, second.session.accountId)
            val p = projects.create(first.session.accountId, CreateProjectRequest("Private $suffix", "Account isolation fixture"))
            assertFailsWith<DomainException> { projects.get(second.session.accountId, p.id) }

            db.tx { c ->
                val rawTokenRows = c.prepareStatement("SELECT count(*) FROM device_session WHERE refresh_token_hash=?").use { ps ->
                    ps.setString(1, first.session.sessionToken)
                    ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
                }
                assertEquals(0, rawTokenRows, "raw Veltrix bearer token must never be persisted")
                val identityCount = c.prepareStatement("SELECT count(*) FROM account_external_identity WHERE account_id=?::uuid AND provider='GOOGLE'").use { ps ->
                    ps.setString(1, first.session.accountId)
                    ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
                }
                assertEquals(1, identityCount)
            }

            auth.signOut(returning.session.sessionToken)
            assertNull(auth.resolve(returning.session.sessionToken))
        }
    }

    @Test
    fun googleCollisionConcurrentReplayRefreshRotationAndDeletionTombstoneFailClosed() {
        val cfg = config() ?: return
        Database(cfg).use { db ->
            val suffix = UUID.randomUUID().toString().take(8)
            val auth = AuthRepository(db)
            val local = auth.register(RegisterRequest("collision-$suffix@example.test", "testing-password-12345", "Local User"))

            val collisionToken = "collision-token-$suffix-" + "c".repeat(32)
            val collisionRepo = FederatedAuthRepository(
                db,
                FixtureVerifier(mapOf(collisionToken to identity("collision-sub-$suffix", "collision-$suffix@example.test"))),
            )
            val collision = assertFailsWith<DomainException> {
                collisionRepo.exchangeGoogle(GoogleIdentityExchangeRequest(collisionToken, "nonce-$suffix-collision-123456"))
            }
            assertEquals("AUTH_ACCOUNT_LINK_REQUIRED", collision.error.code)
            assertEquals(local.accountId, auth.resolve(local.sessionToken)?.accountId)

            val concurrentToken = "concurrent-google-$suffix-" + "g".repeat(32)
            val concurrentRepo = FederatedAuthRepository(
                db,
                FixtureVerifier(mapOf(concurrentToken to identity("concurrent-sub-$suffix", "concurrent-$suffix@example.test"))),
            )
            val request = GoogleIdentityExchangeRequest(concurrentToken, "nonce-$suffix-concurrent-123456")
            val executor = Executors.newFixedThreadPool(2)
            try {
                val futures = (1..2).map {
                    executor.submit(Callable {
                        runCatching { concurrentRepo.exchangeGoogle(request) }
                    })
                }
                val results = futures.map { it.get() }
                assertEquals(1, results.count { it.isSuccess })
                val failed = results.single { it.isFailure }.exceptionOrNull()
                assertTrue(failed is DomainException)
                assertEquals("AUTH_GOOGLE_REPLAY", (failed as DomainException).error.code)
                db.tx { c ->
                    val accounts = c.prepareStatement("SELECT count(*) FROM account_external_identity WHERE provider='GOOGLE' AND provider_subject=?").use { ps ->
                        ps.setString(1, "concurrent-sub-$suffix")
                        ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
                    }
                    assertEquals(1, accounts)
                }
            } finally {
                executor.shutdownNow()
            }

            // Refresh rotation is one atomic consume-and-replace operation.
            val rotateSession = auth.login(LoginRequest("collision-$suffix@example.test", "testing-password-12345"))
            val rotateExecutor = Executors.newFixedThreadPool(2)
            try {
                val rotations = (1..2).map {
                    rotateExecutor.submit(Callable { runCatching { auth.rotate(rotateSession.sessionToken) } })
                }.map { it.get() }
                assertEquals(1, rotations.count { it.isSuccess })
                assertEquals(1, rotations.count { it.exceptionOrNull() is DomainException })
                val winner = rotations.single { it.isSuccess }.getOrThrow()
                assertEquals(rotateSession.accountId, auth.resolve(winner.sessionToken)?.accountId)
                assertNull(auth.resolve(rotateSession.sessionToken))
            } finally {
                rotateExecutor.shutdownNow()
            }

            // Federated-only account deletion creates a hashed identity tombstone before purge.
            val deleteToken = "delete-token-$suffix-" + "d".repeat(32)
            val deleteSubject = "delete-sub-$suffix"
            val deleteRepo = FederatedAuthRepository(db, FixtureVerifier(mapOf(deleteToken to identity(deleteSubject, "delete-$suffix@example.test"))))
            val deleteSession = deleteRepo.exchangeGoogle(GoogleIdentityExchangeRequest(deleteToken, "nonce-$suffix-delete-123456")).session
            val accountData = AccountDataRepository(db)
            accountData.requestDeletion(deleteSession.accountId, AccountDeletionRequest(password = null, confirmation = "DELETE"))
            assertNull(auth.resolve(deleteSession.sessionToken))

            val afterDeleteToken = "delete-after-soft-$suffix-" + "s".repeat(32)
            val afterSoftDelete = FederatedAuthRepository(db, FixtureVerifier(mapOf(afterDeleteToken to identity(deleteSubject, "delete-$suffix@example.test"))))
            val softError = assertFailsWith<DomainException> {
                afterSoftDelete.exchangeGoogle(GoogleIdentityExchangeRequest(afterDeleteToken, "nonce-$suffix-delete-soft-123456"))
            }
            assertEquals("AUTH_ACCOUNT_DELETED", softError.error.code)

            AccountDeletionWorker(db, enabled = false).use { worker ->
                assertEquals(1, worker.purgeDue(10))
            }
            db.tx { c ->
                val identityRows = c.prepareStatement("SELECT count(*) FROM account_external_identity WHERE account_id=?::uuid").use { ps ->
                    ps.setString(1, deleteSession.accountId)
                    ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
                }
                val replayRows = c.prepareStatement("SELECT count(*) FROM auth_exchange_replay WHERE account_id=?::uuid").use { ps ->
                    ps.setString(1, deleteSession.accountId)
                    ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
                }
                val tombstones = c.prepareStatement("SELECT count(*) FROM external_identity_deletion_tombstone WHERE provider='GOOGLE'").use { ps ->
                    ps.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
                }
                assertEquals(0, identityRows)
                assertEquals(0, replayRows)
                assertTrue(tombstones >= 1)
            }

            val afterPurgeToken = "delete-after-purge-$suffix-" + "p".repeat(32)
            val afterPurge = FederatedAuthRepository(db, FixtureVerifier(mapOf(afterPurgeToken to identity(deleteSubject, "delete-$suffix@example.test"))))
            val purgeError = assertFailsWith<DomainException> {
                afterPurge.exchangeGoogle(GoogleIdentityExchangeRequest(afterPurgeToken, "nonce-$suffix-delete-purge-123456"))
            }
            assertEquals("AUTH_ACCOUNT_DELETED", purgeError.error.code)
        }
    }

    @Test
    fun googleHttpContractMatchesFrontendAndAccountHasProductParity() {
        val cfg = config() ?: return
        val suffix = UUID.randomUUID().toString().take(8)
        val token = "http-google-token-$suffix-" + "h".repeat(32)
        val nonce = "nonce-$suffix-http-123456789"
        val verifier = FixtureVerifier(mapOf(token to identity("http-sub-$suffix", "http-$suffix@example.test", "HTTP Google User")))

        testApplication {
            application { veltrixModule(cfg, verifier) }
            val exchange = client.post("/v1/auth/google") {
                contentType(ContentType.Application.Json)
                setBody("{\"idToken\":\"$token\",\"nonce\":\"$nonce\"}")
            }
            assertEquals(HttpStatusCode.Created, exchange.status)
            val session = Json.decodeFromString<SessionResponse>(exchange.bodyAsText())
            assertTrue(session.sessionToken.length >= 32)
            assertTrue(session.accountId.isNotBlank())
            assertTrue(session.expiresAt.isNotBlank())

            suspend fun authedGet(path: String): io.ktor.client.statement.HttpResponse = client.get(path) {
                header(HttpHeaders.Authorization, "Bearer ${session.sessionToken}")
            }
            assertEquals(HttpStatusCode.OK, authedGet("/v1/profile").status)
            assertEquals(HttpStatusCode.OK, authedGet("/v1/home").status)
            assertEquals(HttpStatusCode.OK, authedGet("/v1/personal").status)
            assertEquals(HttpStatusCode.OK, authedGet("/v1/game/profile").status)

            val store = authedGet("/v1/store")
            assertEquals(HttpStatusCode.OK, store.status)
            val storeBody = store.bodyAsText()
            assertTrue(storeBody.contains("\"availability\""), "Store must expose typed availability metadata")
            assertTrue(storeBody.contains("\"displayName\""), "Store must expose presentation-ready display name")

            val project = client.post("/v1/projects") {
                header(HttpHeaders.Authorization, "Bearer ${session.sessionToken}")
                contentType(ContentType.Application.Json)
                setBody("{\"title\":\"Google Project $suffix\",\"purpose\":\"Product parity verification\"}")
            }
            assertEquals(HttpStatusCode.Created, project.status)

            val export = authedGet("/v1/account/export")
            assertEquals(HttpStatusCode.OK, export.status)
            val exportBody = export.bodyAsText()
            assertTrue(exportBody.contains("account_external_identity"))
            assertFalse(exportBody.contains(token), "raw Google ID token must not be exported")
            assertFalse(exportBody.contains(nonce), "raw nonce must not be exported")

            val logout = client.post("/v1/auth/logout") {
                header(HttpHeaders.Authorization, "Bearer ${session.sessionToken}")
            }
            assertEquals(HttpStatusCode.OK, logout.status)
            assertEquals(HttpStatusCode.Unauthorized, authedGet("/v1/profile").status)
        }
    }

    @Test
    fun missingGoogleClientConfigurationFailsClosed() {
        val cfg = config() ?: return
        val disabled = GoogleIdentityVerifierFactory.production(cfg.copy(googleServerClientIds = emptySet()))
        val error = assertFailsWith<DomainException> { disabled.verify("x".repeat(64), "nonce-1234567890123456") }
        assertEquals("AUTH_GOOGLE_NOT_CONFIGURED", error.error.code)
    }
}
