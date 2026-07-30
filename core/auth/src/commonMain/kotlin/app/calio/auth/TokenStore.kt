package app.calio.auth

import app.calio.crypto.SecretStore
import app.calio.model.ExternalAccountId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * The tokens of every connected account, kept where the platform keeps secrets.
 *
 * Nothing here reaches the database. The account row records that a connection exists; what the
 * connection is worth stays behind the operating system's protection, so a copied database file
 * grants nobody access to a calendar.
 */
class TokenStore(private val secrets: SecretStore) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun save(accountId: ExternalAccountId, tokens: TokenSet) {
        secrets.put(keyFor(accountId), json.encodeToString(tokens.toStored()))
    }

    suspend fun load(accountId: ExternalAccountId): TokenSet? {
        val stored = secrets.get(keyFor(accountId)) ?: return null
        // An unreadable secret is treated as no secret: the format changed, or the platform could
        // not open it. Either way the answer is a fresh sign-in, not a crash on startup.
        return runCatching { json.decodeFromString<StoredTokens>(stored).toTokenSet() }.getOrNull()
    }

    suspend fun clear(accountId: ExternalAccountId) {
        secrets.remove(keyFor(accountId))
    }

    private fun keyFor(accountId: ExternalAccountId): String = "oauth:${accountId.value}"

    private fun TokenSet.toStored() = StoredTokens(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtMillis = expiresAt.toEpochMilliseconds(),
        scopes = scopes,
    )

    private fun StoredTokens.toTokenSet() = TokenSet(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAt = Instant.fromEpochMilliseconds(expiresAtMillis),
        scopes = scopes,
    )
}

@Serializable
private data class StoredTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_at") val expiresAtMillis: Long,
    val scopes: List<String> = emptyList(),
)
