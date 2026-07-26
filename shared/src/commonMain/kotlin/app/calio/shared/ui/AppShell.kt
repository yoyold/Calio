package app.calio.shared.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The destinations the application is organised into.
 *
 * Declared as data rather than as a `when` inside the navigation bar, so adding a destination is one
 * entry here instead of an edit in three places.
 */
enum class AppDestination(val label: String, val shortLabel: String) {
    Calendar("Calendar", "Cal"),
    Tasks("Tasks", "Tasks"),
    Search("Search", "Find"),
    Settings("Settings", "More"),
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
        val useRail = maxWidth >= RAIL_BREAKPOINT

        if (useRail) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail {
                    AppDestination.entries.forEach { destination ->
                        NavigationRailItem(
                            selected = destination == selected,
                            onClick = { onSelect(destination) },
                            icon = { Text(destination.shortLabel) },
                            label = { Text(destination.label) },
                        )
                    }
                }
                content(Modifier.fillMaxSize())
            }
        } else {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        AppDestination.entries.forEach { destination ->
                            NavigationBarItem(
                                selected = destination == selected,
                                onClick = { onSelect(destination) },
                                icon = { Text(destination.shortLabel) },
                                label = { Text(destination.label) },
                            )
                        }
                    }
                },
            ) { insets ->
                content(Modifier.fillMaxSize().padding(insets))
            }
        }
    }
}

/** Shown where a feature is not part of the application yet. */
@Composable
fun PlaceholderScreen(title: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        }
    }
}

/** Below this width a rail would take away more room than it gives back. */
private val RAIL_BREAKPOINT = 840.dp
