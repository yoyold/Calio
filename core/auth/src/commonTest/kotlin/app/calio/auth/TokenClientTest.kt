package app.calio.auth

import app.calio.net.FakeHttpTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class TokenClientTest {

    private val now = Instant.parse("2026-08-05T08:30:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private val transport = FakeHttpTransport()
    private val client = TokenClient(transport, clock)

    private val config = GoogleOAuth.config(
        clientId = "1234.apps.googleusercontent.com",
        redirectUri = "http://127.0.0.1:41234/oauth2/redirect",
    )

    private fun sentForm(): Map<String, String> =
        queryParametersOf("?" + transport.requests.single().body.orEmpty())

    @Test
    fun `redeeming a code yields tokens`() = runTest {
        transport.enqueue(
            200,
            """{"access_token":"at","refresh_token":"rt","expires_in":3599,"scope":"a b"}""",
        )

        val result = client.exchange(config, code = "the-code", codeVerifier = "the-verifier")

        val tokens = (result as TokenResult.Success).tokens
        assertEquals("at", tokens.accessToken)
        assertEquals("rt", tokens.refreshToken)
        assertEquals(now + 3599.seconds, tokens.expiresAt)
        assertEquals(listOf("a", "b"), tokens.scopes)
    }

    @Test
    fun `the verifier is what redeems the code`() = runTest {
        transport.enqueue(200, """{"access_token":"at","expires_in":3599}""")

        client.exchange(config, code = "the-code", codeVerifier = "the-verifier")

        val form = sentForm()
        assertEquals("authorization_code", form["grant_type"])
        assertEquals("the-code", form["code"])
        assertEquals("the-verifier", form["code_verifier"])
        assertEquals(config.redirectUri, form["redirect_uri"])
    }

    @Test
    fun `the request is form encoded, as the specification requires`() = runTest {
        transport.enqueue(200, """{"access_token":"at","expires_in":3599}""")

        client.exchange(config, code = "the-code", codeVerifier = "the-verifier")

        assertEquals("application/x-www-form-urlencoded", transport.requests.single().contentType)
    }

    @Test
    fun `a refresh keeps the token it was given`() = runTest {
        // Google answers a refresh without repeating the refresh token. Reading that as a withdrawal
        // would sign the user out roughly once an hour.
        transport.enqueue(200, """{"access_token":"newer","expires_in":3599}""")

        val result = client.refresh(config, refreshToken = "rt")

        assertEquals("rt", (result as TokenResult.Success).tokens.refreshToken)
    }

    @Test
    fun `a rotated refresh token replaces the old one`() = runTest {
        transport.enqueue(200, """{"access_token":"newer","refresh_token":"rotated","expires_in":3599}""")

        val result = client.refresh(config, refreshToken = "rt")

        assertEquals("rotated", (result as TokenResult.Success).tokens.refreshToken)
    }

    @Test
    fun `a withdrawn grant asks for a new sign-in`() = runTest {
        transport.enqueue(400, """{"error":"invalid_grant","error_description":"Token has been expired or revoked."}""")

        val result = client.refresh(config, refreshToken = "rt")

        assertEquals("invalid_grant", (result as TokenResult.SignInRequired).error)
    }

    @Test
    fun `a server that is having a bad day does not sign anyone out`() = runTest {
        transport.enqueue(503, """{"error":"backend_error"}""")

        assertTrue(client.refresh(config, refreshToken = "rt") is TokenResult.Unavailable)
    }

    @Test
    fun `an unreachable provider does not sign anyone out`() = runTest {
        transport.enqueueFailure("no network")

        assertTrue(client.refresh(config, refreshToken = "rt") is TokenResult.Unavailable)
    }

    @Test
    fun `an unreadable answer does not sign anyone out`() = runTest {
        transport.enqueue(200, "<html>proxy says no</html>")

        assertTrue(client.refresh(config, refreshToken = "rt") is TokenResult.Unavailable)
    }

    @Test
    fun `a success without an access token is not a success`() = runTest {
        transport.enqueue(200, """{"expires_in":3599}""")

        assertTrue(client.refresh(config, refreshToken = "rt") is TokenResult.Unavailable)
    }

    @Test
    fun `a missing lifetime falls back to an hour rather than to expired`() = runTest {
        transport.enqueue(200, """{"access_token":"at"}""")

        val tokens = (client.refresh(config, refreshToken = "rt") as TokenResult.Success).tokens

        assertTrue(tokens.isUsableAt(now))
    }

    @Test
    fun `the client id travels but no secret does`() = runTest {
        transport.enqueue(200, """{"access_token":"at","expires_in":3599}""")

        client.refresh(config, refreshToken = "rt")

        val form = sentForm()
        assertEquals(config.clientId, form["client_id"])
        assertNull(form["client_secret"])
    }
}
