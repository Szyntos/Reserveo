package org.julsz.smnt.auth

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Stored format: "iterations:base64(salt):base64(hash)". No external crypto lib needed. */
object PasswordHasher {
    private const val ITERATIONS = 120_000
    private const val KEY_LENGTH_BITS = 256

    fun hash(password: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val digest = pbkdf2(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        return "$ITERATIONS:${Base64.getEncoder().encodeToString(salt)}:${Base64.getEncoder().encodeToString(digest)}"
    }

    fun verify(password: String, stored: String): Boolean {
        val parts = stored.split(":")
        if (parts.size != 3) return false
        val iterations = parts[0].toIntOrNull() ?: return false
        val salt = Base64.getDecoder().decode(parts[1])
        val expected = Base64.getDecoder().decode(parts[2])
        val actual = pbkdf2(password.toCharArray(), salt, iterations, expected.size * 8)
        return actual.contentEquals(expected)
    }

    private fun pbkdf2(password: CharArray, salt: ByteArray, iterations: Int, keyLengthBits: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, keyLengthBits)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return factory.generateSecret(spec).encoded
    }
}
