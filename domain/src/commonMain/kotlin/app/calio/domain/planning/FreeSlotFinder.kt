package app.calio.domain.planning

import app.calio.datetime.DateRange
import app.calio.datetime.InstantRange
import app.calio.domain.recurrence.EventOccurrence
import app.calio.model.WorkingHours
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A stretch of a working day that nothing has claimed yet. */
data class FreeSlot(
    val date: LocalDate,
    val range: InstantRange,
) {
    val duration: Duration get() = range.duration
}

/**
 * Reports the gaps left in the working hours of each day.
 *
 * The calculation is per day and inside the user's own working hours, because a gap at three in the
 * morning is not free time. Non-working days produce nothing at all rather than a day-long slot.
 *
 * Days are converted through the time zone rather than assumed to be 24 hours long. On the two days
 * a year that daylight saving moves, a working day is genuinely an hour shorter or longer, and an
 * assumption of 24 would place every slot after the change an hour off.
 */
class FreeSlotFinder {

    operator fun invoke(
        dates: DateRange,
        workingHours: WorkingHours,
        occurrences: List<EventOccurrence>,
        zone: TimeZone,
        minimumDuration: Duration = DEFAULT_MINIMUM,
    ): List<FreeSlot> {
        // Everything that occupies its slot counts, all-day entries included. Whether an all-day
        // entry blocks the day is decided by its busy status, which is the user's choice, not by a
        // rule hidden in here.
        val blocked = occurrences.filter { it.occupiesTime }.map { it.utcRange }.mergeAdjacent()

        return dates.flatMap { date ->
            val window = workingHours.windowFor(date.dayOfWeek) ?: return@flatMap emptyList()

            val workingDay = InstantRange(
                start = LocalDateTime(date, window.start).toInstant(zone),
                endExclusive = LocalDateTime(date, window.endExclusive).toInstant(zone),
            )

            workingDay.minus(blocked)
                .filter { it.duration >= minimumDuration }
                .map { FreeSlot(date, it) }
        }
    }

    private companion object {
        /** Shorter gaps exist on any busy day and are not usable time. */
        val DEFAULT_MINIMUM = 15.minutes
    }
}
