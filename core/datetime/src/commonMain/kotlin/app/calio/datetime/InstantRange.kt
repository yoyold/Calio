package app.calio.datetime

import kotlin.time.Duration
import kotlin.time.Instant

/**
 * A half-open range of instants, used as the window a query or an expansion is limited to.
 *
 * This is the type the data layer compares event bounds against, which is why it is expressed in
 * instants rather than in local time: overlap then reduces to two integer comparisons.
 */
data class InstantRange(
    val start: Instant,
    val endExclusive: Instant,
) {

    init {
        require(endExclusive >= start) { "an instant range must not end before it starts" }
    }

    val duration: Duration get() = endExclusive - start

    val isEmpty: Boolean get() = start == endExclusive

    operator fun contains(instant: Instant): Boolean = instant >= start && instant < endExclusive

    /** Half-open overlap: ranges that merely touch at a boundary do not overlap. */
    fun overlaps(other: InstantRange): Boolean =
        start < other.endExclusive && other.start < endExclusive

    /** The shared part of two ranges, or null when they do not overlap. */
    fun intersect(other: InstantRange): InstantRange? {
        if (!overlaps(other)) return null
        return InstantRange(
            start = maxOf(start, other.start),
            endExclusive = minOf(endExclusive, other.endExclusive),
        )
    }

    /** Grows the range by the given amount on both sides, used to pre-load neighbouring periods. */
    fun expandedBy(amount: Duration): InstantRange =
        InstantRange(start - amount, endExclusive + amount)
}
