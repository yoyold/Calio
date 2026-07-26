package app.calio.data.repository

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FtsQueryTest {

    @Test
    fun `a single word becomes a prefix search`() {
        assertEquals("\"budget\"*", FtsQuery.from("budget"))
    }

    @Test
    fun `only the last word is a prefix, the earlier ones are complete`() {
        assertEquals("\"quarterly\" \"bud\"*", FtsQuery.from("quarterly bud"))
    }

    @Test
    fun `surrounding and repeated whitespace is ignored`() {
        assertEquals("\"one\" \"two\"*", FtsQuery.from("   one    two  "))
    }

    @Test
    fun `an empty query asks for nothing`() {
        assertNull(FtsQuery.from(""))
        assertNull(FtsQuery.from("    "))
    }

    @Test
    fun `syntax characters are dropped rather than obeyed`() {
        // Without this the parentheses would be read as grouping and the search would fail.
        assertEquals("\"meeting\"*", FtsQuery.from("(meeting)"))
        assertEquals("\"budget\"*", FtsQuery.from("budget*"))
        assertEquals("\"minus\"*", FtsQuery.from("-minus"))
    }

    @Test
    fun `a query of nothing but syntax asks for nothing`() {
        assertNull(FtsQuery.from("*"))
        assertNull(FtsQuery.from("()"))
    }

    @Test
    fun `quotes are escaped instead of ending the term`() {
        assertEquals("\"\"\"quoted\"\"\"*", FtsQuery.from("\"quoted\""))
    }

    @Test
    fun `operators lose their meaning because they are quoted`() {
        // "a OR b" would otherwise widen the search; quoted, OR is just a word to look for.
        assertEquals("\"a\" \"OR\" \"b\"*", FtsQuery.from("a OR b"))
    }
}
