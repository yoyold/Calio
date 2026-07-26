package app.calio.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.calio.designsystem.CalioTheme

/**
 * The header of a screen.
 *
 * A plain row rather than a top app bar because the calendar needs the full height for its grid, and
 * a bar that collapses on scroll would move the hour rows while the user is reading them.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable () -> Unit = {},
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = CalioTheme.spacing.extraLarge,
                    vertical = CalioTheme.spacing.large,
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                if (subtitle != null) {
                    Text(
                        text = subtitle.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Text(text = title, style = MaterialTheme.typography.headlineMedium)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                actions()
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/**
 * The standard way of saying that there is nothing here.
 *
 * A muted icon above two lines of text, never a blank area: an empty screen without an explanation
 * reads as something that failed to load.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.width(320.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CalioTheme.spacing.small),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
            modifier = Modifier.size(32.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
