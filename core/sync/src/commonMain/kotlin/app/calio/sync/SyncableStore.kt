package app.calio.sync

import app.calio.model.Revision
import kotlinx.coroutines.flow.Flow
import kotlin.time.Instant

/** One entry of the outbox, as the engine sees it. */
data class PendingChange(
    val sequence: Long,
    val entityType: SyncedEntityType,
    val entityId: String,
    val operation: SyncOperation,
    val revision: Revision,
)

/** An incoming change once its payload has been opened. */
data class IncomingChange(
    val entityType: SyncedEntityType,
    val entityId: String,
    val operation: SyncOperation,
    val revision: Revision,
    val payload: String,
)

/**
 * What the engine needs from local storage.
 *
 * Declared here rather than in the data layer so the engine can be exercised without a database, and
 * so the storage technology can change without the synchronisation rules moving with it. The engine
 * never learns what an event looks like: to it a record is a type, an id, a revision and a string.
 */
interface SyncableStore {

    suspend fun pendingChanges(limit: Int): List<PendingChange>

    /** How many local changes are still waiting, so a scheduler can react instead of polling. */
    fun observePendingCount(): Flow<Int>

    /** The serialised record, or null when it is gone — which is what a deletion looks like. */
    suspend fun payloadFor(entityType: SyncedEntityType, entityId: String): String?

    suspend fun localRevision(entityType: SyncedEntityType, entityId: String): Revision?

    /**
     * Writes an incoming change.
     *
     * Applying a remote change must not produce an outbox entry of its own: the change already
     * exists everywhere it came from, and echoing it back would make two devices talk forever.
     */
    suspend fun applyRemote(change: IncomingChange)

    suspend fun recordConflict(
        entityType: SyncedEntityType,
        entityId: String,
        localRevision: Revision,
        remoteRevision: Revision,
        discardedPayload: String,
        at: Instant,
    )

    /** Acknowledges everything up to and including [sequence]. */
    suspend fun markPushed(sequence: Long, at: Instant)

    suspend fun cursor(): String?

    suspend fun setCursor(cursor: String?, at: Instant)

    /** Feeds a revision seen from another device into the local clock. */
    suspend fun observeRemoteRevision(revision: Revision)
}
