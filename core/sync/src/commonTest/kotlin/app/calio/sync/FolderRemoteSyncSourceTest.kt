package app.calio.sync

import app.calio.model.DeviceId
import app.calio.model.Revision
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FolderRemoteSyncSourceTest {

    private val fileSystem = FakeFileSystem()
    private val folder = "/shared/calio".toPath()

    private val remote = FolderRemoteSyncSource(folder, fileSystem)

    @AfterTest
    fun tearDown() = fileSystem.close()

    private fun envelope(
        id: String,
        millis: Long,
        counter: Int = 0,
        device: String = "device-a",
        payload: String = "payload for $id",
    ) = SyncEnvelope(
        entityType = SyncedEntityType.EVENT,
        entityId = id,
        operation = SyncOperation.UPSERT,
        revision = Revision.of(millis, counter, DeviceId(device)),
        payload = SealedPayload(scheme = "plain", ciphertext = payload),
    )

    @Test
    fun `an empty folder has nothing to give`() = runTest {
        val page = remote.pull(cursor = null)

        assertTrue(page.envelopes.isEmpty())
        assertTrue(!page.hasMore)
    }

    @Test
    fun `what was pushed comes back whole`() = runTest {
        val original = envelope("event-1", millis = 100)

        remote.push(listOf(original))

        assertEquals(listOf(original), remote.pull(cursor = null).envelopes)
    }

    @Test
    fun `one file is written per change`() = runTest {
        remote.push(listOf(envelope("event-1", 100), envelope("event-2", 200)))

        assertEquals(2, fileSystem.list(folder).size)
    }

    @Test
    fun `changes come back in the order their revisions were made`() = runTest {
        remote.push(listOf(envelope("c", 300), envelope("a", 100), envelope("b", 200)))

        val ids = remote.pull(cursor = null).envelopes.map { it.entityId }

        assertEquals(listOf("a", "b", "c"), ids)
    }

    @Test
    fun `pushing the same change twice leaves one file`() = runTest {
        // A round that was interrupted after writing but before acknowledging repeats itself.
        val same = envelope("event-1", 100)

        remote.push(listOf(same))
        remote.push(listOf(same))

        assertEquals(1, fileSystem.list(folder).size)
        assertEquals(1, remote.pull(cursor = null).envelopes.size)
    }

    @Test
    fun `the cursor resumes where the last round stopped`() = runTest {
        remote.push(listOf(envelope("a", 100), envelope("b", 200)))
        val first = remote.pull(cursor = null)

        remote.push(listOf(envelope("c", 300)))
        val second = remote.pull(first.cursor)

        assertEquals(listOf("c"), second.envelopes.map { it.entityId })
    }

    @Test
    fun `a full page says there is more to come`() = runTest {
        remote.push(listOf(envelope("a", 100), envelope("b", 200), envelope("c", 300)))

        val page = remote.pull(cursor = null, limit = 2)

        assertEquals(2, page.envelopes.size)
        assertTrue(page.hasMore)
        assertTrue(!remote.pull(page.cursor, limit = 2).hasMore)
    }

    @Test
    fun `two devices see each other through the same folder`() = runTest {
        val other = FolderRemoteSyncSource(folder, fileSystem)

        remote.push(listOf(envelope("from-a", 100, device = "device-a")))
        other.push(listOf(envelope("from-b", 200, device = "device-b")))

        assertEquals(
            listOf("from-a", "from-b"),
            other.pull(cursor = null).envelopes.map { it.entityId },
        )
    }

    @Test
    fun `changes from the same millisecond stay apart`() = runTest {
        remote.push(
            listOf(
                envelope("a", 100, counter = 0, device = "device-a"),
                envelope("b", 100, counter = 0, device = "device-b"),
                envelope("c", 100, counter = 1, device = "device-a"),
            ),
        )

        assertEquals(3, remote.pull(cursor = null).envelopes.size)
    }

    @Test
    fun `a file that cannot be read is skipped rather than fatal`() = runTest {
        // Half a folder is more use than none, and the cursor moves past the damage.
        remote.push(listOf(envelope("good", 100)))
        fileSystem.write(folder / "00000000000200-0-device-a-broken.calio.json") {
            writeUtf8("{ not json")
        }

        val page = remote.pull(cursor = null)

        assertEquals(listOf("good"), page.envelopes.map { it.entityId })
    }

    @Test
    fun `files that are not ours are ignored`() = runTest {
        fileSystem.createDirectories(folder)
        fileSystem.write(folder / "desktop.ini") { writeUtf8("something else entirely") }
        remote.push(listOf(envelope("event-1", 100)))

        assertEquals(listOf("event-1"), remote.pull(cursor = null).envelopes.map { it.entityId })
    }

    @Test
    fun `pushing nothing does not create a folder`() = runTest {
        remote.push(emptyList())

        assertTrue(!fileSystem.exists(folder))
    }
}
