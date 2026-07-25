package app.calio.domain.planning

import app.calio.datetime.InstantRange
import app.calio.domain.recurrence.EventOccurrence

/**
 * Interval arithmetic shared by the planning use cases.
 *
 * All ranges are half open, so two blocks that merely touch leave no gap between them and produce no
 * overlap. That single convention is what keeps "back to back" from being a special case anywhere
 * else in this package.
 */

/** The occupied ranges, overlapping and touching ones collapsed into single blocks. */
internal fun List<InstantRange>.mergeAdjacent(): List<InstantRange> {
    if (size < 2) return filterNot { it.isEmpty }

    val sorted = filterNot { it.isEmpty }.sortedBy { it.start }
    val merged = mutableListOf<InstantRange>()

    for (range in sorted) {
        val last = merged.lastOrNull()
        if (last != null && range.start <= last.endExclusive) {
            if (range.endExclusive > last.endExclusive) {
                merged[merged.lastIndex] = InstantRange(last.start, range.endExclusive)
            }
        } else {
            merged += range
        }
    }

    return merged
}

/** What remains of this range once every blocking range has been taken out of it. */
internal fun InstantRange.minus(blockers: List<InstantRange>): List<InstantRange> {
    if (isEmpty) return emptyList()

    val remaining = mutableListOf<InstantRange>()
    var cursor = start

    for (blocker in blockers.mergeAdjacent()) {
        if (blocker.endExclusive <= cursor) continue
        if (blocker.start >= endExclusive) break

        if (blocker.start > cursor) remaining += InstantRange(cursor, blocker.start)
        cursor = maxOf(cursor, blocker.endExclusive)
        if (cursor >= endExclusive) break
    }

    if (cursor < endExclusive) remaining += InstantRange(cursor, endExclusive)

    return remaining
}

internal val EventOccurrence.utcRange: InstantRange
    get() = InstantRange(timeRange.startUtc, timeRange.endUtcExclusive)
