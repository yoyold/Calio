package app.calio.feature.calendar

import app.calio.datetime.InstantRange
import app.calio.domain.repository.CalendarRepository
import app.calio.domain.repository.CategoryRepository
import app.calio.domain.repository.EventRepository
import app.calio.model.AuditFields
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.CalioColor
import app.calio.model.Category
import app.calio.model.CategoryId
import app.calio.model.DeviceId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.EventTimeRange
import app.calio.model.RecurrenceOverride
import app.calio.model.RecurrenceRule
import app.calio.model.Revision
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.time.Instant

/**
 * Stand-ins for the repository contracts.
 *
 * The view model was written against interfaces declared in the domain layer, so a screen can be
 * driven entirely from memory. That is the point of pointing the dependency inwards: none of this
 * needs a database, a driver or a dispatcher.
 */
internal val testZone = TimeZone.of("Europe/Berlin")

private val testDevice = DeviceId("test-device")

private val testAudit = AuditFields(
    createdAt = Instant.parse("2026-07-01T08:00:00Z"),
    updatedAt = Instant.parse("2026-07-01T08:00:00Z"),
    revision = Revision.of(1_751_356_800_000, 0, testDevice),
    originDevice = testDevice,
)

internal val testCalendar = Calendar(
    id = CalendarId("calendar-1"),
    name = "Work",
    color = CalioColor(0xFF1B6EF3),
    audit = testAudit,
)

internal fun testEvent(
    id: String = "event-1",
    title: String = "Standup",
    start: LocalDateTime,
    endExclusive: LocalDateTime,
    recurrence: RecurrenceRule? = null,
): Event = Event(
    id = EventId(id),
    calendarId = testCalendar.id,
    title = title,
    timeRange = EventTimeRange.Zoned(start, endExclusive, testZone),
    audit = testAudit,
    recurrence = recurrence,
)

internal fun testAllDayEvent(id: String, title: String, range: EventTimeRange.AllDay): Event = Event(
    id = EventId(id),
    calendarId = testCalendar.id,
    title = title,
    timeRange = range,
    audit = testAudit,
)

internal class FakeEventRepository(
    events: List<Event> = emptyList(),
    overrides: Map<EventId, List<RecurrenceOverride>> = emptyMap(),
) : EventRepository {

    private val state = MutableStateFlow(events)
    private val overrideState = MutableStateFlow(overrides)

    override fun observeInRange(window: InstantRange, calendarIds: Set<CalendarId>?): Flow<List<Event>> =
        state.map { all ->
            all.filter { event ->
                val rangeEnd = if (event.recurrence == null) event.timeRange.endUtcExclusive else null
                event.timeRange.startUtc < window.endExclusive &&
                    (rangeEnd == null || rangeEnd > window.start)
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
        overrideState.value = overrideState.value + (
            override.eventId to
                (overrideState.value[override.eventId].orEmpty() + override)
            )
    }

    override suspend fun removeOverride(id: EventId, originalStart: LocalDateTime) {
        overrideState.value = overrideState.value + (
            id to overrideState.value[id].orEmpty().filterNot { it.originalStart == originalStart }
            )
    }
}

internal class FakeCalendarRepository(calendars: List<Calendar> = listOf(testCalendar)) : CalendarRepository {
    private val state = MutableStateFlow(calendars)

    override fun observeAll(): Flow<List<Calendar>> = state
    override fun observeVisible(): Flow<List<Calendar>> = state.map { all -> all.filter { it.isVisible } }
    override suspend fun byId(id: CalendarId): Calendar? = state.value.find { it.id == id }
    override suspend fun upsert(calendar: Calendar) {
        state.value = state.value.filterNot { it.id == calendar.id } + calendar
    }

    override suspend fun setVisible(id: CalendarId, isVisible: Boolean) = Unit
    override suspend fun setDefault(id: CalendarId) = Unit
    override suspend fun delete(id: CalendarId) {
        state.value = state.value.filterNot { it.id == id }
    }
}

internal class FakeCategoryRepository(categories: List<Category> = emptyList()) : CategoryRepository {
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
