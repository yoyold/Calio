package app.calio.sync

/**
 * Where synchronised changes are stored between devices.
 *
 * The contract is deliberately the weakest one that can work: keep opaque blobs, hand them back in a
 * stable order, and issue a cursor. A synced folder, a file server or a small endpoint of one's own
 * all satisfy it, so the choice of backend never reaches the rest of the application.
 */
interface RemoteSyncSource {

    suspend fun push(envelopes: List<SyncEnvelope>): PushResult

    suspend fun pull(cursor: String?, limit: Int = DEFAULT_PAGE_SIZE): PullResult

    companion object {
        const val DEFAULT_PAGE_SIZE: Int = 200
    }
}

/**
 * The remote for an installation that syncs with nothing.
 *
 * A local-only application is not a degraded one: it is the normal case until a second device
 * appears. Accepting and forgetting keeps the outbox from growing without bound in the meantime.
 */
class NoOpRemoteSyncSource : RemoteSyncSource {

    override suspend fun push(envelopes: List<SyncEnvelope>): PushResult =
        PushResult(accepted = envelopes.size)

    override suspend fun pull(cursor: String?, limit: Int): PullResult =
        PullResult(envelopes = emptyList(), cursor = cursor)
}
