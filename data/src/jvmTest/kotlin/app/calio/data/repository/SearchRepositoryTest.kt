package app.calio.data.repository

import app.calio.data.TestEnvironment
import app.calio.data.calendar
import app.calio.data.event
import app.calio.data.task
import app.calio.domain.repository.SearchHit
import app.calio.model.EventId
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchRepositoryTest {

    private val environment = TestEnvironment()
    private val search = environment.search

    @AfterTest
    fun tearDown() = environment.close()

    private suspend fun seed() {
        environment.calendars.upsert(calendar())
        environment.events.upsert(
            event(id = "titled", title = "Budget planning").copy(notes = "Bring last year's numbers"),
        )
        environment.events.upsert(
            event(id = "mentioned", title = "Weekly").copy(description = "Budget follow up"),
        )
        environment.tasks.upsert(task(id = "task-1", title = "Budget spreadsheet"))
    }

    @Test
    fun `search spans events and tasks`() = runTest {
        seed()

        val hits = search.search("budget")

        assertEquals(3, hits.size)
        assertEquals(1, hits.count { it is SearchHit.TaskHit })
    }

    @Test
    fun `the ranking from the index is preserved`() = runTest {
        seed()

        val events = search.search("budget").filterIsInstance<SearchHit.EventHit>()

        assertEquals("titled", events.first().event.id.value)
    }

    @Test
    fun `matches in notes are found too`() = runTest {
        seed()

        val hits = search.search("numbers").filterIsInstance<SearchHit.EventHit>()

        assertEquals(listOf(EventId("titled")), hits.map { it.event.id })
    }

    @Test
    fun `a blank query returns nothing rather than everything`() = runTest {
        seed()

        assertTrue(search.search("   ").isEmpty())
    }

    @Test
    fun `a deleted event drops out of the results`() = runTest {
        seed()

        environment.events.delete(EventId("titled"))

        val hits = search.search("budget").filterIsInstance<SearchHit.EventHit>()
        assertEquals(listOf(EventId("mentioned")), hits.map { it.event.id })
    }

    @Test
    fun `the limit is respected`() = runTest {
        seed()

        assertEquals(2, search.search("budget", limit = 2).size)
    }
}
