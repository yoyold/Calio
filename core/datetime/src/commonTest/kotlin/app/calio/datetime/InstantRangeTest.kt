package app.calio.datetime

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class InstantRangeTest {

    private fun at(hour: Int) =
        Instant.parse("2026-08-03T${hour.toString().padStart(2, '0')}:00:00Z")

    private fun range(startHour: Int, endHour: Int) =
        InstantRange(start = at(startHour), endExclusive = at(endHour))

    @Test
    fun `the end is exclusive`() {
        val window = range(9, 12)

        assertTrue(Instant.parse("2026-08-03T09:00:00Z") in window)
        assertFalse(Instant.parse("2026-08-03T12:00:00Z") in window)
        assertEquals(3.hours, window.duration)
    }

    @Test
    fun `rejects a range that ends before it starts`() {
        assertFailsWith<IllegalArgumentException> {
            InstantRange(
                Instant.parse("2026-08-03T12:00:00Z"),
                Instant.parse("2026-08-03T09:00:00Z"),
            )
        }
    }

    @Test
    fun `ranges that only touch do not overlap`() {
        assertFalse(range(9, 12).overlaps(range(12, 14)))
        assertNull(range(9, 12).intersect(range(12, 14)))
    }

    @Test
    fun `the intersection is the shared part`() {
        assertEquals(range(10, 12), range(9, 12).intersect(range(10, 14)))
        assertEquals(range(10, 11), range(9, 14).intersect(range(10, 11)))
    }

    @Test
    fun `expanding grows the range on both sides`() {
        val expanded = range(9, 12).expandedBy(1.hours)

        assertEquals(range(8, 13), expanded)
    }

    @Test
    fun `an empty range contains nothing`() {
        val empty = range(9, 9)

        assertTrue(empty.isEmpty)
        assertFalse(Instant.parse("2026-08-03T09:00:00Z") in empty)
    }
}
