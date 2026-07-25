package app.calio.domain.planning

import app.calio.datetime.InstantRange
import app.calio.domain.recurrence.EventOccurrence
import app.calio.model.EventKind

/** Two appointments that claim the same time, and the stretch they have in common. */
data class Conflict(
    val first: EventOccurrence,
    val second: EventOccurrence,
    val overlap: InstantRange,
)

/**
 * Finds appointments that collide.
 *
 * Two rules decide what counts, and both are deliberate. An occurrence only conflicts when it
 * actually occupies its slot, so anything marked free or cancelled is ignored — otherwise every
 * "out of office" entry would set off a warning. Buffers never conflict either: they exist to be
 * pushed aside, and warning about the very block the application suggested would be noise.
 */
class ConflictDetector {

    /** Whether [candidate] collides with anything in [others], and with what. */
    fun conflictsFor(
        candidate: EventOccurrence,
        others: List<EventOccurrence>,
    ): List<EventOccurrence> {
        if (!candidate.blocksSlot) return emptyList()

        val candidateRange = candidate.utcRange
        return others
            .filter { it.blocksSlot && !it.isSameOccurrenceAs(candidate) }
            .filter { it.utcRange.overlaps(candidateRange) }
            .sortedBy { it.timeRange.startUtc }
    }

    /**
     * Every colliding pair in [occurrences], each reported once.
     *
     * The list is walked in start order and the inner loop stops as soon as a later appointment
     * begins after the current one ends, so a full day costs little more than sorting it.
     */
    fun allConflicts(occurrences: List<EventOccurrence>): List<Conflict> {
        val blocking = occurrences.filter { it.blocksSlot }.sortedBy { it.timeRange.startUtc }
        val conflicts = mutableListOf<Conflict>()

        for (index in blocking.indices) {
            val current = blocking[index]
            val currentRange = current.utcRange

            for (other in blocking.drop(index + 1)) {
                val otherRange = other.utcRange
                if (otherRange.start >= currentRange.endExclusive) break

                currentRange.intersect(otherRange)?.let { overlap ->
                    conflicts += Conflict(current, other, overlap)
                }
            }
        }

        return conflicts
    }

    fun hasConflict(candidate: EventOccurrence, others: List<EventOccurrence>): Boolean =
        conflictsFor(candidate, others).isNotEmpty()
}

private val EventOccurrence.blocksSlot: Boolean
    get() = occupiesTime && event.kind != EventKind.BUFFER

private fun EventOccurrence.isSameOccurrenceAs(other: EventOccurrence): Boolean =
    eventId == other.eventId && originalStart == other.originalStart
