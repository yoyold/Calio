package app.calio.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ColorTest {

    private val calendarColor = CalioColor.of(0x21, 0x96, 0xF3)
    private val categoryColor = CalioColor.of(0xE9, 0x1E, 0x63)
    private val overrideColor = CalioColor.of(0x4C, 0xAF, 0x50)

    private val calendar = Calendar(
        id = CalendarId("calendar-1"),
        name = "Work",
        color = calendarColor,
        audit = audit(),
    )

    private val category = Category(
        id = CategoryId("category-1"),
        name = "Study",
        color = categoryColor,
        audit = audit(),
    )

    @Test
    fun `splits an argb value into its components`() {
        val color = CalioColor.of(red = 0x21, green = 0x96, blue = 0xF3, alpha = 0x80)

        assertEquals(0x80, color.alpha)
        assertEquals(0x21, color.red)
        assertEquals(0x96, color.green)
        assertEquals(0xF3, color.blue)
    }

    @Test
    fun `defaults to a fully opaque colour`() {
        assertEquals(0xFF, CalioColor.of(0, 0, 0).alpha)
    }

    @Test
    fun `rejects a component outside the byte range`() {
        assertFailsWith<IllegalArgumentException> { CalioColor.of(256, 0, 0) }
    }

    @Test
    fun `an explicit event colour wins over everything else`() {
        assertEquals(overrideColor, resolveEventColor(overrideColor, category, calendar))
    }

    @Test
    fun `the category colour wins over the calendar colour`() {
        assertEquals(categoryColor, resolveEventColor(null, category, calendar))
    }

    @Test
    fun `the calendar colour is the last resort`() {
        assertEquals(calendarColor, resolveEventColor(null, null, calendar))
    }
}
