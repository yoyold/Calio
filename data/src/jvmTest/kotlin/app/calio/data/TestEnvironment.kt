package app.calio.data

import app.calio.data.repository.CalendarRepositoryImpl
import app.calio.data.repository.CategoryRepositoryImpl
import app.calio.data.repository.EventRepositoryImpl
import app.calio.data.repository.SearchRepositoryImpl
import app.calio.data.repository.SettingsRepositoryImpl
import app.calio.data.repository.TaskRepositoryImpl
import app.calio.data.sync.HybridLogicalClock
import app.calio.database.CalioDatabase
import app.calio.model.AuditFields
import app.calio.model.CalendarId
import app.calio.model.CalioColor
import app.calio.model.DeviceId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.EventTimeRange
import app.calio.model.RecurrenceRule
import app.calio.model.Revision
import app.calio.model.Task
import app.calio.model.TaskId
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import java.util.Properties
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import app.calio.model.Calendar as CalendarEntity

internal val berlin = TimeZone.of("Europe/Berlin")

internal val testDevice = DeviceId("device-a")

/** A clock the test moves by hand, so nothing depends on how fast the machine runs. */
internal class TestClock(private var current: Instant = Instant.parse("2026-07-25T10:00:00Z")) : Clock {
    override fun now(): Instant = current

    fun advanceBy(duration: Duration) {
        current += duration
    }
}

/**
 * A complete data layer wired against an in-memory database.
 *
 * The repositories are exercised against the real schema and the real SQL rather than against
 * mocks, so a test failure means the behaviour is wrong, not that a stub drifted.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class TestEnvironment(
    val clock: TestClock = TestClock(),
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
    deviceId: DeviceId = testDevice,
) : AutoCloseable {

    private val driver = JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties(),
        schema = CalioDatabase.Schema,
    )

    val database: CalioDatabase = CalioDatabase(driver)

    init {
        driver.execute(identifier = null, sql = "PRAGMA foreign_keys = ON;", parameters = 0)
    }

    val revisions = HybridLogicalClock(database, deviceId, clock)
    val calendars = CalendarRepositoryImpl(database, revisions, dispatcher, clock)
    val categories = CategoryRepositoryImpl(database, revisions, dispatcher, clock)
    val events = EventRepositoryImpl(database, revisions, dispatcher, clock)
    val tasks = TaskRepositoryImpl(database, revisions, dispatcher, clock)
    val search = SearchRepositoryImpl(database, dispatcher)
    val settings = SettingsRepositoryImpl(database, dispatcher)

    fun pendingChanges() = database.syncQueries.selectPendingChanges(limit = 100).executeAsList()

    override fun close() = driver.close()
}

private val seedAudit = AuditFields(
    createdAt = Instant.parse("2026-07-01T08:00:00Z"),
    updatedAt = Instant.parse("2026-07-01T08:00:00Z"),
    revision = Revision.of(1_751_356_800_000, 0, testDevice),
    originDevice = testDevice,
)

internal fun calendar(
    id: String = "calendar-1",
    name: String = "Work",
    isVisible: Boolean = true,
): CalendarEntity = CalendarEntity(
    id = CalendarId(id),
    name = name,
    color = CalioColor(0xFF2196F3),
    audit = seedAudit,
    isVisible = isVisible,
)

internal fun event(
    id: String = "event-1",
    calendarId: String = "calendar-1",
    title: String = "Standup",
    start: LocalDateTime = LocalDateTime(2026, 8, 3, 9, 0),
    endExclusive: LocalDateTime = LocalDateTime(2026, 8, 3, 9, 30),
    recurrence: RecurrenceRule? = null,
): Event = Event(
    id = EventId(id),
    calendarId = CalendarId(calendarId),
    title = title,
    timeRange = EventTimeRange.Zoned(start, endExclusive, berlin),
    audit = seedAudit,
    recurrence = recurrence,
)

internal fun task(
    id: String = "task-1",
    title: String = "Write the report",
): Task = Task(
    id = TaskId(id),
    title = title,
    audit = seedAudit,
)
