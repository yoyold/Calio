package app.calio.desktop

import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.calio.database.JvmDatabaseDriverFactory
import app.calio.shared.CalioApp
import app.calio.shared.CalioContainer
import kotlinx.coroutines.Dispatchers
import java.nio.file.Files
import java.nio.file.Path

/**
 * Desktop entry point.
 *
 * Its whole job is to obtain a window and a database file. Everything the user sees comes from the
 * shared module, so the desktop build cannot drift away from the Android one by accident.
 */
fun main() = application {
    val container = remember {
        CalioContainer(
            driverFactory = JvmDatabaseDriverFactory(databaseFile().toString()),
            dispatcher = Dispatchers.IO,
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Calio",
        state = rememberWindowState(size = DpSize(1_180.dp, 820.dp)),
    ) {
        CalioApp(container)
    }
}

/**
 * The database lives in the user's home directory rather than next to the executable, so an
 * installation into a read-only location still works and an update never touches the data.
 */
private fun databaseFile(): Path {
    val directory = Path.of(System.getProperty("user.home"), ".calio")
    Files.createDirectories(directory)
    return directory.resolve("calio.db")
}
