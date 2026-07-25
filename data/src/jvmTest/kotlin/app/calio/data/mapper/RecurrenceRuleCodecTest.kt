package app.calio.data.mapper

import app.calio.model.Frequency
import app.calio.model.RecurrenceEnd
import app.calio.model.RecurrenceRule
import app.calio.model.WeekDayOccurrence
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Rules are stored in the RFC 5545 format so import and export of ICS files are a copy rather than
 * a translation. The round trips below are what guarantee nothing is lost on the way through.
 */
class RecurrenceRuleCodecTest {

    private fun roundTrip(rule: RecurrenceRule) {
        assertEquals(rule, RecurrenceRuleCodec.decode(RecurrenceRuleCodec.encode(rule)))
    }

    @Test
    fun `a plain daily rule survives a round trip`() {
        roundTrip(RecurrenceRule(Frequency.DAILY))
    }

    @Test
    fun `an interval survives a round trip`() {
        roundTrip(RecurrenceRule(Frequency.WEEKLY, interval = 3))
    }

    @Test
    fun `weekdays with and without a position survive a round trip`() {
        roundTrip(
            RecurrenceRule(
                frequency = Frequency.MONTHLY,
                byWeekDays = setOf(
                    WeekDayOccurrence(DayOfWeek.TUESDAY, position = 2),
                    WeekDayOccurrence(DayOfWeek.FRIDAY, position = -1),
                    WeekDayOccurrence(DayOfWeek.MONDAY),
                ),
            ),
        )
    }

    @Test
    fun `month days month numbers and a week start survive a round trip`() {
        roundTrip(
            RecurrenceRule(
                frequency = Frequency.YEARLY,
                byMonthDays = setOf(1, -1),
                byMonths = setOf(3, 9),
                weekStart = DayOfWeek.SUNDAY,
            ),
        )
    }

    @Test
    fun `both kinds of end survive a round trip`() {
        roundTrip(RecurrenceRule(Frequency.DAILY, end = RecurrenceEnd.AfterCount(10)))
        roundTrip(RecurrenceRule(Frequency.DAILY, end = RecurrenceEnd.OnDate(LocalDate(2026, 12, 31))))
    }

    @Test
    fun `the encoding omits everything that is at its default`() {
        assertEquals("FREQ=DAILY", RecurrenceRuleCodec.encode(RecurrenceRule(Frequency.DAILY)))
    }

    @Test
    fun `the encoding matches the standard notation`() {
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            interval = 2,
            byWeekDays = setOf(
                WeekDayOccurrence(DayOfWeek.MONDAY),
                WeekDayOccurrence(DayOfWeek.WEDNESDAY),
            ),
            end = RecurrenceEnd.OnDate(LocalDate(2026, 12, 31)),
        )

        assertEquals("FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;UNTIL=20261231", RecurrenceRuleCodec.encode(rule))
    }

    @Test
    fun `a rule written by another application is understood`() {
        val decoded = RecurrenceRuleCodec.decode("FREQ=MONTHLY;BYDAY=-1FR;COUNT=12;WKST=SU")

        assertEquals(Frequency.MONTHLY, decoded.frequency)
        assertEquals(setOf(WeekDayOccurrence(DayOfWeek.FRIDAY, -1)), decoded.byWeekDays)
        assertEquals(RecurrenceEnd.AfterCount(12), decoded.end)
        assertEquals(DayOfWeek.SUNDAY, decoded.weekStart)
    }

    @Test
    fun `an until value carrying a time is accepted`() {
        val decoded = RecurrenceRuleCodec.decode("FREQ=DAILY;UNTIL=20261231T235959Z")

        assertEquals(RecurrenceEnd.OnDate(LocalDate(2026, 12, 31)), decoded.end)
    }

    @Test
    fun `lower case keys are accepted`() {
        assertEquals(Frequency.DAILY, RecurrenceRuleCodec.decode("freq=daily").frequency)
    }
}
