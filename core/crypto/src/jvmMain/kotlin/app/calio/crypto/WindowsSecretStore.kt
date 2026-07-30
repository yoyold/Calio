package app.calio.crypto

import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Secrets sealed by Windows against the signed-in account.
 *
 * The data protection API derives its key from the Windows login, so the sealed bytes are useless on
 * another machine and to another user on this one — including anyone who copies the folder out of a
 * backup. That is exactly the property a refresh token needs and the reason the file lives outside
 * the database.
 */
class WindowsSecretStore(
    private val directory: Path,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SecretStore {

    override suspend fun put(key: String, secret: String): Unit = withContext(dispatcher) {
        Files.createDirectories(directory)
        val sealed = Crypt32Util.cryptProtectData(secret.encodeToByteArray())

        // Written beside the target and moved into place, so an interrupted write leaves the
        // previous secret intact rather than a half file that unseals to nothing.
        val pending = Files.createTempFile(directory, "secret", ".tmp")
        Files.write(pending, sealed)
        Files.move(pending, fileFor(key), StandardCopyOption.REPLACE_EXISTING)
    }

    override suspend fun get(key: String): String? = withContext(dispatcher) {
        val file = fileFor(key)
        if (!Files.exists(file)) return@withContext null

        // A secret that cannot be unsealed is gone: the Windows account changed, or the file was
        // carried over from another machine. Reporting nothing asks for a fresh sign-in, which is
        // the only thing that can actually help.
        runCatching { Crypt32Util.cryptUnprotectData(Files.readAllBytes(file)).decodeToString() }.getOrNull()
    }

    override suspend fun remove(key: String) {
        withContext(dispatcher) { Files.deleteIfExists(fileFor(key)) }
    }

    // The file name is a hash of the key, so a folder listing does not say which accounts exist.
    private fun fileFor(key: String): Path =
        directory.resolve(sha256(key.encodeToByteArray()).encodeBase64Url() + ".bin")
}

/** True when the data protection API is available, which is what [WindowsSecretStore] needs. */
val isWindows: Boolean
    get() = System.getProperty("os.name").orEmpty().startsWith("Windows")

/**
 * The store for the desktop application.
 *
 * There is deliberately no fallback to an unprotected file: a refresh token kept in the clear would
 * be worse than one the user has to enter again, and silently downgrading is the kind of decision
 * nobody finds out about until it matters.
 */
fun desktopSecretStore(directory: Path): SecretStore {
    check(isWindows) { "secure secret storage is only implemented for Windows" }
    return WindowsSecretStore(directory)
}
