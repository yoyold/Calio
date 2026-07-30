package app.calio.auth

import app.calio.model.ExternalAccountId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock

/**
 * Hands out a usable access token, refreshing it when there is no longer one.
 *
 * Everything that talks to a provider goes through here, so no caller has to know when a token
 * expires or what to do about it. The lock matters: several calendars synchronising at once would
 * otherwise each start a refresh, and a provider that rotates refresh tokens invalidates the old one
 * the moment the first of them succeeds — the others would then sign the account out.
 */
class AccessTokenProvider(
    private val tokens: TokenStore,
    private val client: TokenClient,
    private val clock: Clock = Clock.System,
    /** Called once when a grant turns out to be gone, so the account can be marked for a new sign-in. */
    private val onSignInRequired: suspend (ExternalAccountId) -> Unit = {},
) {

    private val refreshing = Mutex()

    suspend fun accessToken(accountId: ExternalAccountId, config: OAuthConfig): AccessToken =
        refreshing.withLock {
            val stored = tokens.load(accountId) ?: return AccessToken.SignInRequired

            if (stored.isUsableAt(clock.now())) return AccessToken.Valid(stored.accessToken)

            val refreshToken = stored.refreshToken ?: return signInRequired(accountId)

            when (val result = client.refresh(config, refreshToken)) {
                is TokenResult.Success -> {
                    tokens.save(accountId, result.tokens)
                    AccessToken.Valid(result.tokens.accessToken)
                }

                is TokenResult.SignInRequired -> signInRequired(accountId)

                // The stored tokens are kept: the grant is presumably still good, the network is not.
                is TokenResult.Unavailable -> AccessToken.Unavailable(result.message)
            }
        }

    /** Forgets an account's tokens, which is the part of disconnecting that the database cannot do. */
    suspend fun forget(accountId: ExternalAccountId) {
        refreshing.withLock { tokens.clear(accountId) }
    }

    private suspend fun signInRequired(accountId: ExternalAccountId): AccessToken {
        // The tokens are dropped rather than kept as a reminder: they open nothing, and a stored
        // secret that cannot be used is only a secret waiting to leak.
        tokens.clear(accountId)
        onSignInRequired(accountId)
        return AccessToken.SignInRequired
    }
}

sealed interface AccessToken {

    data class Valid(val value: String) : AccessToken

    /** The account has to be connected again before anything can be synchronised. */
    data object SignInRequired : AccessToken

    /** Nothing to do but try later. */
    data class Unavailable(val message: String) : AccessToken
}
