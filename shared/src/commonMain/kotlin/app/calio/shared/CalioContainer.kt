package app.calio.shared

import app.calio.data.repository.CalendarRepositoryImpl
import app.calio.data.repository.CategoryRepositoryImpl
import app.calio.data.repository.EventRepositoryImpl
import app.calio.data.repository.SearchRepositoryImpl
import app.calio.data.repository.SettingsRepositoryImpl
import app.calio.data.repository.TaskRepositoryImpl
import app.calio.data.sync.HybridLogicalClock
import app.calio.database.CalioDatabase
import app.calio.database.DatabaseDriverFactory
import app.calio.database.createCalioDatabase
import app.calio.domain.planning.BufferPlanner
import app.calio.domain.planning.ConflictDetector
import app.calio.domain.planning.FreeSlotFinder
import app.calio.domain.planning.OverlapLayoutCalculator
import app.calio.domain.recurrence.RecurrenceExpander
import app.calio.domain.repository.CalendarRepository
import app.calio.domain.repository.CategoryRepository
import app.calio.domain.repository.EventRepository
import app.calio.domain.repository.SearchRepository
import app.calio.domain.repository.SettingsRepository
import app.calio.domain.repository.TaskRepository
import app.calio.domain.sync.RevisionSource
import app.calio.model.DeviceId
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The composition root.
 *
 * This is the only place in the project that sees both the contracts and the implementations. Every
 * other module depends on interfaces alone, which is what keeps features testable and lets storage,
 * synchronisation or the clock be replaced by changing a single line here.
 *
 * The object graph is built by hand rather than by a dependency injection framework. At this size a
 * constructor call is easier to follow than a container, it fails at compile time instead of at
 * start-up, and the wiring stays visible in one screen of code.
 */
class CalioContainer(
    driverFactory: DatabaseDriverFactory,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clock: Clock = Clock.System,
) {

    val database: CalioDatabase = createCalioDatabase(driverFactory)

    /**
     * The identity of this installation.
     *
     * It is written to the synchronisation state the first time the database is opened and read back
     * afterwards, so it survives restarts without a separate settings store. It has to be stable:
     * it is the tiebreaker that makes revision ordering total across devices.
     */
    val deviceId: DeviceId = resolveDeviceId()

    val revisions: RevisionSource = HybridLogicalClock(database, deviceId, clock)

    val calendars: CalendarRepository = CalendarRepositoryImpl(database, revisions, dispatcher, clock)
    val categories: CategoryRepository = CategoryRepositoryImpl(database, revisions, dispatcher, clock)
    val events: EventRepository = EventRepositoryImpl(database, revisions, dispatcher, clock)
    val tasks: TaskRepository = TaskRepositoryImpl(database, revisions, dispatcher, clock)
    val search: SearchRepository = SearchRepositoryImpl(database, dispatcher)
    val settings: SettingsRepository = SettingsRepositoryImpl(database, dispatcher)

    val recurrenceExpander: RecurrenceExpander = RecurrenceExpander()
    val overlapLayout: OverlapLayoutCalculator = OverlapLayoutCalculator()
    val conflictDetector: ConflictDetector = ConflictDetector()
    val freeSlotFinder: FreeSlotFinder = FreeSlotFinder()
    val bufferPlanner: BufferPlanner = BufferPlanner()

    @OptIn(ExperimentalUuidApi::class)
    private fun resolveDeviceId(): DeviceId {
        database.syncQueries.initialiseSyncState(Uuid.random().toString())
        return DeviceId(database.syncQueries.selectSyncState().executeAsOne().device_id)
    }
}
