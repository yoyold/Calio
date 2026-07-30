package app.calio.crypto

import java.security.MessageDigest
import java.security.SecureRandom

actual fun sha256(input: ByteArray): ByteArray =
    MessageDigest.getInstance("SHA-256").digest(input)

// Seeded by the operating system and safe to share between threads.
private val secureRandom = SecureRandom()

actual fun randomBytes(count: Int): ByteArray {
    require(count > 0) { "asking for no random bytes is a mistake, not a shortcut" }
    return ByteArray(count).also(secureRandom::nextBytes)
}
