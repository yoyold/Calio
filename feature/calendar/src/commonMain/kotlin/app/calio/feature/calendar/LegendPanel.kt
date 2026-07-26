package app.calio.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.calio.designsystem.CalioTheme
import app.calio.model.CalioColor
import app.calio.ui.clockLabel

/**
 * The colour legend, and the switches that go with it.
 *
 * The two belong together: a legend that only explains colours leaves the user hunting for the
 * setting that turns one off, and a list of switches without swatches does not say what will
 * disappear. Ticking one off hides its entries everywhere — day, week, month and year alike.
 */
@Composable
fun LegendPanel(
    state: CalendarUiState,
    onEvent: (CalendarUiEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(CalioTheme.spacing.large),
        verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
    ) {
        SectionLabel("Calendars")
        state.calendars.forEach { calendar ->
            LegendRow(
                label = calendar.name,
                color = calendar.color,
                isChecked = calendar.isVisible,
                onCheckedChange = { isVisible ->
                    onEvent(CalendarUiEvent.SetCalendarVisible(calendar.id, isVisible))
                },
            )
        }

        if (state.categories.isNotEmpty()) {
            Spacer()
            SectionLabel("Categories")
            state.categories.forEach { category ->
                LegendRow(
                    label = category.name,
                    color = category.color,
                    isChecked = state.isCategoryVisible(category.id),
                    onCheckedChange = { isVisible ->
                        onEvent(CalendarUiEvent.SetCategoryVisible(category.id, isVisible))
                    },
                )
            }
        }

        Spacer()
        SectionLabel("In the grid")
        // The shading in the grid had no explanation anywhere, which made it read as something being
        // wrong rather than as the working day. Naming it here is the cheapest possible fix.
        WorkingHoursKey(state)
        SwatchRow(label = "Focus block", color = CalioTheme.colors.focusBlock)
        SwatchRow(label = "Buffer", color = CalioTheme.colors.bufferBlock.copy(alpha = 0.35f))
        SwatchRow(label = "Now", color = CalioTheme.colors.nowIndicator)
    }
}

@Composable
private fun Spacer() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(vertical = CalioTheme.spacing.small),
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = CalioTheme.spacing.extraSmall),
    )
}

@Composable
private fun LegendRow(
    label: String,
    color: CalioColor,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!isChecked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isChecked, onCheckedChange = onCheckedChange)
        Box(
            Modifier
                .size(12.dp)
                .background(
                    // A switched-off entry keeps its colour but loses its weight, so the list still
                    // reads as a legend rather than turning into rows of grey.
                    color = Color(color.argb.toInt()).copy(alpha = if (isChecked) 1f else 0.35f),
                    shape = CircleShape,
                ),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isChecked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.outline
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = CalioTheme.spacing.small),
        )
    }
}

@Composable
private fun WorkingHoursKey(state: CalendarUiState) {
    val window = state.workingHours.windowFor(state.anchor.dayOfWeek)
        ?: state.workingHours.days.values.firstOrNull()

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The swatches are drawn exactly the way the grid draws them: the surface with the same
        // tints on top, so the key and the thing it explains can never diverge.
        Column {
            Box(
                Modifier
                    .width(14.dp)
                    .height(11.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                    .background(CalioTheme.colors.workingHoursBand, RoundedCornerShape(2.dp)),
            )
            Box(
                Modifier
                    .width(14.dp)
                    .height(11.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(2.dp))
                    .background(CalioTheme.colors.nonWorkingHours, RoundedCornerShape(2.dp)),
            )
        }
        Column(Modifier.padding(start = CalioTheme.spacing.small)) {
            Text(
                text = "Working hours",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = window?.let { "${it.start.clockLabel()} to ${it.endExclusive.clockLabel()}" }
                    ?: "No working hours set",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Hours outside them are dimmed.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun SwatchRow(label: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).background(color, CircleShape))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = CalioTheme.spacing.small),
        )
    }
}
