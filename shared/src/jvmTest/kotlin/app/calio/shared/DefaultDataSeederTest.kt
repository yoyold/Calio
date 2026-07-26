package app.calio.shared

import app.calio.database.CalioDatabase
import app.calio.database.DatabaseDriverFactory
import app.calio.model.CalendarId
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.Properties
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The composition root and the first-start behaviour, exercised together.
 *
 * This is the seam where an application either comes up or does not, and none of the layers below it
 * can catch a mistake made here: a container that wires the wrong repository still compiles.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultDataSeederTest {

    private val driver = JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties(),
        schema = CalioDatabase.Schema,
    )

    private val container = CalioContainer(
        driverFactory = DatabaseDriverFactory { driver },
        dispatcher = UnconfinedTestDispatcher(),
    )

    private fun seeder() = DefaultDataSeeder(container.calendars, container.categories, container.deviceId)

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun `a fresh installation gets a default calendar and the built in categories`() = runTest {
        seeder().seedIfEmpty()

        val calendars = container.calendars.observeAll().first()
        val categories = container.categories.observeAll().first()

        assertEquals(1, calendars.size)
        assertTrue(calendars.single().isDefault)
        assertEquals(
            listOf("Work", "Private", "Family", "Study", "Leisure"),
            categories.map { it.name },
        )
        assertTrue(categories.all { it.isBuiltIn })
    }

    @Test
    fun `seeding twice does not duplicate anything`() = runTest {
        seeder().seedIfEmpty()
        seeder().seedIfEmpty()

        assertEquals(1, container.calendars.observeAll().first().size)
        assertEquals(5, container.categories.observeAll().first().size)
    }

    @Test
    fun `an installation that already has calendars is left untouched`() = runTest {
        // A device that has synchronised already receives its calendars from elsewhere. Seeding
        // again would create duplicates that no merge could undo.
        container.calendars.upsert(
            app.calio.model.Calendar(
                id = CalendarId("from-another-device"),
                name = "Work",
                color = app.calio.model.CalioColor(0xFF000000),
                audit = app.calio.model.AuditFields.forNewEntity(
                    now = kotlin.time.Clock.System.now(),
                    deviceId = container.deviceId,
                ),
            ),
        )

        seeder().seedIfEmpty()

        assertEquals(listOf("from-another-device"), container.calendars.observeAll().first().map { it.id.value })
    }

    @Test
    fun `the device id is stable across containers on the same database`() {
        val second = CalioContainer(
            driverFactory = DatabaseDriverFactory { driver },
            dispatcher = UnconfinedTestDispatcher(),
        )

        assertEquals(container.deviceId, second.deviceId)
        assertTrue(container.deviceId.value.isNotBlank())
    }

    @Test
    fun `seeded data is reported for synchronisation`() = runTest {
        seeder().seedIfEmpty()

        val changes = container.database.syncQueries.selectPendingChanges(limit = 100).executeAsList()

        assertEquals(1, changes.count { it.entity_type == "CALENDAR" })
        assertEquals(5, changes.count { it.entity_type == "CATEGORY" })
    }
}
