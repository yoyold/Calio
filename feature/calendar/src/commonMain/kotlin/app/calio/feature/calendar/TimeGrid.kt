package app.calio.feature.calendar

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.calio.datetime.dayLengthIn
import app.calio.designsystem.CalioTheme
import app.calio.designsystem.readableContentColor
import app.calio.domain.recurrence.EventOccurrence
import app.calio.model.EventKind
import app.calio.model.EventTimeRange
import app.calio.ui.clockLabel
import app.calio.ui.weekdayAbbreviation
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlin.time.Instant

/**
 * The scrollable hour grid behind the day and week views.
 *
 * A day is measured from local midnight to local midnight rather than assumed to be 24 hours, so the
 * two days a year that daylight saving moves are drawn 23 or 25 hours tall and every appointment
 * after the transition still lands on its own hour line.
 */
@Composable
fun TimeGrid(
    state: CalendarUiState,
    now: Instant,
    modifier: Modifier = Modifier,
    hourHeight: Dp = DEFAULT_HOUR_HEIGHT,
) {
    if (state.days.isEmpty()) return

    Column(modifier.fillMaxSize()) {
        DayHeaderRow(state)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (state.hasAllDayEntries) {
            AllDayRow(state)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        Row(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            HourAxis(state.zone, state.days.first().date, hourHeight)
            state.days.forEachIndexed { index, day ->
                if (index > 0) VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DayColumn(
                    day = day,
                    state = state,
                    now = now,
                    hourHeight = hourHeight,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DayHeaderRow(state: CalendarUiState) {
    Row(Modifier.fillMaxWidth().padding(vertical = CalioTheme.spacing.small)) {
        Box(Modifier.width(AXIS_WIDTH))
        state.days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.date.weekdayAbbreviation(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Today is marked by a filled disc rather than a coloured number: the number stays
                // the same weight as its neighbours, so the row does not look misaligned.
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(
                            color = if (day.isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color.Transparent
                            },
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = day.date.day.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (day.isToday) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        }
    }
}

/**
 * All-day entries sit above the grid rather than inside it.
 *
 * They have no position on an hour axis, and stretching them over the whole column would hide every
 * timed appointment behind them.
 */
@Composable
private fun AllDayRow(state: CalendarUiState) {
    Row(Modifier.fillMaxWidth().padding(vertical = CalioTheme.spacing.extraSmall)) {
        Box(Modifier.width(AXIS_WIDTH), contentAlignment = Alignment.CenterEnd) {
            Text(
                text = "All day",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = CalioTheme.spacing.small),
            )
        }
        state.days.forEach { day ->
            Column(
                modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                day.allDay.forEach { entry ->
                    val background = Color(entry.color.argb.toInt())
                    Text(
                        text = entry.occurrence.event.title,
                        style = MaterialTheme.typography.labelMedium,
                        color = readableContentColor(background),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(CalioTheme.radius.small))
                            .background(background)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun HourAxis(zone: TimeZone, date: LocalDate, hourHeight: Dp) {
    val hours = (date.dayLengthIn(zone).inWholeMinutes / MINUTES_PER_HOUR).toInt()

    Column(Modifier.width(AXIS_WIDTH).height(hourHeight * hours)) {
        repeat(hours) { hour ->
            Box(Modifier.height(hourHeight).fillMaxWidth()) {
                // The label sits on the line it belongs to, nudged up by half its own height.
                Text(
                    text = LocalTime(hour.coerceAtMost(23), 0).clockLabel(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = (-6).dp)
                        .padding(end = CalioTheme.spacing.small),
                )
            }
        }
    }
}

@Composable
private fun DayColumn(
    day: CalendarDay,
    state: CalendarUiState,
    now: Instant,
    hourHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val dayStart = day.date.atStartOfDayIn(state.zone)
    val dayMinutes = day.date.dayLengthIn(state.zone).inWholeMinutes
    val columnHeight = hourHeight * (dayMinutes / MINUTES_PER_HOUR.toFloat())

    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val nonWorking = CalioTheme.colors.nonWorkingHours
    val workingWindow = state.workingHours.windowFor(day.date.dayOfWeek)

    BoxWithConstraints(modifier.height(columnHeight)) {
        val columnWidth = maxWidth

        Canvas(Modifier.fillMaxSize()) {
            // Hours outside the working day are shaded rather than hidden, so a late meeting is
            // still visible but the eye is drawn to the part of the day that is planned.
            val minuteHeight = size.height / dayMinutes
            if (workingWindow == null) {
                drawRect(color = nonWorking)
            } else {
                val startY = workingWindow.start.toMinuteOfDay() * minuteHeight
                val endY = workingWindow.endExclusive.toMinuteOfDay() * minuteHeight
                drawRect(color = nonWorking, size = size.copy(height = startY))
                drawRect(
                    color = nonWorking,
                    topLeft = Offset(0f, endY),
                    size = size.copy(height = size.height - endY),
                )
            }

            val hours = dayMinutes / MINUTES_PER_HOUR
            for (hour in 1..hours) {
                val y = hour * MINUTES_PER_HOUR * minuteHeight
                drawLine(
                    color = lineColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }
        }

        day.timed.forEach { entry ->
            val startMinutes = minutesBetween(dayStart, entry.occurrence.timeRange.startUtc)
                .coerceIn(0, dayMinutes)
            val endMinutes = minutesBetween(dayStart, entry.occurrence.timeRange.endUtcExclusive)
                .coerceIn(0, dayMinutes)

            val slotWidth = columnWidth / entry.columnCount

            EventBlock(
                entry = entry,
                modifier = Modifier
                    .offset(
                        x = slotWidth * entry.column,
                        y = hourHeight * (startMinutes / MINUTES_PER_HOUR.toFloat()),
                    )
                    .width(slotWidth)
                    .height(
                        maxOf(
                            hourHeight * ((endMinutes - startMinutes) / MINUTES_PER_HOUR.toFloat()),
                            MINIMUM_BLOCK_HEIGHT,
                        ),
                    )
                    .padding(end = 2.dp),
            )
        }

        if (day.isToday) {
            val minutesNow = minutesBetween(dayStart, now)
            if (minutesNow in 0..dayMinutes) {
                NowIndicator(
                    modifier = Modifier
                        .offset(y = hourHeight * (minutesNow / MINUTES_PER_HOUR.toFloat()))
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun EventBlock(entry: TimedEntry, modifier: Modifier = Modifier) {
    val base = Color(entry.color.argb.toInt())
    val isBuffer = entry.occurrence.event.kind == EventKind.BUFFER
    val background = if (isBuffer) base.copy(alpha = 0.25f) else base
    val content = if (isBuffer) MaterialTheme.colorScheme.onSurface else readableContentColor(base)

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(CalioTheme.radius.small))
            .background(background)
            .padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = entry.occurrence.event.title,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = entry.occurrence.startClockLabel(),
            style = MaterialTheme.typography.labelSmall,
            color = content.copy(alpha = 0.8f),
            maxLines = 1,
        )
    }
}

/** The current time, drawn across the whole column so it reads as a horizon rather than a marker. */
@Composable
private fun NowIndicator(modifier: Modifier = Modifier) {
    val color = CalioTheme.colors.nowIndicator

    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(color))
    }
}

private fun EventOccurrence.startClockLabel(): String = when (val range = timeRange) {
    is EventTimeRange.Zoned -> range.start.time.clockLabel()
    is EventTimeRange.AllDay -> "All day"
}

private fun LocalTime.toMinuteOfDay(): Float = (hour * MINUTES_PER_HOUR + minute).toFloat()

private fun minutesBetween(from: Instant, to: Instant): Long = (to - from).inWholeMinutes

private const val MINUTES_PER_HOUR = 60L
private val AXIS_WIDTH = 52.dp
private val DEFAULT_HOUR_HEIGHT = 56.dp

/** Short appointments still need room for one line of text to be worth drawing. */
private val MINIMUM_BLOCK_HEIGHT = 22.dp
