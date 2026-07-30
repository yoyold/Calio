package app.calio.auth

import app.calio.crypto.InMemorySecretStore
import app.calio.model.ExternalAccountId
import app.calio.net.FakeHttpTransport
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AccessTokenProviderTest {

    private val start = Instant.parse("2026-08-05T08:30:00Z")
    private var now = start
    private val clock = object : Clock {
        override fun now(): Instant = now
    }

    private val transport = FakeHttpTransport()
    private val store = TokenStore(InMemorySecretStore())
    private val signedOut = mutableListOf<ExternalAccountId>()

    private val provider = AccessTokenProvider(
        tokens = store,
        client = TokenClient(transport, clock),
        clock = clock,
        onSignInRequired = { signedOut += it },
    )

    private val account = ExternalAccountId("account-1")
    private val config = GoogleOAuth.config("1234.apps.googleusercontent.com", "http://127.0.0.1:1/cb")

    private suspend fun givenTokens(expiresIn: Duration = 1.hours, refreshToken: String? = "rt") {
        store.save(
            account,
            TokenSet(accessToken = "at", refreshToken = refreshToken, expiresAt = now + expiresIn),
        )
    }

    @Test
    fun `a token that is still good is handed out unchanged`() = runTest {
        givenTokens()

        assertEquals(AccessToken.Valid("at"), provider.accessToken(account, config))
        assertTrue(transport.requests.isEmpty())
    }

    @Test
    fun `an expired token is refreshed`() = runTest {
        givenTokens()
        transport.enqueue(200, """{"access_token":"fresh","expires_in":3599}""")

        now = start + 2.hours

        assertEquals(AccessToken.Valid("fresh"), provider.accessToken(account, config))
    }

    @Test
    fun `a token about to expire is refreshed before it is used`() = runTest {
        // A request that sets off with thirty seconds left arrives after the token has died.
        givenTokens(expiresIn = 30.seconds)
        transport.enqueue(200, """{"access_token":"fresh","expires_in":3599}""")

        assertEquals(AccessToken.Valid("fresh"), provider.accessToken(account, config))
    }

    @Test
    fun `a refreshed token is kept for next time`() = runTest {
        givenTokens()
        transport.enqueue(200, """{"access_token":"fresh","expires_in":3599}""")
        now = start + 2.hours

        provider.accessToken(account, config)

        assertEquals("fresh", store.load(account)?.accessToken)
    }

    @Test
    fun `an account that was never connected asks for a sign-in`() = runTest {
        assertEquals(AccessToken.SignInRequired, provider.accessToken(account, config))
    }

    @Test
    fun `an expired token without a refresh token asks for a sign-in`() = runTest {
        givenTokens(refreshToken = null)
        now = start + 2.hours

        assertEquals(AccessToken.SignInRequired, provider.accessToken(account, config))
    }

    @Test
    fun `a withdrawn grant asks for a sign-in and is reported once`() = runTest {
        givenTokens()
        transport.enqueue(400, """{"error":"invalid_grant"}""")
        now = start + 2.hours

        assertEquals(AccessToken.SignInRequired, provider.accessToken(account, config))
        assertEquals(listOf(account), signedOut)
    }

    @Test
    fun `a withdrawn grant leaves no tokens behind`() = runTest {
        givenTokens()
        transport.enqueue(400, """{"error":"invalid_grant"}""")
        now = start + 2.hours

        provider.accessToken(account, config)

        assertNull(store.load(account))
    }

    @Test
    fun `an outage keeps the tokens and reports nothing`() = runTest {
        givenTokens()
        transport.enqueueFailure("no network")
        now = start + 2.hours

        assertTrue(provider.accessToken(account, config) is AccessToken.Unavailable)
        assertEquals("rt", store.load(account)?.refreshToken)
        assertTrue(signedOut.isEmpty())
    }

    @Test
    fun `a second caller uses the token the first one fetched`() = runTest {
        // Only one refresh is answered. A provider that rotates refresh tokens invalidates the old
        // one as soon as the first refresh succeeds, so a second attempt with it would sign the
        // account out for no reason.
        givenTokens()
        transport.enqueue(200, """{"access_token":"fresh","expires_in":3599}""")
        now = start + 2.hours

        val first = provider.accessToken(account, config)
        val second = provider.accessToken(account, config)

        assertEquals(AccessToken.Valid("fresh"), first)
        assertEquals(AccessToken.Valid("fresh"), second)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun `forgetting an account removes its tokens`() = runTest {
        givenTokens()

        provider.forget(account)

        assertNull(store.load(account))
    }

    @Test
    fun `a refresh that is a minute overdue still counts as expired`() = runTest {
        givenTokens(expiresIn = 30.minutes)

        now = start + 31.minutes
        transport.enqueue(200, """{"access_token":"fresh","expires_in":3599}""")

        assertEquals(AccessToken.Valid("fresh"), provider.accessToken(account, config))
    }
}
