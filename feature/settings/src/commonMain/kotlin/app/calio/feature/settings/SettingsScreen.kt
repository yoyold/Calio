package app.calio.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
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
import app.calio.model.CalioColor
import app.calio.model.DayWindow
import app.calio.model.ThemeMode
import app.calio.sync.SyncStatus
import app.calio.ui.ScreenHeader
import app.calio.ui.clockLabel
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()

    SettingsScreen(
        state = state,
        syncStatus = syncStatus,
        onEvent = viewModel::onEvent,
        modifier = modifier,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    syncStatus: SyncStatus,
    onEvent: (SettingsUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        ScreenHeader(title = "Settings")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CalioTheme.spacing.extraLarge),
            verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.large),
        ) {
            AppearanceSection(state, onEvent)
            SyncSection(state, syncStatus, onEvent)
            WorkingHoursSection(state, onEvent)
            BufferSection(state, onEvent)
            CalendarsSection(state, onEvent)
            CategoriesSection(state, onEvent)
        }
    }

    (state.editingCalendar ?: state.editingCategory)?.let { draft ->
        ColorDraftDialog(
            draft = draft,
            isCalendar = state.editingCalendar != null,
            onEvent = onEvent,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    SettingsSection("Appearance") {
        Label("Theme")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = mode == state.settings.themeMode,
                    onClick = { onEvent(SettingsUiEvent.SetThemeMode(mode)) },
                    label = { Text(mode.name.lowercase().replaceFirstChar { it.uppercase() }) },
                )
            }
        }

        Label("Week starts on")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
            listOf(DayOfWeek.MONDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY).forEach { day ->
                FilterChip(
                    selected = day == state.settings.weekStart,
                    onClick = { onEvent(SettingsUiEvent.SetWeekStart(day)) },
                    label = { Text(day.label()) },
                )
            }
        }
    }
}

/**
 * A weekday is either a working day with a window or not a working day at all.
 *
 * The switch and the two times are the same setting seen from two sides, so switching a day off
 * removes its window rather than remembering a hidden one.
 */
