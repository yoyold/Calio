package app.calio.auth

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A real socket rather than a stand-in: what is being checked is that a browser arriving at the
 * redirect address is understood, and only an actual HTTP request can show that.
 */
class LoopbackRedirectReceiverTest {

    private suspend fun visit(url: String): Int = withContext(Dispatchers.IO) {
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        try {
            connection.responseCode
        } finally {
            connection.disconnect()
        }
    }

    @Test
    fun `the address to hand the provider names the loopback interface`() {
        LoopbackRedirectReceiver().use { receiver ->
            assertTrue(receiver.redirectUri.startsWith("http://127.0.0.1:"))
            assertTrue(receiver.redirectUri.endsWith("/oauth2/redirect"))
        }
    }

    @Test
    fun `a free port is chosen rather than a fixed one`() {
        LoopbackRedirectReceiver().use { first ->
            LoopbackRedirectReceiver().use { second ->
                assertTrue(first.redirectUri != second.redirectUri)
            }
        }
    }

    @Test
    fun `the redirect the browser follows comes back whole`() = runTest {
        LoopbackRedirectReceiver().use { receiver ->
            val redirect = async { receiver.awaitRedirect() }

            visit("${receiver.redirectUri}?code=4%2F0Ab_c&state=xyz")

            val parameters = queryParametersOf(redirect.await())
            assertEquals("4/0Ab_c", parameters["code"])
            assertEquals("xyz", parameters["state"])
        }
    }

    @Test
    fun `the browser is given a page rather than left hanging`() = runTest {
        LoopbackRedirectReceiver().use { receiver ->
            val redirect = async { receiver.awaitRedirect() }

            val status = visit("${receiver.redirectUri}?code=abc&state=xyz")

            redirect.await()
            assertEquals(200, status)
        }
    }

    @Test
    fun `a refusal arrives just as a code does`() = runTest {
        LoopbackRedirectReceiver().use { receiver ->
            val redirect = async { receiver.awaitRedirect() }

            visit("${receiver.redirectUri}?error=access_denied&state=xyz")

            assertEquals("access_denied", queryParametersOf(redirect.await())["error"])
        }
    }
}
