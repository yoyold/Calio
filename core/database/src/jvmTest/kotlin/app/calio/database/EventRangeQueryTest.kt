package app.calio.database

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.hours

/**
 * The query the calendar views live on. Everything the user sees in a day, week, month or year
 * passes through it, so its boundary behaviour is worth pinning down precisely.
 */
class EventRangeQueryTest {

    private val fixture = TestDatabase()
    private val db = fixture.db

    private val windowStart = 1_754_179_200_000L // 2026-08-03T00:00:00Z
    private val windowEnd = windowStart + 24.hours.inWholeMilliseconds

    init {
        db.putCalendar()
    }

    @AfterTest
    fun tearDown() = fixture.close()

    private fun idsInWindow(): List<String> =
        db.eventsQueries.selectInRange(rangeStartUtc = windowStart, rangeEndUtc = windowEnd)
            .executeAsList()
            .map { it.id }

    @Test
    fun `an event inside the window is returned`() {
        db.putEvent(id = "inside", startUtc = windowStart + 1, endUtc = windowStart + 2)

        assertEquals(listOf("inside"), idsInWindow())
    }

    @Test
    fun `an event ending exactly at the window start is not returned`() {
        db.putEvent(id = "before", startUtc = windowStart - 100, endUtc = windowStart)

        assertEquals(emptyList(), idsInWindow())
    }

    @Test
    fun `an event starting exactly at the window end is not returned`() {
        db.putEvent(id = "after", startUtc = windowEnd, endUtc = windowEnd + 100)

        assertEquals(emptyList(), idsInWindow())
    }

    @Test
    fun `an event starting exactly at the window start is returned`() {
        db.putEvent(id = "edge", startUtc = windowStart, endUtc = windowStart + 100)

        assertEquals(listOf("edge"), idsInWindow())
    }

    @Test
    fun `an event spanning the whole window is returned`() {
        db.putEvent(id = "spanning", startUtc = windowStart - 5_000, endUtc = windowEnd + 5_000)

        assertEquals(listOf("spanning"), idsInWindow())
    }

    @Test
    fun `a soft deleted event is not returned`() {
        db.putEvent(id = "gone", startUtc = windowStart + 1, endUtc = windowStart + 2, deletedAt = NOW)

        assertEquals(emptyList(), idsInWindow())
    }

    @Test
    fun `results are ordered by start time`() {
        db.putEvent(id = "later", startUtc = windowStart + 9_000, endUtc = windowStart + 10_000)
        db.putEvent(id = "earlier", startUtc = windowStart + 1_000, endUtc = windowStart + 2_000)

        assertEquals(listOf("earlier", "later"), idsInWindow())
    }

    @Test
    fun `an open ended series that started long ago is returned`() {
        db.putEvent(
            id = "daily",
            startUtc = windowStart - 365L * 24 * 60 * 60 * 1000,
            endUtc = windowStart - 365L * 24 * 60 * 60 * 1000 + 3_600_000,
            recurrenceRule = "FREQ=DAILY",
            recurrenceUntilUtc = null,
        )

        assertEquals(listOf("daily"), idsInWindow())
    }

    @Test
    fun `a series that already ended before the window is skipped`() {
        db.putEvent(
            id = "finished",
            startUtc = windowStart - 100_000,
            endUtc = windowStart - 90_000,
            recurrenceRule = "FREQ=DAILY",
            recurrenceUntilUtc = windowStart - 1,
        )

        assertEquals(emptyList(), idsInWindow())
    }

    @Test
    fun `a series that starts after the window is skipped`() {
        db.putEvent(
            id = "future",
            startUtc = windowEnd + 1,
            endUtc = windowEnd + 100,
            recurrenceRule = "FREQ=WEEKLY",
            recurrenceUntilUtc = null,
        )

        assertEquals(emptyList(), idsInWindow())
    }

    @Test
    fun `a series still running into the window is returned even though no occurrence is stored`() {
        db.putEvent(
            id = "running",
            startUtc = windowStart - 100_000,
            endUtc = windowStart - 90_000,
            recurrenceRule = "FREQ=WEEKLY",
            recurrenceUntilUtc = windowEnd + 100_000,
        )

        assertEquals(listOf("running"), idsInWindow())
    }

    @Test
    fun `hidden calendars are excluded from the visible query only`() {
        db.putCalendar(id = "hidden", name = "Archive", isVisible = 0)
        db.putEvent(id = "visible-event", startUtc = windowStart + 1, endUtc = windowStart + 2)
        db.putEvent(
            id = "hidden-event",
            calendarId = "hidden",
            startUtc = windowStart + 3,
            endUtc = windowStart + 4,
        )

        val visible = db.eventsQueries
            .selectInRangeForVisibleCalendars(rangeStartUtc = windowStart, rangeEndUtc = windowEnd)
            .executeAsList()
            .map { it.id }

        assertEquals(listOf("visible-event", "hidden-event"), idsInWindow())
        assertEquals(listOf("visible-event"), visible)
    }
}
