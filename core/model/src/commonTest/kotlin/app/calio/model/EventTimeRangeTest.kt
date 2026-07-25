package app.calio.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class EventTimeRangeTest {

    private val berlin = TimeZone.of("Europe/Berlin")
    private val newYork = TimeZone.of("America/New_York")

    @Test
    fun `converts a zoned range to the matching utc instants`() {
        val range = EventTimeRange.Zoned(
            start = LocalDateTime(2026, 8, 3, 14, 0),
            endExclusive = LocalDateTime(2026, 8, 3, 15, 0),
            timeZone = berlin,
        )

        assertEquals(Instant.parse("2026-08-03T12:00:00Z"), range.startUtc)
        assertEquals(Instant.parse("2026-08-03T13:00:00Z"), range.endUtcExclusive)
    }

    @Test
    fun `the same wall clock time in another zone is another instant`() {
        val wallClock = LocalDateTime(2026, 8, 3, 14, 0)
        val end = LocalDateTime(2026, 8, 3, 15, 0)

        val inBerlin = EventTimeRange.Zoned(wallClock, end, berlin)
        val inNewYork = EventTimeRange.Zoned(wallClock, end, newYork)

        assertTrue(inBerlin.startUtc < inNewYork.startUtc)
    }

    @Test
    fun `a range crossing the spring forward is shorter than its wall clock span`() {
        // On 29 March 2026 Europe/Berlin skips from 02:00 to 03:00.
        val range = EventTimeRange.Zoned(
            start = LocalDateTime(2026, 3, 29, 1, 0),
            endExclusive = LocalDateTime(2026, 3, 29, 4, 0),
            timeZone = berlin,
        )

        assertEquals(2.hours, range.duration)
    }

    @Test
    fun `a range crossing the fall back is longer than its wall clock span`() {
        // On 25 October 2026 Europe/Berlin repeats the hour from 02:00 to 03:00.
        val range = EventTimeRange.Zoned(
            start = LocalDateTime(2026, 10, 25, 1, 0),
            endExclusive = LocalDateTime(2026, 10, 25, 4, 0),
            timeZone = berlin,
        )

        assertEquals(4.hours, range.duration)
    }

    @Test
    fun `rejects a zoned range that ends before it starts`() {
        assertFailsWith<IllegalArgumentException> {
            EventTimeRange.Zoned(
                start = LocalDateTime(2026, 8, 3, 15, 0),
                endExclusive = LocalDateTime(2026, 8, 3, 14, 0),
                timeZone = berlin,
            )
        }
    }

    @Test
    fun `a single day all day event spans exactly one day`() {
        val range = EventTimeRange.AllDay.singleDay(LocalDate(2026, 8, 3))

        assertEquals(1, range.dayCount)
        assertEquals(LocalDate(2026, 8, 4), range.endDateExclusive)
        assertEquals(Instant.parse("2026-08-03T00:00:00Z"), range.startUtc)
        assertEquals(Instant.parse("2026-08-04T00:00:00Z"), range.endUtcExclusive)
    }

    @Test
    fun `an all day event keeps its dates regardless of any time zone`() {
        val range = EventTimeRange.AllDay(LocalDate(2026, 12, 24), LocalDate(2026, 12, 27))

        assertEquals(3, range.dayCount)
        assertTrue(range.isAllDay)
    }

    @Test
    fun `rejects an all day range without any day in it`() {
        assertFailsWith<IllegalArgumentException> {
            EventTimeRange.AllDay(LocalDate(2026, 8, 3), LocalDate(2026, 8, 3))
        }
    }

    @Test
    fun `ranges that only touch at a boundary do not overlap`() {
        val morning = EventTimeRange.Zoned(
            LocalDateTime(2026, 8, 3, 9, 0),
            LocalDateTime(2026, 8, 3, 10, 0),
            berlin,
        )
        val afterwards = EventTimeRange.Zoned(
            LocalDateTime(2026, 8, 3, 10, 0),
            LocalDateTime(2026, 8, 3, 11, 0),
            berlin,
        )

        assertFalse(morning.overlaps(afterwards))
        assertFalse(afterwards.overlaps(morning))
    }

    @Test
    fun `a contained range overlaps its container in both directions`() {
        val meeting = EventTimeRange.Zoned(
            LocalDateTime(2026, 8, 3, 9, 0),
            LocalDateTime(2026, 8, 3, 12, 0),
            berlin,
        )
        val call = EventTimeRange.Zoned(
            LocalDateTime(2026, 8, 3, 10, 0),
            LocalDateTime(2026, 8, 3, 10, 30),
            berlin,
        )

        assertTrue(meeting.overlaps(call))
        assertTrue(call.overlaps(meeting))
    }

    @Test
    fun `overlap works across a zoned and an all day range`() {
        val allDay = EventTimeRange.AllDay.singleDay(LocalDate(2026, 8, 3))
        val duringThatDay = EventTimeRange.Zoned(
            LocalDateTime(2026, 8, 3, 14, 0),
            LocalDateTime(2026, 8, 3, 15, 0),
            berlin,
        )

        assertTrue(allDay.overlaps(duringThatDay))
    }
}
