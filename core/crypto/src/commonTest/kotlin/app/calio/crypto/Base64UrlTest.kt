package app.calio.crypto

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Base64UrlTest {

    @Test
    fun `the standard examples encode as specified`() {
        val expected = mapOf(
            "" to "",
            "f" to "Zg",
            "fo" to "Zm8",
            "foo" to "Zm9v",
            "foob" to "Zm9vYg",
            "fooba" to "Zm9vYmE",
            "foobar" to "Zm9vYmFy",
        )

        expected.forEach { (plain, encoded) ->
            assertEquals(encoded, plain.encodeToByteArray().encodeBase64Url(), "encoding '$plain'")
        }
    }

    @Test
    fun `no padding is written`() {
        assertEquals("Zg", byteArrayOf('f'.code.toByte()).encodeBase64Url())
    }

    @Test
    fun `the two characters that differ from ordinary base64 are used`() {
        // These bytes are what produces '+' and '/' in the standard alphabet. A provider rejects
        // those in a query parameter, which is the whole reason for the URL-safe variant.
        val encoded = byteArrayOf(0xFB.toByte(), 0xFF.toByte(), 0xBF.toByte()).encodeBase64Url()

        assertEquals("-_-_", encoded)
    }

    @Test
    fun `every byte value survives the round trip`() {
        val everything = ByteArray(256) { it.toByte() }

        assertContentEquals(everything, everything.encodeBase64Url().decodeBase64Url())
    }

    @Test
    fun `lengths that are not a multiple of three survive the round trip`() {
        repeat(10) { length ->
            val bytes = ByteArray(length) { (it * 7 + 3).toByte() }
            assertContentEquals(bytes, bytes.encodeBase64Url().decodeBase64Url(), "length $length")
        }
    }

    @Test
    fun `padding is accepted when a server adds it`() {
        assertContentEquals("fo".encodeToByteArray(), "Zm8=".decodeBase64Url())
    }

    @Test
    fun `a character outside the alphabet is refused`() {
        assertFailsWith<IllegalArgumentException> { "Zm9v!".decodeBase64Url() }
    }
}
