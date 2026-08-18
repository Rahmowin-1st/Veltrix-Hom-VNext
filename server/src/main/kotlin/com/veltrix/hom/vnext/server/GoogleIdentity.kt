package com.veltrix.hom.vnext.server

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.gson.GsonFactory
import com.veltrix.hom.vnext.core.DomainError
import com.veltrix.hom.vnext.core.DomainException
import com.veltrix.hom.vnext.core.ErrorCategory
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.time.Instant

/** Verified, bounded identity claims. Raw Google tokens and nonces never cross this boundary. */
data class VerifiedGoogleIdentity(
    val subject: String,
    val email: String?,
    val emailVerified: Boolean,
    val displayName: String?,
    val pictureUrl: String?,
    val tokenExpiresAt: Instant,
)

fun interface GoogleIdentityVerifier {
    fun verify(idToken: String, expectedNonce: String): VerifiedGoogleIdentity
}

object GoogleIdentityVerifierFactory {
    fun production(config: ServerConfig): GoogleIdentityVerifier =
        if (config.googleServerClientIds.isEmpty()) DisabledGoogleIdentityVerifier
        else GoogleApiIdentityVerifier(config.googleServerClientIds)
}

private object DisabledGoogleIdentityVerifier : GoogleIdentityVerifier {
    override fun verify(idToken: String, expectedNonce: String): VerifiedGoogleIdentity =
        throw DomainException(
            DomainError(
                "AUTH_GOOGLE_NOT_CONFIGURED",
                ErrorCategory.TEMPORARY_UNAVAILABLE,
                "Google sign-in is not configured",
                retryable = false,
            ),
        )
}

/**
 * Production Google ID-token verification. GoogleIdTokenVerifier validates the RS256 signature,
 * issuer, audience and token time claims using Google's rotating public keys. The OIDC nonce is
 * then compared here because it is application/request specific.
 */
class GoogleApiIdentityVerifier private constructor(private val verifier: GoogleIdTokenVerifier) : GoogleIdentityVerifier {
    constructor(clientIds: Set<String>) : this(
        buildVerifier(clientIds, GoogleNetHttpTransport.newTrustedTransport(), GsonFactory.getDefaultInstance()),
    )

    /** Test-only seam that still executes Google's real signature/issuer/audience/time verifier. */
    internal constructor(clientIds: Set<String>, transport: HttpTransport, jsonFactory: JsonFactory) : this(
        buildVerifier(clientIds, transport, jsonFactory),
    )

    companion object {
        private fun buildVerifier(clientIds: Set<String>, transport: HttpTransport, jsonFactory: JsonFactory): GoogleIdTokenVerifier {
            require(clientIds.isNotEmpty()) { "At least one Google server client ID is required" }
            return GoogleIdTokenVerifier.Builder(transport, jsonFactory)
                .setAudience(clientIds.sorted())
                .setIssuers(listOf("https://accounts.google.com", "accounts.google.com"))
                .build()
        }
    }

    override fun verify(idToken: String, expectedNonce: String): VerifiedGoogleIdentity {
        val token = try {
            verifier.verify(idToken)
        } catch (_: IOException) {
            throw DomainException(
                DomainError(
                    "AUTH_GOOGLE_UNAVAILABLE",
                    ErrorCategory.TEMPORARY_UNAVAILABLE,
                    "Google identity verification is temporarily unavailable",
                    retryable = true,
                ),
            )
        } catch (_: GeneralSecurityException) {
            throw invalidGoogleToken()
        } catch (_: IllegalArgumentException) {
            throw invalidGoogleToken()
        } ?: throw invalidGoogleToken()

        val payload = token.payload
        val subject = payload.subject?.trim()?.takeIf { it.length in 1..255 } ?: throw invalidGoogleToken()
        val actualNonce = payload.nonce?.trim()?.takeIf { it.isNotEmpty() } ?: throw nonceMismatch()
        if (!constantTimeEquals(actualNonce, expectedNonce)) throw nonceMismatch()
        val expiresSeconds = payload.expirationTimeSeconds ?: throw invalidGoogleToken()
        val expiresAt = runCatching { Instant.ofEpochSecond(expiresSeconds) }.getOrElse { throw invalidGoogleToken() }
        if (!expiresAt.isAfter(Instant.now())) throw invalidGoogleToken()

        val email = payload.email?.trim()?.lowercase()?.takeIf { it.length in 3..320 }
        val displayName = (payload["name"] as? String)?.trim()?.takeIf { it.isNotEmpty() }?.take(80)
        val picture = (payload["picture"] as? String)?.trim()?.takeIf { it.startsWith("https://") && it.length <= 2048 }
        return VerifiedGoogleIdentity(
            subject = subject,
            email = email,
            emailVerified = payload.emailVerified == true,
            displayName = displayName,
            pictureUrl = picture,
            tokenExpiresAt = expiresAt,
        )
    }

    private fun constantTimeEquals(a: String, b: String): Boolean =
        MessageDigest.isEqual(a.toByteArray(Charsets.UTF_8), b.toByteArray(Charsets.UTF_8))

    private fun invalidGoogleToken() = DomainException(
        DomainError("AUTH_GOOGLE_INVALID", ErrorCategory.AUTH, "Google identity token is invalid or expired"),
    )

    private fun nonceMismatch() = DomainException(
        DomainError("AUTH_GOOGLE_NONCE_MISMATCH", ErrorCategory.AUTH, "Google identity nonce is invalid"),
    )
}
