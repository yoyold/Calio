package app.calio.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A remote that keeps everything in memory.
 *
 * It is a full implementation rather than a stub: an append-only log with a cursor is exactly what
 * the contract asks for, and having one that needs no infrastructure is what makes it possible to
 * exercise two devices against each other in a test that runs in milliseconds.
 *
 * The cursor is the position in the log, so a device that stops halfway resumes where it left off.
 */
class InMemoryRemoteSyncSource : RemoteSyncSource {

    private val mutex = Mutex()
    private val log = mutableListOf<SyncEnvelope>()

    val size: Int get() = log.size

    override suspend fun push(envelopes: List<SyncEnvelope>): PushResult = mutex.withLock {
        log += envelopes
        PushResult(accepted = envelopes.size)
    }

    override suspend fun pull(cursor: String?, limit: Int): PullResult = mutex.withLock {
        val from = cursor?.toIntOrNull() ?: 0
        val page = log.drop(from).take(limit)
        val next = from + page.size

        PullResult(
            envelopes = page,
            cursor = next.toString(),
            hasMore = next < log.size,
        )
    }
}
