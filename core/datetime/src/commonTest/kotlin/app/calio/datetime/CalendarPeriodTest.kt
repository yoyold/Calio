package app.calio.datetime

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarPeriodTest {

    private val someMonday = LocalDate(2026, 8, 3)

    @Test
    fun `the anchor of a period is its canonical first day`() {
        val date = LocalDate(2026, 8, 17)

        assertEquals(date, CalendarPeriod.DAY.anchorOf(date))
        assertEquals(LocalDate(2026, 8, 17), CalendarPeriod.WEEK.anchorOf(date))
        assertEquals(LocalDate(2026, 8, 1), CalendarPeriod.MONTH.anchorOf(date))
        assertEquals(LocalDate(2026, 1, 1), CalendarPeriod.YEAR.anchorOf(date))
    }

    @Test
    fun `a day period covers a single day`() {
        assertEquals(1, CalendarPeriod.DAY.rangeOf(someMonday).dayCount)
    }

    @Test
    fun `stepping through months never drifts away from a long month`() {
        // Starting on 31 January, naive month arithmetic would clamp to 28 February and then stay
        // on the 28th for the rest of the year.
        var date = LocalDate(2026, 1, 31)

        repeat(12) { date = CalendarPeriod.MONTH.next(date) }

        assertEquals(LocalDate(2027, 1, 1), date)
    }

    @Test
    fun `stepping forwards and back returns to the same period`() {
        val dates = listOf(LocalDate(2026, 1, 31), LocalDate(2026, 2, 28), LocalDate(2028, 2, 29))

        for (date in dates) {
            for (period in CalendarPeriod.entries) {
                val anchor = period.anchorOf(date)
                val roundTrip = period.previous(period.next(date))

                assertEquals(anchor, roundTrip, "$period round trip failed for $date")
            }
        }
    }

    @Test
    fun `a week step moves exactly seven days`() {
        assertEquals(LocalDate(2026, 8, 10), CalendarPeriod.WEEK.next(someMonday))
        assertEquals(LocalDate(2026, 7, 27), CalendarPeriod.WEEK.previous(someMonday))
    }

    @Test
    fun `a week step from mid week snaps to the week start first`() {
        assertEquals(LocalDate(2026, 8, 10), CalendarPeriod.WEEK.next(LocalDate(2026, 8, 6)))
    }

    @Test
    fun `a year step lands on the first of january`() {
        assertEquals(LocalDate(2027, 1, 1), CalendarPeriod.YEAR.next(someMonday))
        assertEquals(LocalDate(2025, 1, 1), CalendarPeriod.YEAR.previous(someMonday))
    }

    @Test
    fun `a month grid is always six full weeks`() {
        for (month in 1..12) {
            val grid = CalendarPeriod.MONTH.gridRangeOf(LocalDate(2026, month, 15))

            assertEquals(42, grid.dayCount, "month $month")
            assertEquals(DayOfWeek.MONDAY, grid.start.dayOfWeek, "month $month")
        }
    }

    @Test
    fun `a month grid starts on the configured week start`() {
        val grid = CalendarPeriod.MONTH.gridRangeOf(someMonday, weekStart = DayOfWeek.SUNDAY)

        assertEquals(DayOfWeek.SUNDAY, grid.start.dayOfWeek)
        assertEquals(42, grid.dayCount)
    }

    @Test
    fun `a month grid contains every day of its month`() {
        val month = CalendarPeriod.MONTH.rangeOf(someMonday)
        val grid = CalendarPeriod.MONTH.gridRangeOf(someMonday)

        assertTrue(month.all { it in grid })
        assertTrue(grid.start <= month.start)
        assertTrue(grid.endExclusive >= month.endExclusive)
    }

    @Test
    fun `day week and year views render exactly their own range`() {
        for (period in listOf(CalendarPeriod.DAY, CalendarPeriod.WEEK, CalendarPeriod.YEAR)) {
            assertEquals(period.rangeOf(someMonday), period.gridRangeOf(someMonday))
        }
    }
}
