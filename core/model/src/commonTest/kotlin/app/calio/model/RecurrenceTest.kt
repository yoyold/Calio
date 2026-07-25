package app.calio.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecurrenceTest {

    @Test
    fun `defaults to an unbounded weekly repetition of every single period`() {
        val rule = RecurrenceRule(frequency = Frequency.WEEKLY)

        assertEquals(1, rule.interval)
        assertEquals(RecurrenceEnd.Never, rule.end)
        assertFalse(rule.isBounded)
    }

    @Test
    fun `reports a bounded rule as bounded`() {
        val untilDate = RecurrenceRule(Frequency.DAILY, end = RecurrenceEnd.OnDate(LocalDate(2026, 12, 31)))
        val untilCount = RecurrenceRule(Frequency.DAILY, end = RecurrenceEnd.AfterCount(10))

        assertTrue(untilDate.isBounded)
        assertTrue(untilCount.isBounded)
    }

    @Test
    fun `rejects an interval below one`() {
        assertFailsWith<IllegalArgumentException> {
            RecurrenceRule(Frequency.DAILY, interval = 0)
        }
    }

    @Test
    fun `rejects a month day of zero`() {
        assertFailsWith<IllegalArgumentException> {
            RecurrenceRule(Frequency.MONTHLY, byMonthDays = setOf(0))
        }
    }

    @Test
    fun `accepts a month day counted from the end of the month`() {
        val rule = RecurrenceRule(Frequency.MONTHLY, byMonthDays = setOf(-1))

        assertEquals(setOf(-1), rule.byMonthDays)
    }

    @Test
    fun `rejects a month outside the year`() {
        assertFailsWith<IllegalArgumentException> {
            RecurrenceRule(Frequency.YEARLY, byMonths = setOf(13))
        }
    }

    @Test
    fun `rejects a weekday position of zero`() {
        assertFailsWith<IllegalArgumentException> {
            WeekDayOccurrence(DayOfWeek.TUESDAY, position = 0)
        }
    }

    @Test
    fun `accepts the last friday of a period`() {
        val occurrence = WeekDayOccurrence(DayOfWeek.FRIDAY, position = -1)

        assertEquals(DayOfWeek.FRIDAY, occurrence.dayOfWeek)
        assertEquals(-1, occurrence.position)
    }

    @Test
    fun `rejects a recurrence that produces no occurrence at all`() {
        assertFailsWith<IllegalArgumentException> {
            RecurrenceEnd.AfterCount(0)
        }
    }

    @Test
    fun `a cancelled occurrence must not carry a patch`() {
        assertFailsWith<IllegalArgumentException> {
            RecurrenceOverride(
                eventId = EventId("event-1"),
                originalStart = LocalDateTime(2026, 8, 3, 9, 0),
                type = OverrideType.CANCELLED,
                patch = EventPatch(title = "moved"),
            )
        }
    }

    @Test
    fun `a modified occurrence stores only the fields that differ`() {
        val override = RecurrenceOverride(
            eventId = EventId("event-1"),
            originalStart = LocalDateTime(2026, 8, 3, 9, 0),
            type = OverrideType.MODIFIED,
            patch = EventPatch(title = "Standup, extended"),
        )

        assertEquals("Standup, extended", override.patch?.title)
        assertEquals(null, override.patch?.notes)
    }
}
