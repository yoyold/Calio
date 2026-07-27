package app.calio.sync

import app.calio.model.DeviceId
import app.calio.model.Revision
import kotlin.time.Instant

internal data class StoredRecord(val revision: Revision, val payload: String?)

internal data class RecordedConflict(
    val entityType: SyncedEntityType,
    val entityId: String,
    val localRevision: Revision,
    val remoteRevision: Revision,
    val discardedPayload: String,
)

/**
 * A device's storage, in memory.
 *
 * It behaves like the real one in the ways the engine can tell apart: revisions are kept per record,
 * local edits queue in an outbox, applying a remote change writes without queueing, and the logical
 * clock is pushed forward by anything seen from elsewhere. Two of these plus one remote is a pair of
 * devices.
 */
internal class InMemorySyncStore(private val deviceId: DeviceId) : SyncableStore {

    private val records = mutableMapOf<Pair<SyncedEntityType, String>, StoredRecord>()
    private val outbox = mutableListOf<PendingChange>()
    private val pushedUpTo = mutableSetOf<Long>()

    val conflicts = mutableListOf<RecordedConflict>()

    private val pendingCount = kotlinx.coroutines.flow.MutableStateFlow(0)

    private var sequence = 0L
    private var clockMillis = 0L
    private var counter = 0
    private var cursor: String? = null

    val allRecords: Map<Pair<SyncedEntityType, String>, StoredRecord> get() = records.toMap()

    /** A local edit: writes the record and queues it, exactly as a repository does. */
    fun edit(
        entityType: SyncedEntityType,
        entityId: String,
        payload: String?,
        atMillis: Long,
    ): Revision {
        val revision = nextRevision(atMillis)
        records[entityType to entityId] = StoredRecord(revision, payload)
        outbox += PendingChange(
            sequence = ++sequence,
            entityType = entityType,
            entityId = entityId,
            operation = if (payload == null) SyncOperation.DELETE else SyncOperation.UPSERT,
            revision = revision,
        )
        return revision
    }

    private fun nextRevision(atMillis: Long): Revision {
        if (atMillis > clockMillis) {
            clockMillis = atMillis
            counter = 0
        } else {
            counter++
        }
        return Revision.of(clockMillis, counter, deviceId)
    }

    override fun observePendingCount(): kotlinx.coroutines.flow.Flow<Int> = pendingCount

    override suspend fun pendingChanges(limit: Int): List<PendingChange> =
        outbox.filterNot { it.sequence in pushedUpTo }.take(limit)

    override suspend fun payloadFor(entityType: SyncedEntityType, entityId: String): String? =
        records[entityType to entityId]?.payload

    override suspend fun localRevision(entityType: SyncedEntityType, entityId: String): Revision? =
        records[entityType to entityId]?.revision

    override suspend fun applyRemote(change: IncomingChange) {
        records[change.entityType to change.entityId] = StoredRecord(
            revision = change.revision,
            payload = if (change.operation == SyncOperation.DELETE) null else change.payload,
        )
    }

    override suspend fun recordConflict(
        entityType: SyncedEntityType,
        entityId: String,
        localRevision: Revision,
        remoteRevision: Revision,
        discardedPayload: String,
        at: Instant,
    ) {
        conflicts += RecordedConflict(
            entityType,
            entityId,
            localRevision,
            remoteRevision,
            discardedPayload,
        )
    }

    override suspend fun markPushed(sequence: Long, at: Instant) {
        outbox.filter { it.sequence <= sequence }.forEach { pushedUpTo += it.sequence }
    }

    override suspend fun cursor(): String? = cursor

    override suspend fun setCursor(cursor: String?, at: Instant) {
        this.cursor = cursor
    }

    override suspend fun observeRemoteRevision(revision: Revision) {
        when {
            revision.physicalMillis > clockMillis -> {
                clockMillis = revision.physicalMillis
                counter = revision.counter + 1
            }

            revision.physicalMillis == clockMillis && revision.counter >= counter -> {
                counter = revision.counter + 1
            }
        }
    }
}
