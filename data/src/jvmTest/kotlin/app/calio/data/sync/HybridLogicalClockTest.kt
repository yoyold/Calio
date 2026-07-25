package app.calio.data.sync

import app.calio.data.TestEnvironment
import app.calio.data.testDevice
import app.calio.model.DeviceId
import app.calio.model.Revision
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class HybridLogicalClockTest {

    private val environment = TestEnvironment()
    private val clock = environment.revisions

    @AfterTest
    fun tearDown() = environment.close()

    @Test
    fun `every revision is strictly greater than the one before`() = runTest {
        val revisions = List(50) { clock.next() }

        assertEquals(revisions.sorted(), revisions)
        assertEquals(revisions.distinct().size, revisions.size)
    }

    @Test
    fun `a standing wall clock is compensated by the counter`() = runTest {
        val first = clock.next()
        val second = clock.next()

        assertEquals(first.physicalMillis, second.physicalMillis)
        assertEquals(first.counter + 1, second.counter)
        assertTrue(first < second)
    }

    @Test
    fun `the counter restarts once the wall clock moves on`() = runTest {
        clock.next()
        clock.next()
        environment.clock.advanceBy(5.milliseconds)

        val afterwards = clock.next()

        assertEquals(0, afterwards.counter)
    }

    @Test
    fun `a revision from the future pushes the clock past it`() = runTest {
        val fromAnotherDevice = Revision.of(
            physicalMillis = environment.clock.now().toEpochMilliseconds() + 60_000,
            counter = 4,
            deviceId = DeviceId("device-b"),
        )

        clock.observe(fromAnotherDevice)
        val local = clock.next()

        assertTrue(fromAnotherDevice < local)
    }

    @Test
    fun `a revision from the past leaves the clock alone`() = runTest {
        val before = clock.next()
        clock.observe(Revision.of(1_000, 0, DeviceId("device-b")))
        val after = clock.next()

        assertEquals(before.physicalMillis, after.physicalMillis)
        assertTrue(before < after)
    }

    @Test
    fun `the same millisecond from another device is still overtaken`() = runTest {
        val local = clock.next()
        val remote = Revision.of(local.physicalMillis, local.counter + 3, DeviceId("device-b"))

        clock.observe(remote)

        assertTrue(remote < clock.next())
    }

    @Test
    fun `the clock survives a restart without repeating a revision`() = runTest {
        val before = clock.next()

        val restarted = HybridLogicalClock(environment.database, testDevice, environment.clock)
        val after = restarted.next()

        assertTrue(before < after)
    }

    @Test
    fun `the device id is carried into every revision`() = runTest {
        assertEquals(testDevice, clock.next().deviceId)
    }
}
