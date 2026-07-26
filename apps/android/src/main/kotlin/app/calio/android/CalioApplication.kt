package app.calio.android

import android.app.Application
import app.calio.database.AndroidDatabaseDriverFactory
import app.calio.shared.CalioContainer
import kotlinx.coroutines.Dispatchers

/**
 * Holds the object graph for the lifetime of the process.
 *
 * The container opens the database, so it must outlive any single activity: rebuilding it on a
 * rotation would close and reopen the database on every configuration change.
 */
class CalioApplication : Application() {

    lateinit var container: CalioContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = CalioContainer(
            driverFactory = AndroidDatabaseDriverFactory(applicationContext),
            dispatcher = Dispatchers.IO,
        )
    }
}
