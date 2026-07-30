package app.calio.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PkceTest {

    @Test
    fun `the published example derives the published challenge`() {
        // The worked example from the PKCE specification. If this holds, a provider will accept
        // what Calio sends; if it does not, sign-in fails with an error that explains nothing.
        val pkce = PkceChallenge.of("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", pkce.challenge)
    }

    @Test
    fun `the digest method is the one that offers protection`() {
        assertEquals("S256", PkceChallenge.generate().method)
    }

    @Test
    fun `a generated verifier has the length the specification allows`() {
        val verifier = PkceChallenge.generate().verifier

        assertEquals(43, verifier.length)
    }

    @Test
    fun `a generated verifier uses only characters that survive a URL`() {
        val allowed = ('A'..'Z') + ('a'..'z') + ('0'..'9') + listOf('-', '.', '_', '~')

        assertTrue(PkceChallenge.generate().verifier.all { it in allowed })
    }

    @Test
    fun `two sign-ins do not share a verifier`() {
        assertTrue(PkceChallenge.generate().verifier != PkceChallenge.generate().verifier)
    }

    @Test
    fun `a verifier that is too short is refused`() {
        assertFailsWith<IllegalArgumentException> { PkceChallenge.of("too-short") }
    }

    @Test
    fun `a state value is not reused`() {
        assertTrue(newStateValue() != newStateValue())
    }
}
