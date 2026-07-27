package app.calio.sync

import app.calio.model.Revision
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath

/**
 * A remote that is nothing but a folder.
 *
 * One file per change, never modified once written. That makes it work over anything that copies
 * files between machines — a synced cloud folder, a network share, a memory stick — with no server
 * to run and nothing to configure beyond a path. It is also the cheapest possible thing that
 * satisfies the contract, which is exactly what the contract was kept weak for.
 *
 * Files are named so that sorting them by name is a stable order, and the cursor is simply the last
 * name read. Two devices writing at the same moment cannot collide, because each name carries the
 * revision that produced it — which is unique by construction.
 */
class FolderRemoteSyncSource(
    private val directory: Path,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) : RemoteSyncSource {

    constructor(path: String, fileSystem: FileSystem = FileSystem.SYSTEM) :
        this(path.toPath(), fileSystem)

    private val mutex = Mutex()

    private val json = Json {
        // A folder written by a newer version must not stop an older one from reading the rest.
        ignoreUnknownKeys = true
    }

    override suspend fun push(envelopes: List<SyncEnvelope>): PushResult = mutex.withLock {
        if (envelopes.isEmpty()) return PushResult(accepted = 0)

        fileSystem.createDirectories(directory)
        envelopes.forEach { envelope ->
            val target = directory / envelope.fileName()
            // A change is written once and never touched again, so a file that already exists holds
            // the same content and rewriting it would only risk a torn read on the other side.
            if (!fileSystem.exists(target)) {
                fileSystem.write(target) { writeUtf8(json.encodeToString(envelope.toDto())) }
            }
        }

        PushResult(accepted = envelopes.size)
    }

    override suspend fun pull(cursor: String?, limit: Int): PullResult = mutex.withLock {
        if (!fileSystem.exists(directory)) return PullResult(emptyList(), cursor)

        val names = fileSystem.list(directory)
            .map { it.name }
            .filter { it.endsWith(SUFFIX) }
            .sorted()
            .let { all -> if (cursor == null) all else all.filter { it > cursor } }

        val page = names.take(limit)
        if (page.isEmpty()) return PullResult(emptyList(), cursor)

        // A file that cannot be read is skipped rather than fatal: half a folder is still better
        // than none, and the cursor moves past it so it does not block every later round.
        val envelopes = page.mapNotNull { name ->
            runCatching {
                json.decodeFromString<EnvelopeDto>(fileSystem.read(directory / name) { readUtf8() })
                    .toDomain()
            }.getOrNull()
        }

        PullResult(
            envelopes = envelopes,
            cursor = page.last(),
            hasMore = page.size < names.size,
        )
    }

    /**
     * The revision is already unique across devices, so it makes the name unique too. It is put
     * after the entity id rather than in front of it so that all versions of one record sort
     * together, which makes the folder readable by a human looking for a problem.
     */
    private fun SyncEnvelope.fileName(): String =
        "${revision.physicalMillis.toString().padStart(SORTABLE_DIGITS, '0')}-" +
            "${revision.counter}-${revision.deviceId.value}-$entityId$SUFFIX"

    private companion object {
        const val SUFFIX = ".calio.json"

        /** Enough digits for epoch milliseconds to stay sortable as text well past this century. */
        const val SORTABLE_DIGITS = 14
    }
}

@Serializable
private data class EnvelopeDto(
    val entityType: String,
    val entityId: String,
    val operation: String,
    val revision: String,
    val scheme: String,
    val ciphertext: String,
    val nonce: String? = null,
    val keyId: String? = null,
)

private fun SyncEnvelope.toDto() = EnvelopeDto(
    entityType = entityType.name,
    entityId = entityId,
    operation = operation.name,
    revision = revision.value,
    scheme = payload.scheme,
    ciphertext = payload.ciphertext,
    nonce = payload.nonce,
    keyId = payload.keyId,
)

private fun EnvelopeDto.toDomain() = SyncEnvelope(
    entityType = SyncedEntityType.valueOf(entityType),
    entityId = entityId,
    operation = SyncOperation.valueOf(operation),
    revision = Revision(revision),
    payload = SealedPayload(scheme = scheme, ciphertext = ciphertext, nonce = nonce, keyId = keyId),
)
