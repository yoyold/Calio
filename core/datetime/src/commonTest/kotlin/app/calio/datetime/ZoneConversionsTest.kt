package app.calio.datetime

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

class ZoneConversionsTest {

    private val berlin = TimeZone.of("Europe/Berlin")
    private val tokyo = TimeZone.of("Asia/Tokyo")

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now(): Instant = instant
    }

    @Test
    fun `an ordinary day lasts twenty four hours`() {
        assertEquals(24.hours, LocalDate(2026, 8, 3).dayLengthIn(berlin))
    }

    @Test
    fun `the day of the spring forward is one hour short`() {
        // Europe/Berlin skips from 02:00 to 03:00 on 29 March 2026.
        assertEquals(23.hours, LocalDate(2026, 3, 29).dayLengthIn(berlin))
    }

    @Test
    fun `the day of the fall back is one hour long`() {
        // Europe/Berlin repeats the hour from 02:00 to 03:00 on 25 October 2026.
        assertEquals(25.hours, LocalDate(2026, 10, 25).dayLengthIn(berlin))
    }

    @Test
    fun `a zone without daylight saving keeps every day at twenty four hours`() {
        assertEquals(24.hours, LocalDate(2026, 3, 29).dayLengthIn(tokyo))
        assertEquals(24.hours, LocalDate(2026, 10, 25).dayLengthIn(tokyo))
    }

    @Test
    fun `the day window starts at local midnight`() {
        val window = LocalDate(2026, 8, 3).dayWindowIn(berlin)

        assertEquals(Instant.parse("2026-08-02T22:00:00Z"), window.start)
        assertEquals(Instant.parse("2026-08-03T22:00:00Z"), window.endExclusive)
    }

    @Test
    fun `an instant belongs to different days in different zones`() {
        val instant = Instant.parse("2026-08-03T22:30:00Z")

        assertEquals(LocalDate(2026, 8, 4), instant.toLocalDate(berlin))
        assertEquals(LocalDate(2026, 8, 4), instant.toLocalDate(tokyo))
        assertEquals(LocalDate(2026, 8, 3), instant.toLocalDate(TimeZone.UTC))
    }

    @Test
    fun `a day contains the instants of that day in its own zone`() {
        val day = LocalDate(2026, 8, 3)
        val lateEvening = Instant.parse("2026-08-03T21:30:00Z")

        assertTrue(day.contains(lateEvening, TimeZone.UTC))
        assertFalse(day.contains(lateEvening, tokyo))
    }

    @Test
    fun `today is read from the injected clock`() {
        val clock = FixedClock(Instant.parse("2026-08-03T22:30:00Z"))

        assertEquals(LocalDate(2026, 8, 3), clock.today(TimeZone.UTC))
        assertEquals(LocalDate(2026, 8, 4), clock.today(berlin))
    }
}
