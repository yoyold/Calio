package app.calio.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.calio.datetime.CalendarPeriod
import app.calio.datetime.InstantRange
import app.calio.datetime.dayWindowIn
import app.calio.datetime.today
import app.calio.domain.planning.OverlapLayoutCalculator
import app.calio.domain.recurrence.EventOccurrence
import app.calio.domain.recurrence.RecurrenceExpander
import app.calio.domain.repository.CalendarRepository
import app.calio.domain.repository.CategoryRepository
import app.calio.domain.repository.EventRepository
import app.calio.domain.repository.SettingsRepository
import app.calio.model.AppSettings
import app.calio.model.CalioColor
import app.calio.model.Event
import app.calio.model.EventTimeRange
import app.calio.model.resolveEventColor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * Turns stored events into the rows and columns the calendar draws.
 *
 * The screen never sees a repository. It receives one immutable state and sends back events, which
 * keeps every decision — which days are visible, which occurrences fall on them, how overlapping
 * ones share the width — in one testable place.
 *
 * Only the visible range is ever expanded. Paging to another week re-runs the query rather than
 * holding a year of occurrences in memory.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    private val events: EventRepository,
    private val calendars: CalendarRepository,
    private val categories: CategoryRepository,
    private val expander: RecurrenceExpander,
    private val layout: OverlapLayoutCalculator,
    private val settings: SettingsRepository,
    private val zone: TimeZone,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val selection = MutableStateFlow(
        Selection(period = CalendarPeriod.WEEK, anchor = clock.today(zone)),
    )

    /** The week start and the working hours come from the settings, so changing them redraws. */
    val state: StateFlow<CalendarUiState> = combine(selection, settings.observe()) { current, config ->
        current to config
    }.flatMapLatest { (current, config) ->
        observeDays(current, config.weekStart).map { days -> current.toState(days, config) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
            initialValue = selection.value.toState(emptyList(), AppSettings()),
        )

    private val weekStart: DayOfWeek get() = state.value.weekStart

    fun onEvent(event: CalendarUiEvent) = when (event) {
        is CalendarUiEvent.SelectPeriod -> selection.update { it.copy(period = event.period) }
        is CalendarUiEvent.SelectDate -> selection.update { it.copy(anchor = event.date) }
        CalendarUiEvent.GoToPrevious -> selection.update {
            it.copy(anchor = it.period.previous(it.anchor, weekStart))
        }

        CalendarUiEvent.GoToNext -> selection.update {
            it.copy(anchor = it.period.next(it.anchor, weekStart))
        }

        CalendarUiEvent.GoToToday -> selection.update { it.copy(anchor = clock.today(zone)) }

        is CalendarUiEvent.OpenDay -> selection.update {
            it.copy(anchor = event.date, period = CalendarPeriod.DAY)
        }
    }

    private fun observeDays(selection: Selection, weekStart: DayOfWeek): Flow<List<CalendarDay>> {
        // The grid range rather than the plain period: a month view draws whole weeks, so it needs
        // the leading and trailing days of the neighbouring months as well.
        val dates = selection.period.gridRangeOf(selection.anchor, weekStart)
        val window = dates.toInstantRange(zone)

        return combine(
            events.observeInRange(window),
            events.observeAllOverrides(),
            calendars.observeAll(),
            categories.observeAll(),
        ) { visibleEvents, overrides, allCalendars, allCategories ->
            val calendarsById = allCalendars.associateBy { it.id }
            val categoriesById = allCategories.associateBy { it.id }

            val occurrences = visibleEvents.flatMap { event ->
                expander.expand(event, window, overrides[event.id].orEmpty())
            }

            val colourOf = { event: Event ->
                val calendar = calendarsById[event.calendarId]
                if (calendar == null) {
                    CalioColor(FALLBACK_COLOR)
                } else {
                    resolveEventColor(
                        colorOverride = event.colorOverride,
                        category = event.categoryId?.let(categoriesById::get),
                        calendar = calendar,
                    )
                }
            }

            dates.map { date -> buildDay(date, occurrences, colourOf) }
        }
    }

    private fun buildDay(
        date: LocalDate,
        occurrences: List<EventOccurrence>,
        colourOf: (Event) -> CalioColor,
    ): CalendarDay {
        val onThisDay = occurrences.filter { it.appearsOn(date, zone) }
        val (allDay, timed) = onThisDay.partition { it.timeRange.isAllDay }

        return CalendarDay(
            date = date,
            timed = layout(timed).map { positioned ->
                TimedEntry(
                    occurrence = positioned.occurrence,
                    column = positioned.column,
                    columnCount = positioned.columnCount,
                    color = colourOf(positioned.occurrence.event),
                )
            },
            allDay = allDay.map { AllDayEntry(it, colourOf(it.event)) },
            isToday = date == clock.today(zone),
        )
    }

    private fun Selection.toState(days: List<CalendarDay>, config: AppSettings) = CalendarUiState(
        period = period,
        anchor = anchor,
        today = clock.today(zone),
        zone = zone,
        weekStart = config.weekStart,
        days = days,
        workingHours = config.workingHours,
    )

    private data class Selection(val period: CalendarPeriod, val anchor: LocalDate)

    private companion object {
        /** Long enough to survive a rotation, short enough not to keep querying an unseen screen. */
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        const val FALLBACK_COLOR = 0xFF9E9E9E
    }
}

/**
 * Whether this occurrence belongs on [date].
 *
 * The two kinds of appointment are matched differently on purpose. A timed one is compared as
 * instants, because that is what it is. An all-day one is compared as dates: it is anchored to UTC
 * midnight purely so it can share an index, and putting that anchor back through a zone would make
 * a single day east of Greenwich appear on two days and west of it on the day before.
 */
private fun EventOccurrence.appearsOn(date: LocalDate, zone: TimeZone): Boolean =
    when (val range = timeRange) {
        is EventTimeRange.AllDay -> date >= range.startDate && date < range.endDateExclusive
        is EventTimeRange.Zoned -> {
            val window = date.dayWindowIn(zone)
            range.startUtc < window.endExclusive && window.start < range.endUtcExclusive
        }
    }
