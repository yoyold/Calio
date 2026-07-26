package app.calio.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import app.calio.designsystem.CalioTheme
import app.calio.model.AppSettings
import app.calio.model.ThemeMode
import app.calio.feature.calendar.CalendarScreen
import app.calio.feature.calendar.CalendarViewModel
import app.calio.feature.eventeditor.EditorTarget
import app.calio.feature.eventeditor.EventEditorScreen
import app.calio.feature.eventeditor.EventEditorViewModel
import app.calio.feature.search.SearchScreen
import app.calio.feature.settings.SettingsScreen
import app.calio.feature.settings.SettingsViewModel
import app.calio.feature.search.SearchViewModel
import app.calio.feature.tasks.TasksScreen
import app.calio.feature.tasks.TasksViewModel
import app.calio.shared.ui.AppDestination
import app.calio.shared.ui.AppShell
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
    systemInDarkTheme: Boolean = isSystemInDarkTheme(),
) {
    var destination by remember { mutableStateOf(AppDestination.Calendar) }
    var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }

    val settings by container.settings.observe().collectAsState(AppSettings())

    LaunchedEffect(container) {
        DefaultDataSeeder(container.calendars, container.categories, container.deviceId).seedIfEmpty()
    }

    val useDarkTheme = when (settings.themeMode) {
        ThemeMode.SYSTEM -> systemInDarkTheme
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    CalioTheme(useDarkTheme = useDarkTheme) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AppShell(selected = destination, onSelect = { destination = it }) { contentModifier ->
                when (destination) {
                    AppDestination.Calendar -> CalendarScreen(
                        viewModel = rememberCalendarViewModel(container),
                        onCreateEvent = { date -> editorTarget = EditorTarget.New(date) },
                        onOpenEvent = { id, start -> editorTarget = EditorTarget.Edit(id, start) },
                        modifier = contentModifier,
                    )

                    AppDestination.Tasks -> TasksScreen(
                        viewModel = rememberTasksViewModel(container),
                        modifier = contentModifier,
                    )

                    AppDestination.Search -> SearchScreen(
                        viewModel = rememberSearchViewModel(container),
                        onOpenEvent = { id -> editorTarget = EditorTarget.Edit(id) },
                        // A result only knows which task it is, not where it sits in the list, so
                        // this hands over to the task screen rather than pretending to scroll to it.
                        onOpenTask = { destination = AppDestination.Tasks },
                        modifier = contentModifier,
                    )

                    AppDestination.Settings -> SettingsScreen(
                        viewModel = rememberSettingsViewModel(container),
                        modifier = contentModifier,
                    )
                }
            }

            editorTarget?.let { target ->
                EventEditorDialog(
                    container = container,
                    target = target,
                    onClose = { editorTarget = null },
                )
            }
        }
    }
}

/**
 * The editor is shown over whatever the user was looking at.
 *
 * A dialog rather than a destination of its own, because an editor is a detour: closing it has to
 * return to exactly the day that was on screen, and a separate destination would have to restore
 * that by hand.
 */
@Composable
private fun EventEditorDialog(
    container: CalioContainer,
    target: EditorTarget,
    onClose: () -> Unit,
) {
    Dialog(
        onDismissRequest = onClose,
        // The platform default width is too narrow for a form and cannot be widened from inside.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 640.dp)
                .padding(CalioTheme.spacing.large),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
        ) {
            Box(Modifier.fillMaxWidth()) {
                EventEditorScreen(
                    viewModel = rememberEventEditorViewModel(container, target),
                    onClose = onClose,
                )
            }
        }
    }
}

/**
 * Builds the view models from the container.
 *
 * The factories sit here, in the composition root, so a feature module never learns which repository
 * implementations exist — it only ever sees the contracts it was compiled against.
 */
@Composable
private fun rememberCalendarViewModel(container: CalioContainer): CalendarViewModel = viewModel {
    CalendarViewModel(
        events = container.events,
        calendars = container.calendars,
        categories = container.categories,
        expander = container.recurrenceExpander,
        layout = container.overlapLayout,
        settings = container.settings,
        zone = TimeZone.currentSystemDefault(),
    )
}

@Composable
private fun rememberSettingsViewModel(container: CalioContainer): SettingsViewModel = viewModel {
    SettingsViewModel(
        settings = container.settings,
        calendars = container.calendars,
        categories = container.categories,
    )
}

@Composable
private fun rememberSearchViewModel(container: CalioContainer): SearchViewModel = viewModel {
    SearchViewModel(
        search = container.search,
        calendars = container.calendars,
        categories = container.categories,
    )
}

@Composable
private fun rememberTasksViewModel(container: CalioContainer): TasksViewModel = viewModel {
    TasksViewModel(
        tasks = container.tasks,
        categories = container.categories,
        zone = TimeZone.currentSystemDefault(),
    )
}

@Composable
private fun rememberEventEditorViewModel(
    container: CalioContainer,
    target: EditorTarget,
): EventEditorViewModel = viewModel(key = target.toString()) {
    EventEditorViewModel(
        events = container.events,
        calendars = container.calendars,
        categories = container.categories,
        expander = container.recurrenceExpander,
        conflictDetector = container.conflictDetector,
        target = target,
        zone = TimeZone.currentSystemDefault(),
    )
}
