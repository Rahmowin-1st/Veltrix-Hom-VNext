package com.veltrix.hom.vnext.server.foundation

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

object PasswordHasher {
    private const val ITERATIONS = 600_000
    private const val KEY_BITS = 256
    private const val SALT_BYTES = 16
    private val rng = SecureRandom()

    fun hash(password: CharArray): String {
        require(password.size in 12..1024) { "Password length must be 12..1024 characters" }
        val salt = ByteArray(SALT_BYTES).also(rng::nextBytes)
        val digest = derive(password, salt, ITERATIONS)
        return "pbkdf2-sha256\$$ITERATIONS\$${b64(salt)}\$${b64(digest)}"
    }

    fun verify(password: CharArray, encoded: String): Boolean {
        val parts = encoded.split('$')
        if (parts.size != 4 || parts[0] != "pbkdf2-sha256") return false
        val iterations = parts[1].toIntOrNull() ?: return false
        if (iterations < 100_000 || iterations > 5_000_000) return false
        val salt = runCatching { Base64.getUrlDecoder().decode(parts[2]) }.getOrNull() ?: return false
        val expected = runCatching { Base64.getUrlDecoder().decode(parts[3]) }.getOrNull() ?: return false
        val actual = derive(password, salt, iterations)
        return MessageDigest.isEqual(expected, actual)
    }

    private fun derive(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }

    private fun b64(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

data class SessionMaterial(val clientToken: String, val storedHashHex: String)

object SessionTokens {
    private val rng = SecureRandom()
    fun generate(): SessionMaterial {
        val raw = ByteArray(32).also(rng::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw)
        return SessionMaterial(token, hash(token))
    }
    fun hash(token: String): String = MessageDigest.getInstance("SHA-256").digest(token.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    fun matches(clientToken: String, storedHashHex: String): Boolean = MessageDigest.isEqual(hash(clientToken).toByteArray(), storedHashHex.toByteArray())
}
