package app.calio.domain.planning

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IntervalsTest {

    @Test
    fun `overlapping blocks collapse into one`() {
        val merged = listOf(range(9, 12), range(11, 14)).mergeAdjacent()

        assertEquals(listOf(range(9, 14)), merged)
    }

    @Test
    fun `touching blocks collapse into one`() {
        val merged = listOf(range(9, 12), range(12, 14)).mergeAdjacent()

        assertEquals(listOf(range(9, 14)), merged)
    }

    @Test
    fun `separate blocks stay separate and are ordered`() {
        val merged = listOf(range(15, 16), range(9, 10)).mergeAdjacent()

        assertEquals(listOf(range(9, 10), range(15, 16)), merged)
    }

    @Test
    fun `a block contained in another disappears into it`() {
        val merged = listOf(range(9, 17), range(11, 12)).mergeAdjacent()

        assertEquals(listOf(range(9, 17)), merged)
    }

    @Test
    fun `subtracting nothing leaves the range whole`() {
        assertEquals(listOf(range(9, 17)), range(9, 17).minus(emptyList()))
    }

    @Test
    fun `a block in the middle splits the range in two`() {
        val remaining = range(9, 17).minus(listOf(range(12, 13)))

        assertEquals(listOf(range(9, 12), range(13, 17)), remaining)
    }

    @Test
    fun `a block covering everything leaves nothing`() {
        assertTrue(range(9, 17).minus(listOf(range(8, 18))).isEmpty())
    }

    @Test
    fun `a block at the edge only trims the range`() {
        assertEquals(listOf(range(12, 17)), range(9, 17).minus(listOf(range(7, 12))))
        assertEquals(listOf(range(9, 12)), range(9, 17).minus(listOf(range(12, 20))))
    }

    @Test
    fun `blocks outside the range are ignored`() {
        assertEquals(listOf(range(9, 17)), range(9, 17).minus(listOf(range(6, 8), range(18, 20))))
    }

    @Test
    fun `several blocks leave several gaps`() {
        val remaining = range(9, 18).minus(listOf(range(10, 11), range(13, 14), range(16, 17)))

        assertEquals(
            listOf(range(9, 10), range(11, 13), range(14, 16), range(17, 18)),
            remaining,
        )
    }

    @Test
    fun `unsorted blocks give the same result as sorted ones`() {
        val blocks = listOf(range(16, 17), range(10, 11), range(13, 14))

        assertEquals(range(9, 18).minus(blocks.sortedBy { it.start }), range(9, 18).minus(blocks))
    }
}
