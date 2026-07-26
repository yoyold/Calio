package app.calio.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.calio.designsystem.CalioTheme
import app.calio.shared.ui.AppDestination
import app.calio.shared.ui.AppShell
import app.calio.shared.ui.OverviewScreen
import app.calio.shared.ui.PlaceholderScreen

/**
 * The root of the user interface, shared by both applications.
 *
 * The platform entry points differ only in how they obtain a window and a database file; everything
 * the user sees starts here, which is what keeps the two builds from diverging.
 */
@Composable
fun CalioApp(
    container: CalioContainer,
    useDarkTheme: Boolean = isSystemInDarkTheme(),
) {
    var destination by remember { mutableStateOf(AppDestination.Calendar) }

    LaunchedEffect(container) {
        DefaultDataSeeder(container.calendars, container.categories, container.deviceId).seedIfEmpty()
    }

    CalioTheme(useDarkTheme = useDarkTheme) {
        Surface(Modifier) {
            AppShell(selected = destination, onSelect = { destination = it }) { contentModifier ->
                when (destination) {
                    AppDestination.Calendar -> OverviewScreen(container, contentModifier)
                    else -> PlaceholderScreen(destination.label, contentModifier)
                }
            }
        }
    }
}
