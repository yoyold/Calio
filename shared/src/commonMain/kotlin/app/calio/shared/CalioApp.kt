package app.calio.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import app.calio.designsystem.CalioTheme
import app.calio.feature.calendar.CalendarScreen
import app.calio.feature.calendar.CalendarViewModel
import app.calio.shared.ui.AppDestination
import app.calio.shared.ui.AppShell
import app.calio.shared.ui.PlaceholderScreen
import kotlinx.datetime.TimeZone

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
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppShell(selected = destination, onSelect = { destination = it }) { contentModifier ->
                when (destination) {
                    AppDestination.Calendar -> CalendarScreen(
                        viewModel = rememberCalendarViewModel(container),
                        modifier = contentModifier,
                    )

                    else -> PlaceholderScreen(destination, contentModifier)
                }
            }
        }
    }
}

/**
 * Builds the calendar view model from the container.
 *
 * The factory sits here, in the composition root, so the feature module never learns which
 * repository implementations exist — it only ever sees the contracts it was compiled against.
 */
@Composable
private fun rememberCalendarViewModel(container: CalioContainer): CalendarViewModel = viewModel {
    CalendarViewModel(
        events = container.events,
        calendars = container.calendars,
        categories = container.categories,
        expander = container.recurrenceExpander,
        layout = container.overlapLayout,
        zone = TimeZone.currentSystemDefault(),
    )
}
