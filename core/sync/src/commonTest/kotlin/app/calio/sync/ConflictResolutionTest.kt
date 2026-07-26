package app.calio.sync

import app.calio.model.DeviceId
import app.calio.model.Revision
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The full decision table.
 *
 * Convergence rests on this function returning the same answer on every device, so it is checked as
 * data rather than inferred from a synchronisation run.
 */
class ConflictResolutionTest {

    private val deviceA = DeviceId("device-a")
    private val deviceB = DeviceId("device-b")

    private fun revision(millis: Long, counter: Int = 0, device: DeviceId = deviceA) =
        Revision.of(millis, counter, device)

    @Test
    fun `an unknown record is taken as it comes`() {
        assertEquals(
            Resolution.Apply,
            resolveIncoming(local = null, remote = revision(100), hasPendingLocalChange = false),
        )
    }

    @Test
    fun `a newer remote change replaces a settled local one`() {
        assertEquals(
            Resolution.Apply,
            resolveIncoming(revision(100), revision(200), hasPendingLocalChange = false),
        )
    }

    @Test
    fun `an older remote change is ignored`() {
        assertEquals(
            Resolution.KeepLocal,
            resolveIncoming(revision(200), revision(100), hasPendingLocalChange = false),
        )
    }

    @Test
    fun `a change coming back to its own device is ignored`() {
        // Pushing puts the change on the remote; pulling brings the same revision back.
        val own = revision(200)

        assertEquals(Resolution.KeepLocal, resolveIncoming(own, own, hasPendingLocalChange = false))
        assertEquals(Resolution.KeepLocal, resolveIncoming(own, own, hasPendingLocalChange = true))
    }

    @Test
    fun `a newer remote change over an unsent local one is a conflict`() {
        assertEquals(
            Resolution.ApplyAndRecordConflict,
            resolveIncoming(revision(100), revision(200), hasPendingLocalChange = true),
        )
    }

    @Test
    fun `an older remote change is ignored even with an unsent local one`() {
        assertEquals(
            Resolution.KeepLocal,
            resolveIncoming(revision(300), revision(200), hasPendingLocalChange = true),
        )
    }

    @Test
    fun `the same millisecond is decided by the counter`() {
        assertEquals(
            Resolution.Apply,
            resolveIncoming(revision(100, counter = 1), revision(100, counter = 2), false),
        )
        assertEquals(
            Resolution.KeepLocal,
            resolveIncoming(revision(100, counter = 2), revision(100, counter = 1), false),
        )
    }

    @Test
    fun `the device id breaks a complete tie, the same way everywhere`() {
        val fromA = revision(100, 0, deviceA)
        val fromB = revision(100, 0, deviceB)

        // On A the change from B is newer; on B the change from A is older. Both devices therefore
        // end up on B's version without ever comparing notes.
        assertEquals(Resolution.Apply, resolveIncoming(local = fromA, remote = fromB, false))
        assertEquals(Resolution.KeepLocal, resolveIncoming(local = fromB, remote = fromA, false))
    }

    @Test
    fun `a pending local change alone does not stop an incoming one`() {
        // A pending change is not a veto: it decides whether the loss is recorded, nothing more.
        assertEquals(
            Resolution.ApplyAndRecordConflict,
            resolveIncoming(revision(100), revision(101), hasPendingLocalChange = true),
        )
    }
}
