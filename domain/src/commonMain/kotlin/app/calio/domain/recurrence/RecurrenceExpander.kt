package app.calio.domain.recurrence

import app.calio.datetime.InstantRange
import app.calio.datetime.plusDays
import app.calio.model.Event
import app.calio.model.EventTimeRange
import app.calio.model.OverrideType
import app.calio.model.RecurrenceEnd
import app.calio.model.RecurrenceOverride
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration

/**
 * Turns an event and its exceptions into the occurrences that fall inside a window.
 *
 * This is the single place where a stored event becomes something the calendar can draw, and it is
 * deliberately a pure function: no repository, no clock, no zone from the environment. Everything it
 * needs is passed in, which is what makes the awkward cases — leap days, short months, daylight
 * saving, exceptions that move an occurrence out of view — testable as plain data.
 */
class RecurrenceExpander(
    private val maxOccurrencesPerEvent: Int = DEFAULT_MAX_OCCURRENCES,
) {

    fun expand(
        event: Event,
        window: InstantRange,
        overrides: List<RecurrenceOverride> = emptyList(),
    ): List<EventOccurrence> {
        val rule = event.recurrence
            ?: return listOfNotNull(singleOccurrence(event, window))

        val overridesByStart = overrides.associateBy { it.originalStart }
        val seedStart = event.timeRange.localStart
        val end = rule.end
        val occurrences = mutableListOf<EventOccurrence>()

        var produced = 0
        for (date in recurrenceDates(rule, seedStart.date)) {
            if (produced >= maxOccurrencesPerEvent) break
            if (end is RecurrenceEnd.AfterCount && produced >= end.count) break
            if (end is RecurrenceEnd.OnDate && date > end.date) break

            val range = event.timeRange.shiftedTo(date)
            // Occurrences are generated in order, so once one starts after the window there can be
            // no further hit and the potentially infinite sequence can be abandoned.
            if (range.startUtc >= window.endExclusive) break

            // A cancelled occurrence still counts towards a COUNT limit: the rule produced it, the
            // exception only removed it from view.
            produced++

            val originalStart = LocalDateTime(date, seedStart.time)
            val override = overridesByStart[originalStart]
            if (override?.type == OverrideType.CANCELLED) continue

            val patch = override?.patch
            val occurrence = if (patch != null) {
                EventOccurrence(
                    event = event.applyPatch(patch),
                    originalStart = originalStart,
                    timeRange = patch.timeRange ?: range,
                    isException = true,
                )
            } else {
                EventOccurrence(event, originalStart, range)
            }

            if (occurrence.timeRange.overlaps(window)) occurrences += occurrence
        }

        return occurrences + movedIntoWindow(event, window, overrides, occurrences)
    }

    /**
     * Exceptions that were moved into the window from outside it.
     *
     * Without this pass an occurrence dragged from last week into today would vanish: the rule that
     * generated it points at a date the window no longer covers.
     */
    private fun movedIntoWindow(
        event: Event,
        window: InstantRange,
        overrides: List<RecurrenceOverride>,
        alreadyFound: List<EventOccurrence>,
    ): List<EventOccurrence> {
        val foundStarts = alreadyFound.mapTo(mutableSetOf()) { it.originalStart }

        return overrides.mapNotNull { override ->
            val patch = override.patch ?: return@mapNotNull null
            val movedRange = patch.timeRange ?: return@mapNotNull null
            if (override.originalStart in foundStarts) return@mapNotNull null
            if (!movedRange.overlaps(window)) return@mapNotNull null

            EventOccurrence(
                event = event.applyPatch(patch),
                originalStart = override.originalStart,
                timeRange = movedRange,
                isException = true,
            )
        }
    }

    private fun singleOccurrence(event: Event, window: InstantRange): EventOccurrence? =
        if (event.timeRange.overlaps(window)) {
            EventOccurrence(event, event.timeRange.localStart, event.timeRange)
        } else {
            null
        }

    private companion object {
        /** Guard against a rule that would otherwise fill a view with thousands of entries. */
        const val DEFAULT_MAX_OCCURRENCES = 2_000
    }
}

/** The wall-clock start, which for an all-day event is midnight on its first day. */
internal val EventTimeRange.localStart: LocalDateTime
    get() = when (this) {
        is EventTimeRange.Zoned -> start
        is EventTimeRange.AllDay -> LocalDateTime(startDate, kotlinx.datetime.LocalTime(0, 0))
    }

/**
 * The same event on another day, keeping its wall-clock time and its wall-clock length.
 *
 * A 09:00 meeting stays a 09:00 meeting after a daylight-saving change even though the number of
 * elapsed hours since the previous occurrence is not 24. That is why the shift is applied to the
 * local values and the instants are derived afterwards, never the other way round.
 */
internal fun EventTimeRange.shiftedTo(date: LocalDate): EventTimeRange = when (this) {
    is EventTimeRange.Zoned -> {
        val newStart = LocalDateTime(date, start.time)
        EventTimeRange.Zoned(
            start = newStart,
            endExclusive = newStart.plusCivil(civilDuration()),
            timeZone = timeZone,
        )
    }

    is EventTimeRange.AllDay -> EventTimeRange.AllDay(
        startDate = date,
        endDateExclusive = date.plusDays(startDate.daysUntil(endDateExclusive)),
    )
}

/**
 * The wall-clock length of a zoned range.
 *
 * Both ends are read as if they were in UTC. That is not a claim about the event's zone — it is the
 * standard way to measure civil time, where an hour is always an hour and daylight saving does not
 * exist.
 */
private fun EventTimeRange.Zoned.civilDuration(): Duration =
    endExclusive.toInstant(TimeZone.UTC) - start.toInstant(TimeZone.UTC)

private fun LocalDateTime.plusCivil(duration: Duration): LocalDateTime =
    (toInstant(TimeZone.UTC) + duration).toLocalDateTime(TimeZone.UTC)

private fun EventTimeRange.overlaps(window: InstantRange): Boolean =
    startUtc < window.endExclusive && window.start < endUtcExclusive
