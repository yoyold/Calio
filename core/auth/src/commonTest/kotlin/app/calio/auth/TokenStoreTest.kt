package app.calio.auth

import app.calio.crypto.InMemorySecretStore
import app.calio.model.ExternalAccountId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant

class TokenStoreTest {

    private val secrets = InMemorySecretStore()
    private val store = TokenStore(secrets)

    private val account = ExternalAccountId("account-1")
    private val tokens = TokenSet(
        accessToken = "at",
        refreshToken = "rt",
        expiresAt = Instant.parse("2026-08-05T09:30:00Z"),
        scopes = listOf(GoogleOAuth.CALENDAR_SCOPE),
    )

    @Test
    fun `tokens survive being written and read`() = runTest {
        store.save(account, tokens)

        assertEquals(tokens, store.load(account))
    }

    @Test
    fun `an account that was never connected has no tokens`() = runTest {
        assertNull(store.load(ExternalAccountId("nobody")))
    }

    @Test
    fun `two accounts keep their own tokens`() = runTest {
        store.save(account, tokens)
        store.save(ExternalAccountId("account-2"), tokens.copy(accessToken = "other"))

        assertEquals("at", store.load(account)?.accessToken)
        assertEquals("other", store.load(ExternalAccountId("account-2"))?.accessToken)
    }

    @Test
    fun `disconnecting removes the tokens`() = runTest {
        store.save(account, tokens)

        store.clear(account)

        assertNull(store.load(account))
    }

    @Test
    fun `a secret that cannot be read is treated as no secret`() = runTest {
        // What is stored survives an application update, and a format that changed must not stop
        // the application from starting. Asking for a new sign-in is the worst that may happen.
        secrets.put("oauth:${account.value}", "not json at all")

        assertNull(store.load(account))
    }

    @Test
    fun `an account without a refresh token is still stored`() = runTest {
        store.save(account, tokens.copy(refreshToken = null))

        assertNull(store.load(account)?.refreshToken)
    }
}
