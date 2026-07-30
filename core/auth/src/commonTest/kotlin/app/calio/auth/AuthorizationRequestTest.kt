package app.calio.auth

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AuthorizationRequestTest {

    private val config = GoogleOAuth.config(
        clientId = "1234.apps.googleusercontent.com",
        redirectUri = "http://127.0.0.1:41234/oauth2/redirect",
    )

    private val request = AuthorizationRequest(
        config = config,
        pkce = PkceChallenge.of("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        state = "state-value",
    )

    private fun parametersOfRequest() = queryParametersOf(request.url)

    @Test
    fun `the request goes to the provider's authorization endpoint`() {
        assertTrue(request.url.startsWith("${GoogleOAuth.AUTHORIZATION_ENDPOINT}?"))
    }

    @Test
    fun `a code is asked for, not a token`() {
        // The implicit flow hands a token straight to the browser and has been discouraged for
        // years; the code flow keeps the token off the address bar and out of the history.
        assertEquals("code", parametersOfRequest()["response_type"])
    }

    @Test
    fun `the challenge travels but the verifier does not`() {
        val parameters = parametersOfRequest()

        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", parameters["code_challenge"])
        assertEquals("S256", parameters["code_challenge_method"])
        assertTrue(request.pkce.verifier !in request.url)
    }

    @Test
    fun `scopes are separated by spaces and escaped`() {
        assertEquals(
            "${GoogleOAuth.CALENDAR_SCOPE} ${GoogleOAuth.EMAIL_SCOPE}",
            parametersOfRequest()["scope"],
        )
        assertTrue("%20" in request.url)
    }

    @Test
    fun `the provider's own parameters are carried along`() {
        val parameters = parametersOfRequest()

        assertEquals("offline", parameters["access_type"])
        assertEquals("consent", parameters["prompt"])
    }

    @Test
    fun `a granted redirect yields the code`() {
        val result = request.readRedirect(
            "http://127.0.0.1:41234/oauth2/redirect?state=state-value&code=4%2F0Ab_c",
        )

        assertEquals(AuthorizationResult.Granted("4/0Ab_c"), result)
    }

    @Test
    fun `a refusal is reported with the reason the provider gave`() {
        val result = request.readRedirect(
            "http://127.0.0.1:41234/oauth2/redirect?error=access_denied&state=state-value",
        )

        assertEquals(AuthorizationResult.Denied("access_denied", null), result)
    }

    @Test
    fun `an answer belonging to another sign-in is refused`() {
        // The code may well be valid; it is simply not the answer to this question, and redeeming it
        // would attach an account nobody asked to connect.
        val result = request.readRedirect(
            "http://127.0.0.1:41234/oauth2/redirect?state=somebody-elses&code=4%2F0Ab_c",
        )

        assertTrue(result is AuthorizationResult.Denied && result.error == "invalid_state")
    }

    @Test
    fun `a redirect without any code is refused`() {
        val result = request.readRedirect("http://127.0.0.1:41234/oauth2/redirect?state=state-value")

        assertTrue(result is AuthorizationResult.Denied && result.error == "invalid_request")
    }

    @Test
    fun `an error outranks a state that does not match`() {
        // The user pressed cancel; there is nothing suspicious to report and nothing to check.
        val result = request.readRedirect("http://127.0.0.1:41234/oauth2/redirect?error=access_denied")

        assertTrue(result is AuthorizationResult.Denied && result.error == "access_denied")
    }
}
