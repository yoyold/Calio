package app.calio.feature.calendar

import app.calio.datetime.CalendarPeriod
import app.calio.domain.recurrence.EventOccurrence
import app.calio.model.CalioColor
import app.calio.model.WorkingHours
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

/**
 * One appointment as the grid needs it: where it is in time, which slice of the column it takes and
 * what colour it is drawn in.
 *
 * The colour is resolved once, in the view model, rather than in the composable. A colour depends on
 * the event, its category and its calendar, and looking that up while drawing would mean three map
 * lookups per appointment on every frame of a scroll.
 */
data class TimedEntry(
    val occurrence: EventOccurrence,
    val column: Int,
    val columnCount: Int,
    val color: CalioColor,
)

data class AllDayEntry(
    val occurrence: EventOccurrence,
    val color: CalioColor,
)

data class CalendarDay(
    val date: LocalDate,
    val timed: List<TimedEntry> = emptyList(),
    val allDay: List<AllDayEntry> = emptyList(),
    val isToday: Boolean = false,
)

/**
 * Everything the calendar screen draws.
 *
 * One immutable object per state of the screen, so a composable is a pure function of it and can be
 * rendered from a fixture without a database behind it.
 */
data class CalendarUiState(
    val period: CalendarPeriod,
    val anchor: LocalDate,
    val today: LocalDate,
    val zone: TimeZone,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val days: List<CalendarDay> = emptyList(),
    val workingHours: WorkingHours = WorkingHours.Default,
) {
    val hasAllDayEntries: Boolean get() = days.any { it.allDay.isNotEmpty() }
}

sealed interface CalendarUiEvent {
    data class SelectPeriod(val period: CalendarPeriod) : CalendarUiEvent
    data class SelectDate(val date: LocalDate) : CalendarUiEvent
    data object GoToPrevious : CalendarUiEvent
    data object GoToNext : CalendarUiEvent
    data object GoToToday : CalendarUiEvent
}
