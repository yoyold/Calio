package app.calio.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calio.designsystem.CalioTheme
import app.calio.designsystem.readableContentColor
import app.calio.domain.recurrence.EventOccurrence
import app.calio.model.CalioColor
import app.calio.ui.weekdayInitials
import kotlinx.datetime.LocalDate

/**
 * The month view: six weeks of cells, whatever the month's shape.
 *
 * Always six rows, even when five would fit. A grid that changes height between months makes the
 * whole screen jump while paging through the year, and one mostly empty row is the cheaper price.
 */
@Composable
fun MonthGrid(
    state: CalendarUiState,
    onSelectDay: (LocalDate) -> Unit,
    onSelectEntry: (EventOccurrence) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.days.isEmpty()) return

    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = CalioTheme.spacing.small)) {
            weekdayInitials(state.weekStart).forEach { initial ->
                Text(
                    text = initial,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        state.days.chunked(DAYS_PER_WEEK).forEach { week ->
            Row(Modifier.fillMaxWidth().weight(1f)) {
                week.forEach { day ->
                    MonthCell(
                        day = day,
                        // Days from the neighbouring months are shown but held back, so the month
                        // being looked at stays readable as a block.
                        isInAnchorMonth = day.date.month == state.anchor.month,
                        onSelectDay = onSelectDay,
                        onSelectEntry = onSelectEntry,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun MonthCell(
    day: CalendarDay,
    isInAnchorMonth: Boolean,
    onSelectDay: (LocalDate) -> Unit,
    onSelectEntry: (EventOccurrence) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries = day.entriesInOrder()

    Column(
        modifier = modifier
            .clickable { onSelectDay(day.date) }
            .padding(horizontal = 3.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    color = if (day.isToday) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = day.date.day.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = when {
                    day.isToday -> MaterialTheme.colorScheme.onPrimary
                    isInAnchorMonth -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.outline
                },
            )
        }

        entries.take(MAX_VISIBLE_ENTRIES).forEach { entry ->
            MonthEntryChip(entry, onClick = { onSelectEntry(entry.occurrence) })
        }

        if (entries.size > MAX_VISIBLE_ENTRIES) {
            Text(
                text = "+${entries.size - MAX_VISIBLE_ENTRIES}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 3.dp),
            )
        }
    }
}

@Composable
private fun MonthEntryChip(entry: MonthEntry, onClick: () -> Unit) {
    val background = Color(entry.color.argb.toInt())

    Text(
        text = entry.occurrence.event.title,
        style = MaterialTheme.typography.labelSmall,
        color = readableContentColor(background),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 1.dp),
    )
}

private data class MonthEntry(val occurrence: EventOccurrence, val color: CalioColor)

/** All-day entries first, then the timed ones in the order they happen. */
private fun CalendarDay.entriesInOrder(): List<MonthEntry> =
    allDay.map { MonthEntry(it.occurrence, it.color) } +
        timed.sortedBy { it.occurrence.timeRange.startUtc }
            .map { MonthEntry(it.occurrence, it.color) }

private const val DAYS_PER_WEEK = 7

/** Beyond this a cell turns into a wall of text, and the count says more than the titles. */
private const val MAX_VISIBLE_ENTRIES = 3
