package app.calio.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.net.URI

/** Hands the sign-in page to whichever browser the user has set as their own. */
class DesktopBrowserLauncher(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BrowserLauncher {

    override suspend fun open(url: String): Unit = withContext(dispatcher) {
        val desktop = Desktop.getDesktop().takeIf {
            Desktop.isDesktopSupported() && it.isSupported(Desktop.Action.BROWSE)
        } ?: error("no browser could be opened on this system")

        desktop.browse(URI(url))
    }
}

/**
 * A whole desktop sign-in, from opening the browser to holding the tokens.
 *
 * The redirect address is only known once the receiver has a port, so the configuration is built
 * around it rather than passed in ready-made.
 */
class DesktopSignIn(
    private val tokenClient: TokenClient,
    private val browser: BrowserLauncher = DesktopBrowserLauncher(),
) {

    suspend fun run(configureFor: (redirectUri: String) -> OAuthConfig): TokenResult =
        LoopbackRedirectReceiver().use { receiver ->
            val config = configureFor(receiver.redirectUri)
            val request = AuthorizationRequest(
                config = config,
                pkce = PkceChallenge.generate(),
                state = newStateValue(),
            )

            browser.open(request.url)

            when (val result = request.readRedirect(receiver.awaitRedirect())) {
                is AuthorizationResult.Granted ->
                    tokenClient.exchange(config, result.code, request.pkce.verifier)

                is AuthorizationResult.Denied ->
                    TokenResult.SignInRequired(result.error, result.description)
            }
        }
}
