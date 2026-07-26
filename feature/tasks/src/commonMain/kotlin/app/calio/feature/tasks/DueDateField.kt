package app.calio.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.calio.datetime.plusDays
import app.calio.designsystem.CalioTheme
import app.calio.ui.shortLabel
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/**
 * Due dates as presets first, calendar second.
 *
 * Almost every task is due today, tomorrow or next week. Making those a single tap and keeping the
 * full calendar for the rest is the difference between a date being set and being skipped.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun DueDateField(
    draft: TaskDraft,
    today: LocalDate,
    onChange: (TaskDraft) -> Unit,
) {
    var isPickerOpen by remember { mutableStateOf(false) }

    val presets = listOf(
        "None" to null,
        "Today" to today,
        "Tomorrow" to today.plusDays(1),
        "Next week" to today.plusDays(7),
    )

    FlowRow(horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small)) {
        presets.forEach { (label, date) ->
            FilterChip(
                selected = draft.dueDate == date,
                onClick = { onChange(draft.copy(dueDate = date)) },
                label = { Text(label) },
            )
        }
        AssistChip(
            onClick = { isPickerOpen = true },
            label = {
                val chosen = draft.dueDate
                Text(if (chosen != null && chosen !in presets.map { it.second }) chosen.shortLabel() else "Pick a date")
            },
            modifier = Modifier,
        )
    }

    if (isPickerOpen) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (draft.dueDate ?: today)
                .atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds(),
        )

        DatePickerDialog(
            onDismissRequest = { isPickerOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onChange(
                                draft.copy(
                                    dueDate = Instant.fromEpochMilliseconds(millis)
                                        .toLocalDateTime(TimeZone.UTC).date,
                                ),
                            )
                        }
                        isPickerOpen = false
                    },
                ) { Text("Select") }
            },
            dismissButton = { TextButton(onClick = { isPickerOpen = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
