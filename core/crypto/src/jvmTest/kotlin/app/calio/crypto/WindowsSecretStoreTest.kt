package app.calio.crypto

import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteRecursively
import kotlin.io.path.listDirectoryEntries
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The Windows data protection API is the real thing here rather than a stand-in, because what is
 * being checked is that a secret written by this application can be read back by it — and nothing
 * short of the actual interface can answer that.
 */
class WindowsSecretStoreTest {

    private val directory: Path = Files.createTempDirectory("calio-secrets")
    private val store = WindowsSecretStore(directory)

    @OptIn(kotlin.io.path.ExperimentalPathApi::class)
    @AfterTest
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `a secret survives being written and read`() = runTest {
        if (!isWindows) return@runTest

        store.put("oauth:account-1", "1//refresh-token-value")

        assertEquals("1//refresh-token-value", store.get("oauth:account-1"))
    }

    @Test
    fun `an unknown key has no secret`() = runTest {
        if (!isWindows) return@runTest

        assertNull(store.get("oauth:nobody"))
    }

    @Test
    fun `writing again replaces the secret and leaves no leftovers`() = runTest {
        if (!isWindows) return@runTest

        store.put("oauth:account-1", "first")
        store.put("oauth:account-1", "second")

        assertEquals("second", store.get("oauth:account-1"))
        assertEquals(1, directory.listDirectoryEntries().size)
    }

    @Test
    fun `removing leaves nothing behind`() = runTest {
        if (!isWindows) return@runTest

        store.put("oauth:account-1", "refresh-token")

        store.remove("oauth:account-1")

        assertNull(store.get("oauth:account-1"))
        assertTrue(directory.listDirectoryEntries().isEmpty())
    }

    @Test
    fun `the stored file does not contain the secret`() = runTest {
        if (!isWindows) return@runTest

        store.put("oauth:account-1", "1//refresh-token-value")

        val stored = directory.listDirectoryEntries().single()
        assertTrue("1//refresh-token-value" !in Files.readAllBytes(stored).decodeToString())
    }

    @Test
    fun `the file name does not reveal which account it belongs to`() = runTest {
        if (!isWindows) return@runTest

        store.put("oauth:someone@example.com", "refresh-token")

        val stored = directory.listDirectoryEntries().single()
        assertTrue("someone" !in stored.fileName.toString())
    }

    @Test
    fun `a damaged file reads as no secret rather than failing`() = runTest {
        if (!isWindows) return@runTest

        store.put("oauth:account-1", "refresh-token")
        val stored = directory.listDirectoryEntries().single()
        Files.write(stored, byteArrayOf(1, 2, 3, 4))

        assertNull(store.get("oauth:account-1"))
    }

    @Test
    fun `two keys do not collide`() = runTest {
        if (!isWindows) return@runTest

        store.put("oauth:account-1", "first")
        store.put("oauth:account-2", "second")

        assertContentEquals(
            listOf("first", "second"),
            listOf(store.get("oauth:account-1"), store.get("oauth:account-2")),
        )
    }
}
