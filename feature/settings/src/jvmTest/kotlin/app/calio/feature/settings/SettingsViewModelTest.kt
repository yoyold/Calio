package app.calio.feature.settings

import app.calio.model.AppSettings
import app.calio.model.BufferPolicy
import app.calio.model.CalendarId
import app.calio.model.CalioColor
import app.calio.model.CategoryId
import app.calio.model.DayWindow
import app.calio.model.ThemeMode
import app.calio.testing.FakeCalendarRepository
import app.calio.testing.FakeCategoryRepository
import app.calio.testing.FakeSettingsRepository
import app.calio.testing.testCalendar
import app.calio.testing.testCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private class Environment(
        val settings: FakeSettingsRepository,
        val calendars: FakeCalendarRepository,
        val categories: FakeCategoryRepository,
        val viewModel: SettingsViewModel,
    )

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.environment(
        initial: AppSettings = AppSettings(),
        calendars: FakeCalendarRepository = FakeCalendarRepository(listOf(testCalendar)),
        categories: FakeCategoryRepository = FakeCategoryRepository(listOf(testCategory)),
    ): Environment {
        val settings = FakeSettingsRepository(initial)
        val viewModel = SettingsViewModel(settings, calendars, categories)
        backgroundScope.launch { viewModel.state.collect { } }
        return Environment(settings, calendars, categories, viewModel)
    }

    @Test
    fun `the screen opens on the stored settings`() = runTest {
        val environment = environment(AppSettings(themeMode = ThemeMode.DARK))

        val state = environment.viewModel.state.first { it.settings.themeMode == ThemeMode.DARK }

        assertEquals(DayOfWeek.MONDAY, state.settings.weekStart)
    }

    @Test
    fun `the theme is written straight through`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }

        environment.viewModel.onEvent(SettingsUiEvent.SetThemeMode(ThemeMode.LIGHT))

        environment.viewModel.state.first { it.settings.themeMode == ThemeMode.LIGHT }
        assertEquals(ThemeMode.LIGHT, environment.settings.stored.themeMode)
    }

    @Test
    fun `the week start is written straight through`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }

        environment.viewModel.onEvent(SettingsUiEvent.SetWeekStart(DayOfWeek.SUNDAY))

        environment.viewModel.state.first { it.settings.weekStart == DayOfWeek.SUNDAY }
        assertEquals(DayOfWeek.SUNDAY, environment.settings.stored.weekStart)
    }

    @Test
    fun `switching a weekday off removes its window rather than hiding one`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }

        environment.viewModel.onEvent(SettingsUiEvent.SetWorkingDay(DayOfWeek.MONDAY, null))

        val state = environment.viewModel.state.first {
            DayOfWeek.MONDAY !in it.settings.workingHours.days
        }
        assertFalse(state.settings.workingHours.isWorkingDay(DayOfWeek.MONDAY))
        assertNull(environment.settings.stored.workingHours.windowFor(DayOfWeek.MONDAY))
    }

    @Test
    fun `a weekday can be given its own hours`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }
        val window = DayWindow(LocalTime(8, 0), LocalTime(13, 30))

        environment.viewModel.onEvent(SettingsUiEvent.SetWorkingDay(DayOfWeek.SATURDAY, window))

        val state = environment.viewModel.state.first {
            it.settings.workingHours.windowFor(DayOfWeek.SATURDAY) != null
        }
        assertEquals(window, state.settings.workingHours.windowFor(DayOfWeek.SATURDAY))
        // The other days are untouched.
        assertEquals(
            DayWindow(LocalTime(9, 0), LocalTime(17, 0)),
            state.settings.workingHours.windowFor(DayOfWeek.MONDAY),
        )
    }

    @Test
    fun `the buffer policy is written straight through`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }
        val policy = BufferPolicy(isEnabled = true, defaultMinutes = 15, maximumGapMinutes = 60)

        environment.viewModel.onEvent(SettingsUiEvent.SetBufferPolicy(policy))

        environment.viewModel.state.first { it.settings.bufferPolicy.isEnabled }
        assertEquals(policy, environment.settings.stored.bufferPolicy)
    }

    @Test
    fun `a new calendar opens on an unused colour`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }

        environment.viewModel.onEvent(SettingsUiEvent.EditCalendar(null))

        val draft = environment.viewModel.state.first { it.editingCalendar != null }.editingCalendar!!
        assertTrue(draft.isNew)
        assertTrue(draft.color != testCalendar.color)
        assertTrue(draft.color != testCategory.color)
    }

    @Test
    fun `a calendar without a name cannot be saved`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }
        environment.viewModel.onEvent(SettingsUiEvent.EditCalendar(null))
        environment.viewModel.state.first { it.editingCalendar != null }

        environment.viewModel.onEvent(SettingsUiEvent.SaveDraft)
        testScheduler.runCurrent()

        assertEquals(1, environment.viewModel.state.value.calendars.size)
    }

    @Test
    fun `a new calendar is created with its name and colour`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }
        environment.viewModel.onEvent(SettingsUiEvent.EditCalendar(null))
        val draft = environment.viewModel.state.first { it.editingCalendar != null }.editingCalendar!!

        environment.viewModel.onEvent(
            SettingsUiEvent.DraftChanged(draft.copy(name = "Family", color = CalioColor(0xFF4CAF50))),
        )
        environment.viewModel.onEvent(SettingsUiEvent.SaveDraft)

        val state = environment.viewModel.state.first { it.calendars.size == 2 }
        val created = state.calendars.single { it.name == "Family" }
        assertEquals(CalioColor(0xFF4CAF50), created.color)
        assertNull(state.editingCalendar)
    }

    @Test
    fun `editing a calendar keeps its identity`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }

        environment.viewModel.onEvent(SettingsUiEvent.EditCalendar(testCalendar.id))
        val draft = environment.viewModel.state.first { it.editingCalendar != null }.editingCalendar!!
        environment.viewModel.onEvent(SettingsUiEvent.DraftChanged(draft.copy(name = "Renamed")))
        environment.viewModel.onEvent(SettingsUiEvent.SaveDraft)

        val state = environment.viewModel.state.first { it.calendars.single().name == "Renamed" }
        assertEquals(testCalendar.id, state.calendars.single().id)
    }

    @Test
    fun `cancelling drops the draft`() = runTest {
        val environment = environment()
        environment.viewModel.state.first { it.calendars.isNotEmpty() }
        environment.viewModel.onEvent(SettingsUiEvent.EditCalendar(testCalendar.id))
        environment.viewModel.state.first { it.editingCalendar != null }

        environment.viewModel.onEvent(SettingsUiEvent.CancelEditing)

        val state = environment.viewModel.state.first { it.editingCalendar == null }
        assertEquals("Work", state.calendars.single().name)
    }

    @Test
    fun `the last calendar cannot be deleted`() = runTest {
        // Every event needs a calendar to live in, so removing the only one would leave the next
        // event nowhere to go.
        val environment = environment()
        val state = environment.viewModel.state.first { it.calendars.isNotEmpty() }
        assertFalse(state.canDeleteCalendar)

        environment.viewModel.onEvent(SettingsUiEvent.DeleteCalendar(testCalendar.id))
        testScheduler.runCurrent()

        assertEquals(1, environment.viewModel.state.value.calendars.size)
    }

    @Test
    fun `one of several calendars can be deleted`() = runTest {
        val second = testCalendar.copy(id = CalendarId("calendar-2"), name = "Private")
        val environment = environment(
            calendars = FakeCalendarRepository(listOf(testCalendar, second)),
        )
        environment.viewModel.state.first { it.calendars.size == 2 }

        environment.viewModel.onEvent(SettingsUiEvent.DeleteCalendar(second.id))

        val state = environment.viewModel.state.first { it.calendars.size == 1 }
        assertEquals(testCalendar.id, state.calendars.single().id)
    }

    @Test
    fun `making a calendar the default clears the previous one`() = runTest {
        val second = testCalendar.copy(id = CalendarId("calendar-2"), name = "Private")
        val environment = environment(
            calendars = FakeCalendarRepository(listOf(testCalendar.copy(isDefault = true), second)),
        )
        environment.viewModel.state.first { it.calendars.size == 2 }

        environment.viewModel.onEvent(SettingsUiEvent.MakeDefaultCalendar(second.id))

        val state = environment.viewModel.state.first { cal -> cal.calendars.any { it.id == second.id && it.isDefault } }
        assertEquals(1, state.calendars.count { it.isDefault })
    }

    @Test
    fun `a category can be created and removed`() = runTest {
        val environment = environment(categories = FakeCategoryRepository(emptyList()))
        environment.viewModel.state.first { it.calendars.isNotEmpty() }

        environment.viewModel.onEvent(SettingsUiEvent.EditCategory(null))
        val draft = environment.viewModel.state.first { it.editingCategory != null }.editingCategory!!
        environment.viewModel.onEvent(SettingsUiEvent.DraftChanged(draft.copy(name = "Sport")))
        environment.viewModel.onEvent(SettingsUiEvent.SaveDraft)

        val created = environment.viewModel.state.first { it.categories.isNotEmpty() }.categories.single()
        assertEquals("Sport", created.name)

        environment.viewModel.onEvent(SettingsUiEvent.DeleteCategory(CategoryId(created.id.value)))
        assertTrue(environment.viewModel.state.first { it.categories.isEmpty() }.categories.isEmpty())
    }
}
