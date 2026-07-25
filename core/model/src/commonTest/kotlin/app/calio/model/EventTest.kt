package app.calio.model

import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EventTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun event(
        title: String = "Standup",
        kind: EventKind = EventKind.STANDARD,
        busyStatus: BusyStatus = BusyStatus.BUSY,
        status: EventStatus = EventStatus.CONFIRMED,
        recurrence: RecurrenceRule? = null,
        auditFields: AuditFields = audit(),
    ) = Event(
        id = EventId("event-1"),
        calendarId = CalendarId("calendar-1"),
        title = title,
        timeRange = EventTimeRange.Zoned(
            LocalDateTime(2026, 8, 3, 9, 0),
            LocalDateTime(2026, 8, 3, 9, 15),
            berlin,
        ),
        audit = auditFields,
        kind = kind,
        busyStatus = busyStatus,
        status = status,
        recurrence = recurrence,
    )

    @Test
    fun `rejects a blank title`() {
        assertFailsWith<IllegalArgumentException> { event(title = "   ") }
    }

    @Test
    fun `a busy confirmed event occupies its slot`() {
        assertTrue(event().occupiesTime)
    }

    @Test
    fun `an event marked free does not occupy its slot`() {
        assertFalse(event(busyStatus = BusyStatus.FREE).occupiesTime)
    }

    @Test
    fun `a cancelled event does not occupy its slot`() {
        assertFalse(event(status = EventStatus.CANCELLED).occupiesTime)
    }

    @Test
    fun `a soft deleted event does not occupy its slot`() {
        val deleted = audit(deletedAt = kotlin.time.Instant.parse("2026-07-02T08:00:00Z"))

        assertFalse(event(auditFields = deleted).occupiesTime)
    }

    @Test
    fun `a focus block still occupies its slot`() {
        assertTrue(event(kind = EventKind.FOCUS).occupiesTime)
    }

    @Test
    fun `an event without a rule is not recurring`() {
        assertFalse(event().isRecurring)
        assertTrue(event(recurrence = RecurrenceRule(Frequency.DAILY)).isRecurring)
    }

    @Test
    fun `a location needs both coordinates or neither`() {
        assertFailsWith<IllegalArgumentException> {
            EventLocation(label = "Office", latitude = 52.52)
        }

        val located = EventLocation(label = "Office", latitude = 52.52, longitude = 13.405)
        assertEquals("Office", located.label)
    }
}
