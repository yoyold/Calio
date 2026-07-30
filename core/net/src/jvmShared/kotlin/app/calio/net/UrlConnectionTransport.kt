package app.calio.net

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * The transport both platforms use, built on the HTTP client that is already in the runtime.
 *
 * Calio makes a small number of straightforward JSON requests. A client library would add megabytes
 * and an engine per platform to do what fifty lines here do.
 */
class UrlConnectionTransport(
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val timeout: Duration = 30.seconds,
) : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse = withContext(dispatcher) {
        val connection = try {
            URI(request.url).toURL().openConnection() as HttpURLConnection
        } catch (failure: IOException) {
            throw HttpFailure("could not reach ${request.url}", failure)
        } catch (failure: IllegalArgumentException) {
            throw HttpFailure("not a usable address: ${request.url}", failure)
        }

        try {
            // The runtime's HTTP client refuses PATCH outright. Every provider Calio talks to
            // accepts the override header instead, which is the established way around it.
            if (request.method == HttpMethod.PATCH) {
                connection.requestMethod = HttpMethod.POST.name
                connection.setRequestProperty("X-HTTP-Method-Override", HttpMethod.PATCH.name)
            } else {
                connection.requestMethod = request.method.name
            }
            connection.connectTimeout = timeout.inWholeMilliseconds.toInt()
            connection.readTimeout = timeout.inWholeMilliseconds.toInt()
            connection.instanceFollowRedirects = false
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }

            request.body?.let { body ->
                connection.doOutput = true
                request.contentType?.let { connection.setRequestProperty("Content-Type", it) }
                connection.outputStream.use { it.write(body.encodeToByteArray()) }
            }

            val status = connection.responseCode
            // A refusal arrives on the error stream, and its body is the part that explains why.
            val stream = if (status in 200..399) connection.inputStream else connection.errorStream
            val body = stream?.use { it.readBytes().decodeToString() }.orEmpty()

            HttpResponse(
                status = status,
                body = body,
                // The status line comes back under a null name, which is not a header.
                headers = connection.headerFields.entries
                    .mapNotNull { (name, values) -> name?.let { it to values.joinToString(separator = ", ") } }
                    .toMap(),
            )
        } catch (failure: IOException) {
            throw HttpFailure("no answer from ${request.url}", failure)
        } finally {
            connection.disconnect()
        }
    }
}
