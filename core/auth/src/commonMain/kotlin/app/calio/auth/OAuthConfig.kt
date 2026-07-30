package app.calio.auth

/**
 * Everything needed to ask a provider for access on the user's behalf.
 *
 * There is no client secret. Calio is an application the user installs, so any secret compiled into
 * it would be readable by anyone who has a copy — which is why the authorization code flow with
 * PKCE exists, and why both Google and Microsoft issue desktop and mobile clients without one.
 */
data class OAuthConfig(
    val clientId: String,
    val authorizationEndpoint: String,
    val tokenEndpoint: String,
    /** Where the provider sends the user back to. Part of the request and checked again at the token endpoint. */
    val redirectUri: String,
    val scopes: List<String>,
    /** Parameters a particular provider needs on the authorization request. */
    val authorizationParameters: Map<String, String> = emptyMap(),
) {
    init {
        require(clientId.isNotBlank()) { "an OAuth client id is required; see the setup notes" }
        require(scopes.isNotEmpty()) { "asking for no scope would grant no access" }
    }
}

/**
 * Google's endpoints and the two parameters that decide whether Calio can work offline.
 *
 * `access_type=offline` is what makes Google issue a refresh token at all; without it the access
 * token expires after an hour and the user has to sign in again. `prompt=consent` forces the
 * consent screen even on a repeat sign-in, because Google returns a refresh token only the first
 * time consent is given — and if that one is ever lost, nothing else brings it back.
 */
object GoogleOAuth {

    const val AUTHORIZATION_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"

    /** Read and write access to the user's calendars. */
    const val CALENDAR_SCOPE = "https://www.googleapis.com/auth/calendar"

    /** Only so the connected account can be shown by the address the user recognises. */
    const val EMAIL_SCOPE = "https://www.googleapis.com/auth/userinfo.email"

    fun config(clientId: String, redirectUri: String): OAuthConfig = OAuthConfig(
        clientId = clientId,
        authorizationEndpoint = AUTHORIZATION_ENDPOINT,
        tokenEndpoint = TOKEN_ENDPOINT,
        redirectUri = redirectUri,
        scopes = listOf(CALENDAR_SCOPE, EMAIL_SCOPE),
        authorizationParameters = mapOf(
            "access_type" to "offline",
            "prompt" to "consent",
        ),
    )
}
