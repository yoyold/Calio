package app.calio.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanningTest {

    @Test
    fun `the default working week runs from monday to friday`() {
        val hours = WorkingHours.Default

        assertTrue(hours.isWorkingDay(DayOfWeek.MONDAY))
        assertTrue(hours.isWorkingDay(DayOfWeek.FRIDAY))
        assertFalse(hours.isWorkingDay(DayOfWeek.SATURDAY))
        assertFalse(hours.isWorkingDay(DayOfWeek.SUNDAY))
    }

    @Test
    fun `a non working day has no window`() {
        assertNull(WorkingHours.Default.windowFor(DayOfWeek.SUNDAY))
        assertEquals(
            DayWindow(LocalTime(9, 0), LocalTime(17, 0)),
            WorkingHours.Default.windowFor(DayOfWeek.WEDNESDAY),
        )
    }

    @Test
    fun `working hours can differ per weekday`() {
        val hours = WorkingHours(
            mapOf(
                DayOfWeek.MONDAY to DayWindow(LocalTime(8, 0), LocalTime(16, 0)),
                DayOfWeek.FRIDAY to DayWindow(LocalTime(8, 0), LocalTime(13, 0)),
            ),
        )

        assertEquals(LocalTime(16, 0), hours.windowFor(DayOfWeek.MONDAY)?.endExclusive)
        assertEquals(LocalTime(13, 0), hours.windowFor(DayOfWeek.FRIDAY)?.endExclusive)
    }

    @Test
    fun `rejects a working day that ends before it starts`() {
        assertFailsWith<IllegalArgumentException> {
            DayWindow(LocalTime(17, 0), LocalTime(9, 0))
        }
    }

    @Test
    fun `buffers are off by default`() {
        assertFalse(BufferPolicy().isEnabled)
    }

    @Test
    fun `rejects a buffer of zero minutes`() {
        assertFailsWith<IllegalArgumentException> { BufferPolicy(defaultMinutes = 0) }
    }
}
