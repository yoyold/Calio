package app.calio.testing

import app.calio.model.AuditFields
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.CalioColor
import app.calio.model.Category
import app.calio.model.CategoryId
import app.calio.model.DeviceId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.EventTimeRange
import app.calio.model.RecurrenceRule
import app.calio.model.Revision
import app.calio.model.Task
import app.calio.model.TaskId
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Entities with sensible defaults, so a test only mentions the fields it actually cares about.
 *
 * Shared between the feature modules rather than copied into each of them: two sets of fixtures
 * drift apart, and then a test passing in one module says nothing about the other.
 */

val testZone: TimeZone = TimeZone.of("Europe/Berlin")

val testDevice: DeviceId = DeviceId("test-device")

val testAudit: AuditFields = AuditFields(
    createdAt = Instant.parse("2026-07-01T08:00:00Z"),
    updatedAt = Instant.parse("2026-07-01T08:00:00Z"),
    revision = Revision.of(1_751_356_800_000, 0, testDevice),
    originDevice = testDevice,
)

val testCalendar: Calendar = Calendar(
    id = CalendarId("calendar-1"),
    name = "Work",
    color = CalioColor(0xFF1B6EF3),
    audit = testAudit,
)

val testCategory: Category = Category(
    id = CategoryId("category-1"),
    name = "Study",
    color = CalioColor(0xFF9C27B0),
    audit = testAudit,
)

fun testEvent(
    id: String = "event-1",
    title: String = "Standup",
    start: LocalDateTime,
    endExclusive: LocalDateTime,
    zone: TimeZone = testZone,
    calendarId: CalendarId = testCalendar.id,
    recurrence: RecurrenceRule? = null,
): Event = Event(
    id = EventId(id),
    calendarId = calendarId,
    title = title,
    timeRange = EventTimeRange.Zoned(start, endExclusive, zone),
    audit = testAudit,
    recurrence = recurrence,
)

fun testAllDayEvent(
    id: String = "event-all-day",
    title: String = "Public holiday",
    range: EventTimeRange.AllDay,
    calendarId: CalendarId = testCalendar.id,
): Event = Event(
    id = EventId(id),
    calendarId = calendarId,
    title = title,
    timeRange = range,
    audit = testAudit,
)

fun testTask(id: String = "task-1", title: String = "Write the report"): Task = Task(
    id = TaskId(id),
    title = title,
    audit = testAudit,
)

/** A clock that never moves, so nothing in a test depends on how fast the machine runs. */
class FixedClock(private val instant: Instant) : Clock {
    override fun now(): Instant = instant
}
