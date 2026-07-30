package app.calio.net

enum class HttpMethod { GET, POST, PUT, PATCH, DELETE }

data class HttpRequest(
    val method: HttpMethod,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val contentType: String? = null,
)

data class HttpResponse(
    val status: Int,
    val body: String,
    val headers: Map<String, String> = emptyMap(),
) {
    val isSuccessful: Boolean get() = status in 200..299
}

/**
 * The one place the application reaches out to the network.
 *
 * A seam rather than a library: the calls Calio makes are a handful of JSON requests, and having
 * them behind an interface means every service built on top can be tested against recorded answers
 * instead of a live account.
 */
fun interface HttpTransport {
    /**
     * Performs the request.
     *
     * A reply the server did not want to give — a refused token, a missing calendar — comes back as
     * an unsuccessful [HttpResponse], because the body explains what went wrong and callers have to
     * read it. Only a request that never reached an answer at all throws [HttpFailure].
     */
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** The request produced no answer: no network, no route, a timeout, a broken connection. */
class HttpFailure(message: String, cause: Throwable? = null) : Exception(message, cause)
