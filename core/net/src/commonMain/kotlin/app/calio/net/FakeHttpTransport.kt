package app.calio.net

/**
 * A transport that answers from a script.
 *
 * It lives beside the real one rather than in a test source set because every module that talks to a
 * provider needs it, and a fake that only some tests can reach gets rewritten in each of them.
 */
class FakeHttpTransport : HttpTransport {

    private val answers = ArrayDeque<Answer>()

    /** Every request that was made, in order, so a test can check what was actually sent. */
    val requests: MutableList<HttpRequest> = mutableListOf()

    fun enqueue(status: Int, body: String, headers: Map<String, String> = emptyMap()): FakeHttpTransport =
        apply { answers.addLast(Answer.Reply(HttpResponse(status, body, headers))) }

    fun enqueueFailure(message: String = "no network"): FakeHttpTransport =
        apply { answers.addLast(Answer.Failure(message)) }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return when (val answer = answers.removeFirstOrNull()) {
            null -> error("no answer left for ${request.method} ${request.url}")
            is Answer.Reply -> answer.response
            is Answer.Failure -> throw HttpFailure(answer.message)
        }
    }

    private sealed interface Answer {
        data class Reply(val response: HttpResponse) : Answer
        data class Failure(val message: String) : Answer
    }
}
