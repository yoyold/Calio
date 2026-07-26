package app.calio.testing

import app.calio.datetime.InstantRange
import app.calio.domain.repository.CalendarRepository
import app.calio.domain.repository.CategoryRepository
import app.calio.domain.repository.EventRepository
import app.calio.domain.repository.SearchHit
import app.calio.domain.repository.SearchRepository
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.Category
import app.calio.model.CategoryId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.RecurrenceOverride
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime

/**
 * In-memory stand-ins for the repository contracts.
 *
 * The view models were written against interfaces declared in the domain layer, so a screen can be
 * driven entirely from memory — no database, no driver, no dispatcher. That is what pointing the
 * dependency inwards buys, and these classes are where the benefit is collected.
 */
class FakeEventRepository(
    events: List<Event> = emptyList(),
    overrides: Map<EventId, List<RecurrenceOverride>> = emptyMap(),
) : EventRepository {

    private val state = MutableStateFlow(events)
    private val overrideState = MutableStateFlow(overrides)

    val stored: List<Event> get() = state.value
    val storedOverrides: Map<EventId, List<RecurrenceOverride>> get() = overrideState.value

    override fun observeInRange(
        window: InstantRange,
        calendarIds: Set<CalendarId>?,
    ): Flow<List<Event>> = state.map { all ->
        all.filter { event ->
            val matchesCalendar = calendarIds == null || event.calendarId in calendarIds
            // A series is kept as long as it started before the window; its occurrences are the
            // expander's business, not the repository's.
            val seriesEnd = if (event.recurrence == null) event.timeRange.endUtcExclusive else null
            matchesCalendar &&
                event.timeRange.startUtc < window.endExclusive &&
                (seriesEnd == null || seriesEnd > window.start)
        }
    }

    override fun observeById(id: EventId): Flow<Event?> = state.map { all -> all.find { it.id == id } }

    override suspend fun byId(id: EventId): Event? = state.value.find { it.id == id }

    override suspend fun upsert(event: Event) {
        state.value = state.value.filterNot { it.id == event.id } + event
    }

    override suspend fun delete(id: EventId) {
        state.value = state.value.filterNot { it.id == id }
    }

    override suspend fun overridesFor(id: EventId): List<RecurrenceOverride> =
        overrideState.value[id].orEmpty()

    override fun observeAllOverrides(): Flow<Map<EventId, List<RecurrenceOverride>>> = overrideState

    override suspend fun upsertOverride(override: RecurrenceOverride) {
        val existing = overrideState.value[override.eventId].orEmpty()
            .filterNot { it.originalStart == override.originalStart }
        overrideState.value = overrideState.value + (override.eventId to existing + override)
    }

    override suspend fun removeOverride(id: EventId, originalStart: LocalDateTime) {
        val remaining = overrideState.value[id].orEmpty().filterNot { it.originalStart == originalStart }
        overrideState.value = overrideState.value + (id to remaining)
    }
}

class FakeCalendarRepository(
    calendars: List<Calendar> = listOf(testCalendar),
) : CalendarRepository {

    private val state = MutableStateFlow(calendars)

    override fun observeAll(): Flow<List<Calendar>> = state
    override fun observeVisible(): Flow<List<Calendar>> = state.map { all -> all.filter { it.isVisible } }
    override suspend fun byId(id: CalendarId): Calendar? = state.value.find { it.id == id }

    override suspend fun upsert(calendar: Calendar) {
        state.value = state.value.filterNot { it.id == calendar.id } + calendar
    }

    override suspend fun setVisible(id: CalendarId, isVisible: Boolean) {
        state.value = state.value.map { if (it.id == id) it.copy(isVisible = isVisible) else it }
    }

    override suspend fun setDefault(id: CalendarId) {
        state.value = state.value.map { it.copy(isDefault = it.id == id) }
    }

    override suspend fun delete(id: CalendarId) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeCategoryRepository(
    categories: List<Category> = emptyList(),
) : CategoryRepository {

    private val state = MutableStateFlow(categories)

    override fun observeAll(): Flow<List<Category>> = state
    override suspend fun byId(id: CategoryId): Category? = state.value.find { it.id == id }

    override suspend fun upsert(category: Category) {
        state.value = state.value.filterNot { it.id == category.id } + category
    }

    override suspend fun delete(id: CategoryId) {
        state.value = state.value.filterNot { it.id == id }
    }
}

class FakeSearchRepository(private val hits: List<SearchHit> = emptyList()) : SearchRepository {
    override suspend fun search(query: String, limit: Int): List<SearchHit> =
        if (query.isBlank()) emptyList() else hits.take(limit)
}
