package app.calio.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.calio.designsystem.CalioTheme
import app.calio.designsystem.icon.CalioIcons
import app.calio.model.EventId
import app.calio.model.EventTimeRange
import app.calio.model.TaskId
import app.calio.ui.EmptyState
import app.calio.ui.ScreenHeader
import app.calio.ui.clockLabel
import app.calio.ui.longLabel
import app.calio.ui.shortLabel

@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenEvent: (EventId) -> Unit,
    onOpenTask: (TaskId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    SearchScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onOpenEvent = onOpenEvent,
        onOpenTask = onOpenTask,
        modifier = modifier,
    )
}

@Composable
fun SearchScreen(
    state: SearchUiState,
    onEvent: (SearchUiEvent) -> Unit,
    onOpenEvent: (EventId) -> Unit,
    onOpenTask: (TaskId) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = "Search")

        OutlinedTextField(
            value = state.query,
            onValueChange = { onEvent(SearchUiEvent.QueryChanged(it)) },
            placeholder = { Text("Titles, notes, locations") },
            singleLine = true,
            leadingIcon = { Icon(CalioIcons.Search, contentDescription = null, Modifier.size(18.dp)) },
            trailingIcon = {
                if (state.hasQuery) {
                    IconButton(onClick = { onEvent(SearchUiEvent.Clear) }) {
                        Icon(CalioIcons.Close, contentDescription = "Clear", Modifier.size(16.dp))
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(CalioTheme.spacing.extraLarge),
        )

        when {
            !state.hasQuery -> Centred {
                EmptyState(
                    icon = CalioIcons.Search,
                    title = "Find anything",
                    message = "Search runs over titles, descriptions, notes and locations of events and tasks.",
                )
            }

            state.isSearching -> Centred { CircularProgressIndicator() }

            state.isEmptyResult -> Centred {
                EmptyState(
                    icon = CalioIcons.Search,
                    title = "No matches",
                    message = "Nothing found for \"${state.query}\".",
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = CalioTheme.spacing.extraLarge,
                    vertical = CalioTheme.spacing.small,
                ),
                verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
            ) {
                items(state.results, key = { it.key() }) { result ->
                    when (result) {
                        is SearchResult.EventResult -> EventResultRow(result) {
                            onOpenEvent(result.event.id)
                        }

                        is SearchResult.TaskResult -> TaskResultRow(result) {
                            onOpenTask(result.task.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Centred(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun EventResultRow(result: SearchResult.EventResult, onClick: () -> Unit) {
    ResultCard(onClick) {
        Box(
            Modifier
                .size(width = 3.dp, height = 32.dp)
                .background(Color(result.color.argb.toInt()), RoundedCornerShape(2.dp)),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = result.event.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = result.event.timeRange.label(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Event",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun TaskResultRow(result: SearchResult.TaskResult, onClick: () -> Unit) {
    ResultCard(onClick) {
        Icon(
            imageVector = CalioIcons.CheckCircle,
            contentDescription = null,
            tint = if (result.task.isCompleted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = result.task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (result.task.isCompleted) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val due = result.task.due?.date
            val category = result.category?.name
            val subtitle = listOfNotNull(due?.shortLabel(), category).joinToString(" · ")
            if (subtitle.isNotEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = "Task",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun ResultCard(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(CalioTheme.radius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(CalioTheme.spacing.medium),
            horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

private fun EventTimeRange.label(): String = when (this) {
    is EventTimeRange.Zoned -> "${start.date.longLabel()}, ${start.time.clockLabel()}"
    is EventTimeRange.AllDay -> "${startDate.longLabel()}, all day"
}

private fun SearchResult.key(): String = when (this) {
    is SearchResult.EventResult -> "event-${event.id.value}"
    is SearchResult.TaskResult -> "task-${task.id.value}"
}
