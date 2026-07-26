package app.calio.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.calio.datetime.dayWindowIn
import app.calio.datetime.isoWeekNumber
import app.calio.datetime.today
import app.calio.designsystem.CalioTheme
import app.calio.designsystem.icon.CalioIcons
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.Event
import app.calio.model.EventTimeRange
import app.calio.model.Task
import app.calio.model.resolveEventColor
import app.calio.shared.CalioContainer
import kotlinx.datetime.TimeZone
import kotlin.time.Clock

/**
 * A summary of the day, and the first screen that reads the whole stack.
 *
 * It exists to make the wiring visible: the calendars, events and tasks shown here come out of the
 * database through the repositories, so anything broken between storage and screen shows up here
 * rather than once a calendar grid has been built on top of it.
 */
@Composable
fun OverviewScreen(container: CalioContainer, modifier: Modifier = Modifier) {
    val zone = TimeZone.currentSystemDefault()
    val date = Clock.System.today(zone)

    val calendars by container.calendars.observeAll().collectAsState(emptyList())
    val events by container.events.observeInRange(date.dayWindowIn(zone)).collectAsState(emptyList())
    val tasks by container.tasks.observeTopLevel().collectAsState(emptyList())

    val calendarsById = calendars.associateBy { it.id }
    val openTasks = tasks.filterNot { it.isCompleted }

    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = date.longLabel(),
            subtitle = "${date.weekdayName()} · Week ${isoWeekNumber(date)}",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CalioTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.large),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium)) {
                StatTile(
                    icon = CalioIcons.Clock,
                    value = events.size.toString(),
                    label = if (events.size == 1) "event today" else "events today",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = CalioIcons.CheckCircle,
                    value = openTasks.size.toString(),
                    label = if (openTasks.size == 1) "open task" else "open tasks",
                    modifier = Modifier.weight(1f),
                )
                StatTile(
                    icon = CalioIcons.Layers,
                    value = calendars.size.toString(),
                    label = if (calendars.size == 1) "calendar" else "calendars",
                    modifier = Modifier.weight(1f),
                )
            }

            SectionCard(title = "Today", icon = CalioIcons.Calendar) {
                if (events.isEmpty()) {
                    SectionEmpty("Nothing scheduled for today.")
                } else {
                    events.forEach { EventRow(it, calendarsById) }
                }
            }

            SectionCard(title = "Tasks", icon = CalioIcons.Tasks) {
                if (tasks.isEmpty()) {
                    SectionEmpty("No tasks yet.")
                } else {
                    tasks.forEach { TaskRow(it) }
                }
            }

            SectionCard(title = "Calendars", icon = CalioIcons.Layers) {
                if (calendars.isEmpty()) {
                    SectionEmpty("No calendars yet.")
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
                        calendars.forEach { CalendarChip(it) }
                    }
                }
            }

            Spacer(Modifier.height(CalioTheme.spacing.large))
        }
    }
}

/**
 * A single number with its unit.
 *
 * The number carries the weight and the label stays quiet, so the row can be read at a glance
 * without any of the tiles competing with the content below them.
 */
@Composable
private fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CalioTheme.radius.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(CalioTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CalioTheme.radius.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(CalioTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            content()
        }
    }
}

@Composable
private fun SectionEmpty(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
    )
}

@Composable
private fun EventRow(event: Event, calendarsById: Map<CalendarId, Calendar>) {
    val calendar = calendarsById[event.calendarId]
    val accent = calendar?.let {
        Color(resolveEventColor(event.colorOverride, category = null, calendar = it).argb.toInt())
    } ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = event.timeRange.startLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        // A thin bar rather than a dot: it survives being scaled down in a dense list and reads as
        // the extent of the appointment rather than as a bullet point.
        Box(
            Modifier
                .width(3.dp)
                .height(28.dp)
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Text(
            text = event.title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TaskRow(task: Task) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CalioIcons.CheckCircle,
            contentDescription = null,
            tint = if (task.isCompleted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = task.title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (task.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@Composable
private fun CalendarChip(calendar: Calendar) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = CalioTheme.spacing.medium,
                vertical = CalioTheme.spacing.small,
            ),
            horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(Color(calendar.color.argb.toInt()), CircleShape),
            )
            Text(calendar.name, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private fun EventTimeRange.startLabel(): String = when (this) {
    is EventTimeRange.Zoned -> start.time.clockLabel()
    is EventTimeRange.AllDay -> "All day"
}
