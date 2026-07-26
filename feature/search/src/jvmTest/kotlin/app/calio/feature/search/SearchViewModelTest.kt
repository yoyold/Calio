package app.calio.feature.search

import app.calio.domain.repository.SearchHit
import app.calio.domain.repository.SearchRepository
import app.calio.model.CalioColor
import app.calio.testing.FakeCalendarRepository
import app.calio.testing.FakeCategoryRepository
import app.calio.testing.testCalendar
import app.calio.testing.testCategory
import app.calio.testing.testEvent
import app.calio.testing.testTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    /** Counts calls so the test can tell how often typing actually reached the index. */
    private class CountingSearchRepository(private val hits: List<SearchHit>) : SearchRepository {
        var calls = 0
            private set

        var lastQuery: String? = null
            private set

        override suspend fun search(query: String, limit: Int): List<SearchHit> {
            calls++
            lastQuery = query
            return hits
        }
    }

    private val event = testEvent(
        title = "Budget planning",
        start = LocalDateTime(2026, 8, 5, 9, 0),
        endExclusive = LocalDateTime(2026, 8, 5, 10, 0),
    )

    private val task = testTask(title = "Budget spreadsheet").copy(categoryId = testCategory.id)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.search(
        hits: List<SearchHit> = emptyList(),
    ): Pair<SearchViewModel, CountingSearchRepository> {
        val repository = CountingSearchRepository(hits)
        val viewModel = SearchViewModel(
            search = repository,
            calendars = FakeCalendarRepository(listOf(testCalendar)),
            categories = FakeCategoryRepository(listOf(testCategory)),
        )
        backgroundScope.launch { viewModel.state.collect { } }
        return viewModel to repository
    }

    @Test
    fun `an empty query searches nothing`() = runTest {
        val (viewModel, repository) = search()

        val state = viewModel.state.first()

        assertFalse(state.hasQuery)
        assertTrue(state.results.isEmpty())
        assertEquals(0, repository.calls)
    }

    @Test
    fun `whitespace alone is not a query`() = runTest {
        val (viewModel, repository) = search()

        viewModel.onEvent(SearchUiEvent.QueryChanged("   "))
        testScheduler.advanceUntilIdle()

        assertEquals(0, repository.calls)
    }

    @Test
    fun `a query returns events and tasks`() = runTest {
        val (viewModel, _) = search(listOf(SearchHit.EventHit(event), SearchHit.TaskHit(task)))

        viewModel.onEvent(SearchUiEvent.QueryChanged("budget"))

        val state = viewModel.state.first { it.results.isNotEmpty() }
        assertEquals(2, state.results.size)
        assertTrue(state.results.first() is SearchResult.EventResult)
        assertTrue(state.results.last() is SearchResult.TaskResult)
    }

    @Test
    fun `the order the index returned is kept`() = runTest {
        val second = testEvent(
            id = "second",
            title = "Weekly",
            start = LocalDateTime(2026, 8, 6, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 6, 10, 0),
        )
        val (viewModel, _) = search(listOf(SearchHit.EventHit(second), SearchHit.EventHit(event)))

        viewModel.onEvent(SearchUiEvent.QueryChanged("budget"))

        val results = viewModel.state.first { it.results.size == 2 }.results
        assertEquals(
            listOf("second", "event-1"),
            results.filterIsInstance<SearchResult.EventResult>().map { it.event.id.value },
        )
    }

    @Test
    fun `results carry the colour of their calendar`() = runTest {
        val (viewModel, _) = search(listOf(SearchHit.EventHit(event)))

        viewModel.onEvent(SearchUiEvent.QueryChanged("budget"))

        val result = viewModel.state.first { it.results.isNotEmpty() }
            .results.filterIsInstance<SearchResult.EventResult>().single()
        assertEquals(CalioColor(0xFF1B6EF3), result.color)
    }

    @Test
    fun `task results carry their category`() = runTest {
        val (viewModel, _) = search(listOf(SearchHit.TaskHit(task)))

        viewModel.onEvent(SearchUiEvent.QueryChanged("budget"))

        val result = viewModel.state.first { it.results.isNotEmpty() }
            .results.filterIsInstance<SearchResult.TaskResult>().single()
        assertEquals(testCategory, result.category)
    }

    @Test
    fun `a burst of typing reaches the index once`() = runTest {
        val (viewModel, repository) = search(listOf(SearchHit.EventHit(event)))

        "budget".forEachIndexed { index, _ ->
            viewModel.onEvent(SearchUiEvent.QueryChanged("budget".take(index + 1)))
            testScheduler.advanceTimeBy(50)
        }
        testScheduler.advanceUntilIdle()

        assertEquals(1, repository.calls)
        assertEquals("budget", repository.lastQuery)
    }

    @Test
    fun `pausing between words searches for each of them`() = runTest {
        val (viewModel, repository) = search(listOf(SearchHit.EventHit(event)))

        viewModel.onEvent(SearchUiEvent.QueryChanged("budget"))
        testScheduler.advanceUntilIdle()
        viewModel.onEvent(SearchUiEvent.QueryChanged("budget meeting"))
        testScheduler.advanceUntilIdle()

        assertEquals(2, repository.calls)
    }

    @Test
    fun `the screen says it is searching while it waits`() = runTest {
        val (viewModel, _) = search(listOf(SearchHit.EventHit(event)))

        viewModel.onEvent(SearchUiEvent.QueryChanged("budget"))

        assertTrue(viewModel.state.first { it.isSearching }.isSearching)
        assertFalse(viewModel.state.first { it.results.isNotEmpty() }.isSearching)
    }

    @Test
    fun `a query without matches is reported as such`() = runTest {
        val (viewModel, _) = search(hits = emptyList())

        viewModel.onEvent(SearchUiEvent.QueryChanged("nothing here"))

        val state = viewModel.state.first { it.isEmptyResult }
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `clearing empties the results`() = runTest {
        val (viewModel, _) = search(listOf(SearchHit.EventHit(event)))
        viewModel.onEvent(SearchUiEvent.QueryChanged("budget"))
        viewModel.state.first { it.results.isNotEmpty() }

        viewModel.onEvent(SearchUiEvent.Clear)

        val state = viewModel.state.first { !it.hasQuery }
        assertTrue(state.results.isEmpty())
        assertFalse(state.isSearching)
    }

    @Test
    fun `an event whose calendar is gone is left out rather than drawn colourless`() = runTest {
        val orphan = testEvent(
            id = "orphan",
            calendarId = app.calio.model.CalendarId("missing"),
            start = LocalDateTime(2026, 8, 5, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 5, 10, 0),
        )
        val (viewModel, _) = search(listOf(SearchHit.EventHit(orphan), SearchHit.TaskHit(task)))

        viewModel.onEvent(SearchUiEvent.QueryChanged("budget"))

        val state = viewModel.state.first { it.results.isNotEmpty() }
        assertEquals(1, state.results.size)
        assertTrue(state.results.single() is SearchResult.TaskResult)
    }
}
