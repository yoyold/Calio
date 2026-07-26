package app.calio.feature.calendar

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.calio.datetime.CalendarPeriod
import app.calio.datetime.isoWeekNumber
import app.calio.designsystem.icon.CalioIcons
import app.calio.domain.recurrence.EventOccurrence
import app.calio.model.EventId
import app.calio.ui.ScreenHeader
import app.calio.ui.headingLabel
import app.calio.ui.longLabel
import app.calio.ui.monthLabel
import app.calio.ui.weekdayName
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The calendar, connected to its view model.
 *
 * The stateless variant below it is the one that does the drawing, so the whole screen can be
 * rendered from a fixture — in a preview, in a screenshot test — without a database behind it.
 */
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onCreateEvent: (LocalDate) -> Unit,
    onOpenEvent: (EventId, LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
    clock: Clock = Clock.System,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // The current-time line is the one thing on this screen that changes without anything happening,
    // so it is ticked here rather than pushed through the state on a timer.
    var now by remember { mutableStateOf(clock.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(NOW_TICK_MILLIS)
            now = clock.now()
        }
    }

    CalendarScreen(
        state = state,
        onEvent = viewModel::onEvent,
        now = now,
        onCreateEvent = onCreateEvent,
        onOpenEvent = onOpenEvent,
        modifier = modifier,
    )
}

@Composable
fun CalendarScreen(
    state: CalendarUiState,
    onEvent: (CalendarUiEvent) -> Unit,
    now: Instant,
    onCreateEvent: (LocalDate) -> Unit,
    onOpenEvent: (EventId, LocalDateTime) -> Unit,
    modifier: Modifier = Modifier,
) {
    var isLegendOpen by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = state.headingTitle(),
            subtitle = state.headingSubtitle(),
            actions = {
                PeriodSwitcher(state.period) { onEvent(CalendarUiEvent.SelectPeriod(it)) }
                TextButton(onClick = { onEvent(CalendarUiEvent.GoToToday) }) { Text("Today") }
                IconButton(onClick = { onEvent(CalendarUiEvent.GoToPrevious) }) {
                    Icon(CalioIcons.ChevronLeft, contentDescription = "Previous", Modifier.size(20.dp))
                }
                IconButton(onClick = { onEvent(CalendarUiEvent.GoToNext) }) {
                    Icon(CalioIcons.ChevronRight, contentDescription = "Next", Modifier.size(20.dp))
                }
                IconButton(onClick = { isLegendOpen = !isLegendOpen }) {
                    Icon(CalioIcons.Layers, contentDescription = "Calendars", Modifier.size(20.dp))
                }
                FilledIconButton(onClick = { onCreateEvent(state.anchor) }) {
                    Icon(CalioIcons.Plus, contentDescription = "New event", Modifier.size(20.dp))
                }
            },
        )

        val openEntry = { occurrence: EventOccurrence ->
            onOpenEvent(occurrence.eventId, occurrence.originalStart)
        }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Wide enough and the legend stays open beside the grid, the way a desktop calendar
            // shows it. Narrower, it would take a third of the width from the thing it explains, so
            // it moves behind the button in the header instead.
            val showsLegendBeside = maxWidth >= LEGEND_BREAKPOINT

            Row(Modifier.fillMaxSize()) {
                if (showsLegendBeside) {
                    LegendPanel(
                        state = state,
                        onEvent = onEvent,
                        modifier = Modifier.width(LEGEND_WIDTH).fillMaxHeight(),
                    )
                    VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                when (state.period) {
                    CalendarPeriod.DAY, CalendarPeriod.WEEK -> TimeGrid(
                        state = state,
                        now = now,
                        onSelectEntry = openEntry,
                        onSelectSlot = onCreateEvent,
                        modifier = Modifier.fillMaxSize(),
                    )

                    CalendarPeriod.MONTH -> MonthGrid(
                        state = state,
                        onSelectDay = { date -> onEvent(CalendarUiEvent.OpenDay(date)) },
                        onSelectEntry = openEntry,
                        modifier = Modifier.fillMaxSize(),
                    )

                    CalendarPeriod.YEAR -> YearGrid(
                        state = state,
                        onSelectDay = { date -> onEvent(CalendarUiEvent.OpenDay(date)) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            if (isLegendOpen && !showsLegendBeside) {
                AlertDialog(
                    onDismissRequest = { isLegendOpen = false },
                    confirmButton = {
                        TextButton(onClick = { isLegendOpen = false }) { Text("Done") }
                    },
                    title = { Text("Calendars and colours") },
                    text = { LegendPanel(state = state, onEvent = onEvent) },
                )
            }
        }
    }
}

private fun CalendarUiState.headingTitle(): String = when (period) {
    CalendarPeriod.DAY -> anchor.longLabel()
    CalendarPeriod.WEEK -> period.rangeOf(anchor, weekStart).headingLabel()
    CalendarPeriod.MONTH -> anchor.monthLabel()
    CalendarPeriod.YEAR -> anchor.year.toString()
}

private fun CalendarUiState.headingSubtitle(): String? = when (period) {
    CalendarPeriod.DAY -> anchor.weekdayName()
    CalendarPeriod.WEEK -> "Week ${isoWeekNumber(anchor)}"
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSwitcher(
    selected: CalendarPeriod,
    onSelect: (CalendarPeriod) -> Unit,
) {
    val options = listOf(
        CalendarPeriod.DAY to "Day",
        CalendarPeriod.WEEK to "Week",
        CalendarPeriod.MONTH to "Month",
        CalendarPeriod.YEAR to "Year",
    )

    SingleChoiceSegmentedButtonRow(Modifier.padding(end = 4.dp)) {
        options.forEachIndexed { index, (period, label) ->
            SegmentedButton(
                selected = period == selected,
                onClick = { onSelect(period) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(label, style = MaterialTheme.typography.labelLarge) },
            )
        }
    }
}

/** Half a minute is often enough for a line that only has to look current, and cheap to redraw. */
private const val NOW_TICK_MILLIS = 30_000L

/** Below this the legend would take more width from the grid than it gives back in clarity. */
private val LEGEND_BREAKPOINT = 1_040.dp

private val LEGEND_WIDTH = 220.dp
