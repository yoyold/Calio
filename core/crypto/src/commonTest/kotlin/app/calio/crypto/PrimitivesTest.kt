package app.calio.crypto

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PrimitivesTest {

    private fun ByteArray.toHex(): String = joinToString(separator = "") { byte ->
        val value = byte.toInt() and 0xFF
        "0123456789abcdef"[value ushr 4].toString() + "0123456789abcdef"[value and 0x0F]
    }

    @Test
    fun `the published digest of abc is produced`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256("abc".encodeToByteArray()).toHex(),
        )
    }

    @Test
    fun `the digest of nothing is the published one`() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256(ByteArray(0)).toHex(),
        )
    }

    @Test
    fun `random bytes are the length that was asked for`() {
        assertEquals(32, randomBytes(32).size)
    }

    @Test
    fun `two draws differ`() {
        // Not a test of the generator's quality, only that one is actually being consulted.
        assertTrue(randomBytes(32).toHex() != randomBytes(32).toHex())
    }

    @Test
    fun `asking for nothing is refused`() {
        assertFailsWith<IllegalArgumentException> { randomBytes(0) }
    }
}
