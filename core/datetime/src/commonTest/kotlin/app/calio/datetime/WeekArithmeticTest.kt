package app.calio.datetime

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WeekArithmeticTest {

    @Test
    fun `a week starting on monday begins on the monday before`() {
        // 3 August 2026 is a Monday, 5 August a Wednesday.
        assertEquals(LocalDate(2026, 8, 3), startOfWeek(LocalDate(2026, 8, 5)))
        assertEquals(LocalDate(2026, 8, 3), startOfWeek(LocalDate(2026, 8, 3)))
        assertEquals(LocalDate(2026, 8, 3), startOfWeek(LocalDate(2026, 8, 9)))
    }

    @Test
    fun `a week starting on sunday begins a day earlier`() {
        assertEquals(
            LocalDate(2026, 8, 2),
            startOfWeek(LocalDate(2026, 8, 5), weekStart = DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun `a week always spans seven days`() {
        val monday = weekOf(LocalDate(2026, 8, 5))
        val sunday = weekOf(LocalDate(2026, 8, 5), weekStart = DayOfWeek.SUNDAY)

        assertEquals(7, monday.dayCount)
        assertEquals(7, sunday.dayCount)
        assertEquals(LocalDate(2026, 8, 10), monday.endExclusive)
    }

    @Test
    fun `a week crossing a month boundary keeps its days together`() {
        val week = weekOf(LocalDate(2026, 9, 1))

        assertEquals(LocalDate(2026, 8, 31), week.start)
        assertEquals(LocalDate(2026, 9, 7), week.endExclusive)
    }

    @Test
    fun `a month range covers exactly that month`() {
        val august = monthOf(LocalDate(2026, 8, 17))

        assertEquals(LocalDate(2026, 8, 1), august.start)
        assertEquals(LocalDate(2026, 9, 1), august.endExclusive)
        assertEquals(31, august.dayCount)
    }

    @Test
    fun `february has twenty nine days in a leap year`() {
        assertEquals(28, monthOf(LocalDate(2026, 2, 10)).dayCount)
        assertEquals(29, monthOf(LocalDate(2028, 2, 10)).dayCount)
    }

    @Test
    fun `a year range covers every day of that year`() {
        assertEquals(365, yearOf(LocalDate(2026, 6, 1)).dayCount)
        assertEquals(366, yearOf(LocalDate(2028, 6, 1)).dayCount)
    }

    @Test
    fun `the iso week number of an ordinary date`() {
        // 3 August 2026 is the Monday of ISO week 32.
        assertEquals(32, isoWeekNumber(LocalDate(2026, 8, 3)))
        assertEquals(32, isoWeekNumber(LocalDate(2026, 8, 9)))
        assertEquals(2026, isoWeekYear(LocalDate(2026, 8, 3)))
    }

    @Test
    fun `late december can already belong to week one of the next year`() {
        // 1 January 2026 is a Thursday, so ISO week 1 of 2026 starts on 29 December 2025.
        assertEquals(1, isoWeekNumber(LocalDate(2025, 12, 29)))
        assertEquals(2026, isoWeekYear(LocalDate(2025, 12, 29)))

        assertEquals(52, isoWeekNumber(LocalDate(2025, 12, 28)))
        assertEquals(2025, isoWeekYear(LocalDate(2025, 12, 28)))
    }

    @Test
    fun `early january can still belong to the last week of the previous year`() {
        // 2026 has 53 ISO weeks, and 1 January 2027 falls into the last of them.
        assertEquals(53, isoWeekNumber(LocalDate(2026, 12, 31)))
        assertEquals(53, isoWeekNumber(LocalDate(2027, 1, 1)))
        assertEquals(2026, isoWeekYear(LocalDate(2027, 1, 1)))

        assertEquals(1, isoWeekNumber(LocalDate(2027, 1, 4)))
        assertEquals(2027, isoWeekYear(LocalDate(2027, 1, 4)))
    }

    @Test
    fun `the first day of a year always starts week one or the last week of the year before`() {
        for (year in 2020..2040) {
            val week = isoWeekNumber(LocalDate(year, 1, 1))

            assertEquals(true, week == 1 || week >= 52, "unexpected week $week for 1 January $year")
        }
    }

    @Test
    fun `every day of an iso week reports the same number`() {
        val week = weekOf(LocalDate(2026, 12, 31))
        val numbers = week.map { isoWeekNumber(it) }.distinct()

        assertEquals(listOf(53), numbers)
    }
}
