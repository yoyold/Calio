package app.calio.domain.recurrence

import app.calio.datetime.InstantRange
import app.calio.model.AuditFields
import app.calio.model.CalendarId
import app.calio.model.DeviceId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.EventTimeRange
import app.calio.model.RecurrenceRule
import app.calio.model.Revision
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

internal val berlin = TimeZone.of("Europe/Berlin")

private val testDevice = DeviceId("test-device")

private val testAudit = AuditFields(
    createdAt = Instant.parse("2026-07-01T08:00:00Z"),
    updatedAt = Instant.parse("2026-07-01T08:00:00Z"),
    revision = Revision.of(1_751_356_800_000, 0, testDevice),
    originDevice = testDevice,
)

internal fun event(
    id: String = "event-1",
    title: String = "Standup",
    start: LocalDateTime = LocalDateTime(2026, 8, 3, 9, 0),
    endExclusive: LocalDateTime = LocalDateTime(2026, 8, 3, 9, 30),
    timeZone: TimeZone = berlin,
    recurrence: RecurrenceRule? = null,
): Event = Event(
    id = EventId(id),
    calendarId = CalendarId("calendar-1"),
    title = title,
    timeRange = EventTimeRange.Zoned(start, endExclusive, timeZone),
    audit = testAudit,
    recurrence = recurrence,
)

internal fun allDayEvent(
    id: String = "event-1",
    range: EventTimeRange.AllDay,
    recurrence: RecurrenceRule? = null,
): Event = Event(
    id = EventId(id),
    calendarId = CalendarId("calendar-1"),
    title = "Holiday",
    timeRange = range,
    audit = testAudit,
    recurrence = recurrence,
)

internal fun window(from: String, toExclusive: String): InstantRange =
    InstantRange(Instant.parse(from), Instant.parse(toExclusive))

internal fun List<EventOccurrence>.localStarts(): List<LocalDateTime> =
    map { (it.timeRange as EventTimeRange.Zoned).start }
