package app.calio.designsystem

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Event colours come from the user, so no palette can guarantee readable text in advance. This is
 * the one piece of the design system that has to be decided at runtime, which makes it the one piece
 * worth testing.
 */
class ReadableContentColorTest {

    private val dark = Color(0xFF1A1A1A)
    private val light = Color(0xFFFFFFFF)

    private fun contentOn(background: Color) = readableContentColor(background, dark, light)

    @Test
    fun `dark text on a light background`() {
        assertEquals(dark, contentOn(Color.White))
        assertEquals(dark, contentOn(Color(0xFFFFF176)))
    }

    @Test
    fun `light text on a dark background`() {
        assertEquals(light, contentOn(Color.Black))
        assertEquals(light, contentOn(Color(0xFF1B6EF3)))
    }

    @Test
    fun `a saturated red carries light text`() {
        assertEquals(light, contentOn(Color(0xFFB3261E)))
    }

    @Test
    fun `a pale category colour carries dark text`() {
        assertEquals(dark, contentOn(Color(0xFFD9E6FF)))
        assertEquals(dark, contentOn(Color(0xFFC8E6C9)))
    }

    @Test
    fun `every colour of the default palette gets a decision`() {
        val palette = listOf(
            Color(0xFF2196F3),
            Color(0xFFE91E63),
            Color(0xFF4CAF50),
            Color(0xFFFF9800),
            Color(0xFF9C27B0),
            Color(0xFF00BCD4),
        )

        palette.forEach { colour ->
            val content = contentOn(colour)
            assertEquals(true, content == dark || content == light, "no decision for $colour")
        }
    }
}
