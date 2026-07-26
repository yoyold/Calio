package app.calio.domain.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OverlapLayoutCalculatorTest {

    private val calculator = OverlapLayoutCalculator()

    private fun layout(vararg occurrences: Pair<String, IntRange>) = calculator(
        occurrences.map { (id, hours) ->
            occurrence(id = id, start = at(hours.first), endExclusive = at(hours.last))
        },
    ).associateBy { it.occurrence.eventId.value }

    @Test
    fun `an empty day produces nothing`() {
        assertTrue(calculator(emptyList()).isEmpty())
    }

    @Test
    fun `a single appointment takes the whole width`() {
        val single = layout("only" to 9..10).getValue("only")

        assertEquals(0, single.column)
        assertEquals(1, single.columnCount)
    }

    @Test
    fun `appointments that follow each other both take the whole width`() {
        val result = layout("first" to 9..10, "second" to 10..11)

        assertEquals(1, result.getValue("first").columnCount)
        assertEquals(1, result.getValue("second").columnCount)
        assertEquals(0, result.getValue("first").column)
        assertEquals(0, result.getValue("second").column)
    }

    @Test
    fun `two overlapping appointments split the width`() {
        val result = layout("left" to 9..11, "right" to 10..12)

        assertEquals(setOf(0, 1), result.values.map { it.column }.toSet())
        assertTrue(result.values.all { it.columnCount == 2 })
    }

    @Test
    fun `a chain of overlaps forms one cluster of equal width`() {
        // A overlaps B and B overlaps C, but A and C only touch. All three still share a width, so
        // the row does not change shape halfway down.
        val result = layout("a" to 9..11, "b" to 10..12, "c" to 11..13)

        assertTrue(result.values.all { it.columnCount == 2 })
        assertEquals(0, result.getValue("a").column)
        assertEquals(1, result.getValue("b").column)
        assertEquals(0, result.getValue("c").column)
    }

    @Test
    fun `three appointments at the same time take a third each`() {
        val result = layout("a" to 9..10, "b" to 9..10, "c" to 9..10)

        assertEquals(setOf(0, 1, 2), result.values.map { it.column }.toSet())
        assertTrue(result.values.all { it.columnCount == 3 })
    }

    @Test
    fun `separate clusters are counted separately`() {
        val result = layout("morning-a" to 9..11, "morning-b" to 10..12, "evening" to 16..17)

        assertEquals(2, result.getValue("morning-a").columnCount)
        assertEquals(1, result.getValue("evening").columnCount)
        assertEquals(0, result.getValue("evening").column)
    }

    @Test
    fun `a column is reused once the appointment in it has ended`() {
        val result = layout("long" to 9..17, "early" to 10..11, "late" to 12..13)

        assertEquals(0, result.getValue("long").column)
        assertEquals(1, result.getValue("early").column)
        assertEquals(1, result.getValue("late").column)
        assertTrue(result.values.all { it.columnCount == 2 })
    }

    @Test
    fun `a contained appointment sits beside its container`() {
        val result = layout("container" to 9..17, "inside" to 11..12)

        assertEquals(0, result.getValue("container").column)
        assertEquals(1, result.getValue("inside").column)
    }

    @Test
    fun `the input order does not change the result`() {
        val ordered = layout("a" to 9..11, "b" to 10..12, "c" to 16..17)
        val shuffled = layout("c" to 16..17, "b" to 10..12, "a" to 9..11)

        assertEquals(
            ordered.mapValues { it.value.column to it.value.columnCount },
            shuffled.mapValues { it.value.column to it.value.columnCount },
        )
    }

    @Test
    fun `every occurrence is placed exactly once`() {
        val input = listOf(
            occurrence(id = "a", start = at(9), endExclusive = at(11)),
            occurrence(id = "b", start = at(10), endExclusive = at(12)),
            occurrence(id = "c", start = at(11), endExclusive = at(13)),
            occurrence(id = "d", start = at(16), endExclusive = at(17)),
        )

        val result = calculator(input)

        assertEquals(input.size, result.size)
        assertEquals(input.map { it.eventId }.toSet(), result.map { it.occurrence.eventId }.toSet())
    }

    @Test
    fun `a column index is always inside the column count`() {
        val result = layout("a" to 9..17, "b" to 10..11, "c" to 10..12, "d" to 13..14)

        assertTrue(result.values.all { it.column in 0 until it.columnCount })
    }
}