@Composable
private fun WorkingHoursSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    SettingsSection("Working hours") {
        DayOfWeek.entries.forEach { day ->
            val window = state.settings.workingHours.days[day]

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
            ) {
                Text(
                    text = day.label(),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(48.dp),
                )
                Switch(
                    checked = window != null,
                    onCheckedChange = { isWorking ->
                        onEvent(
                            SettingsUiEvent.SetWorkingDay(
                                day = day,
                                window = if (isWorking) DefaultWorkingWindow else null,
                            ),
                        )
                    },
                )
                if (window != null) {
                    TimeChip(window.start) { time ->
                        onEvent(
                            SettingsUiEvent.SetWorkingDay(
                                day,
                                window.copyChecked(start = time),
                            ),
                        )
                    }
                    Text("to", style = MaterialTheme.typography.labelSmall)
                    TimeChip(window.endExclusive) { time ->
                        onEvent(
                            SettingsUiEvent.SetWorkingDay(
                                day,
                                window.copyChecked(endExclusive = time),
                            ),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BufferSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    val policy = state.settings.bufferPolicy

    SettingsSection("Buffers between meetings") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Suggest buffers",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = policy.isEnabled,
                onCheckedChange = { onEvent(SettingsUiEvent.SetBufferPolicy(policy.copy(isEnabled = it))) },
            )
        }

        if (policy.isEnabled) {
            Label("Buffer length")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
                listOf(5, 10, 15, 30).forEach { minutes ->
                    FilterChip(
                        selected = minutes == policy.defaultMinutes,
                        onClick = {
                            onEvent(SettingsUiEvent.SetBufferPolicy(policy.copy(defaultMinutes = minutes)))
                        },
                        label = { Text("$minutes min") },
                    )
                }
            }

            Label("Only for gaps up to")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
                listOf(60, 120, 240).forEach { minutes ->
                    FilterChip(
                        selected = minutes == policy.maximumGapMinutes,
                        onClick = {
                            onEvent(
                                SettingsUiEvent.SetBufferPolicy(
                                    policy.copy(maximumGapMinutes = minutes),
                                ),
                            )
                        },
                        label = { Text("${minutes / 60} h") },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarsSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    SettingsSection("Calendars") {
        state.calendars.forEach { calendar ->
            ColorRow(
                name = calendar.name,
                color = calendar.color,
                trailing = {
                    if (calendar.isDefault) {
                        Text(
                            text = "Default",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        TextButton(
                            onClick = { onEvent(SettingsUiEvent.MakeDefaultCalendar(calendar.id)) },
                        ) { Text("Make default") }
                    }
                },
                onEdit = { onEvent(SettingsUiEvent.EditCalendar(calendar.id)) },
                onDelete = if (state.canDeleteCalendar) {
                    { onEvent(SettingsUiEvent.DeleteCalendar(calendar.id)) }
                } else {
                    null
                },
            )
        }
        AddButton("Add calendar") { onEvent(SettingsUiEvent.EditCalendar(null)) }
    }
}

@Composable
private fun CategoriesSection(state: SettingsUiState, onEvent: (SettingsUiEvent) -> Unit) {
    SettingsSection("Categories") {
        state.categories.forEach { category ->
            ColorRow(
                name = category.name,
                color = category.color,
                trailing = {
                    if (category.isBuiltIn) {
                        Text(
                            text = "Built in",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onEdit = { onEvent(SettingsUiEvent.EditCategory(category.id)) },
                // A built-in category can be renamed and recoloured but never removed, so no event
                // can end up pointing at a category that is gone.
                onDelete = if (category.isBuiltIn) {
                    null
                } else {
                    { onEvent(SettingsUiEvent.DeleteCategory(category.id)) }
                },
            )
        }
        AddButton("Add category") { onEvent(SettingsUiEvent.EditCategory(null)) }
    }
}

/**
 * Synchronisation is a path, not an account.
 *
 * Pointing two installations at the same synced folder is enough — no server, no sign-in, and the
 * folder is one the user already backs up. Leaving it empty is a normal state, not an unfinished
 * one, so nothing here nags about it.
 */
@Composable
private fun SyncSection(
    state: SettingsUiState,
    status: SyncStatus,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    var path by remember(state.settings.syncFolderPath) {
        mutableStateOf(state.settings.syncFolderPath.orEmpty())
    }

    SettingsSection("Synchronisation") {
        OutlinedTextField(
            value = path,
            onValueChange = { path = it },
            label = { Text("Shared folder") },
            placeholder = { Text("Leave empty to sync with nothing") },
            singleLine = true,
            trailingIcon = {
                if (path != state.settings.syncFolderPath.orEmpty()) {
                    TextButton(onClick = { onEvent(SettingsUiEvent.SetSyncFolder(path)) }) {
                        Text("Apply")
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "Point both installations at the same folder, for example one that a cloud " +
                "service already keeps in step.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
        ) {
            Text(
                text = status.summary(isConfigured = state.settings.syncFolderPath != null),
                style = MaterialTheme.typography.bodyMedium,
                color = if (status.lastError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { onEvent(SettingsUiEvent.SyncNow) },
                enabled = state.settings.syncFolderPath != null && !status.isRunning,
            ) { Text("Sync now") }
        }
    }
}

/** Says what is happening in one line, because that is all a settings row has room for. */
private fun SyncStatus.summary(isConfigured: Boolean): String = when {
    !isConfigured && pendingChanges > 0 ->
        "Not synchronising. $pendingChanges change${plural(pendingChanges)} waiting."

    !isConfigured -> "Not synchronising."
    isRunning -> "Synchronising…"
    lastError != null -> "Last attempt failed: $lastError"
    pendingChanges > 0 -> "$pendingChanges change${plural(pendingChanges)} waiting to be sent."
    lastSuccessAt != null -> "Up to date."
    else -> "Waiting for the first round."
}

private fun plural(count: Int): String = if (count == 1) "" else "s"

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(CalioTheme.radius.large),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(CalioTheme.spacing.large),
            verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = CalioTheme.spacing.extraSmall),
            )
            content()
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = CalioTheme.spacing.small),
    )
}

@Composable
private fun ColorRow(
    name: String,
    color: CalioColor,
    trailing: @Composable () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium),
    ) {
        Box(Modifier.size(12.dp).background(Color(color.argb.toInt()), CircleShape))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).clickable(onClick = onEdit),
        )
        trailing()
        IconButton(onClick = onEdit) {
            Icon(CalioIcons.Settings, contentDescription = "Edit", Modifier.size(16.dp))
        }
        if (onDelete != null) {
            IconButton(onClick = onDelete) {
                Icon(CalioIcons.Close, contentDescription = "Delete", Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun AddButton(label: String, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = { Icon(CalioIcons.Plus, contentDescription = null, Modifier.size(16.dp)) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeChip(value: LocalTime, onSelect: (LocalTime) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }

    AssistChip(onClick = { isOpen = true }, label = { Text(value.clockLabel()) })

    if (isOpen) {
        val pickerState = rememberTimePickerState(value.hour, value.minute, is24Hour = true)

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
private fun ColorDraftDialog(
    draft: ColorDraft,
    isCalendar: Boolean,
    onEvent: (SettingsUiEvent) -> Unit,
) {
    AlertDialog(
        onDismissRequest = { onEvent(SettingsUiEvent.CancelEditing) },
        confirmButton = {
            TextButton(
                onClick = { onEvent(SettingsUiEvent.SaveDraft) },
                enabled = draft.isValid,
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { onEvent(SettingsUiEvent.CancelEditing) }) { Text("Cancel") }
        },
        title = {
            val what = if (isCalendar) "calendar" else "category"
            Text(if (draft.isNew) "New $what" else "Edit $what")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.medium)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { onEvent(SettingsUiEvent.DraftChanged(draft.copy(name = it))) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
                    CalioPalette.forEach { colour ->
                        val isSelected = colour == draft.color
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color(colour.argb.toInt()), CircleShape)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = CircleShape,
                                )
                                .clickable {
                                    onEvent(SettingsUiEvent.DraftChanged(draft.copy(color = colour)))
                                },
                        )
                    }
                }
            }
        },
    )
}

private fun DayOfWeek.label(): String = when (this) {
    DayOfWeek.MONDAY -> "Mon"
    DayOfWeek.TUESDAY -> "Tue"
    DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY -> "Thu"
    DayOfWeek.FRIDAY -> "Fri"
    DayOfWeek.SATURDAY -> "Sat"
    DayOfWeek.SUNDAY -> "Sun"
}

/** Keeps the window valid while one end is being moved. */
private fun DayWindow.copyChecked(
    start: LocalTime = this.start,
    endExclusive: LocalTime = this.endExclusive,
): DayWindow = if (endExclusive > start) {
    DayWindow(start, endExclusive)
} else {
    this
}
