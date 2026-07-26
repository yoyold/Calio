package app.calio.feature.eventeditor

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.calio.designsystem.CalioTheme
import app.calio.designsystem.icon.CalioIcons
import app.calio.datetime.plusDays
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.Category
import app.calio.model.CategoryId
import app.calio.model.ReminderId
import app.calio.ui.clockLabel
import app.calio.ui.longLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

@Composable
fun EventEditorScreen(
    viewModel: EventEditorViewModel,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.isFinished) {
        onClose()
        return
    }

    EventEditorScreen(
        state = state,
        onEvent = viewModel::onEvent,
        onClose = onClose,
        modifier = modifier,
    )
}

/**
 * The editor as a pure function of its state.
 *
 * Saving stays possible while the event collides with something. A conflict is information, not an
 * error: booking two things at once is sometimes exactly what was meant, and an application that
 * refuses it is one the user has to work around.
 */
@Composable
fun EventEditorScreen(
    state: EventEditorUiState,
    onEvent: (EventEditorUiEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft

    fun update(transform: EventDraft.() -> EventDraft) =
        onEvent(EventEditorUiEvent.DraftChanged(draft.transform()))

    Column(modifier.fillMaxSize()) {
        EditorBar(
            isNew = state.isNew,
            canSave = state.canSave,
            onClose = onClose,
            onSave = { onEvent(EventEditorUiEvent.Save) },
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CalioTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.large),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = { value -> update { copy(title = value) } },
                label = { Text("Title") },
                singleLine = true,
                isError = DraftProblem.BlankTitle in state.problems && draft.title.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            )

            if (state.isSeries) {
                Notice("Changes apply to the whole series.")
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("All day", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = draft.isAllDay,
                    onCheckedChange = { value -> update { copy(isAllDay = value) } },
                )
            }

            TimeRangeFields(
                draft = draft,
                onChange = { changed -> onEvent(EventEditorUiEvent.DraftChanged(changed)) },
            )

            if (DraftProblem.EndBeforeStart in state.problems) {
                Notice(DraftProblem.EndBeforeStart.message, isWarning = true)
            }

            if (state.hasConflicts) {
                Notice(
                    text = "Overlaps " + state.conflicts.joinToString { it.event.title },
                    isWarning = true,
                )
            }

            Section("Calendar") {
                CalendarChips(state.calendars, draft.calendarId) { id ->
                    update { copy(calendarId = id) }
                }
            }

            Section("Category") {
                CategoryChips(state.categories, draft.categoryId) { id ->
                    update { copy(categoryId = id) }
                }
            }

            Section("Repeat") {
                RecurrenceChips(draft.recurrence) { preset -> update { copy(recurrence = preset) } }
            }

            Section("Reminders") {
                ReminderChips(
                    reminders = draft.reminders,
                    onAdd = { onEvent(EventEditorUiEvent.AddReminder) },
                    onRemove = { id -> onEvent(EventEditorUiEvent.RemoveReminder(id)) },
                )
            }

            OutlinedTextField(
                value = draft.location,
                onValueChange = { value -> update { copy(location = value) } },
                label = { Text("Location") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.description,
                onValueChange = { value -> update { copy(description = value) } },
                label = { Text("Description") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = draft.notes,
                onValueChange = { value -> update { copy(notes = value) } },
                label = { Text("Notes") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!state.isNew) {
                DeleteActions(
                    canRemoveSingleOccurrence = state.canRemoveSingleOccurrence,
                    onDelete = { scope -> onEvent(EventEditorUiEvent.Delete(scope)) },
                )
            }
        }
    }
}

@Composable
private fun EditorBar(
    isNew: Boolean,
    canSave: Boolean,
    onClose: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = CalioTheme.spacing.large, vertical = CalioTheme.spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium),
    ) {
        TextButton(onClick = onClose) { Text("Cancel") }
        Text(
            text = if (isNew) "New event" else "Edit event",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onSave, enabled = canSave) { Text("Save") }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@Composable
private fun Notice(text: String, isWarning: Boolean = false) {
    Surface(
        shape = RoundedCornerShape(CalioTheme.radius.medium),
        color = if (isWarning) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isWarning) {
                MaterialTheme.colorScheme.onErrorContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(
                horizontal = CalioTheme.spacing.medium,
                vertical = CalioTheme.spacing.small,
            ),
        )
    }
}

@Composable
private fun TimeRangeFields(draft: EventDraft, onChange: (EventDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("From", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.size(48.dp, 24.dp))
            DateField(draft.startDate) { date ->
                // Moving the start drags the end along, so a range never turns itself inside out
                // while the user is halfway through changing it.
                val shift = draft.startDate.daysUntil(date)
                onChange(draft.copy(startDate = date, endDate = draft.endDate.plusDays(shift)))
            }
            if (!draft.isAllDay) {
                TimeField(draft.startTime) { time -> onChange(draft.copy(startTime = time)) }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("To", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.size(48.dp, 24.dp))
            DateField(draft.endDate) { date -> onChange(draft.copy(endDate = date)) }
            if (!draft.isAllDay) {
                TimeField(draft.endTime) { time -> onChange(draft.copy(endTime = time)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(value: LocalDate, onSelect: (LocalDate) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }

    AssistChip(onClick = { isOpen = true }, label = { Text(value.longLabel()) })

    if (isOpen) {
        // The picker works in UTC milliseconds by convention, so the conversion goes through UTC on
        // both sides rather than through the event's own zone.
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )

        DatePickerDialog(
            onDismissRequest = { isOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onSelect(
                                Instant.fromEpochMilliseconds(millis)
                                    .toLocalDateTime(TimeZone.UTC).date,
                            )
                        }
                        isOpen = false
                    },
                ) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { isOpen = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(value: LocalTime, onSelect: (LocalTime) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }

    AssistChip(onClick = { isOpen = true }, label = { Text(value.clockLabel()) })

    if (isOpen) {
        val pickerState = rememberTimePickerState(
            initialHour = value.hour,
            initialMinute = value.minute,
            is24Hour = true,
        )

        AlertDialog(
            onDismissRequest = { isOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSelect(LocalTime(pickerState.hour, pickerState.minute))
                        isOpen = false
                    },
                ) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { isOpen = false }) { Text("Cancel") } },
            text = { Box(contentAlignment = Alignment.Center) { TimePicker(state = pickerState) } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarChips(
    calendars: List<Calendar>,
    selected: CalendarId?,
    onSelect: (CalendarId) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
        calendars.forEach { calendar ->
            FilterChip(
                selected = calendar.id == selected,
                onClick = { onSelect(calendar.id) },
                label = { Text(calendar.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(Color(calendar.color.argb.toInt()), CircleShape),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryChips(
    categories: List<Category>,
    selected: CategoryId?,
    onSelect: (CategoryId?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("None") },
        )
        categories.forEach { category ->
            FilterChip(
                selected = category.id == selected,
                onClick = { onSelect(category.id) },
                label = { Text(category.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(Color(category.color.argb.toInt()), CircleShape),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RecurrenceChips(selected: RecurrencePreset, onSelect: (RecurrencePreset) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
        RecurrencePreset.entries.forEach { preset ->
            FilterChip(
                selected = preset == selected,
                onClick = { onSelect(preset) },
                label = { Text(preset.label) },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReminderChips(
    reminders: List<ReminderDraft>,
    onAdd: () -> Unit,
    onRemove: (ReminderId) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
        reminders.forEach { reminder ->
            AssistChip(
                onClick = { onRemove(reminder.id) },
                label = { Text(reminder.leadMinutes.asLeadLabel()) },
                trailingIcon = {
                    Icon(CalioIcons.Plus, contentDescription = "Remove", Modifier.size(14.dp))
                },
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text("Add reminder") },
            leadingIcon = { Icon(CalioIcons.Plus, contentDescription = null, Modifier.size(16.dp)) },
        )
    }
}

@Composable
private fun DeleteActions(
    canRemoveSingleOccurrence: Boolean,
    onDelete: (DeleteScope) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
        if (canRemoveSingleOccurrence) {
            TextButton(onClick = { onDelete(DeleteScope.Occurrence) }) { Text("Delete this occurrence") }
            TextButton(onClick = { onDelete(DeleteScope.Series) }) { Text("Delete series") }
        } else {
            TextButton(onClick = { onDelete(DeleteScope.Series) }) { Text("Delete") }
        }
    }
}

private fun Int.asLeadLabel(): String = when {
    this % (24 * 60) == 0 -> "${this / (24 * 60)} day before"
    this % 60 == 0 -> "${this / 60} h before"
    else -> "$this min before"
}
