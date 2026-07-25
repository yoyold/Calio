package app.calio.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RevisionTest {

    private val deviceA = DeviceId("2f1c9d0e-aaaa-4f00-8000-000000000001")
    private val deviceB = DeviceId("2f1c9d0e-bbbb-4f00-8000-000000000002")

    @Test
    fun `parses its components back out`() {
        val revision = Revision.of(physicalMillis = 1_753_400_000_000, counter = 3, deviceId = deviceA)

        assertEquals(1_753_400_000_000, revision.physicalMillis)
        assertEquals(3, revision.counter)
        assertEquals(deviceA, revision.deviceId)
    }

    @Test
    fun `keeps device ids that contain dashes intact`() {
        val revision = Revision.of(physicalMillis = 1, counter = 0, deviceId = deviceA)

        assertEquals(deviceA, revision.deviceId)
    }

    @Test
    fun `orders by physical time first`() {
        val earlier = Revision.of(physicalMillis = 100, counter = 9, deviceId = deviceB)
        val later = Revision.of(physicalMillis = 200, counter = 0, deviceId = deviceA)

        assertTrue(earlier < later)
    }

    @Test
    fun `orders by counter when the physical time is equal`() {
        val first = Revision.of(physicalMillis = 100, counter = 1, deviceId = deviceB)
        val second = Revision.of(physicalMillis = 100, counter = 2, deviceId = deviceA)

        assertTrue(first < second)
    }

    @Test
    fun `falls back to the device id so the ordering is total`() {
        val fromA = Revision.of(physicalMillis = 100, counter = 1, deviceId = deviceA)
        val fromB = Revision.of(physicalMillis = 100, counter = 1, deviceId = deviceB)

        assertTrue(fromA < fromB)
        assertTrue(fromB > fromA)
    }

    @Test
    fun `compares equal only for identical readings`() {
        val one = Revision.of(physicalMillis = 100, counter = 1, deviceId = deviceA)
        val other = Revision.of(physicalMillis = 100, counter = 1, deviceId = deviceA)

        assertEquals(0, one.compareTo(other))
        assertEquals(one, other)
    }

    @Test
    fun `sorting is independent of the input order`() {
        val revisions = listOf(
            Revision.of(200, 0, deviceB),
            Revision.of(100, 5, deviceA),
            Revision.of(200, 0, deviceA),
            Revision.of(100, 4, deviceB),
        )

        assertEquals(revisions.sorted(), revisions.shuffled().sorted())
    }
}
