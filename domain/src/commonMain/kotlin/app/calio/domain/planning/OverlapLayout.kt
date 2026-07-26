package app.calio.domain.planning

import app.calio.domain.recurrence.EventOccurrence

/**
 * An occurrence together with the slice of the day column it should occupy.
 *
 * [columnCount] is shared by every occurrence of the same cluster, not counted per occurrence, so
 * appointments that collide all end up the same width. Sizing each one by how many things it
 * personally overlaps produces columns of different widths inside one cluster, which reads as a
 * rendering fault rather than as information.
 */
data class PositionedOccurrence(
    val occurrence: EventOccurrence,
    val column: Int,
    val columnCount: Int,
)

/**
 * Decides how overlapping appointments share the width of a day.
 *
 * Appointments are grouped into clusters of transitively overlapping entries — A overlaps B and B
 * overlaps C puts all three in one cluster even when A and C do not touch — and inside a cluster
 * each one takes the leftmost column that is still free at its start.
 *
 * Half-open ranges mean an appointment ending at ten and one starting at ten belong to different
 * clusters and each get the full width, which is what the user expects from back-to-back entries.
 */
class OverlapLayoutCalculator {

    operator fun invoke(occurrences: List<EventOccurrence>): List<PositionedOccurrence> {
        if (occurrences.isEmpty()) return emptyList()

        val sorted = occurrences.sortedWith(
            compareBy({ it.timeRange.startUtc }, { it.timeRange.endUtcExclusive }),
        )

        val positioned = mutableListOf<PositionedOccurrence>()
        var cluster = mutableListOf<EventOccurrence>()
        var clusterEnd = sorted.first().timeRange.startUtc

        for (occurrence in sorted) {
            // A start at or after the end of everything seen so far begins a new cluster: nothing
            // before it can still be running.
            if (cluster.isNotEmpty() && occurrence.timeRange.startUtc >= clusterEnd) {
                positioned += layOutCluster(cluster)
                cluster = mutableListOf()
            }

            cluster += occurrence
            if (occurrence.timeRange.endUtcExclusive > clusterEnd) {
                clusterEnd = occurrence.timeRange.endUtcExclusive
            }
        }

        positioned += layOutCluster(cluster)
        return positioned
    }

    private fun layOutCluster(cluster: List<EventOccurrence>): List<PositionedOccurrence> {
        if (cluster.isEmpty()) return emptyList()

        // The instant each column is free again. A column is reusable as soon as the appointment in
        // it has ended, which is what lets a long morning entry sit beside two short afternoon ones.
        val columnEnds = mutableListOf<kotlin.time.Instant>()
        val assignments = cluster.map { occurrence ->
            val free = columnEnds.indexOfFirst { it <= occurrence.timeRange.startUtc }
            val column = if (free >= 0) {
                columnEnds[free] = occurrence.timeRange.endUtcExclusive
                free
            } else {
                columnEnds += occurrence.timeRange.endUtcExclusive
                columnEnds.lastIndex
            }
            occurrence to column
        }

        return assignments.map { (occurrence, column) ->
            PositionedOccurrence(occurrence, column, columnEnds.size)
        }
    }
}
