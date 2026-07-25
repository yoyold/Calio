package app.calio.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

/** The part of a day the user considers working time. */
data class DayWindow(
    val start: LocalTime,
    val endExclusive: LocalTime,
) {
    init {
        require(endExclusive > start) { "a working day must end after it starts" }
    }
}

/**
 * Working hours per weekday. A weekday that is absent from [days] is a non-working day.
 *
 * These are settings rather than events: they shade the background of the day and week grids and
 * they bound the free-slot calculation, but they must not appear as entries in the calendar.
 */
data class WorkingHours(
    val days: Map<DayOfWeek, DayWindow>,
) {
    fun windowFor(dayOfWeek: DayOfWeek): DayWindow? = days[dayOfWeek]

    fun isWorkingDay(dayOfWeek: DayOfWeek): Boolean = dayOfWeek in days

    companion object {
        /** Monday to Friday, 09:00 until 17:00. */
        val Default: WorkingHours = WorkingHours(
            listOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ).associateWith { DayWindow(LocalTime(9, 0), LocalTime(17, 0)) },
        )
    }
}

/**
 * How much breathing room is kept around meetings.
 *
 * Buffers are materialised as events of [EventKind.BUFFER] so the user can see, move and delete
 * them, instead of them being an invisible rule that silently blocks time.
 */
data class BufferPolicy(
    val isEnabled: Boolean = false,
    val defaultMinutes: Int = 10,
    val minimumGapMinutes: Int = 5,
    /**
     * The widest gap still worth protecting. A buffer exists to soften the transition between two
     * appointments; when the next one is hours away the transition takes care of itself, and
     * blocking time anyway would only clutter the day.
     */
    val maximumGapMinutes: Int = 120,
    val appliesToAllDayEvents: Boolean = false,
) {
    init {
        require(defaultMinutes > 0) { "a buffer must be longer than zero minutes" }
        require(minimumGapMinutes >= 0) { "minimum gap must not be negative" }
        require(maximumGapMinutes >= minimumGapMinutes) {
            "the maximum gap must not be smaller than the minimum gap"
        }
    }
}
