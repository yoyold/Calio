package app.calio.domain.planning

import app.calio.model.BusyStatus
import app.calio.model.EventKind
import app.calio.model.EventStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConflictDetectorTest {

    private val detector = ConflictDetector()

    private val morning = occurrence(id = "morning", start = at(9), endExclusive = at(10))

    @Test
    fun `appointments that only touch do not conflict`() {
        val afterwards = occurrence(id = "afterwards", start = at(10), endExclusive = at(11))

        assertTrue(detector.conflictsFor(morning, listOf(afterwards)).isEmpty())
        assertTrue(detector.allConflicts(listOf(morning, afterwards)).isEmpty())
    }

    @Test
    fun `overlapping appointments conflict`() {
        val overlapping = occurrence(id = "overlapping", start = at(9, 30), endExclusive = at(10, 30))

        assertEquals(listOf("overlapping"), detector.conflictsFor(morning, listOf(overlapping)).map { it.eventId.value })
        assertTrue(detector.hasConflict(morning, listOf(overlapping)))
    }

    @Test
    fun `the reported overlap is the shared stretch only`() {
        val overlapping = occurrence(id = "overlapping", start = at(9, 30), endExclusive = at(11))

        val conflict = detector.allConflicts(listOf(morning, overlapping)).single()

        assertEquals(range(9, 10).endExclusive, conflict.overlap.endExclusive)
        assertEquals(at(9, 30), (conflict.second.timeRange as app.calio.model.EventTimeRange.Zoned).start)
    }

    @Test
    fun `an appointment does not conflict with itself`() {
        assertTrue(detector.conflictsFor(morning, listOf(morning)).isEmpty())
    }

    @Test
    fun `an appointment marked free never conflicts`() {
        val free = occurrence(
            id = "free",
            start = at(9, 30),
            endExclusive = at(10, 30),
            busyStatus = BusyStatus.FREE,
        )

        assertTrue(detector.conflictsFor(morning, listOf(free)).isEmpty())
        assertTrue(detector.conflictsFor(free, listOf(morning)).isEmpty())
    }

    @Test
    fun `a cancelled appointment never conflicts`() {
        val cancelled = occurrence(
            id = "cancelled",
            start = at(9, 30),
            endExclusive = at(10, 30),
            status = EventStatus.CANCELLED,
        )

        assertTrue(detector.conflictsFor(morning, listOf(cancelled)).isEmpty())
    }

    @Test
    fun `buffers never conflict, since they exist to be pushed aside`() {
        val buffer = occurrence(
            id = "buffer",
            start = at(9, 30),
            endExclusive = at(10, 30),
            kind = EventKind.BUFFER,
        )

        assertTrue(detector.conflictsFor(morning, listOf(buffer)).isEmpty())
        assertTrue(detector.allConflicts(listOf(morning, buffer)).isEmpty())
    }

    @Test
    fun `focus blocks do conflict, because they are meant to protect time`() {
        val focus = occurrence(
            id = "focus",
            start = at(9, 30),
            endExclusive = at(11),
            kind = EventKind.FOCUS,
        )

        assertTrue(detector.hasConflict(morning, listOf(focus)))
    }

    @Test
    fun `each colliding pair is reported once`() {
        val second = occurrence(id = "second", start = at(9, 30), endExclusive = at(10, 30))
        val third = occurrence(id = "third", start = at(9, 45), endExclusive = at(11))

        val conflicts = detector.allConflicts(listOf(morning, second, third))

        assertEquals(3, conflicts.size)
        assertEquals(
            setOf(setOf("morning", "second"), setOf("morning", "third"), setOf("second", "third")),
            conflicts.map { setOf(it.first.eventId.value, it.second.eventId.value) }.toSet(),
        )
    }

    @Test
    fun `an appointment contained in another conflicts with it`() {
        val allMorning = occurrence(id = "long", start = at(8), endExclusive = at(12))

        assertTrue(detector.hasConflict(morning, listOf(allMorning)))
        assertTrue(detector.hasConflict(allMorning, listOf(morning)))
    }

    @Test
    fun `an all day entry conflicts with what happens during it`() {
        val allDay = allDayOccurrence()

        assertTrue(detector.hasConflict(morning, listOf(allDay)))
    }

    @Test
    fun `a day without collisions reports none`() {
        val occurrences = listOf(
            morning,
            occurrence(id = "noon", start = at(12), endExclusive = at(13)),
            occurrence(id = "late", start = at(16), endExclusive = at(17)),
        )

        assertTrue(detector.allConflicts(occurrences).isEmpty())
        assertFalse(detector.hasConflict(occurrences[1], occurrences))
    }
}
