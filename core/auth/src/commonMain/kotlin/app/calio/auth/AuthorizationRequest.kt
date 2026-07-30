package app.calio.auth

/**
 * The sign-in as it is handed to the browser.
 *
 * The user's password is never seen by Calio: the whole exchange happens on the provider's own page,
 * and what comes back is a one-time code. This object keeps the two values that have to outlive the
 * detour through the browser — the verifier and the state — so that the answer can be checked
 * against the question.
 */
data class AuthorizationRequest(
    val config: OAuthConfig,
    val pkce: PkceChallenge,
    val state: String,
) {

    val url: String
        get() {
            val parameters = buildMap {
                put("client_id", config.clientId)
                put("redirect_uri", config.redirectUri)
                put("response_type", "code")
                put("scope", config.scopes.joinToString(separator = " "))
                put("state", state)
                put("code_challenge", pkce.challenge)
                put("code_challenge_method", pkce.method)
                putAll(config.authorizationParameters)
            }
            return "${config.authorizationEndpoint}?${parameters.toQueryString()}"
        }

    /**
     * Reads the redirect the provider sent the user back to.
     *
     * A state that does not match is refused rather than reported as a failed sign-in: it means this
     * redirect belongs to a different request, and redeeming its code would attach somebody else's
     * account to this installation.
     */
    fun readRedirect(redirectUrl: String): AuthorizationResult {
        val parameters = queryParametersOf(redirectUrl)

        parameters["error"]?.let { error ->
            return AuthorizationResult.Denied(error, parameters["error_description"])
        }

        if (parameters["state"] != state) {
            return AuthorizationResult.Denied(
                error = "invalid_state",
                description = "the answer does not belong to this sign-in",
            )
        }

        val code = parameters["code"]
        return if (code.isNullOrBlank()) {
            AuthorizationResult.Denied("invalid_request", "the provider returned no authorization code")
        } else {
            AuthorizationResult.Granted(code)
        }
    }
}

sealed interface AuthorizationResult {

    /** A one-time code, worthless without the verifier that never left the device. */
    data class Granted(val code: String) : AuthorizationResult

    /** The user said no, closed the page, or the answer did not belong to this request. */
    data class Denied(val error: String, val description: String? = null) : AuthorizationResult
}
