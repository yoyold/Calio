package app.calio.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.calio.datetime.plusDays
import app.calio.datetime.startOfWeek
import app.calio.designsystem.CalioTheme
import app.calio.ui.monthName
import app.calio.ui.weekdayInitials
import kotlinx.datetime.LocalDate

/**
 * The year view: twelve miniature months, shaded by how busy each day is.
 *
 * At this size no title can be read, so the view answers a different question than the others: not
 * what is happening, but when the year is full. Tapping a day zooms straight to it.
 */
@Composable
fun YearGrid(
    state: CalendarUiState,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val load = state.load
    val busiest = load.values.maxOrNull()?.coerceAtLeast(1) ?: 1

    BoxWithConstraints(modifier.fillMaxSize()) {
        val columns = (maxWidth / MINIMUM_MONTH_WIDTH).toInt().coerceIn(1, MAX_COLUMNS)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CalioTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.large),
        ) {
            (1..MONTHS_PER_YEAR).map { LocalDate(state.anchor.year, it, 1) }
                .chunked(columns)
                .forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.large)) {
                        row.forEach { firstOfMonth ->
                            MiniMonth(
                                firstOfMonth = firstOfMonth,
                                state = state,
                                load = load,
                                busiest = busiest,
                                onSelectDay = onSelectDay,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keeps the last row's months the same width as the ones above them.
                        repeat(columns - row.size) { Box(Modifier.weight(1f)) }
                    }
                }
        }
    }
}

@Composable
private fun MiniMonth(
    firstOfMonth: LocalDate,
    state: CalendarUiState,
    load: Map<LocalDate, Int>,
    busiest: Int,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridStart = startOfWeek(firstOfMonth, state.weekStart)
    val weeks = (0 until WEEKS_PER_MONTH_GRID).map { week ->
        (0 until DAYS_PER_WEEK).map { day -> gridStart.plusDays(week * DAYS_PER_WEEK + day) }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = firstOfMonth.monthName(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Row {
            weekdayInitials(state.weekStart).forEach { initial ->
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        weeks.forEach { week ->
            Row {
                week.forEach { date ->
                    MiniDay(
                        date = date,
                        isInMonth = date.month == firstOfMonth.month,
                        isToday = date == state.today,
                        load = load[date] ?: 0,
                        busiest = busiest,
                        onClick = { onSelectDay(date) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniDay(
    date: LocalDate,
    isInMonth: Boolean,
    isToday: Boolean,
    load: Int,
    busiest: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The shade is relative to the busiest day of the year, so a quiet year is not drawn as an empty
    // one and a crowded one does not saturate into a single block of colour.
    val intensity = if (load == 0) 0f else MINIMUM_SHADE + (1f - MINIMUM_SHADE) * (load.toFloat() / busiest)

    val background = when {
        isToday -> MaterialTheme.colorScheme.primary
        load > 0 -> MaterialTheme.colorScheme.primary.copy(alpha = intensity * MAXIMUM_SHADE)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp)
            .background(background, CircleShape)
            .clickable(enabled = isInMonth, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.day.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                !isInMonth -> Color.Transparent
                isToday -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

/** Below this a month is unreadable, so the layout drops to fewer columns instead. */
private val MINIMUM_MONTH_WIDTH = 190.dp

private const val MAX_COLUMNS = 4
private const val MONTHS_PER_YEAR = 12
private const val DAYS_PER_WEEK = 7
private const val WEEKS_PER_MONTH_GRID = 6

/** A day with a single entry still has to be visible against the background. */
private const val MINIMUM_SHADE = 0.35f
private const val MAXIMUM_SHADE = 0.75f
