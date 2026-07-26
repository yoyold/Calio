package app.calio.shared.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import app.calio.designsystem.CalioTheme
import app.calio.designsystem.icon.CalioIcons

/**
 * The destinations the application is organised into.
 *
 * Declared as data rather than as a `when` inside the navigation bar, so adding a destination is one
 * entry here instead of an edit in three places.
 */
enum class AppDestination(val label: String, val icon: ImageVector) {
    Calendar("Calendar", CalioIcons.Calendar),
    Tasks("Tasks", CalioIcons.Tasks),
    Search("Search", CalioIcons.Search),
    Settings("Settings", CalioIcons.Settings),
}

/**
 * The frame around every screen.
 *
 * Which chrome is shown is decided by the width of the window, not by the platform. A desktop window
 * dragged narrow and a phone in portrait are the same situation, and treating them as one is what
 * keeps the two builds from drifting apart.
 */
@Composable
fun AppShell(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        if (maxWidth >= RAIL_BREAKPOINT) {
            Row(Modifier.fillMaxSize()) {
                CalioNavigationRail(selected, onSelect)
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                content(Modifier.fillMaxSize())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxWidth())
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    AppDestination.entries.forEach { destination ->
                        NavigationBarItem(
                            selected = destination == selected,
                            onClick = { onSelect(destination) },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalioNavigationRail(
    selected: AppDestination,
    onSelect: (AppDestination) -> Unit,
) {
    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        header = {
            Spacer(Modifier.height(CalioTheme.spacing.small))
            AppMark()
            Spacer(Modifier.height(CalioTheme.spacing.small))
        },
    ) {
        Spacer(Modifier.height(CalioTheme.spacing.small))
        AppDestination.entries.forEach { destination ->
            NavigationRailItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
            )
        }
    }
}

/** The wordless mark at the top of the rail. */
@Composable
private fun AppMark() {
    Surface(
        modifier = Modifier.size(36.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.primary,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "C",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

/** Below this width a rail would take away more room than it gives back. */
private val RAIL_BREAKPOINT = 840.dp
