package app.calio.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class ReminderTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private val meeting = EventTimeRange.Zoned(
        start = LocalDateTime(2026, 8, 3, 14, 0),
        endExclusive = LocalDateTime(2026, 8, 3, 15, 0),
        timeZone = berlin,
    )

    private fun reminder(trigger: ReminderTrigger) = Reminder(ReminderId("reminder-1"), trigger)

    @Test
    fun `fires the given lead time before the start`() {
        val fiveMinutesBefore = reminder(ReminderTrigger.BeforeStart(5)).fireTime(meeting)
        val oneDayBefore = reminder(ReminderTrigger.BeforeStart(24 * 60)).fireTime(meeting)

        assertEquals(Instant.parse("2026-08-03T11:55:00Z"), fiveMinutesBefore)
        assertEquals(Instant.parse("2026-08-02T12:00:00Z"), oneDayBefore)
    }

    @Test
    fun `fires the given lead time before the end`() {
        val fireTime = reminder(ReminderTrigger.BeforeEnd(30)).fireTime(meeting)

        assertEquals(Instant.parse("2026-08-03T12:30:00Z"), fireTime)
    }

    @Test
    fun `an absolute trigger ignores the event times`() {
        val at = Instant.parse("2026-08-01T06:00:00Z")

        assertEquals(at, reminder(ReminderTrigger.Absolute(at)).fireTime(meeting))
    }

    @Test
    fun `a zero lead time fires exactly at the start`() {
        assertEquals(meeting.startUtc, reminder(ReminderTrigger.BeforeStart(0)).fireTime(meeting))
    }

    @Test
    fun `rejects a negative lead time`() {
        assertFailsWith<IllegalArgumentException> { ReminderTrigger.BeforeStart(-5) }
        assertFailsWith<IllegalArgumentException> { ReminderTrigger.BeforeEnd(-5) }
    }

    @Test
    fun `an event can carry several reminders`() {
        val reminders = listOf(
            reminder(ReminderTrigger.BeforeStart(5)),
            Reminder(ReminderId("reminder-2"), ReminderTrigger.BeforeStart(30)),
            Reminder(ReminderId("reminder-3"), ReminderTrigger.BeforeStart(24 * 60)),
        )

        val fireTimes = reminders.map { it.fireTime(meeting) }.sorted()

        assertEquals(
            listOf(
                Instant.parse("2026-08-02T12:00:00Z"),
                Instant.parse("2026-08-03T11:30:00Z"),
                Instant.parse("2026-08-03T11:55:00Z"),
            ),
            fireTimes,
        )
    }
}
