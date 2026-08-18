package com.veltrix.hom.vnext.server

import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import com.veltrix.hom.vnext.server.foundation.SessionTokens
import java.sql.Connection
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID

class FederatedAuthRepository(
    private val db: Database,
    private val verifier: GoogleIdentityVerifier,
) {
    data class ExchangeResult(val session: SessionResponse, val created: Boolean)

    fun exchangeGoogle(req: GoogleIdentityExchangeRequest): ExchangeResult {
        val idToken = req.idToken.trim()
        val nonce = req.nonce.trim()
        if (idToken.length !in 32..16_384) throw validationAuth("Invalid Google ID token")
        if (nonce.length !in 16..512) throw validationAuth("Invalid Google nonce")

        // Google public-key/network verification happens before the DB transaction.
        val identity = verifier.verify(idToken, nonce)
        val replayHash = sha256("GOOGLE\u0000$idToken\u0000$nonce")
        val sessionToken = SessionTokens.generate()
        val sessionExpires = Instant.now().plus(Duration.ofDays(30))
        val replayExpires = maxOf(identity.tokenExpiresAt.plus(Duration.ofMinutes(10)), Instant.now().plus(Duration.ofMinutes(10)))

        return db.tx { c ->
            val deletedIdentity = c.prepareStatement("SELECT 1 FROM external_identity_deletion_tombstone WHERE provider='GOOGLE' AND provider_subject_hash=? LIMIT 1").use { ps ->
                ps.setString(1, sha256("GOOGLE:${identity.subject}"))
                ps.executeQuery().use { it.next() }
            }
            if (deletedIdentity) throw DomainException(DomainError("AUTH_ACCOUNT_DELETED", ErrorCategory.AUTH, "Account is not active"))
            cleanupExpiredReplays(c)
            val replayInserted = c.prepareStatement(
                """INSERT INTO auth_exchange_replay(replay_hash,provider,expires_at) VALUES (?,'GOOGLE',?) ON CONFLICT DO NOTHING RETURNING replay_hash""",
            ).use { ps ->
                ps.setString(1, replayHash)
                ps.setObject(2, replayExpires.atOffset(ZoneOffset.UTC))
                ps.executeQuery().use { it.next() }
            }
            if (!replayInserted) throw DomainException(
                DomainError("AUTH_GOOGLE_REPLAY", ErrorCategory.AUTH, "Google identity exchange was already used"),
            )

            val linked = c.prepareStatement(
                """SELECT i.account_id,a.deleted_at FROM account_external_identity i JOIN account a ON a.id=i.account_id WHERE i.provider='GOOGLE' AND i.provider_subject=? FOR UPDATE OF i,a""",
            ).use { ps ->
                ps.setString(1, identity.subject)
                ps.executeQuery().use { rs ->
                    if (!rs.next()) null else rs.getObject(1, UUID::class.java).toString() to rs.getObject(2, OffsetDateTime::class.java)
                }
            }

            val created: Boolean
            val accountId: String
            if (linked != null) {
                if (linked.second != null) throw DomainException(
                    DomainError("AUTH_ACCOUNT_DELETED", ErrorCategory.AUTH, "Account is not active"),
                )
                accountId = linked.first
                created = false
                updateIdentitySnapshots(c, accountId, identity)
            } else {
                rejectUnsafeEmailCollision(c, identity.email)
                accountId = c.prepareStatement("INSERT INTO account DEFAULT VALUES RETURNING id").use { ps ->
                    ps.executeQuery().use { rs -> rs.next(); rs.getObject(1, UUID::class.java).toString() }
                }
                c.prepareStatement(
                    """INSERT INTO user_profile(account_id,display_name,preferred_language,timezone) VALUES (?::uuid,?,'en','UTC')""",
                ).use { ps ->
                    ps.setString(1, accountId)
                    ps.setString(2, identity.displayName?.takeIf { it.isNotBlank() } ?: "Veltrix User")
                    ps.executeUpdate()
                }
                c.prepareStatement(
                    """INSERT INTO account_external_identity(account_id,provider,provider_subject,email_snapshot,email_verified,display_name_snapshot,picture_url_snapshot,last_login_at) VALUES (?::uuid,'GOOGLE',?,?,?,?,?,now())""",
                ).use { ps ->
                    ps.setString(1, accountId)
                    ps.setString(2, identity.subject)
                    ps.setString(3, identity.email)
                    ps.setBoolean(4, identity.emailVerified)
                    ps.setString(5, identity.displayName)
                    ps.setString(6, identity.pictureUrl)
                    ps.executeUpdate()
                }
                created = true
            }

            c.prepareStatement(
                """INSERT INTO device_session(account_id,refresh_token_hash,device_label,expires_at) VALUES (?::uuid,?,'google',?)""",
            ).use { ps ->
                ps.setString(1, accountId)
                ps.setString(2, sessionToken.storedHashHex)
                ps.setObject(3, sessionExpires.atOffset(ZoneOffset.UTC))
                ps.executeUpdate()
            }
            c.prepareStatement("UPDATE auth_exchange_replay SET account_id=?::uuid WHERE replay_hash=?").use { ps ->
                ps.setString(1, accountId)
                ps.setString(2, replayHash)
                if (ps.executeUpdate() != 1) throw IllegalStateException("Replay record missing after insert")
            }
            ExchangeResult(SessionResponse(sessionToken.clientToken, accountId, sessionExpires.toString()), created)
        }
    }

    private fun updateIdentitySnapshots(c: Connection, accountId: String, identity: VerifiedGoogleIdentity) {
        c.prepareStatement(
            """UPDATE account_external_identity SET email_snapshot=?,email_verified=?,display_name_snapshot=?,picture_url_snapshot=?,last_login_at=now(),updated_at=now() WHERE account_id=?::uuid AND provider='GOOGLE' AND provider_subject=?""",
        ).use { ps ->
            ps.setString(1, identity.email)
            ps.setBoolean(2, identity.emailVerified)
            ps.setString(3, identity.displayName)
            ps.setString(4, identity.pictureUrl)
            ps.setString(5, accountId)
            ps.setString(6, identity.subject)
            if (ps.executeUpdate() != 1) throw IllegalStateException("Google identity mapping disappeared")
        }
    }

    private fun rejectUnsafeEmailCollision(c: Connection, email: String?) {
        if (email.isNullOrBlank()) return
        val collision = c.prepareStatement(
            """SELECT EXISTS(
                SELECT 1 FROM account_credential ac JOIN account a ON a.id=ac.account_id
                WHERE ac.login_normalized=? AND a.deleted_at IS NULL
                UNION ALL
                SELECT 1 FROM account_external_identity i JOIN account a ON a.id=i.account_id
                WHERE lower(i.email_snapshot)=? AND a.deleted_at IS NULL
            )""",
        ).use { ps ->
            val normalized=email.lowercase()
            ps.setString(1, normalized)
            ps.setString(2, normalized)
            ps.executeQuery().use { rs -> rs.next(); rs.getBoolean(1) }
        }
        if (collision) throw DomainException(
            DomainError(
                "AUTH_ACCOUNT_LINK_REQUIRED",
                ErrorCategory.CONFLICT,
                "Existing account requires an explicit trusted linking flow",
            ),
        )
    }

    private fun cleanupExpiredReplays(c: Connection) {
        c.prepareStatement(
            """DELETE FROM auth_exchange_replay WHERE replay_hash IN (SELECT replay_hash FROM auth_exchange_replay WHERE expires_at<now() ORDER BY expires_at LIMIT 1000)""",
        ).use { it.executeUpdate() }
    }

    private fun validationAuth(message: String) = DomainException(
        DomainError("AUTH_GOOGLE_INVALID_REQUEST", ErrorCategory.VALIDATION, message),
    )
}
