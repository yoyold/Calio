package app.calio.domain.recurrence

import app.calio.model.Event
import app.calio.model.RecurrenceEnd
import kotlin.time.Instant

/**
 * The instant after which a series can produce nothing more, or null when it never ends.
 *
 * This is stored alongside the event so the range query can skip finished series without expanding
 * them. It is derived rather than entered: a rule limited by a count only reveals its end by being
 * walked, which is cheap here and would be expensive in every subsequent read.
 */
fun seriesEndUtc(event: Event, maxCountedOccurrences: Int = MAX_COUNTED_OCCURRENCES): Instant? {
    val rule = event.recurrence ?: return event.timeRange.endUtcExclusive
    val seed = event.timeRange.localStart.date

    return when (val end = rule.end) {
        is RecurrenceEnd.Never -> null

        is RecurrenceEnd.OnDate -> recurrenceDates(rule, seed)
            .takeWhile { it <= end.date }
            .lastOrNull()
            ?.let { event.timeRange.shiftedTo(it).endUtcExclusive }
            ?: event.timeRange.endUtcExclusive

        // A very long count is treated as open ended: walking it would cost more than the query it
        // is meant to speed up, and an over-wide bound is only a missed optimisation, never a wrong
        // result.
        is RecurrenceEnd.AfterCount -> if (end.count > maxCountedOccurrences) {
            null
        } else {
            recurrenceDates(rule, seed)
                .take(end.count)
                .lastOrNull()
                ?.let { event.timeRange.shiftedTo(it).endUtcExclusive }
        }
    }
}

private const val MAX_COUNTED_OCCURRENCES = 5_000
