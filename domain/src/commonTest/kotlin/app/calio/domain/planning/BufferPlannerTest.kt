package app.calio.domain.planning

import app.calio.model.BufferPolicy
import app.calio.model.BusyStatus
import app.calio.model.EventKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes

class BufferPlannerTest {

    private val planner = BufferPlanner()

    private val policy = BufferPolicy(
        isEnabled = true,
        defaultMinutes = 10,
        minimumGapMinutes = 5,
        maximumGapMinutes = 120,
    )

    private val firstMeeting = occurrence(id = "first", start = at(9), endExclusive = at(10))

    @Test
    fun `nothing is suggested while buffers are switched off`() {
        val second = occurrence(id = "second", start = at(11), endExclusive = at(12))

        assertTrue(planner(listOf(firstMeeting, second), policy.copy(isEnabled = false)).isEmpty())
    }

    @Test
    fun `a buffer fills the start of a comfortable gap`() {
        val second = occurrence(id = "second", start = at(11), endExclusive = at(12))

        val suggestion = planner(listOf(firstMeeting, second), policy).single()

        assertEquals(range(10, 10).start, suggestion.range.start)
        assertEquals(10.minutes, suggestion.range.duration)
        assertEquals("first", suggestion.after.value)
        assertEquals("second", suggestion.before.value)
    }

    @Test
    fun `a gap narrower than the buffer is filled completely`() {
        val second = occurrence(id = "second", start = at(10, 6), endExclusive = at(11))

        val suggestion = planner(listOf(firstMeeting, second), policy).single()

        assertEquals(6.minutes, suggestion.range.duration)
    }

    @Test
    fun `back to back appointments get nothing, because there is no room`() {
        val second = occurrence(id = "second", start = at(10), endExclusive = at(11))

        assertTrue(planner(listOf(firstMeeting, second), policy).isEmpty())
    }

    @Test
    fun `a gap below the minimum is left alone`() {
        val second = occurrence(id = "second", start = at(10, 3), endExclusive = at(11))

        assertTrue(planner(listOf(firstMeeting, second), policy).isEmpty())
    }

    @Test
    fun `a gap wider than the maximum needs no protecting`() {
        val second = occurrence(id = "second", start = at(15), endExclusive = at(16))

        assertTrue(planner(listOf(firstMeeting, second), policy).isEmpty())
    }

    @Test
    fun `existing buffers are not padded again`() {
        val buffer = occurrence(
            id = "buffer",
            start = at(10, 30),
            endExclusive = at(10, 40),
            kind = EventKind.BUFFER,
        )
        val second = occurrence(id = "second", start = at(11), endExclusive = at(12))

        val suggestions = planner(listOf(firstMeeting, buffer, second), policy)

        assertEquals(1, suggestions.size)
        assertEquals("second", suggestions.single().before.value)
    }

    @Test
    fun `appointments marked free are not padded`() {
        val free = occurrence(
            id = "free",
            start = at(11),
            endExclusive = at(12),
            busyStatus = BusyStatus.FREE,
        )

        assertTrue(planner(listOf(firstMeeting, free), policy).isEmpty())
    }

    @Test
    fun `overlapping appointments are measured from the later end`() {
        val overlapping = occurrence(id = "overlapping", start = at(9, 30), endExclusive = at(10, 30))
        val third = occurrence(id = "third", start = at(11), endExclusive = at(12))

        val suggestion = planner(listOf(firstMeeting, overlapping, third), policy).single()

        assertEquals("overlapping", suggestion.after.value)
        assertEquals(10.minutes, suggestion.range.duration)
    }

    @Test
    fun `a single appointment has no transition to protect`() {
        assertTrue(planner(listOf(firstMeeting), policy).isEmpty())
    }

    @Test
    fun `all day entries are skipped unless the policy asks for them`() {
        val allDay = allDayOccurrence()
        val second = occurrence(id = "second", start = at(11), endExclusive = at(12))

        assertEquals(
            listOf("first"),
            planner(listOf(allDay, firstMeeting, second), policy).map { it.after.value },
        )
    }

    @Test
    fun `each gap in a chain gets its own suggestion`() {
        val second = occurrence(id = "second", start = at(11), endExclusive = at(12))
        val third = occurrence(id = "third", start = at(13), endExclusive = at(14))

        val suggestions = planner(listOf(firstMeeting, second, third), policy)

        assertEquals(listOf("first" to "second", "second" to "third"), suggestions.map { it.after.value to it.before.value })
    }
}
