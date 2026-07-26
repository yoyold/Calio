package app.calio.shared.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.calio.datetime.dayWindowIn
import app.calio.datetime.today
import app.calio.designsystem.CalioTheme
import app.calio.model.Calendar
import app.calio.model.Event
import app.calio.model.EventTimeRange
import app.calio.model.Task
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(CalioTheme.spacing.extraLarge),
        verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.large),
    ) {
        Text(date.toString(), style = MaterialTheme.typography.displaySmall)
        Text(
            text = "${events.size} scheduled today, ${tasks.count { !it.isCompleted }} open tasks",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        HorizontalDivider()
        SectionTitle("Calendars")
        calendars.forEach { CalendarRow(it) }

        HorizontalDivider()
        SectionTitle("Today")
        if (events.isEmpty()) {
            EmptyLine("Nothing scheduled.")
        } else {
            events.forEach { EventRow(it) }
        }

        HorizontalDivider()
        SectionTitle("Tasks")
        if (tasks.isEmpty()) {
            EmptyLine("Nothing to do.")
        } else {
            tasks.forEach { TaskRow(it) }
        }

        Spacer(Modifier.height(CalioTheme.spacing.extraLarge))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CalendarRow(calendar: Calendar) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(CalioTheme.spacing.medium)
                .background(Color(calendar.color.argb.toInt()), CircleShape),
        )
        Text(calendar.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun EventRow(event: Event) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium),
    ) {
        Text(
            text = event.timeRange.startLabel(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(event.title, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun TaskRow(task: Task) {
    Text(
        text = if (task.isCompleted) "Done: ${task.title}" else task.title,
        style = MaterialTheme.typography.bodyLarge,
    )
}

private fun EventTimeRange.startLabel(): String = when (this) {
    is EventTimeRange.Zoned -> start.time.toString()
    is EventTimeRange.AllDay -> "All day"
}
