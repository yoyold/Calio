package app.calio.domain.planning

import app.calio.datetime.DateRange
import app.calio.model.BusyStatus
import app.calio.model.DayWindow
import app.calio.model.WorkingHours
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

class FreeSlotFinderTest {

    private val finder = FreeSlotFinder()

    // 3 August 2026 is a Monday.
    private val monday = DateRange.singleDay(august3)

    private val nineToFive = WorkingHours(
        mapOf(DayOfWeek.MONDAY to DayWindow(LocalTime(9, 0), LocalTime(17, 0))),
    )

    @Test
    fun `an empty working day is one long free slot`() {
        val slots = finder(monday, nineToFive, emptyList(), utc)

        assertEquals(listOf(range(9, 17)), slots.map { it.range })
        assertEquals(august3, slots.single().date)
    }

    @Test
    fun `a non working day offers nothing`() {
        val sunday = DateRange.singleDay(LocalDate(2026, 8, 2))

        assertTrue(finder(sunday, nineToFive, emptyList(), utc).isEmpty())
    }

    @Test
    fun `an appointment in the middle leaves a slot on each side`() {
        val meeting = occurrence(start = at(12), endExclusive = at(13))

        val slots = finder(monday, nineToFive, listOf(meeting), utc)

        assertEquals(listOf(range(9, 12), range(13, 17)), slots.map { it.range })
    }

    @Test
    fun `a day filled end to end offers nothing`() {
        val allDayMeeting = occurrence(start = at(8), endExclusive = at(18))

        assertTrue(finder(monday, nineToFive, listOf(allDayMeeting), utc).isEmpty())
    }

    @Test
    fun `appointments outside the working hours do not shorten the day`() {
        val earlyBird = occurrence(id = "early", start = at(6), endExclusive = at(8))
        val evening = occurrence(id = "evening", start = at(19), endExclusive = at(20))

        val slots = finder(monday, nineToFive, listOf(earlyBird, evening), utc)

        assertEquals(listOf(range(9, 17)), slots.map { it.range })
    }

    @Test
    fun `overlapping appointments only block their combined stretch`() {
        val first = occurrence(id = "first", start = at(10), endExclusive = at(12))
        val second = occurrence(id = "second", start = at(11), endExclusive = at(13))

        val slots = finder(monday, nineToFive, listOf(first, second), utc)

        assertEquals(listOf(range(9, 10), range(13, 17)), slots.map { it.range })
    }

    @Test
    fun `an appointment marked free does not block anything`() {
        val optional = occurrence(start = at(12), endExclusive = at(13), busyStatus = BusyStatus.FREE)

        assertEquals(listOf(range(9, 17)), finder(monday, nineToFive, listOf(optional), utc).map { it.range })
    }

    @Test
    fun `an all day entry that occupies the day leaves nothing`() {
        val vacation = allDayOccurrence()

        assertTrue(finder(monday, nineToFive, listOf(vacation), utc).isEmpty())
    }

    @Test
    fun `slots shorter than the minimum are not offered`() {
        val before = occurrence(id = "before", start = at(9), endExclusive = at(12))
        val after = occurrence(id = "after", start = at(12, 10), endExclusive = at(17))

        val withDefault = finder(monday, nineToFive, listOf(before, after), utc)
        val withShortMinimum = finder(monday, nineToFive, listOf(before, after), utc, 5.minutes)

        assertTrue(withDefault.isEmpty())
        assertEquals(10.minutes, withShortMinimum.single().duration)
    }

    @Test
    fun `several days are reported separately`() {
        val workingWeek = WorkingHours(
            mapOf(
                DayOfWeek.MONDAY to DayWindow(LocalTime(9, 0), LocalTime(17, 0)),
                DayOfWeek.TUESDAY to DayWindow(LocalTime(9, 0), LocalTime(12, 0)),
            ),
        )
        val twoDays = DateRange(august3, LocalDate(2026, 8, 6))

        val slots = finder(twoDays, workingWeek, emptyList(), utc)

        assertEquals(listOf(august3, LocalDate(2026, 8, 4)), slots.map { it.date })
        assertEquals(listOf(8.hours, 3.hours), slots.map { it.duration })
    }

    @Test
    fun `a working day shortened by daylight saving is genuinely shorter`() {
        // Europe/Berlin skips the hour from 02:00 to 03:00 on Sunday 29 March 2026.
        val nightShift = WorkingHours(
            mapOf(DayOfWeek.SUNDAY to DayWindow(LocalTime(1, 0), LocalTime(6, 0))),
        )
        val transitionDay = DateRange.singleDay(LocalDate(2026, 3, 29))

        val slot = finder(transitionDay, nightShift, emptyList(), berlinZone).single()

        assertEquals(4.hours, slot.duration)
    }
}
