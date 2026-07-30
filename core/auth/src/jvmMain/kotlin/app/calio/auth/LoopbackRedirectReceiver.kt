package app.calio.auth

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.coroutines.coroutineContext

/**
 * Catches the redirect at the end of a desktop sign-in.
 *
 * A desktop application has no address of its own for the provider to send the user back to, so it
 * listens on the loopback interface for as long as the sign-in lasts. Both Google and Microsoft
 * support this for installed applications and accept any port, which is why one is asked for rather
 * than fixed: a fixed port would collide with whatever else is running and could be occupied by
 * another program before Calio got there.
 *
 * Only the loopback address is bound, so nothing outside the machine can reach it.
 */
class LoopbackRedirectReceiver(
    private val path: String = "/oauth2/redirect",
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AutoCloseable {

    private val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())

    /** The address to put in the request, known only once the port has been assigned. */
    val redirectUri: String = "http://127.0.0.1:${server.localPort}$path"

    /**
     * Waits for the browser to arrive and returns the address it asked for.
     *
     * Accepting a connection cannot be interrupted, so the socket is closed if the surrounding work
     * is cancelled — a sign-in the user has walked away from must not hold a thread for ever.
     */
    suspend fun awaitRedirect(): String = withContext(dispatcher) {
        val closeOnCancellation = coroutineContext[Job]?.invokeOnCompletion { cause ->
            if (cause != null) close()
        }

        try {
            server.accept().use { socket ->
                // Only the request line is of interest: "GET /oauth2/redirect?code=… HTTP/1.1".
                val requestLine = socket.getInputStream().bufferedReader().readLine().orEmpty()
                val target = requestLine.split(' ').getOrNull(1).orEmpty()

                val writer = socket.getOutputStream().writer()
                writer.write(CLOSING_PAGE)
                writer.flush()

                "http://127.0.0.1:${server.localPort}$target"
            }
        } finally {
            closeOnCancellation?.dispose()
        }
    }

    override fun close() {
        server.close()
    }

    private companion object {
        val CLOSING_PAGE = buildString {
            val body = """
                <!doctype html>
                <meta charset="utf-8">
                <title>Calio</title>
                <body style="font-family: system-ui, sans-serif; text-align: center; padding: 4rem">
                <h1>Signed in</h1>
                <p>You can close this tab and go back to Calio.</p>
            """.trimIndent()

            append("HTTP/1.1 200 OK\r\n")
            append("Content-Type: text/html; charset=utf-8\r\n")
            append("Content-Length: ${body.encodeToByteArray().size}\r\n")
            append("Connection: close\r\n\r\n")
            append(body)
        }
    }
}
