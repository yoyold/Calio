package app.calio.feature.eventeditor

import app.calio.model.Attachment
import app.calio.model.AttachmentId
import app.calio.model.Attendee
import app.calio.model.AttendeeId
import app.calio.model.CalendarId
import app.calio.model.EventId
import app.calio.model.EventTimeRange
import app.calio.model.Frequency
import app.calio.model.RecurrenceRule
import app.calio.testing.testAudit
import app.calio.testing.testCalendar
import app.calio.testing.testEvent
import app.calio.testing.testZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EventDraftTest {

    private fun draft(
        title: String = "Review",
        isAllDay: Boolean = false,
        startDate: LocalDate = LocalDate(2026, 8, 3),
        startTime: LocalTime = LocalTime(9, 0),
        endDate: LocalDate = LocalDate(2026, 8, 3),
        endTime: LocalTime = LocalTime(10, 0),
        calendarId: CalendarId? = testCalendar.id,
    ) = EventDraft(
        title = title,
        calendarId = calendarId,
        isAllDay = isAllDay,
        startDate = startDate,
        startTime = startTime,
        endDate = endDate,
        endTime = endTime,
        timeZone = testZone,
    )

    @Test
    fun `a timed draft keeps its wall clock times and zone`() {
        val range = draft().toTimeRange() as EventTimeRange.Zoned

        assertEquals(LocalDateTime(2026, 8, 3, 9, 0), range.start)
        assertEquals(LocalDateTime(2026, 8, 3, 10, 0), range.endExclusive)
        assertEquals(testZone, range.timeZone)
    }

    @Test
    fun `the editor shows the last day, storage keeps the end exclusive`() {
        // "Third to the fifth" is three days and is stored as 3 August until 6 August.
        val range = draft(
            isAllDay = true,
            startDate = LocalDate(2026, 8, 3),
            endDate = LocalDate(2026, 8, 5),
        ).toTimeRange() as EventTimeRange.AllDay

        assertEquals(LocalDate(2026, 8, 3), range.startDate)
        assertEquals(LocalDate(2026, 8, 6), range.endDateExclusive)
        assertEquals(3, range.dayCount)
    }

    @Test
    fun `a single all day event covers exactly one day`() {
        val range = draft(
            isAllDay = true,
            startDate = LocalDate(2026, 8, 3),
            endDate = LocalDate(2026, 8, 3),
        ).toTimeRange() as EventTimeRange.AllDay

        assertEquals(1, range.dayCount)
    }

    @Test
    fun `an all day event survives the trip through the editor unchanged`() {
        val original = app.calio.testing.testAllDayEvent(
            range = EventTimeRange.AllDay(LocalDate(2026, 8, 3), LocalDate(2026, 8, 6)),
        )

        val roundTripped = original.toDraft()
            .toEvent(existing = original, id = original.id, audit = testAudit)

        assertEquals(original.timeRange, roundTripped.timeRange)
    }

    @Test
    fun `a timed event survives the trip through the editor unchanged`() {
        val original = testEvent(
            start = LocalDateTime(2026, 8, 3, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 3, 9, 30),
        )

        val roundTripped = original.toDraft()
            .toEvent(existing = original, id = original.id, audit = testAudit)

        assertEquals(original.timeRange, roundTripped.timeRange)
        assertEquals(original.title, roundTripped.title)
    }

    @Test
    fun `a blank title is a problem`() {
        assertTrue(DraftProblem.BlankTitle in draft(title = "   ").problems())
    }

    @Test
    fun `a draft without a calendar is a problem`() {
        assertTrue(DraftProblem.NoCalendar in draft(calendarId = null).problems())
    }

    @Test
    fun `an end before the start is a problem`() {
        val backwards = draft(startTime = LocalTime(11, 0), endTime = LocalTime(10, 0))

        assertTrue(DraftProblem.EndBeforeStart in backwards.problems())
    }

    @Test
    fun `an all day event may start and end on the same day`() {
        val singleDay = draft(
            isAllDay = true,
            startDate = LocalDate(2026, 8, 3),
            endDate = LocalDate(2026, 8, 3),
        )

        assertTrue(singleDay.isValid)
    }

    @Test
    fun `an all day event may not end before it starts`() {
        val backwards = draft(
            isAllDay = true,
            startDate = LocalDate(2026, 8, 5),
            endDate = LocalDate(2026, 8, 3),
        )

        assertTrue(DraftProblem.EndBeforeStart in backwards.problems())
    }

    @Test
    fun `every problem is reported at once`() {
        val bad = draft(title = "", calendarId = null, startTime = LocalTime(11, 0), endTime = LocalTime(9, 0))

        assertEquals(
            setOf(DraftProblem.BlankTitle, DraftProblem.NoCalendar, DraftProblem.EndBeforeStart),
            bad.problems(),
        )
    }

    @Test
    fun `blank optional fields are stored as nothing rather than as empty text`() {
        val event = draft().copy(location = "  ", description = "", notes = " ")
            .toEvent(existing = null, id = EventId("event-1"), audit = testAudit)

        assertNull(event.location)
        assertNull(event.description)
        assertNull(event.notes)
    }

    @Test
    fun `fields the editor cannot change are carried over`() {
        // Renaming an event must not drop the people invited to it.
        val existing = testEvent(
            start = LocalDateTime(2026, 8, 3, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 3, 10, 0),
        ).copy(
            attendees = listOf(Attendee(AttendeeId("attendee-1"), name = "Alex")),
            attachments = listOf(
                Attachment(AttachmentId("attachment-1"), "agenda.pdf", "application/pdf", 10, "abc"),
            ),
        )

        val renamed = existing.toDraft().copy(title = "Renamed")
            .toEvent(existing = existing, id = existing.id, audit = testAudit)

        assertEquals(existing.attendees, renamed.attendees)
        assertEquals(existing.attachments, renamed.attachments)
        assertEquals("Renamed", renamed.title)
    }

    @Test
    fun `a repeat preset becomes a recurrence rule and back`() {
        val weekly = draft().copy(recurrence = RecurrencePreset.Weekly, recurrenceInterval = 2)

        val rule = weekly.toRecurrenceRule()

        assertEquals(RecurrenceRule(Frequency.WEEKLY, interval = 2), rule)
        assertEquals(RecurrencePreset.Weekly, RecurrencePreset.of(rule))
        assertEquals(RecurrencePreset.None, RecurrencePreset.of(null))
    }

    @Test
    fun `a draft without a repeat produces no rule`() {
        assertNull(draft().toRecurrenceRule())
    }
}
