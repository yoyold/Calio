package app.calio.domain.recurrence

import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.EventTimeRange
import kotlinx.datetime.LocalDateTime

/**
 * One concrete appearance of an event in the calendar.
 *
 * A non-recurring event has exactly one occurrence; a series has as many as the visible window
 * contains. Occurrences are never stored — they are derived on read, so a change to the series can
 * never leave stale copies behind.
 *
 * [originalStart] is the start the rule produced, before any exception moved it. It is the identity
 * of the occurrence within its series: it is what an override is keyed by, what a deep link carries
 * and what tells two occurrences of the same event apart.
 */
data class EventOccurrence(
    val event: Event,
    val originalStart: LocalDateTime,
    val timeRange: EventTimeRange,
    val isException: Boolean = false,
) {
    val eventId: EventId get() = event.id

    /** Whether this occurrence counts when detecting conflicts and computing free slots. */
    val occupiesTime: Boolean get() = event.occupiesTime
}
