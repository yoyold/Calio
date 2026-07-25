package app.calio.domain.planning

import app.calio.datetime.InstantRange
import app.calio.domain.recurrence.EventOccurrence
import app.calio.domain.recurrence.event
import app.calio.model.BusyStatus
import app.calio.model.EventKind
import app.calio.model.EventStatus
import app.calio.model.EventTimeRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

internal val utc = TimeZone.UTC
internal val berlinZone = TimeZone.of("Europe/Berlin")

internal val august3 = LocalDate(2026, 8, 3)

internal fun at(hour: Int, minute: Int = 0, date: LocalDate = august3): LocalDateTime =
    LocalDateTime(date, kotlinx.datetime.LocalTime(hour, minute))

/** A concrete appointment, built without going through storage or the expander. */
internal fun occurrence(
    id: String = "event-1",
    start: LocalDateTime,
    endExclusive: LocalDateTime,
    zone: TimeZone = utc,
    kind: EventKind = EventKind.STANDARD,
    busyStatus: BusyStatus = BusyStatus.BUSY,
    status: EventStatus = EventStatus.CONFIRMED,
): EventOccurrence {
    val source = event(id = id, start = start, endExclusive = endExclusive, timeZone = zone)
        .copy(kind = kind, busyStatus = busyStatus, status = status)

    return EventOccurrence(source, start, source.timeRange)
}

internal fun allDayOccurrence(
    id: String = "event-all-day",
    date: LocalDate = august3,
    busyStatus: BusyStatus = BusyStatus.BUSY,
): EventOccurrence {
    val range = EventTimeRange.AllDay.singleDay(date)
    val source = event(id = id, start = at(0), endExclusive = at(1)).copy(
        timeRange = range,
        busyStatus = busyStatus,
    )

    return EventOccurrence(source, at(0, date = date), range)
}

internal fun range(fromHour: Int, toHour: Int, date: LocalDate = august3): InstantRange = InstantRange(
    start = Instant.parse(isoAt(date, fromHour)),
    endExclusive = Instant.parse(isoAt(date, toHour)),
)

private fun isoAt(date: LocalDate, hour: Int): String =
    "${date}T${hour.toString().padStart(2, '0')}:00:00Z"
