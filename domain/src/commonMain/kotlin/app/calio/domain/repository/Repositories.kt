package app.calio.domain.repository

import app.calio.datetime.InstantRange
import app.calio.model.AppSettings
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.Category
import app.calio.model.CategoryId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.RecurrenceOverride
import app.calio.model.Task
import app.calio.model.TaskId
import kotlinx.datetime.LocalDateTime
import kotlinx.coroutines.flow.Flow

/**
 * The contracts the domain layer needs from storage.
 *
 * They are declared here, in the layer that uses them, and implemented in the data layer. That is
 * what points the dependency inwards: a use case can be tested against a fake, and the storage
 * technology can be replaced without the domain noticing.
 *
 * Reads are `Flow` because the user interface must update when data changes rather than poll for it.
 * Writes are `suspend` and take whole entities; assembling an entity from user input is the caller's
 * job, so a repository can never see a half-built object.
 */
interface CalendarRepository {
    fun observeAll(): Flow<List<Calendar>>
    fun observeVisible(): Flow<List<Calendar>>
    suspend fun byId(id: CalendarId): Calendar?
    suspend fun upsert(calendar: Calendar)
    suspend fun setVisible(id: CalendarId, isVisible: Boolean)
    suspend fun setDefault(id: CalendarId)
    suspend fun delete(id: CalendarId)
}

interface CategoryRepository {
    fun observeAll(): Flow<List<Category>>
    suspend fun byId(id: CategoryId): Category?
    suspend fun upsert(category: Category)

    /** Built-in categories cannot be deleted, so an event can never point at a missing category. */
    suspend fun delete(id: CategoryId)
}

interface EventRepository {
    /**
     * Every event that can possibly appear in [window], including series whose occurrences have to
     * be expanded. Passing null for [calendarIds] means "whatever the user currently has visible".
     */
    fun observeInRange(window: InstantRange, calendarIds: Set<CalendarId>? = null): Flow<List<Event>>

    fun observeById(id: EventId): Flow<Event?>
    suspend fun byId(id: EventId): Event?
    suspend fun upsert(event: Event)
    suspend fun delete(id: EventId)

    suspend fun overridesFor(id: EventId): List<RecurrenceOverride>

    /**
     * Every exception, grouped by the series it belongs to.
     *
     * Exceptions are rare compared to events, so they are delivered as a whole rather than per
     * window. That keeps the subscription alive while the user pages through the calendar.
     */
    fun observeAllOverrides(): Flow<Map<EventId, List<RecurrenceOverride>>>

    suspend fun upsertOverride(override: RecurrenceOverride)
    suspend fun removeOverride(id: EventId, originalStart: LocalDateTime)
}

interface TaskRepository {
    /** Every living task, parents and subtasks alike, for a list that groups them itself. */
    fun observeAll(): Flow<List<Task>>

    fun observeTopLevel(): Flow<List<Task>>
    fun observeSubtasks(parentId: TaskId): Flow<List<Task>>
    fun observeDueInRange(window: InstantRange): Flow<List<Task>>
    suspend fun byId(id: TaskId): Task?
    suspend fun upsert(task: Task)
    suspend fun setCompleted(id: TaskId, isCompleted: Boolean)
    suspend fun delete(id: TaskId)
}

/**
 * The user's settings, read and written as a whole.
 *
 * Reading returns the defaults until something has been written, so no screen has to deal with the
 * question of whether the application has been configured yet.
 */
interface SettingsRepository {
    fun observe(): Flow<AppSettings>
    suspend fun update(settings: AppSettings)
}

/** A single search entry point, because the index spans events and tasks alike. */
interface SearchRepository {
    suspend fun search(query: String, limit: Int = DEFAULT_SEARCH_LIMIT): List<SearchHit>

    companion object {
        const val DEFAULT_SEARCH_LIMIT: Int = 50
    }
}

sealed interface SearchHit {
    data class EventHit(val event: Event) : SearchHit
    data class TaskHit(val task: Task) : SearchHit
}
