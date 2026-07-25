package app.calio.domain.recurrence

import app.calio.model.EventPatch
import app.calio.model.EventStatus
import app.calio.model.EventTimeRange
import app.calio.model.Frequency
import app.calio.model.OverrideType
import app.calio.model.RecurrenceEnd
import app.calio.model.RecurrenceOverride
import app.calio.model.RecurrenceRule
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class RecurrenceExpanderTest {

    private val expander = RecurrenceExpander()

    private val firstWeekOfAugust = window("2026-08-03T00:00:00Z", "2026-08-10T00:00:00Z")

    @Test
    fun `a single event inside the window yields one occurrence`() {
        val single = event()

        val occurrences = expander.expand(single, firstWeekOfAugust)

        assertEquals(1, occurrences.size)
        assertEquals(LocalDateTime(2026, 8, 3, 9, 0), occurrences.single().originalStart)
        assertFalse(occurrences.single().isException)
    }

    @Test
    fun `a single event outside the window yields nothing`() {
        val single = event(
            start = LocalDateTime(2026, 9, 1, 9, 0),
            endExclusive = LocalDateTime(2026, 9, 1, 9, 30),
        )

        assertTrue(expander.expand(single, firstWeekOfAugust).isEmpty())
    }

    @Test
    fun `a daily series fills the window`() {
        val daily = event(recurrence = RecurrenceRule(Frequency.DAILY))

        val starts = expander.expand(daily, firstWeekOfAugust).localStarts()

        assertEquals(7, starts.size)
        assertEquals(LocalDateTime(2026, 8, 3, 9, 0), starts.first())
        assertEquals(LocalDateTime(2026, 8, 9, 9, 0), starts.last())
    }

    @Test
    fun `occurrences keep the wall clock time of the series`() {
        val daily = event(recurrence = RecurrenceRule(Frequency.DAILY))

        val occurrences = expander.expand(daily, firstWeekOfAugust)

        assertTrue(occurrences.all { (it.timeRange as EventTimeRange.Zoned).start.time == LocalDateTime(2026, 8, 3, 9, 0).time })
    }

    @Test
    fun `occurrences keep their wall clock length across a daylight saving change`() {
        // Europe/Berlin skips an hour on 29 March 2026.
        val daily = event(
            start = LocalDateTime(2026, 3, 27, 9, 0),
            endExclusive = LocalDateTime(2026, 3, 27, 10, 0),
            recurrence = RecurrenceRule(Frequency.DAILY),
        )

        val occurrences = expander.expand(
            daily,
            window("2026-03-27T00:00:00Z", "2026-03-31T00:00:00Z"),
        )

        assertTrue(occurrences.all { it.timeRange.duration == 1.hours })
        assertEquals(
            listOf(9, 9, 9, 9),
            occurrences.map { (it.timeRange as EventTimeRange.Zoned).start.hour },
        )
    }

    @Test
    fun `the instant of an occurrence shifts when the offset changes`() {
        val daily = event(
            start = LocalDateTime(2026, 3, 28, 9, 0),
            endExclusive = LocalDateTime(2026, 3, 28, 10, 0),
            recurrence = RecurrenceRule(Frequency.DAILY),
        )

        val occurrences = expander.expand(
            daily,
            window("2026-03-28T00:00:00Z", "2026-03-30T00:00:00Z"),
        )

        // 09:00 on the day before the change is 08:00 UTC, on the day after it is 07:00 UTC.
        assertEquals(Instant.parse("2026-03-28T08:00:00Z"), occurrences.first().timeRange.startUtc)
        assertEquals(Instant.parse("2026-03-29T07:00:00Z"), occurrences.last().timeRange.startUtc)
    }

    @Test
    fun `a count limited series stops after the given number of occurrences`() {
        val limited = event(
            recurrence = RecurrenceRule(Frequency.DAILY, end = RecurrenceEnd.AfterCount(3)),
        )

        val starts = expander.expand(limited, firstWeekOfAugust).localStarts()

        assertEquals(3, starts.size)
        assertEquals(LocalDateTime(2026, 8, 5, 9, 0), starts.last())
    }

    @Test
    fun `a cancelled occurrence still counts towards the limit`() {
        // The rule produced the occurrence; the exception only removed it from view. Anything else
        // would silently extend a series every time the user deletes one of its entries.
        val limited = event(
            recurrence = RecurrenceRule(Frequency.DAILY, end = RecurrenceEnd.AfterCount(3)),
        )
        val cancelledSecond = RecurrenceOverride(
            eventId = limited.id,
            originalStart = LocalDateTime(2026, 8, 4, 9, 0),
            type = OverrideType.CANCELLED,
        )

        val starts = expander.expand(limited, firstWeekOfAugust, listOf(cancelledSecond)).localStarts()

        assertEquals(
            listOf(LocalDateTime(2026, 8, 3, 9, 0), LocalDateTime(2026, 8, 5, 9, 0)),
            starts,
        )
    }

    @Test
    fun `a series ending on a date stops on that date`() {
        val until = event(
            recurrence = RecurrenceRule(
                frequency = Frequency.DAILY,
                end = RecurrenceEnd.OnDate(LocalDate(2026, 8, 5)),
            ),
        )

        val starts = expander.expand(until, firstWeekOfAugust).localStarts()

        assertEquals(3, starts.size)
        assertEquals(LocalDateTime(2026, 8, 5, 9, 0), starts.last())
    }

    @Test
    fun `a cancelled occurrence disappears`() {
        val daily = event(recurrence = RecurrenceRule(Frequency.DAILY))
        val cancelled = RecurrenceOverride(
            eventId = daily.id,
            originalStart = LocalDateTime(2026, 8, 5, 9, 0),
            type = OverrideType.CANCELLED,
        )

        val starts = expander.expand(daily, firstWeekOfAugust, listOf(cancelled)).localStarts()

        assertEquals(6, starts.size)
        assertFalse(LocalDateTime(2026, 8, 5, 9, 0) in starts)
    }

    @Test
    fun `a modified occurrence overrides only the fields it carries`() {
        val daily = event(title = "Standup", recurrence = RecurrenceRule(Frequency.DAILY))
        val renamed = RecurrenceOverride(
            eventId = daily.id,
            originalStart = LocalDateTime(2026, 8, 5, 9, 0),
            type = OverrideType.MODIFIED,
            patch = EventPatch(title = "Standup with the client"),
        )

        val occurrences = expander.expand(daily, firstWeekOfAugust, listOf(renamed))
        val exception = occurrences.single { it.isException }

        assertEquals("Standup with the client", exception.event.title)
        assertEquals(daily.calendarId, exception.event.calendarId)
        assertEquals(EventStatus.CONFIRMED, exception.event.status)
        assertEquals(6, occurrences.count { !it.isException })
    }

    @Test
    fun `a moved occurrence keeps the original start as its identity`() {
        val daily = event(recurrence = RecurrenceRule(Frequency.DAILY))
        val moved = RecurrenceOverride(
            eventId = daily.id,
            originalStart = LocalDateTime(2026, 8, 5, 9, 0),
            type = OverrideType.MODIFIED,
            patch = EventPatch(
                timeRange = EventTimeRange.Zoned(
                    LocalDateTime(2026, 8, 5, 14, 0),
                    LocalDateTime(2026, 8, 5, 15, 0),
                    berlin,
                ),
            ),
        )

        val exception = expander.expand(daily, firstWeekOfAugust, listOf(moved)).single { it.isException }

        assertEquals(LocalDateTime(2026, 8, 5, 9, 0), exception.originalStart)
        assertEquals(
            LocalDateTime(2026, 8, 5, 14, 0),
            (exception.timeRange as EventTimeRange.Zoned).start,
        )
    }

    @Test
    fun `an occurrence moved out of the window disappears from it`() {
        val daily = event(
            recurrence = RecurrenceRule(Frequency.DAILY, end = RecurrenceEnd.AfterCount(7)),
        )
        val movedAway = RecurrenceOverride(
            eventId = daily.id,
            originalStart = LocalDateTime(2026, 8, 5, 9, 0),
            type = OverrideType.MODIFIED,
            patch = EventPatch(
                timeRange = EventTimeRange.Zoned(
                    LocalDateTime(2026, 9, 5, 9, 0),
                    LocalDateTime(2026, 9, 5, 10, 0),
                    berlin,
                ),
            ),
        )

        val starts = expander.expand(daily, firstWeekOfAugust, listOf(movedAway)).localStarts()

        assertEquals(6, starts.size)
        assertFalse(LocalDateTime(2026, 8, 5, 9, 0) in starts)
    }

    @Test
    fun `an occurrence moved into the window appears in it`() {
        // Without this the entry would vanish entirely: the rule points at a day the window no
        // longer covers, and the moved copy is the only thing left.
        val daily = event(
            start = LocalDateTime(2026, 7, 1, 9, 0),
            endExclusive = LocalDateTime(2026, 7, 1, 9, 30),
            recurrence = RecurrenceRule(
                frequency = Frequency.DAILY,
                end = RecurrenceEnd.OnDate(LocalDate(2026, 7, 10)),
            ),
        )
        val movedIn = RecurrenceOverride(
            eventId = daily.id,
            originalStart = LocalDateTime(2026, 7, 6, 9, 0),
            type = OverrideType.MODIFIED,
            patch = EventPatch(
                timeRange = EventTimeRange.Zoned(
                    LocalDateTime(2026, 8, 5, 11, 0),
                    LocalDateTime(2026, 8, 5, 12, 0),
                    berlin,
                ),
            ),
        )

        val occurrences = expander.expand(daily, firstWeekOfAugust, listOf(movedIn))

        assertEquals(1, occurrences.size)
        assertEquals(LocalDateTime(2026, 7, 6, 9, 0), occurrences.single().originalStart)
        assertTrue(occurrences.single().isException)
    }

    @Test
    fun `an all day series keeps its length`() {
        val weekly = allDayEvent(
            range = EventTimeRange.AllDay(LocalDate(2026, 8, 3), LocalDate(2026, 8, 5)),
            recurrence = RecurrenceRule(Frequency.WEEKLY),
        )

        val occurrences = expander.expand(
            weekly,
            window("2026-08-01T00:00:00Z", "2026-08-20T00:00:00Z"),
        )

        assertEquals(3, occurrences.size)
        assertTrue(occurrences.all { (it.timeRange as EventTimeRange.AllDay).dayCount == 2 })
        assertEquals(
            listOf(LocalDate(2026, 8, 3), LocalDate(2026, 8, 10), LocalDate(2026, 8, 17)),
            occurrences.map { (it.timeRange as EventTimeRange.AllDay).startDate },
        )
    }

    @Test
    fun `an endless series is capped so a single event cannot flood a view`() {
        val capped = RecurrenceExpander(maxOccurrencesPerEvent = 10)
        val daily = event(recurrence = RecurrenceRule(Frequency.DAILY))

        val occurrences = capped.expand(
            daily,
            window("2026-08-03T00:00:00Z", "2027-08-03T00:00:00Z"),
        )

        assertEquals(10, occurrences.size)
    }

    @Test
    fun `a series that starts after the window yields nothing`() {
        val daily = event(
            start = LocalDateTime(2026, 9, 1, 9, 0),
            endExclusive = LocalDateTime(2026, 9, 1, 9, 30),
            recurrence = RecurrenceRule(Frequency.DAILY),
        )

        assertTrue(expander.expand(daily, firstWeekOfAugust).isEmpty())
    }

    @Test
    fun `occurrences of a weekly series land on the same weekday`() {
        val weekly = event(recurrence = RecurrenceRule(Frequency.WEEKLY))

        val starts = expander.expand(
            weekly,
            window("2026-08-03T00:00:00Z", "2026-09-01T00:00:00Z"),
        ).localStarts()

        assertEquals(
            listOf(
                LocalDateTime(2026, 8, 3, 9, 0),
                LocalDateTime(2026, 8, 10, 9, 0),
                LocalDateTime(2026, 8, 17, 9, 0),
                LocalDateTime(2026, 8, 24, 9, 0),
                LocalDateTime(2026, 8, 31, 9, 0),
            ),
            starts,
        )
    }
}
