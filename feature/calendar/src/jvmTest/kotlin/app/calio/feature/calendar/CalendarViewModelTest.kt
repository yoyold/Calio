package app.calio.feature.calendar

import app.calio.datetime.CalendarPeriod
import app.calio.domain.planning.OverlapLayoutCalculator
import app.calio.domain.recurrence.RecurrenceExpander
import app.calio.model.Event
import app.calio.model.EventTimeRange
import app.calio.model.Frequency
import app.calio.model.RecurrenceRule
import app.calio.testing.FakeCalendarRepository
import app.calio.testing.FakeCategoryRepository
import app.calio.testing.FakeEventRepository
import app.calio.testing.testAllDayEvent
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModelTest {

    // Wednesday, 5 August 2026, mid-morning in Berlin.
    private val fixedNow = Instant.parse("2026-08-05T08:30:00Z")
    private val clock = object : Clock {
        override fun now(): Instant = fixedNow
    }

    private val monday = LocalDate(2026, 8, 3)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(events: List<Event>): CalendarViewModel {
        val viewModel = CalendarViewModel(
            events = FakeEventRepository(events),
            calendars = FakeCalendarRepository(),
            categories = FakeCategoryRepository(),
            expander = RecurrenceExpander(),
            layout = OverlapLayoutCalculator(),
            zone = testZone,
            clock = clock,
        )
        // The state only produces values while it is being observed, which is what stops an unseen
        // screen from querying. The test therefore has to subscribe like the interface would.
        backgroundScope.launch { viewModel.state.collect { } }
        return viewModel
    }

    @Test
    fun `the week containing today is shown first`() = runTest {
        val viewModel = viewModel(emptyList())

        val state = viewModel.state.first { it.days.isNotEmpty() }

        assertEquals(CalendarPeriod.WEEK, state.period)
        assertEquals(7, state.days.size)
        assertEquals(monday, state.days.first().date)
        assertEquals(LocalDate(2026, 8, 9), state.days.last().date)
    }

    @Test
    fun `today is marked exactly once`() = runTest {
        val viewModel = viewModel(emptyList())

        val state = viewModel.state.first { it.days.isNotEmpty() }

        assertEquals(1, state.days.count { it.isToday })
        assertEquals(LocalDate(2026, 8, 5), state.days.single { it.isToday }.date)
    }

    @Test
    fun `an appointment lands on its own day only`() = runTest {
        val event = testEvent(
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 10, 0),
        )
        val viewModel = viewModel(listOf(event))

        val days = viewModel.state.first { it.days.any { day -> day.timed.isNotEmpty() } }.days

        assertEquals(1, days.sumOf { it.timed.size })
        assertEquals(LocalDate(2026, 8, 5), days.single { it.timed.isNotEmpty() }.date)
    }

    @Test
    fun `a daily series produces one occurrence per day of the week`() = runTest {
        val series = testEvent(
            start = LocalDateTime(2026, 8, 3, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 3, 9, 15),
            recurrence = RecurrenceRule(Frequency.DAILY),
        )
        val viewModel = viewModel(listOf(series))

        val days = viewModel.state.first { it.days.any { day -> day.timed.isNotEmpty() } }.days

        assertEquals(7, days.count { it.timed.size == 1 })
    }

    @Test
    fun `overlapping appointments are given columns to share`() = runTest {
        val first = testEvent(
            id = "first",
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 11, 0),
        )
        val second = testEvent(
            id = "second",
            start = LocalDateTime(2026, 8, 5, 10, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 12, 0),
        )
        val viewModel = viewModel(listOf(first, second))

        val day = viewModel.state.first { it.days.any { day -> day.timed.size == 2 } }
            .days.single { it.timed.size == 2 }

        assertEquals(setOf(0, 1), day.timed.map { it.column }.toSet())
        assertTrue(day.timed.all { it.columnCount == 2 })
    }

    @Test
    fun `an all day entry is kept apart from the timed ones`() = runTest {
        val holiday = testAllDayEvent(
            id = "holiday",
            title = "Public holiday",
            range = EventTimeRange.AllDay.singleDay(LocalDate(2026, 8, 5)),
        )
        val viewModel = viewModel(listOf(holiday))

        val state = viewModel.state.first { it.hasAllDayEntries }
        val day = state.days.single { it.allDay.isNotEmpty() }

        assertEquals(LocalDate(2026, 8, 5), day.date)
        assertTrue(day.timed.isEmpty())
    }

    @Test
    fun `appointments take the colour of their calendar`() = runTest {
        val event = testEvent(
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 10, 0),
        )
        val viewModel = viewModel(listOf(event))

        val entry = viewModel.state.first { it.days.any { day -> day.timed.isNotEmpty() } }
            .days.flatMap { it.timed }.single()

        assertEquals(testCalendar.color, entry.color)
    }

    @Test
    fun `paging moves a whole week at a time`() = runTest {
        val viewModel = viewModel(emptyList())
        viewModel.state.first { it.days.isNotEmpty() }

        viewModel.onEvent(CalendarUiEvent.GoToNext)

        val next = viewModel.state.first { it.days.firstOrNull()?.date == LocalDate(2026, 8, 10) }
        assertEquals(LocalDate(2026, 8, 16), next.days.last().date)
        assertTrue(next.days.none { it.isToday })
    }

    @Test
    fun `going back to today returns to the current week`() = runTest {
        val viewModel = viewModel(emptyList())
        viewModel.state.first { it.days.isNotEmpty() }
        viewModel.onEvent(CalendarUiEvent.GoToNext)
        viewModel.state.first { it.days.firstOrNull()?.date == LocalDate(2026, 8, 10) }

        viewModel.onEvent(CalendarUiEvent.GoToToday)

        val back = viewModel.state.first { it.days.firstOrNull()?.date == monday }
        assertEquals(1, back.days.count { it.isToday })
    }

    @Test
    fun `switching to the day view shows a single day`() = runTest {
        val viewModel = viewModel(emptyList())
        viewModel.state.first { it.days.isNotEmpty() }

        viewModel.onEvent(CalendarUiEvent.SelectPeriod(CalendarPeriod.DAY))

        val state = viewModel.state.first { it.period == CalendarPeriod.DAY && it.days.size == 1 }
        assertEquals(LocalDate(2026, 8, 5), state.days.single().date)
        assertTrue(state.days.single().isToday)
    }

    @Test
    fun `selecting a date moves the view to it`() = runTest {
        val viewModel = viewModel(emptyList())
        viewModel.state.first { it.days.isNotEmpty() }

        viewModel.onEvent(CalendarUiEvent.SelectDate(LocalDate(2026, 12, 24)))

        val state = viewModel.state.first { it.anchor == LocalDate(2026, 12, 24) }
        assertEquals(LocalDate(2026, 12, 21), state.days.first().date)
    }
}
