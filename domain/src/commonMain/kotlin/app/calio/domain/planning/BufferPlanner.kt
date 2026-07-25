package app.calio.domain.planning

import app.calio.datetime.InstantRange
import app.calio.domain.recurrence.EventOccurrence
import app.calio.model.BufferPolicy
import app.calio.model.EventId
import app.calio.model.EventKind
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * A proposed breather between two appointments.
 *
 * It is a suggestion, not a change: the caller decides whether to turn it into an event of
 * [EventKind.BUFFER], which is what makes buffers visible, movable and deletable instead of an
 * invisible rule that silently blocks time.
 */
data class BufferSuggestion(
    val range: InstantRange,
    val after: EventId,
    val before: EventId,
)

/**
 * Proposes buffers in the gaps between appointments.
 *
 * A buffer is only useful where a transition is actually tight, so the gap has to be wide enough to
 * hold one and narrow enough to still be a transition. Back-to-back appointments therefore get
 * nothing: there is no room to insert anything without moving what the user already scheduled, and
 * quietly shortening a meeting is not this application's decision to make.
 */
class BufferPlanner {

    operator fun invoke(
        occurrences: List<EventOccurrence>,
        policy: BufferPolicy,
    ): List<BufferSuggestion> {
        if (!policy.isEnabled) return emptyList()

        val relevant = occurrences
            .filter { it.qualifies(policy) }
            .sortedBy { it.timeRange.startUtc }

        if (relevant.size < 2) return emptyList()

        val suggestions = mutableListOf<BufferSuggestion>()
        var previous = relevant.first()

        for (current in relevant.drop(1)) {
            // Overlapping appointments have no gap between them; the later end wins so the next gap
            // is measured from the end of the whole block.
            if (current.timeRange.startUtc < previous.timeRange.endUtcExclusive) {
                if (current.timeRange.endUtcExclusive > previous.timeRange.endUtcExclusive) previous = current
                continue
            }

            val gap = current.timeRange.startUtc - previous.timeRange.endUtcExclusive
            val gapMinutes = gap.inWholeMinutes

            if (gapMinutes >= policy.minimumGapMinutes && gapMinutes <= policy.maximumGapMinutes) {
                val length = minOf(policy.defaultMinutes.minutes, gap)
                if (length > Duration.ZERO) {
                    suggestions += BufferSuggestion(
                        range = InstantRange(
                            start = previous.timeRange.endUtcExclusive,
                            endExclusive = previous.timeRange.endUtcExclusive + length,
                        ),
                        after = previous.eventId,
                        before = current.eventId,
                    )
                }
            }

            previous = current
        }

        return suggestions
    }
}

private fun EventOccurrence.qualifies(policy: BufferPolicy): Boolean = when {
    !occupiesTime -> false
    // A buffer around a buffer is pointless, and one around an all-day entry blocks a moment that
    // was never a transition in the first place.
    event.kind == EventKind.BUFFER -> false
    timeRange.isAllDay -> policy.appliesToAllDayEvents
    else -> true
}
