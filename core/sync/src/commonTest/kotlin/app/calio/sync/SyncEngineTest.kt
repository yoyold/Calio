package app.calio.sync

import app.calio.model.DeviceId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class SyncEngineTest {

    private val remote = InMemoryRemoteSyncSource()

    private val clock = object : Clock {
        override fun now(): Instant = Instant.parse("2026-08-05T08:30:00Z")
    }

    private val deviceA = Device("device-a")
    private val deviceB = Device("device-b")

    private inner class Device(name: String) {
        val store = InMemorySyncStore(DeviceId(name))
        val engine = SyncEngine(store, remote, PassthroughCipher(), clock)

        suspend fun sync() = engine.sync()

        fun edit(id: String, payload: String?, atMillis: Long) =
            store.edit(SyncedEntityType.EVENT, id, payload, atMillis)

        fun payload(id: String) = store.allRecords[SyncedEntityType.EVENT to id]?.payload

        fun revision(id: String) = store.allRecords[SyncedEntityType.EVENT to id]?.revision
    }

    @Test
    fun `nothing to do is not an error`() = runTest {
        val outcome = deviceA.sync()

        assertEquals(SyncOutcome(), outcome)
        assertTrue(!outcome.changedAnything)
    }

    @Test
    fun `a local change reaches the other device`() = runTest {
        deviceA.edit("event-1", "Standup", atMillis = 100)

        deviceA.sync()
        deviceB.sync()

        assertEquals("Standup", deviceB.payload("event-1"))
        assertEquals(deviceA.revision("event-1"), deviceB.revision("event-1"))
    }

    @Test
    fun `a change already sent is not sent again`() = runTest {
        deviceA.edit("event-1", "Standup", atMillis = 100)

        deviceA.sync()
        val second = deviceA.sync()

        assertEquals(0, second.pushed)
        assertEquals(1, remote.size)
    }

    @Test
    fun `a device ignores its own change coming back`() = runTest {
        // Pushing puts the change in the log, and the same round pulls it straight back.
        deviceA.edit("event-1", "Standup", atMillis = 100)

        val first = deviceA.sync()
        val second = deviceA.sync()

        assertEquals(0, first.applied)
        assertEquals(1, first.skipped)
        assertEquals(SyncOutcome(), second)
    }

    @Test
    fun `a deletion travels as a tombstone`() = runTest {
        deviceA.edit("event-1", "Standup", atMillis = 100)
        deviceA.sync()
        deviceB.sync()

        deviceA.edit("event-1", payload = null, atMillis = 200)
        deviceA.sync()
        deviceB.sync()

        assertNull(deviceB.payload("event-1"))
        assertEquals(deviceA.revision("event-1"), deviceB.revision("event-1"))
    }

    @Test
    fun `the later edit wins and both devices agree on it`() = runTest {
        deviceA.edit("event-1", "From A", atMillis = 100)
        deviceB.edit("event-1", "From B", atMillis = 200)

        deviceA.sync()
        deviceB.sync()
        deviceA.sync()

        assertEquals("From B", deviceA.payload("event-1"))
        assertEquals("From B", deviceB.payload("event-1"))
    }

    @Test
    fun `the version that lost is kept so it can be restored`() = runTest {
        // Both edit the same record while offline; A's edit is older and therefore loses.
        deviceA.edit("event-1", "From A", atMillis = 100)
        deviceB.edit("event-1", "From B", atMillis = 200)
        deviceB.sync()

        val outcome = deviceA.sync()

        assertEquals(1, outcome.conflicts)
        val conflict = deviceA.store.conflicts.single()
        assertEquals("From A", conflict.discardedPayload)
        assertEquals("From B", deviceA.payload("event-1"))
    }

    @Test
    fun `an edit that wins raises no conflict`() = runTest {
        deviceA.edit("event-1", "From A", atMillis = 300)
        deviceB.edit("event-1", "From B", atMillis = 100)
        deviceB.sync()

        val outcome = deviceA.sync()

        assertEquals(0, outcome.conflicts)
        assertEquals("From A", deviceA.payload("event-1"))
    }

    @Test
    fun `two devices converge whichever order they sync in`() = runTest {
        deviceA.edit("event-1", "A one", atMillis = 100)
        deviceB.edit("event-2", "B one", atMillis = 110)
        deviceA.edit("event-1", "A two", atMillis = 120)
        deviceB.edit("event-1", "B two", atMillis = 130)

        // Two rounds each, interleaved, is enough for both to have seen everything.
        deviceB.sync()
        deviceA.sync()
        deviceB.sync()
        deviceA.sync()

        assertEquals(deviceA.store.allRecords, deviceB.store.allRecords)
        assertEquals("B two", deviceA.payload("event-1"))
        assertEquals("B one", deviceA.payload("event-2"))
    }

    @Test
    fun `syncing repeatedly changes nothing further`() = runTest {
        deviceA.edit("event-1", "Standup", atMillis = 100)
        deviceA.sync()
        deviceB.sync()
        val settled = deviceB.store.allRecords

        repeat(3) {
            deviceA.sync()
            deviceB.sync()
        }

        assertEquals(settled, deviceB.store.allRecords)
        assertEquals(settled, deviceA.store.allRecords)
    }

    @Test
    fun `a local edit after receiving one is ordered after it`() = runTest {
        // The clock must be pushed past anything seen from elsewhere, or the next local edit could
        // sort before a change this device has already applied.
        deviceB.edit("event-1", "From B", atMillis = 500)
        deviceB.sync()
        deviceA.sync()

        val afterwards = deviceA.edit("event-1", "From A afterwards", atMillis = 10)

        assertTrue(deviceB.revision("event-1")!! < afterwards)
    }

    @Test
    fun `pages are pulled until the remote runs out`() = runTest {
        val small = SyncEngine(deviceB.store, remote, PassthroughCipher(), clock, pageSize = 2)
        repeat(5) { index -> deviceA.edit("event-$index", "Entry $index", atMillis = 100L + index) }
        deviceA.sync()

        val outcome = small.sync()

        assertEquals(5, outcome.applied)
        assertEquals(5, deviceB.store.allRecords.size)
    }

    @Test
    fun `a payload that cannot be opened is left alone rather than applied empty`() = runTest {
        // Standing in for a record encrypted with a key this device does not have.
        remote.push(
            listOf(
                SyncEnvelope(
                    entityType = SyncedEntityType.EVENT,
                    entityId = "event-1",
                    operation = SyncOperation.UPSERT,
                    revision = app.calio.model.Revision.of(100, 0, DeviceId("device-c")),
                    payload = SealedPayload(scheme = "aes-gcm", ciphertext = "unreadable"),
                ),
            ),
        )

        val outcome = deviceA.sync()

        assertEquals(0, outcome.applied)
        assertEquals(1, outcome.skipped)
        assertNull(deviceA.payload("event-1"))
    }

    @Test
    fun `the local-only remote accepts everything and returns nothing`() = runTest {
        val offline = SyncEngine(deviceA.store, NoOpRemoteSyncSource(), PassthroughCipher(), clock)
        deviceA.edit("event-1", "Standup", atMillis = 100)

        val outcome = offline.sync()

        assertEquals(1, outcome.pushed)
        assertEquals(0, outcome.applied)
        assertEquals(0, remote.size)
    }
}
