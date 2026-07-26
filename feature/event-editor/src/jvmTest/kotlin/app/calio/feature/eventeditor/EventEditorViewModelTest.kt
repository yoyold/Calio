package app.calio.feature.eventeditor

import app.calio.domain.planning.ConflictDetector
import app.calio.domain.recurrence.RecurrenceExpander
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.Frequency
import app.calio.model.OverrideType
import app.calio.model.RecurrenceRule
import app.calio.testing.FakeCalendarRepository
import app.calio.testing.FakeCategoryRepository
import app.calio.testing.FakeEventRepository
import app.calio.testing.FixedClock
import app.calio.testing.testCalendar
import app.calio.testing.testEvent
import app.calio.testing.testZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class EventEditorViewModelTest {

    private val clock = FixedClock(Instant.parse("2026-08-05T08:30:00Z"))
    private val august5 = LocalDate(2026, 8, 5)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.editor(
        target: EditorTarget,
        repository: FakeEventRepository = FakeEventRepository(),
    ): Pair<EventEditorViewModel, FakeEventRepository> {
        val viewModel = EventEditorViewModel(
            events = repository,
            calendars = FakeCalendarRepository(listOf(testCalendar.copy(isDefault = true))),
            categories = FakeCategoryRepository(),
            expander = RecurrenceExpander(),
            conflictDetector = ConflictDetector(),
            target = target,
            zone = testZone,
            clock = clock,
        )
        backgroundScope.launch { viewModel.state.collect { } }
        return viewModel to repository
    }

    @Test
    fun `a new event starts on the chosen day in the default calendar`() = runTest {
        val (viewModel, _) = editor(EditorTarget.New(august5, LocalTime(14, 0)))

        val state = viewModel.state.first { it.draft.calendarId != null }

        assertTrue(state.isNew)
        assertEquals(august5, state.draft.startDate)
        assertEquals(LocalTime(14, 0), state.draft.startTime)
        assertEquals(LocalTime(15, 0), state.draft.endTime)
        assertEquals(testCalendar.id, state.draft.calendarId)
    }

    @Test
    fun `a new event cannot be saved without a title`() = runTest {
        val (viewModel, repository) = editor(EditorTarget.New(august5))
        viewModel.state.first { it.draft.calendarId != null }

        assertFalse(viewModel.state.value.canSave)

        viewModel.onEvent(EventEditorUiEvent.Save)
        testScheduler.runCurrent()

        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun `saving writes the event and closes the editor`() = runTest {
        val (viewModel, repository) = editor(EditorTarget.New(august5, LocalTime(14, 0)))
        val loaded = viewModel.state.first { it.draft.calendarId != null }

        viewModel.onEvent(EventEditorUiEvent.DraftChanged(loaded.draft.copy(title = "Review")))
        viewModel.onEvent(EventEditorUiEvent.Save)

        val finished = viewModel.state.first { it.isFinished }
        assertTrue(finished.isFinished)
        assertEquals(listOf("Review"), repository.stored.map { it.title })
        assertEquals(testCalendar.id, repository.stored.single().calendarId)
    }

    @Test
    fun `an existing event opens with its own values`() = runTest {
        val existing = testEvent(
            title = "Quarterly review",
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 10, 30),
        )
        val (viewModel, _) = editor(
            target = EditorTarget.Edit(existing.id),
            repository = FakeEventRepository(listOf(existing)),
        )

        val state = viewModel.state.first { it.draft.title == "Quarterly review" }

        assertFalse(state.isNew)
        assertEquals(LocalTime(9, 0), state.draft.startTime)
        assertEquals(LocalTime(10, 30), state.draft.endTime)
    }

    @Test
    fun `editing an existing event replaces it instead of adding another`() = runTest {
        val existing = testEvent(
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 10, 0),
        )
        val (viewModel, repository) = editor(
            target = EditorTarget.Edit(existing.id),
            repository = FakeEventRepository(listOf(existing)),
        )
        val loaded = viewModel.state.first { it.draft.eventId != null }

        viewModel.onEvent(EventEditorUiEvent.DraftChanged(loaded.draft.copy(title = "Renamed")))
        viewModel.onEvent(EventEditorUiEvent.Save)
        viewModel.state.first { it.isFinished }

        assertEquals(1, repository.stored.size)
        assertEquals("Renamed", repository.stored.single().title)
        assertEquals(existing.id, repository.stored.single().id)
    }

    @Test
    fun `an overlapping appointment is reported while the time is being chosen`() = runTest {
        val existing = testEvent(
            id = "existing",
            title = "Standup",
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 10, 0),
        )
        val (viewModel, _) = editor(
            target = EditorTarget.New(august5),
            repository = FakeEventRepository(listOf(existing)),
        )
        val loaded = viewModel.state.first { it.draft.calendarId != null }

        viewModel.onEvent(
            EventEditorUiEvent.DraftChanged(
                loaded.draft.copy(
                    title = "Interview",
                    startTime = LocalTime(9, 30),
                    endTime = LocalTime(10, 30),
                ),
            ),
        )

        val state = viewModel.state.first { it.hasConflicts }
        assertEquals(listOf("Standup"), state.conflicts.map { it.event.title })
    }

    @Test
    fun `a conflict never blocks saving`() = runTest {
        val existing = testEvent(
            id = "existing",
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 10, 0),
        )
        val (viewModel, repository) = editor(
            target = EditorTarget.New(august5),
            repository = FakeEventRepository(listOf(existing)),
        )
        val loaded = viewModel.state.first { it.draft.calendarId != null }

        viewModel.onEvent(
            EventEditorUiEvent.DraftChanged(
                loaded.draft.copy(title = "Interview", startTime = LocalTime(9, 30)),
            ),
        )
        val conflicted = viewModel.state.first { it.hasConflicts }
        assertTrue(conflicted.canSave)

        viewModel.onEvent(EventEditorUiEvent.Save)
        viewModel.state.first { it.isFinished }

        assertEquals(2, repository.stored.size)
    }

    @Test
    fun `an event does not report a conflict with itself`() = runTest {
        val existing = testEvent(
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 10, 0),
        )
        val (viewModel, _) = editor(
            target = EditorTarget.Edit(existing.id),
            repository = FakeEventRepository(listOf(existing)),
        )

        val state = viewModel.state.first { it.draft.eventId != null }
        testScheduler.runCurrent()

        assertFalse(state.hasConflicts)
    }

    @Test
    fun `reminders are added with distinct presets and can be removed again`() = runTest {
        val (viewModel, _) = editor(EditorTarget.New(august5))
        viewModel.state.first { it.draft.calendarId != null }

        viewModel.onEvent(EventEditorUiEvent.AddReminder)
        viewModel.onEvent(EventEditorUiEvent.AddReminder)
        val withTwo = viewModel.state.first { it.draft.reminders.size == 2 }

        assertEquals(2, withTwo.draft.reminders.map { it.leadMinutes }.distinct().size)

        viewModel.onEvent(EventEditorUiEvent.RemoveReminder(withTwo.draft.reminders.first().id))
        val withOne = viewModel.state.first { it.draft.reminders.size == 1 }

        assertEquals(withTwo.draft.reminders.last().id, withOne.draft.reminders.single().id)
    }

    @Test
    fun `a series is recognised and reports that changes apply to all of it`() = runTest {
        val series = seriesEvent()
        val (viewModel, _) = editor(
            target = EditorTarget.Edit(series.id, LocalDateTime(2026, 8, 5, 9, 0)),
            repository = FakeEventRepository(listOf(series)),
        )

        val state = viewModel.state.first { it.draft.eventId != null }

        assertTrue(state.isSeries)
        assertTrue(state.canRemoveSingleOccurrence)
        assertEquals(RecurrencePreset.Daily, state.draft.recurrence)
    }

    @Test
    fun `removing one occurrence cancels it and leaves the series alone`() = runTest {
        val series = seriesEvent()
        val (viewModel, repository) = editor(
            target = EditorTarget.Edit(series.id, LocalDateTime(2026, 8, 5, 9, 0)),
            repository = FakeEventRepository(listOf(series)),
        )
        viewModel.state.first { it.draft.eventId != null }

        viewModel.onEvent(EventEditorUiEvent.Delete(DeleteScope.Occurrence))
        viewModel.state.first { it.isFinished }

        assertEquals(1, repository.stored.size)
        val override = repository.storedOverrides.getValue(series.id).single()
        assertEquals(OverrideType.CANCELLED, override.type)
        assertEquals(LocalDateTime(2026, 8, 5, 9, 0), override.originalStart)
    }

    @Test
    fun `deleting the series removes the event itself`() = runTest {
        val series = seriesEvent()
        val (viewModel, repository) = editor(
            target = EditorTarget.Edit(series.id, LocalDateTime(2026, 8, 5, 9, 0)),
            repository = FakeEventRepository(listOf(series)),
        )
        viewModel.state.first { it.draft.eventId != null }

        viewModel.onEvent(EventEditorUiEvent.Delete(DeleteScope.Series))
        viewModel.state.first { it.isFinished }

        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun `a single event offers no per occurrence deletion`() = runTest {
        val single = testEvent(
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 10, 0),
        )
        val (viewModel, repository) = editor(
            target = EditorTarget.Edit(single.id),
            repository = FakeEventRepository(listOf(single)),
        )
        val state = viewModel.state.first { it.draft.eventId != null }

        assertFalse(state.canRemoveSingleOccurrence)

        viewModel.onEvent(EventEditorUiEvent.Delete(DeleteScope.Series))
        viewModel.state.first { it.isFinished }

        assertTrue(repository.stored.isEmpty())
    }

    private fun seriesEvent(): Event = testEvent(
        id = "series",
        title = "Standup",
        start = LocalDateTime(2026, 8, 3, 9, 0),
        endExclusive = LocalDateTime(2026, 8, 3, 9, 15),
        recurrence = RecurrenceRule(Frequency.DAILY),
    )
}
