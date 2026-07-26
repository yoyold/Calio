package app.calio.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.calio.domain.repository.CalendarRepository
import app.calio.domain.repository.CategoryRepository
import app.calio.domain.repository.SettingsRepository
import app.calio.model.AppSettings
import app.calio.model.AuditFields
import app.calio.model.BufferPolicy
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.CalioColor
import app.calio.model.Category
import app.calio.model.CategoryId
import app.calio.model.DayWindow
import app.calio.model.DeviceId
import app.calio.model.ThemeMode
import app.calio.model.WorkingHours
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class ColorDraft(val id: String?, val name: String, val color: CalioColor) {
    val isNew: Boolean get() = id == null
    val isValid: Boolean get() = name.isNotBlank()
}

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val calendars: List<Calendar> = emptyList(),
    val categories: List<Category> = emptyList(),
    val editingCalendar: ColorDraft? = null,
    val editingCategory: ColorDraft? = null,
) {
    /** The last calendar cannot be removed: every event needs one to live in. */
    val canDeleteCalendar: Boolean get() = calendars.size > 1
}

sealed interface SettingsUiEvent {
    data class SetThemeMode(val mode: ThemeMode) : SettingsUiEvent
    data class SetWeekStart(val day: DayOfWeek) : SettingsUiEvent
    data class SetWorkingDay(val day: DayOfWeek, val window: DayWindow?) : SettingsUiEvent
    data class SetBufferPolicy(val policy: BufferPolicy) : SettingsUiEvent

    data class EditCalendar(val id: CalendarId?) : SettingsUiEvent
    data class EditCategory(val id: CategoryId?) : SettingsUiEvent
    data class DraftChanged(val draft: ColorDraft) : SettingsUiEvent
    data object SaveDraft : SettingsUiEvent
    data object CancelEditing : SettingsUiEvent

    data class MakeDefaultCalendar(val id: CalendarId) : SettingsUiEvent
    data class DeleteCalendar(val id: CalendarId) : SettingsUiEvent
    data class DeleteCategory(val id: CategoryId) : SettingsUiEvent
}

/**
 * Settings, calendars and categories.
 *
 * They share a screen because they share a question — how the calendar is set up — and splitting
 * them would mean three view models coordinating one back stack for a handful of fields.
 */
@OptIn(ExperimentalUuidApi::class)
class SettingsViewModel(
    private val settings: SettingsRepository,
    private val calendars: CalendarRepository,
    private val categories: CategoryRepository,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val editingCalendar = MutableStateFlow<ColorDraft?>(null)
    private val editingCategory = MutableStateFlow<ColorDraft?>(null)

    val state: StateFlow<SettingsUiState> = combine(
        settings.observe(),
        calendars.observeAll(),
        categories.observeAll(),
        editingCalendar,
        editingCategory,
    ) { current, allCalendars, allCategories, calendarDraft, categoryDraft ->
        SettingsUiState(
            settings = current,
            calendars = allCalendars,
            categories = allCategories,
            editingCalendar = calendarDraft,
            editingCategory = categoryDraft,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = SettingsUiState(),
    )

    fun onEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.SetThemeMode -> updateSettings { copy(themeMode = event.mode) }
            is SettingsUiEvent.SetWeekStart -> updateSettings { copy(weekStart = event.day) }

            is SettingsUiEvent.SetWorkingDay -> updateSettings {
                val days = workingHours.days.toMutableMap()
                // A day without a window is a day off, so removing the entry is the whole change.
                if (event.window == null) days.remove(event.day) else days[event.day] = event.window
                copy(workingHours = WorkingHours(days))
            }

            is SettingsUiEvent.SetBufferPolicy -> updateSettings { copy(bufferPolicy = event.policy) }

            is SettingsUiEvent.EditCalendar -> viewModelScope.launch {
                editingCalendar.value = event.id?.let { id ->
                    calendars.byId(id)?.let { ColorDraft(it.id.value, it.name, it.color) }
                } ?: ColorDraft(id = null, name = "", color = nextPaletteColor())
            }

            is SettingsUiEvent.EditCategory -> viewModelScope.launch {
                editingCategory.value = event.id?.let { id ->
                    categories.byId(id)?.let { ColorDraft(it.id.value, it.name, it.color) }
                } ?: ColorDraft(id = null, name = "", color = nextPaletteColor())
            }

            is SettingsUiEvent.DraftChanged -> {
                if (editingCalendar.value != null) editingCalendar.value = event.draft
                if (editingCategory.value != null) editingCategory.value = event.draft
            }

            SettingsUiEvent.SaveDraft -> viewModelScope.launch { saveDraft() }

            SettingsUiEvent.CancelEditing -> {
                editingCalendar.value = null
                editingCategory.value = null
            }

            is SettingsUiEvent.MakeDefaultCalendar -> viewModelScope.launch {
                calendars.setDefault(event.id)
            }

            is SettingsUiEvent.DeleteCalendar -> viewModelScope.launch {
                if (calendars.observeAll().first().size > 1) calendars.delete(event.id)
            }

            is SettingsUiEvent.DeleteCategory -> viewModelScope.launch {
                categories.delete(event.id)
            }
        }
    }

    private fun updateSettings(transform: AppSettings.() -> AppSettings) {
        viewModelScope.launch { settings.update(state.value.settings.transform()) }
    }

    private suspend fun saveDraft() {
        val calendarDraft = editingCalendar.value
        val categoryDraft = editingCategory.value

        when {
            calendarDraft?.isValid == true -> {
                val existing = calendarDraft.id?.let { calendars.byId(CalendarId(it)) }
                calendars.upsert(
                    existing?.copy(name = calendarDraft.name.trim(), color = calendarDraft.color)
                        ?: Calendar(
                            id = CalendarId(Uuid.random().toString()),
                            name = calendarDraft.name.trim(),
                            color = calendarDraft.color,
                            audit = newAudit(),
                            sortOrder = state.value.calendars.size,
                        ),
                )
                editingCalendar.value = null
            }

            categoryDraft?.isValid == true -> {
                val existing = categoryDraft.id?.let { categories.byId(CategoryId(it)) }
                categories.upsert(
                    existing?.copy(name = categoryDraft.name.trim(), color = categoryDraft.color)
                        ?: Category(
                            id = CategoryId(Uuid.random().toString()),
                            name = categoryDraft.name.trim(),
                            color = categoryDraft.color,
                            audit = newAudit(),
                            sortOrder = state.value.categories.size,
                        ),
                )
                editingCategory.value = null
            }
        }
    }

    private fun newAudit() = AuditFields.forNewEntity(clock.now(), DEVICE_PLACEHOLDER)

    /** A new calendar or category opens on a colour that is not already in use, where possible. */
    private fun nextPaletteColor(): CalioColor {
        val used = (state.value.calendars.map { it.color } + state.value.categories.map { it.color })
            .toSet()
        return CalioPalette.firstOrNull { it !in used } ?: CalioPalette.first()
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        val DEVICE_PLACEHOLDER = DeviceId("pending")
    }
}

/** The colours offered for calendars and categories. */
val CalioPalette: List<CalioColor> = listOf(
    CalioColor(0xFF1B6EF3),
    CalioColor(0xFF4CAF50),
    CalioColor(0xFFE91E63),
    CalioColor(0xFF9C27B0),
    CalioColor(0xFFFF9800),
    CalioColor(0xFF00BCD4),
    CalioColor(0xFF795548),
    CalioColor(0xFF607D8B),
)

/** The default window a weekday gets when it is switched on. */
val DefaultWorkingWindow: DayWindow = DayWindow(LocalTime(9, 0), LocalTime(17, 0))
