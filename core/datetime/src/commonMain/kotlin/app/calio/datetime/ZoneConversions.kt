package app.calio.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * The instants covered by this calendar day in the given zone.
 *
 * Deliberately not "start of day plus 24 hours": on the day a daylight-saving change happens that
 * would land an hour early or an hour late, which silently drops or duplicates events at the edge.
 */
fun LocalDate.dayWindowIn(zone: TimeZone): InstantRange = InstantRange(
    start = atStartOfDayIn(zone),
    endExclusive = plusDays(1).atStartOfDayIn(zone),
)

/**
 * How long this calendar day actually lasts in the given zone — 23, 24 or 25 hours.
 *
 * The day and week grids need this to size their hour rows; assuming 24 would misplace every event
 * after the transition on two days a year.
 */
fun LocalDate.dayLengthIn(zone: TimeZone): Duration = dayWindowIn(zone).duration

fun Instant.toLocalDate(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

/** The current date in the given zone. Injecting the clock keeps this testable and deterministic. */
fun Clock.today(zone: TimeZone): LocalDate = now().toLocalDate(zone)

/** Whether [instant] falls on this calendar day in the given zone. */
fun LocalDate.contains(instant: Instant, zone: TimeZone): Boolean = instant in dayWindowIn(zone)
