package app.calio.domain.recurrence

import app.calio.model.Frequency
import app.calio.model.RecurrenceRule
import app.calio.model.WeekDayOccurrence
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The date mathematics on its own — no events, no zones, no exceptions.
 *
 * These are the cases that go wrong quietly: a series on the 31st, the last Friday of a month, a
 * birthday on 29 February, and a week that starts on Sunday.
 */
class RecurrenceDatesTest {

    private fun dates(rule: RecurrenceRule, seed: LocalDate, count: Int): List<LocalDate> =
        recurrenceDates(rule, seed).take(count).toList()

    @Test
    fun `a daily rule produces consecutive days`() {
        val result = dates(RecurrenceRule(Frequency.DAILY), LocalDate(2026, 8, 3), 3)

        assertEquals(
            listOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 4), LocalDate(2026, 8, 5)),
            result,
        )
    }

    @Test
    fun `a daily interval skips the days in between`() {
        val result = dates(RecurrenceRule(Frequency.DAILY, interval = 3), LocalDate(2026, 8, 3), 3)

        assertEquals(
            listOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 6), LocalDate(2026, 8, 9)),
            result,
        )
    }

    @Test
    fun `a daily rule crosses a month boundary without stumbling`() {
        val result = dates(RecurrenceRule(Frequency.DAILY), LocalDate(2026, 8, 30), 3)

        assertEquals(
            listOf(LocalDate(2026, 8, 30), LocalDate(2026, 8, 31), LocalDate(2026, 9, 1)),
            result,
        )
    }

    @Test
    fun `a weekly rule without weekdays repeats the seed weekday`() {
        val result = dates(RecurrenceRule(Frequency.WEEKLY), LocalDate(2026, 8, 3), 3)

        assertEquals(
            listOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 10), LocalDate(2026, 8, 17)),
            result,
        )
    }

    @Test
    fun `a weekly rule fires on every selected weekday`() {
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            byWeekDays = setOf(
                WeekDayOccurrence(DayOfWeek.MONDAY),
                WeekDayOccurrence(DayOfWeek.WEDNESDAY),
                WeekDayOccurrence(DayOfWeek.FRIDAY),
            ),
        )

        assertEquals(
            listOf(
                LocalDate(2026, 8, 3),
                LocalDate(2026, 8, 5),
                LocalDate(2026, 8, 7),
                LocalDate(2026, 8, 10),
            ),
            dates(rule, LocalDate(2026, 8, 3), 4),
        )
    }

    @Test
    fun `a fortnightly rule skips the week in between, not the weekdays`() {
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            interval = 2,
            byWeekDays = setOf(
                WeekDayOccurrence(DayOfWeek.MONDAY),
                WeekDayOccurrence(DayOfWeek.FRIDAY),
            ),
        )

        assertEquals(
            listOf(
                LocalDate(2026, 8, 3),
                LocalDate(2026, 8, 7),
                LocalDate(2026, 8, 17),
                LocalDate(2026, 8, 21),
            ),
            dates(rule, LocalDate(2026, 8, 3), 4),
        )
    }

    @Test
    fun `the week start decides which days belong to the same week`() {
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            interval = 2,
            byWeekDays = setOf(
                WeekDayOccurrence(DayOfWeek.SUNDAY),
                WeekDayOccurrence(DayOfWeek.MONDAY),
            ),
            weekStart = DayOfWeek.SUNDAY,
        )

        // Starting on Monday 3 August, the Sunday of the same Sunday-based week is 2 August and has
        // already passed, so the next hit is the Sunday two weeks later.
        assertEquals(
            listOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 16), LocalDate(2026, 8, 17)),
            dates(rule, LocalDate(2026, 8, 3), 3),
        )
    }

    @Test
    fun `a monthly rule on the thirty first skips the months that are too short`() {
        val result = dates(RecurrenceRule(Frequency.MONTHLY), LocalDate(2026, 1, 31), 4)

        assertEquals(
            listOf(
                LocalDate(2026, 1, 31),
                LocalDate(2026, 3, 31),
                LocalDate(2026, 5, 31),
                LocalDate(2026, 7, 31),
            ),
            result,
        )
    }

    @Test
    fun `a monthly rule on the last day of the month follows the month length`() {
        val rule = RecurrenceRule(Frequency.MONTHLY, byMonthDays = setOf(-1))

        assertEquals(
            listOf(
                LocalDate(2026, 1, 31),
                LocalDate(2026, 2, 28),
                LocalDate(2026, 3, 31),
                LocalDate(2026, 4, 30),
            ),
            dates(rule, LocalDate(2026, 1, 1), 4),
        )
    }

    @Test
    fun `a monthly rule can select the second tuesday`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            byWeekDays = setOf(WeekDayOccurrence(DayOfWeek.TUESDAY, position = 2)),
        )

        assertEquals(
            listOf(LocalDate(2026, 1, 13), LocalDate(2026, 2, 10), LocalDate(2026, 3, 10)),
            dates(rule, LocalDate(2026, 1, 1), 3),
        )
    }

    @Test
    fun `a monthly rule can select the last friday`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            byWeekDays = setOf(WeekDayOccurrence(DayOfWeek.FRIDAY, position = -1)),
        )

        assertEquals(
            listOf(LocalDate(2026, 1, 30), LocalDate(2026, 2, 27), LocalDate(2026, 3, 27)),
            dates(rule, LocalDate(2026, 1, 1), 3),
        )
    }

    @Test
    fun `a monthly rule without a position takes every matching weekday`() {
        val rule = RecurrenceRule(
            frequency = Frequency.MONTHLY,
            byWeekDays = setOf(WeekDayOccurrence(DayOfWeek.FRIDAY)),
        )

        assertEquals(
            listOf(
                LocalDate(2026, 1, 2),
                LocalDate(2026, 1, 9),
                LocalDate(2026, 1, 16),
                LocalDate(2026, 1, 23),
                LocalDate(2026, 1, 30),
                LocalDate(2026, 2, 6),
            ),
            dates(rule, LocalDate(2026, 1, 1), 6),
        )
    }

    @Test
    fun `a yearly rule repeats the same day every year`() {
        val result = dates(RecurrenceRule(Frequency.YEARLY), LocalDate(2026, 8, 3), 3)

        assertEquals(
            listOf(LocalDate(2026, 8, 3), LocalDate(2027, 8, 3), LocalDate(2028, 8, 3)),
            result,
        )
    }

    @Test
    fun `a yearly rule on the twenty ninth of february only fires in leap years`() {
        val result = dates(RecurrenceRule(Frequency.YEARLY), LocalDate(2028, 2, 29), 3)

        assertEquals(
            listOf(LocalDate(2028, 2, 29), LocalDate(2032, 2, 29), LocalDate(2036, 2, 29)),
            result,
        )
    }

    @Test
    fun `a yearly rule can fire in several months`() {
        val rule = RecurrenceRule(Frequency.YEARLY, byMonths = setOf(3, 9))

        assertEquals(
            listOf(LocalDate(2026, 3, 15), LocalDate(2026, 9, 15), LocalDate(2027, 3, 15)),
            dates(rule, LocalDate(2026, 3, 15), 3),
        )
    }

    @Test
    fun `a weekday earlier in the seed week is not produced before the seed`() {
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            byWeekDays = setOf(
                WeekDayOccurrence(DayOfWeek.MONDAY),
                WeekDayOccurrence(DayOfWeek.WEDNESDAY),
            ),
        )

        // The Monday of that week is 3 August and lies before the seed, so it must be skipped.
        assertEquals(
            listOf(LocalDate(2026, 8, 5), LocalDate(2026, 8, 10), LocalDate(2026, 8, 12)),
            dates(rule, LocalDate(2026, 8, 5), 3),
        )
    }
}
