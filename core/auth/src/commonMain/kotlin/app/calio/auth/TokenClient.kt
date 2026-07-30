package app.calio.auth

import app.calio.net.HttpFailure
import app.calio.net.HttpMethod
import app.calio.net.HttpRequest
import app.calio.net.HttpTransport
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * The half of the sign-in that happens without a browser.
 *
 * Redeeming the code and refreshing the access token are the same request with different fields, so
 * they share one path and one reading of the answer.
 */
class TokenClient(
    private val transport: HttpTransport,
    private val clock: Clock = Clock.System,
) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exchange(config: OAuthConfig, code: String, codeVerifier: String): TokenResult =
        request(
            config = config,
            form = mapOf(
                "grant_type" to "authorization_code",
                "code" to code,
                "client_id" to config.clientId,
                "redirect_uri" to config.redirectUri,
                "code_verifier" to codeVerifier,
            ),
            previousRefreshToken = null,
        )

    suspend fun refresh(config: OAuthConfig, refreshToken: String): TokenResult =
        request(
            config = config,
            form = mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to refreshToken,
                "client_id" to config.clientId,
            ),
            previousRefreshToken = refreshToken,
        )

    private suspend fun request(
        config: OAuthConfig,
        form: Map<String, String>,
        previousRefreshToken: String?,
    ): TokenResult {
        val response = try {
            transport.execute(
                HttpRequest(
                    method = HttpMethod.POST,
                    url = config.tokenEndpoint,
                    headers = mapOf("Accept" to "application/json"),
                    body = form.toQueryString(),
                    contentType = "application/x-www-form-urlencoded",
                ),
            )
        } catch (failure: HttpFailure) {
            // A sign-in cannot be lost because a train went into a tunnel.
            return TokenResult.Unavailable(failure.message ?: "the provider could not be reached")
        }

        val payload = runCatching { json.decodeFromString<TokenPayload>(response.body) }.getOrNull()
            ?: return TokenResult.Unavailable("the provider answered with something unreadable")

        if (!response.isSuccessful || payload.accessToken == null) {
            val error = payload.error ?: "http_${response.status}"
            return if (error in PERMANENT_ERRORS) {
                TokenResult.SignInRequired(error, payload.errorDescription)
            } else {
                TokenResult.Unavailable(payload.errorDescription ?: error)
            }
        }

        return TokenResult.Success(
            TokenSet(
                accessToken = payload.accessToken,
                // A refresh answer normally repeats nothing: the token that was sent stays valid,
                // and treating its absence as a withdrawal would sign the user out on every refresh.
                refreshToken = payload.refreshToken ?: previousRefreshToken,
                expiresAt = clock.now() + (payload.expiresIn ?: DEFAULT_LIFETIME_SECONDS).seconds,
                scopes = payload.scope?.split(' ')?.filter { it.isNotBlank() }.orEmpty(),
            ),
        )
    }

    private companion object {
        /**
         * The refusals that mean the grant itself is gone — consent withdrawn, password changed,
         * client removed. Everything else is worth trying again, and the difference decides whether
         * the user is asked to sign in or simply left alone.
         */
        val PERMANENT_ERRORS = setOf("invalid_grant", "invalid_client", "unauthorized_client", "invalid_scope")

        /** Providers may leave the lifetime out; an hour is what they all use in practice. */
        const val DEFAULT_LIFETIME_SECONDS = 3600L
    }
}

sealed interface TokenResult {

    data class Success(val tokens: TokenSet) : TokenResult

    /** The grant is gone for good. Only a new sign-in helps. */
    data class SignInRequired(val error: String, val description: String? = null) : TokenResult

    /** Nothing is wrong with the account; the answer just did not arrive. Try again later. */
    data class Unavailable(val message: String) : TokenResult
}

/**
 * The token endpoint's answer, successful or not.
 *
 * A separate type so the model stays free of serialisation annotations, as everywhere else in the
 * project.
 */
@Serializable
private data class TokenPayload(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    val scope: String? = null,
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
)
