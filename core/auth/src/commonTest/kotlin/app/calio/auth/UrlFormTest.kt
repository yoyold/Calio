package app.calio.auth

import kotlin.test.Test
import kotlin.test.assertEquals

class UrlFormTest {

    @Test
    fun `unreserved characters are left alone`() {
        assertEquals("abcXYZ0123-._~", "abcXYZ0123-._~".percentEncode())
    }

    @Test
    fun `a space becomes a percent escape rather than a plus`() {
        // Scopes are separated by spaces, and a '+' in the authorization request would be read back
        // as a literal plus by some providers.
        assertEquals("%20", " ".percentEncode())
    }

    @Test
    fun `the characters that would end a parameter are escaped`() {
        assertEquals("a%26b%3Dc%3Fd%23e", "a&b=c?d#e".percentEncode())
    }

    @Test
    fun `a URL is escaped whole, as a scope has to be`() {
        assertEquals(
            "https%3A%2F%2Fwww.googleapis.com%2Fauth%2Fcalendar",
            "https://www.googleapis.com/auth/calendar".percentEncode(),
        )
    }

    @Test
    fun `text outside the ASCII range survives`() {
        assertEquals("Gesprächsnotiz", "Gesprächsnotiz".percentEncode().percentDecode())
    }

    @Test
    fun `a plus in a query is read as a space`() {
        assertEquals("access denied", "access+denied".percentDecode())
    }

    @Test
    fun `an incomplete escape is left as written rather than throwing`() {
        assertEquals("100%", "100%".percentDecode())
    }

    @Test
    fun `parameters are read out of the query`() {
        val parameters = queryParametersOf("http://127.0.0.1:8080/cb?code=abc&state=xyz")

        assertEquals("abc", parameters["code"])
        assertEquals("xyz", parameters["state"])
    }

    @Test
    fun `parameters are read out of the fragment as well`() {
        val parameters = queryParametersOf("http://127.0.0.1:8080/cb#error=access_denied")

        assertEquals("access_denied", parameters["error"])
    }

    @Test
    fun `an address without parameters yields none`() {
        assertEquals(emptyMap(), queryParametersOf("http://127.0.0.1:8080/cb"))
    }

    @Test
    fun `an escaped value is decoded on the way out`() {
        val parameters = queryParametersOf("http://127.0.0.1/cb?error_description=user%20said%20no")

        assertEquals("user said no", parameters["error_description"])
    }
}
