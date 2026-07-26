package app.calio.feature.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.calio.designsystem.CalioTheme
import app.calio.designsystem.icon.CalioIcons
import app.calio.model.Priority
import app.calio.model.Task
import app.calio.ui.EmptyState
import app.calio.ui.ScreenHeader
import app.calio.ui.shortLabel

@Composable
fun TasksScreen(viewModel: TasksViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TasksScreen(state = state, onEvent = viewModel::onEvent, modifier = modifier)
}

@Composable
fun TasksScreen(
    state: TasksUiState,
    onEvent: (TasksUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(
            title = "Tasks",
            subtitle = "${state.counts[TaskFilter.Open] ?: 0} open",
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CalioTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.large),
        ) {
            FilterChips(state) { filter -> onEvent(TasksUiEvent.SelectFilter(filter)) }

            QuickAddField(
                placeholder = "Add a task",
                onSubmit = { title -> onEvent(TasksUiEvent.QuickAdd(title)) },
            )

            if (state.isEmpty) {
                Box(Modifier.fillMaxWidth().padding(top = CalioTheme.spacing.huge), Alignment.Center) {
                    EmptyState(
                        icon = CalioIcons.Tasks,
                        title = "Nothing here",
                        message = "Tasks matching this filter will show up in the list.",
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
                    state.items.forEach { item ->
                        TaskCard(
                            item = item,
                            isExpanded = item.task.id in state.expanded,
                            onEvent = onEvent,
                        )
                    }
                }
            }
        }
    }

    state.editing?.let { draft ->
        TaskEditorDialog(
            draft = draft,
            state = state,
            onEvent = onEvent,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterChips(state: TasksUiState, onSelect: (TaskFilter) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
        TaskFilter.entries.forEach { filter ->
            val count = state.counts[filter] ?: 0
            FilterChip(
                selected = filter == state.filter,
                onClick = { onSelect(filter) },
                label = { Text(if (count > 0) "${filter.label} $count" else filter.label) },
            )
        }
    }
}

/**
 * One line, one task.
 *
 * The field keeps the focus and clears itself after each entry, because tasks arrive in bursts:
 * having to reach for the field again between two thoughts is what makes a list not get written.
 */
@Composable
private fun QuickAddField(placeholder: String, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }

    fun submit() {
        if (text.isNotBlank()) {
            onSubmit(text)
            text = ""
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = { Icon(CalioIcons.Plus, contentDescription = null, Modifier.size(18.dp)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        trailingIcon = {
            if (text.isNotBlank()) {
                TextButton(onClick = { submit() }) { Text("Add") }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TaskCard(
    item: TaskItem,
    isExpanded: Boolean,
    onEvent: (TasksUiEvent) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(CalioTheme.radius.medium),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(CalioTheme.spacing.medium)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(
                    checked = item.task.isCompleted,
                    onCheckedChange = { checked ->
                        onEvent(TasksUiEvent.ToggleCompleted(item.task.id, checked))
                    },
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onEvent(TasksUiEvent.StartEditing(item.task.id)) },
                ) {
                    Text(
                        text = item.task.title,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.task.isCompleted) TextDecoration.LineThrough else null,
                        color = if (item.task.isCompleted) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    TaskMeta(item)
                }

                if (item.hasSubtasks) {
                    IconButton(onClick = { onEvent(TasksUiEvent.ToggleExpanded(item.task.id)) }) {
                        Icon(
                            imageVector = if (isExpanded) CalioIcons.ChevronLeft else CalioIcons.ChevronRight,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                IconButton(onClick = { onEvent(TasksUiEvent.Delete(item.task.id)) }) {
                    Icon(CalioIcons.Close, contentDescription = "Delete", Modifier.size(16.dp))
                }
            }

            if (item.hasSubtasks) {
                LinearProgressIndicator(
                    progress = { item.progressPercent / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = CalioTheme.spacing.extraSmall),
                )
            }

            if (isExpanded) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.padding(vertical = CalioTheme.spacing.small),
                )
                item.subtasks.forEach { subtask -> SubtaskRow(subtask, onEvent) }
                QuickAddField(
                    placeholder = "Add a subtask",
                    onSubmit = { title -> onEvent(TasksUiEvent.AddSubtask(item.task.id, title)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskMeta(item: TaskItem) {
    val dueDate = item.task.due?.date
    if (dueDate == null && item.task.priority == Priority.NONE && item.category == null &&
        !item.hasSubtasks
    ) {
        return
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        if (dueDate != null) {
            Text(
                text = dueDate.shortLabel(),
                style = MaterialTheme.typography.labelSmall,
                color = if (item.isOverdue) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = if (item.isOverdue) FontWeight.SemiBold else null,
            )
        }
        if (item.task.priority != Priority.NONE) {
            Text(
                text = item.task.priority.name.lowercase().replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item.category?.let { category ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(Color(category.color.argb.toInt()), CircleShape),
                )
                Text(
                    text = category.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (item.hasSubtasks) {
            Text(
                text = "${item.completedSubtasks}/${item.subtasks.size}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SubtaskRow(subtask: Task, onEvent: (TasksUiEvent) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            checked = subtask.isCompleted,
            onCheckedChange = { checked ->
                onEvent(TasksUiEvent.ToggleCompleted(subtask.id, checked))
            },
        )
        Text(
            text = subtask.title,
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = if (subtask.isCompleted) TextDecoration.LineThrough else null,
            color = if (subtask.isCompleted) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onEvent(TasksUiEvent.Delete(subtask.id)) }) {
            Icon(CalioIcons.Close, contentDescription = "Delete", Modifier.size(14.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskEditorDialog(
    draft: TaskDraft,
    state: TasksUiState,
    onEvent: (TasksUiEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(TasksUiEvent.CancelEditing) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(TasksUiEvent.SaveDraft) },
                enabled = state.canSaveDraft,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(TasksUiEvent.CancelEditing) }) { Text("Cancel") }
        },
        title = { Text("Edit task") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium),
            ) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onEvent(TasksUiEvent.DraftChanged(draft.copy(title = it))) },
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = {
                        onEvent(TasksUiEvent.DraftChanged(draft.copy(description = it)))
                    },
                    label = { Text("Description") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = "Priority",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
                    Priority.entries.forEach { priority ->
                        FilterChip(
                            selected = priority == draft.priority,
                            onClick = {
                                onEvent(TasksUiEvent.DraftChanged(draft.copy(priority = priority)))
                            },
                            label = {
                                Text(priority.name.lowercase().replaceFirstChar { it.uppercase() })
                            },
                        )
                    }
                }

                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
                    FilterChip(
                        selected = draft.categoryId == null,
                        onClick = { onEvent(TasksUiEvent.DraftChanged(draft.copy(categoryId = null))) },
                        label = { Text("None") },
                    )
                    state.categories.forEach { category ->
                        FilterChip(
                            selected = category.id == draft.categoryId,
                            onClick = {
                                onEvent(TasksUiEvent.DraftChanged(draft.copy(categoryId = category.id)))
                            },
                            label = { Text(category.name) },
                        )
                    }
                }

                Text(
                    text = "Due",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DueDateField(
                    draft = draft,
                    today = state.today,
                    onChange = { onEvent(TasksUiEvent.DraftChanged(it)) },
                )
            }
        },
    )
}
