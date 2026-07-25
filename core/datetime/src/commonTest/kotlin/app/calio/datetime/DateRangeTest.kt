package app.calio.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class DateRangeTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    @Test
    fun `the end is exclusive`() {
        val range = DateRange(LocalDate(2026, 8, 3), LocalDate(2026, 8, 10))

        assertTrue(LocalDate(2026, 8, 3) in range)
        assertTrue(LocalDate(2026, 8, 9) in range)
        assertFalse(LocalDate(2026, 8, 10) in range)
        assertFalse(LocalDate(2026, 8, 2) in range)
        assertEquals(LocalDate(2026, 8, 9), range.lastDay)
    }

    @Test
    fun `a single day range holds exactly that day`() {
        val range = DateRange.singleDay(LocalDate(2026, 8, 3))

        assertEquals(1, range.dayCount)
        assertEquals(listOf(LocalDate(2026, 8, 3)), range.toList())
    }

    @Test
    fun `an empty range has no days and no last day`() {
        val range = DateRange(LocalDate(2026, 8, 3), LocalDate(2026, 8, 3))

        assertTrue(range.isEmpty)
        assertEquals(0, range.dayCount)
        assertEquals(emptyList(), range.toList())
        assertNull(range.lastDay)
    }

    @Test
    fun `rejects a range that ends before it starts`() {
        assertFailsWith<IllegalArgumentException> {
            DateRange(LocalDate(2026, 8, 10), LocalDate(2026, 8, 3))
        }
    }

    @Test
    fun `iterates every day in order across a month boundary`() {
        val range = DateRange(LocalDate(2026, 8, 30), LocalDate(2026, 9, 2))

        assertEquals(
            listOf(LocalDate(2026, 8, 30), LocalDate(2026, 8, 31), LocalDate(2026, 9, 1)),
            range.toList(),
        )
    }

    @Test
    fun `iterates across a leap day`() {
        val range = DateRange(LocalDate(2028, 2, 28), LocalDate(2028, 3, 1))

        assertEquals(listOf(LocalDate(2028, 2, 28), LocalDate(2028, 2, 29)), range.toList())
    }

    @Test
    fun `ranges that only touch do not overlap`() {
        val first = DateRange(LocalDate(2026, 8, 3), LocalDate(2026, 8, 10))
        val second = DateRange(LocalDate(2026, 8, 10), LocalDate(2026, 8, 17))

        assertFalse(first.overlaps(second))
        assertFalse(second.overlaps(first))
    }

    @Test
    fun `overlapping ranges are detected from both sides`() {
        val week = DateRange(LocalDate(2026, 8, 3), LocalDate(2026, 8, 10))
        val twoDays = DateRange(LocalDate(2026, 8, 9), LocalDate(2026, 8, 11))

        assertTrue(week.overlaps(twoDays))
        assertTrue(twoDays.overlaps(week))
    }

    @Test
    fun `converting to instants uses the given zone`() {
        val range = DateRange.singleDay(LocalDate(2026, 8, 3)).toInstantRange(berlin)

        assertEquals(Instant.parse("2026-08-02T22:00:00Z"), range.start)
        assertEquals(Instant.parse("2026-08-03T22:00:00Z"), range.endExclusive)
    }
}
