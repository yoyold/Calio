package app.calio.auth

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

/**
 * What a sign-in is worth afterwards.
 *
 * The access token is short-lived and sent with every request. The refresh token is the durable one:
 * it is what allows Calio to keep working without asking again, and losing it means a new sign-in
 * while leaking it means standing access to the calendar. It is stored only through a
 * [app.calio.crypto.SecretStore].
 */
data class TokenSet(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Instant,
    val scopes: List<String> = emptyList(),
) {
    /**
     * Whether the access token can still be used.
     *
     * The margin is what keeps a request from setting off with a token that expires while it is in
     * flight — the clocks of two machines are never quite the same, and a rejected request costs
     * more than a refresh done slightly early.
     */
    fun isUsableAt(now: Instant, margin: Duration = DEFAULT_MARGIN): Boolean = now + margin < expiresAt

    companion object {
        val DEFAULT_MARGIN: Duration = 60.seconds
    }
}
