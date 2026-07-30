package app.calio.auth

/**
 * Opens the provider's sign-in page.
 *
 * Deliberately the system browser and not a window of Calio's own: the user is about to type a
 * password, and they should be able to see the provider's address bar and their own saved session
 * while doing it. An application that renders the login form itself is indistinguishable from one
 * that reads it.
 */
fun interface BrowserLauncher {
    suspend fun open(url: String)
}
