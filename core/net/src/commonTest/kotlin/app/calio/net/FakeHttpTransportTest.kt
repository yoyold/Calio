package app.calio.net

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeHttpTransportTest {

    private val transport = FakeHttpTransport()

    @Test
    fun `answers are given in the order they were queued`() = runTest {
        transport.enqueue(200, "first").enqueue(200, "second")

        assertEquals("first", transport.execute(HttpRequest(HttpMethod.GET, "https://example.test/a")).body)
        assertEquals("second", transport.execute(HttpRequest(HttpMethod.GET, "https://example.test/b")).body)
    }

    @Test
    fun `every request is recorded`() = runTest {
        transport.enqueue(200, "{}")

        transport.execute(HttpRequest(HttpMethod.POST, "https://example.test/token", body = "grant_type=x"))

        val recorded = transport.requests.single()
        assertEquals(HttpMethod.POST, recorded.method)
        assertEquals("grant_type=x", recorded.body)
    }

    @Test
    fun `a refusal is an answer, not an exception`() = runTest {
        transport.enqueue(400, """{"error":"invalid_grant"}""")

        val response = transport.execute(HttpRequest(HttpMethod.POST, "https://example.test/token"))

        assertFalse(response.isSuccessful)
        assertTrue("invalid_grant" in response.body)
    }

    @Test
    fun `a request that never arrives fails`() = runTest {
        transport.enqueueFailure()

        assertFailsWith<HttpFailure> {
            transport.execute(HttpRequest(HttpMethod.GET, "https://example.test/a"))
        }
    }

    @Test
    fun `every status between 200 and 299 counts as success`() {
        assertTrue(HttpResponse(204, "").isSuccessful)
        assertFalse(HttpResponse(301, "").isSuccessful)
        assertFalse(HttpResponse(500, "").isSuccessful)
    }
}
