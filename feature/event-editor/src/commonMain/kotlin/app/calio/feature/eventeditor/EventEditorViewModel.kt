package app.calio.feature.eventeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.calio.datetime.dayWindowIn
import app.calio.datetime.today
import app.calio.domain.planning.ConflictDetector
import app.calio.domain.recurrence.EventOccurrence
import app.calio.domain.recurrence.RecurrenceExpander
import app.calio.domain.repository.CalendarRepository
import app.calio.domain.repository.CategoryRepository
import app.calio.domain.repository.EventRepository
import app.calio.model.AuditFields
import app.calio.model.Calendar
import app.calio.model.Category
import app.calio.model.DeviceId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.EventTimeRange
import app.calio.model.OverrideType
import app.calio.model.RecurrenceOverride
import app.calio.model.ReminderId
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What the editor was opened for. */
sealed interface EditorTarget {
    data class New(val date: LocalDate, val time: LocalTime? = null) : EditorTarget

    /**
     * [occurrenceStart] identifies which entry of a series was tapped. Without it the editor could
     * not offer to remove a single occurrence, only the whole series.
     */
    data class Edit(val eventId: EventId, val occurrenceStart: LocalDateTime? = null) : EditorTarget
}

enum class DeleteScope { Occurrence, Series }

data class EventEditorUiState(
    val draft: EventDraft,
    val calendars: List<Calendar> = emptyList(),
    val categories: List<Category> = emptyList(),
    val problems: Set<DraftProblem> = emptySet(),
    val conflicts: List<EventOccurrence> = emptyList(),
    val isSeries: Boolean = false,
    val canRemoveSingleOccurrence: Boolean = false,
    val isFinished: Boolean = false,
) {
    val isNew: Boolean get() = draft.isNew
    val canSave: Boolean get() = problems.isEmpty()
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

sealed interface EventEditorUiEvent {
    data class DraftChanged(val draft: EventDraft) : EventEditorUiEvent
    data object AddReminder : EventEditorUiEvent
    data class RemoveReminder(val id: ReminderId) : EventEditorUiEvent
    data object Save : EventEditorUiEvent
    data class Delete(val scope: DeleteScope) : EventEditorUiEvent
}

/**
 * Holds the draft while it is being edited and writes it once.
 *
 * Conflicts are reported as the user types rather than on save. A warning that only appears after
 * the fact is a complaint; one that appears while the time is being chosen is information, and it
 * never blocks saving — double booking is sometimes exactly what was intended.
 */
@OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)
class EventEditorViewModel(
    private val events: EventRepository,
    private val calendars: CalendarRepository,
    private val categories: CategoryRepository,
    private val expander: RecurrenceExpander,
    private val conflictDetector: ConflictDetector,
    private val target: EditorTarget,
    private val zone: TimeZone,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val draft = MutableStateFlow(emptyDraft())
    private val finished = MutableStateFlow(false)

    private var existing: Event? = null

    val state: StateFlow<EventEditorUiState> = combine(
        draft,
        calendars.observeVisible(),
        categories.observeAll(),
        conflictsForDraft(),
        finished,
    ) { current, availableCalendars, availableCategories, conflicts, isFinished ->
        EventEditorUiState(
            draft = current,
            calendars = availableCalendars,
            categories = availableCategories,
            problems = current.problems(),
            conflicts = conflicts,
            isSeries = existing?.isRecurring == true,
            canRemoveSingleOccurrence = existing?.isRecurring == true && occurrenceStart() != null,
            isFinished = isFinished,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = EventEditorUiState(draft.value),
    )

    init {
        viewModelScope.launch { load() }
    }

    fun onEvent(event: EventEditorUiEvent) {
        when (event) {
            is EventEditorUiEvent.DraftChanged -> draft.value = event.draft

            EventEditorUiEvent.AddReminder -> draft.update { current ->
                val used = current.reminders.map { it.leadMinutes }.toSet()
                val lead = REMINDER_PRESETS.firstOrNull { it !in used } ?: REMINDER_PRESETS.last()
                current.copy(
                    reminders = current.reminders + ReminderDraft(ReminderId(newId()), lead),
                )
            }

            is EventEditorUiEvent.RemoveReminder -> draft.update { current ->
                current.copy(reminders = current.reminders.filterNot { it.id == event.id })
            }

            EventEditorUiEvent.Save -> viewModelScope.launch { save() }

            is EventEditorUiEvent.Delete -> viewModelScope.launch { delete(event.scope) }
        }
    }

    private suspend fun load() {
        val available = calendars.observeVisible().first()
        val defaultCalendar = available.firstOrNull { it.isDefault } ?: available.firstOrNull()

        when (target) {
            is EditorTarget.New -> draft.update { current ->
                current.copy(calendarId = current.calendarId ?: defaultCalendar?.id)
            }

            is EditorTarget.Edit -> {
                val loaded = events.byId(target.eventId) ?: return
                existing = loaded
                draft.value = loaded.toDraft()
            }
        }
    }

    private suspend fun save() {
        val current = draft.value
        if (!current.isValid) return

        val id = current.eventId ?: EventId(newId())
        val now = clock.now()
        val audit = existing?.audit?.copy(updatedAt = now)
            ?: AuditFields.forNewEntity(now, DEVICE_PLACEHOLDER)

        // The repository stamps the revision and the device on write, so the audit passed here only
        // has to carry the creation time.
        events.upsert(current.toEvent(existing = existing, id = id, audit = audit))
        finished.value = true
    }

    private suspend fun delete(scope: DeleteScope) {
        val id = draft.value.eventId ?: return

        when (scope) {
            DeleteScope.Series -> events.delete(id)
            DeleteScope.Occurrence -> {
                val start = occurrenceStart() ?: return events.delete(id)
                events.upsertOverride(
                    RecurrenceOverride(
                        eventId = id,
                        originalStart = start,
                        type = OverrideType.CANCELLED,
                    ),
                )
            }
        }

        finished.value = true
    }

    /**
     * Appointments the draft would collide with, recomputed whenever the draft moves.
     *
     * The event being edited is filtered out: an appointment always overlaps itself, and reporting
     * that would make the warning permanent.
     */
    private fun conflictsForDraft(): Flow<List<EventOccurrence>> = draft.flatMapLatest { current ->
        val window = current.startDate.dayWindowIn(zone)

        events.observeInRange(window).map { candidates ->
            val others = candidates
                .filterNot { it.id == current.eventId }
                .flatMap { expander.expand(it, window) }

            val draftOccurrence = current.asOccurrence() ?: return@map emptyList()
            conflictDetector.conflictsFor(draftOccurrence, others)
        }
    }

    private fun EventDraft.asOccurrence(): EventOccurrence? {
        if (!isValid) return null
        val range = toTimeRange()
        val event = toEvent(
            existing = existing,
            id = eventId ?: EventId(DRAFT_ID),
            audit = AuditFields.forNewEntity(clock.now(), DEVICE_PLACEHOLDER),
        )

        return EventOccurrence(
            event = event,
            originalStart = when (range) {
                is EventTimeRange.Zoned -> range.start
                is EventTimeRange.AllDay -> LocalDateTime(range.startDate, LocalTime(0, 0))
            },
            timeRange = range,
        )
    }

    private fun occurrenceStart(): LocalDateTime? = (target as? EditorTarget.Edit)?.occurrenceStart

    private fun emptyDraft(): EventDraft {
        val date = (target as? EditorTarget.New)?.date ?: clock.today(zone)
        val start = (target as? EditorTarget.New)?.time ?: LocalTime(9, 0)
        val end = LocalTime((start.hour + 1).coerceAtMost(23), start.minute)

        return EventDraft(
            startDate = date,
            startTime = start,
            endDate = date,
            endTime = end,
            timeZone = zone,
        )
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        const val DRAFT_ID = "draft"

        /** The audit device is a placeholder; the repository replaces it when it writes. */
        val DEVICE_PLACEHOLDER = DeviceId("pending")

        val REMINDER_PRESETS = listOf(10, 30, 60, 24 * 60)

        fun newId(): String = Uuid.random().toString()
    }
}
