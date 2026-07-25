package app.calio.datetime

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The span of time a calendar view shows, and the arithmetic for moving between spans.
 *
 * This is the logic behind the view switcher, the arrow keys and the swipe pager. It lives here as a
 * pure function of dates so that the same rules apply to a keyboard shortcut, a gesture and a deep
 * link, and so the awkward cases can be tested without a view.
 */
enum class CalendarPeriod {
    DAY,
    WEEK,
    MONTH,
    YEAR,
    ;

    /**
     * The canonical date representing the period that contains [date].
     *
     * Navigation always steps from the canonical anchor rather than from the date the user happened
     * to have selected. Stepping month by month from 31 January would otherwise land on 28 February
     * and stay stuck at the 28th for the rest of the year.
     */
    fun anchorOf(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): LocalDate = when (this) {
        DAY -> date
        WEEK -> startOfWeek(date, weekStart)
        MONTH -> firstDayOfMonth(date)
        YEAR -> firstDayOfYear(date)
    }

    /** The days this period covers, without any padding. */
    fun rangeOf(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): DateRange = when (this) {
        DAY -> DateRange.singleDay(date)
        WEEK -> weekOf(date, weekStart)
        MONTH -> monthOf(date)
        YEAR -> yearOf(date)
    }

    /**
     * The days the view actually renders, including the leading and trailing days a month grid needs
     * to show whole weeks.
     *
     * A month grid is always six weeks, even when five would fit. A grid that changes height between
     * months makes the whole screen jump when the user pages through the year, and the price is one
     * mostly empty row.
     */
    fun gridRangeOf(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): DateRange =
        when (this) {
            DAY, WEEK, YEAR -> rangeOf(date, weekStart)
            MONTH -> {
                val gridStart = startOfWeek(firstDayOfMonth(date), weekStart)
                DateRange(gridStart, gridStart.plusDays(MONTH_GRID_DAYS))
            }
        }

    fun next(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        step(date, weekStart, forward = true)

    fun previous(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
        step(date, weekStart, forward = false)

    private fun step(date: LocalDate, weekStart: DayOfWeek, forward: Boolean): LocalDate {
        val anchor = anchorOf(date, weekStart)
        val direction = if (forward) 1 else -1
        return when (this) {
            DAY -> anchor.plusDays(direction)
            WEEK -> anchor.plusDays(direction * DAYS_IN_WEEK)
            MONTH -> anchor.plus(direction, DateTimeUnit.MONTH)
            YEAR -> anchor.plus(direction, DateTimeUnit.YEAR)
        }
    }

    private companion object {
        const val DAYS_IN_WEEK = 7
        const val MONTH_GRID_DAYS = 42
    }
}
