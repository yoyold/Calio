package app.calio.database

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The full-text index is maintained entirely by triggers, so nothing can forget to update it.
 * These tests exist because a stale search index fails silently: results simply go missing.
 */
class SearchIndexTest {

    private val fixture = TestDatabase()
    private val db = fixture.db

    init {
        db.putCalendar()
    }

    @AfterTest
    fun tearDown() = fixture.close()

    private fun search(query: String): List<String> =
        db.searchQueries.search(query = query, limit = 20)
            .executeAsList()
            .mapNotNull { it.entity_id }

    @Test
    fun `an inserted event becomes findable by its title`() {
        db.putEvent(id = "event-1", title = "Quarterly review", startUtc = 1, endUtc = 2)

        assertEquals(listOf("event-1"), search("quarterly"))
    }

    @Test
    fun `description notes and location are searchable too`() {
        db.putEvent(
            id = "event-1",
            title = "Meeting",
            description = "Discuss the roadmap",
            notes = "Bring the printout",
            location = "Conference room",
            startUtc = 1,
            endUtc = 2,
        )

        assertEquals(listOf("event-1"), search("roadmap"))
        assertEquals(listOf("event-1"), search("printout"))
        assertEquals(listOf("event-1"), search("conference"))
    }

    @Test
    fun `search ignores diacritics`() {
        db.putEvent(id = "event-1", title = "Büro aufräumen", startUtc = 1, endUtc = 2)

        assertEquals(listOf("event-1"), search("buro"))
        assertEquals(listOf("event-1"), search("büro"))
    }

    @Test
    fun `search is case insensitive`() {
        db.putEvent(id = "event-1", title = "Standup", startUtc = 1, endUtc = 2)

        assertEquals(listOf("event-1"), search("STANDUP"))
    }

    @Test
    fun `a match in the title ranks above a match in the description`() {
        db.putEvent(id = "mentioned", title = "Weekly", description = "Prepare budget", startUtc = 1, endUtc = 2)
        db.putEvent(id = "titled", title = "Budget", startUtc = 3, endUtc = 4)

        assertEquals(listOf("titled", "mentioned"), search("budget"))
    }

    @Test
    fun `renaming an event updates the index in both directions`() {
        db.putEvent(id = "event-1", title = "Old name", startUtc = 1, endUtc = 2)

        db.eventsQueries.update(
            calendarId = "calendar-1",
            categoryId = null,
            title = "New name",
            description = null,
            notes = null,
            locationLabel = null,
            locationLat = null,
            locationLon = null,
            isAllDay = 0,
            startLocal = "2026-08-03T09:00",
            endLocal = "2026-08-03T10:00",
            timeZoneId = "Europe/Berlin",
            startUtc = 1,
            endUtc = 2,
            recurrenceRule = null,
            recurrenceUntilUtc = null,
            colorOverride = null,
            kind = "STANDARD",
            busyStatus = "BUSY",
            status = "CONFIRMED",
            updatedAt = NOW,
            revision = revision(),
            originDevice = TEST_DEVICE,
            id = "event-1",
        )

        assertEquals(listOf("event-1"), search("new"))
        assertTrue(search("old").isEmpty())
        assertEquals(1, db.searchQueries.countIndexed().executeAsOne())
    }

    @Test
    fun `a soft deleted event disappears from the index`() {
        db.putEvent(id = "event-1", title = "Cancelled workshop", startUtc = 1, endUtc = 2)

        db.eventsQueries.softDelete(deletedAt = NOW, revision = revision(), id = "event-1")

        assertTrue(search("workshop").isEmpty())
        assertEquals(0, db.searchQueries.countIndexed().executeAsOne())
    }

    @Test
    fun `an event that is already deleted on insert never enters the index`() {
        db.putEvent(id = "event-1", title = "Tombstone", startUtc = 1, endUtc = 2, deletedAt = NOW)

        assertEquals(0, db.searchQueries.countIndexed().executeAsOne())
    }

    @Test
    fun `a hard deleted event disappears from the index`() {
        db.putEvent(id = "event-1", title = "Removed", startUtc = 1, endUtc = 2)

        db.eventsQueries.deleteHard("event-1")

        assertEquals(0, db.searchQueries.countIndexed().executeAsOne())
    }

    @Test
    fun `tasks are indexed alongside events and can be told apart`() {
        db.putEvent(id = "event-1", title = "Budget meeting", startUtc = 1, endUtc = 2)
        db.putTask(id = "task-1", title = "Budget spreadsheet")

        assertEquals(2, search("budget").size)
        assertEquals(1, db.searchQueries.countIndexedOfType("EVENT").executeAsOne())
        assertEquals(1, db.searchQueries.countIndexedOfType("TASK").executeAsOne())

        val eventsOnly = db.searchQueries.searchEvents(query = "budget", limit = 20)
            .executeAsList()
            .mapNotNull { it.entity_id }

        assertEquals(listOf("event-1"), eventsOnly)
    }

    @Test
    fun `a prefix query finds partial words`() {
        db.putEvent(id = "event-1", title = "Retrospective", startUtc = 1, endUtc = 2)

        assertEquals(listOf("event-1"), search("retro*"))
    }
}
