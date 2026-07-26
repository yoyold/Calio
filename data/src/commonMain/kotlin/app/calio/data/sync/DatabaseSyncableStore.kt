package app.calio.data.sync

import app.calio.data.mapper.EventPatchCodec
import app.calio.data.mapper.toDomain
import app.calio.data.repository.writeCalendar
import app.calio.data.repository.writeCategory
import app.calio.data.repository.writeEvent
import app.calio.data.repository.writeTask
import app.calio.database.CalioDatabase
import app.calio.domain.sync.RevisionSource
import app.calio.model.Revision
import app.calio.sync.IncomingChange
import app.calio.sync.PendingChange
import app.calio.sync.SyncOperation
import app.calio.sync.SyncableStore
import app.calio.sync.SyncedEntityType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The synchronisation engine's view of the database.
 *
 * Its whole reason for existing is that applying an incoming change must **not** go through a
 * repository. A repository stamps a new revision and appends an outbox entry, which is right for a
 * local edit and wrong here twice over: the revision would replace the one that decides ordering,
 * and the outbox entry would send the change straight back to where it came from.
 */
@OptIn(ExperimentalUuidApi::class)
class DatabaseSyncableStore(
    private val database: CalioDatabase,
    private val revisions: RevisionSource,
    private val dispatcher: CoroutineDispatcher,
) : SyncableStore {

    override suspend fun pendingChanges(limit: Int): List<PendingChange> = withContext(dispatcher) {
        database.syncQueries.selectPendingChanges(limit.toLong()).executeAsList().map { row ->
            PendingChange(
                sequence = row.seq,
                entityType = SyncedEntityType.valueOf(row.entity_type),
                entityId = row.entity_id,
                operation = SyncOperation.valueOf(row.operation),
                revision = Revision(row.revision),
            )
        }
    }

    /**
     * The record as it stands, including its tombstone if it has one.
     *
     * A deletion travels as the deleted record rather than as an absence, so the other device learns
     * which record died and at which revision, instead of merely finding something missing.
     */
    override suspend fun payloadFor(
        entityType: SyncedEntityType,
        entityId: String,
    ): String? = withContext(dispatcher) {
        when (entityType) {
            SyncedEntityType.CALENDAR ->
                database.calendarsQueries.selectById(entityId).executeAsOneOrNull()
                    ?.let { EntityCodec.encode(it.toDomain()) }

            SyncedEntityType.CATEGORY ->
                database.categoriesQueries.selectById(entityId).executeAsOneOrNull()
                    ?.let { EntityCodec.encode(it.toDomain()) }

            SyncedEntityType.EVENT ->
                database.eventsQueries.selectById(entityId).executeAsOneOrNull()?.let { row ->
                    EntityCodec.encode(
                        event = row.toDomain(
                            reminders = database.remindersQueries.selectForEvent(entityId)
                                .executeAsList().map { it.toDomain() },
                            attendees = database.participantsQueries.selectAttendees(entityId)
                                .executeAsList().map { it.toDomain() },
                            attachments = database.participantsQueries.selectAttachments(entityId)
                                .executeAsList().map { it.toDomain() },
                        ),
                        overrides = database.eventsQueries.selectOverrides(entityId)
                            .executeAsList().map { it.toDomain() },
                    )
                }

            SyncedEntityType.TASK ->
                database.tasksQueries.selectById(entityId).executeAsOneOrNull()?.let { row ->
                    EntityCodec.encode(
                        row.toDomain(
                            reminders = database.remindersQueries.selectForTask(entityId)
                                .executeAsList().map { it.toDomain() },
                        ),
                    )
                }
        }
    }

    override suspend fun localRevision(
        entityType: SyncedEntityType,
        entityId: String,
    ): Revision? = withContext(dispatcher) {
        when (entityType) {
            SyncedEntityType.CALENDAR ->
                database.calendarsQueries.selectById(entityId).executeAsOneOrNull()?.revision

            SyncedEntityType.CATEGORY ->
                database.categoriesQueries.selectById(entityId).executeAsOneOrNull()?.revision

            SyncedEntityType.EVENT ->
                database.eventsQueries.selectById(entityId).executeAsOneOrNull()?.revision

            SyncedEntityType.TASK ->
                database.tasksQueries.selectById(entityId).executeAsOneOrNull()?.revision
        }?.let(::Revision)
    }

    override suspend fun applyRemote(change: IncomingChange): Unit = withContext(dispatcher) {
        if (change.payload.isBlank()) return@withContext

        database.transaction {
            when (change.entityType) {
                SyncedEntityType.CALENDAR -> {
                    val incoming = EntityCodec.decodeCalendar(change.payload)
                    // Visibility never travels, so the local setting is kept. A calendar switched
                    // off here stays off even when the other device renames it.
                    val wasVisible = database.calendarsQueries.selectById(incoming.id.value)
                        .executeAsOneOrNull()?.is_visible?.let { it != 0L } ?: true
                    database.writeCalendar(incoming.copy(isVisible = wasVisible))
                }

                SyncedEntityType.CATEGORY ->
                    database.writeCategory(EntityCodec.decodeCategory(change.payload))

                SyncedEntityType.EVENT -> {
                    val decoded = EntityCodec.decodeEvent(change.payload)
                    database.writeEvent(decoded.event)
                    // Exceptions belong to their series and are replaced with it, so an exception
                    // deleted elsewhere disappears here instead of lingering.
                    database.eventsQueries.deleteOverridesFor(decoded.event.id.value)
                    decoded.overrides.forEach { override ->
                        database.eventsQueries.insertOverride(
                            event_id = override.eventId.value,
                            original_start = override.originalStart.toString(),
                            type = override.type.name,
                            patch_json = override.patch?.let(EventPatchCodec::encode),
                        )
                    }
                }

                SyncedEntityType.TASK ->
                    database.writeTask(EntityCodec.decodeTask(change.payload))
            }
        }
    }

    override suspend fun recordConflict(
        entityType: SyncedEntityType,
        entityId: String,
        localRevision: Revision,
        remoteRevision: Revision,
        discardedPayload: String,
        at: Instant,
    ): Unit = withContext(dispatcher) {
        database.syncQueries.recordConflict(
            id = Uuid.random().toString(),
            entity_type = entityType.name,
            entity_id = entityId,
            local_revision = localRevision.value,
            remote_revision = remoteRevision.value,
            discarded_payload = discardedPayload,
            detected_at = at.toEpochMilliseconds(),
        )
    }

    override suspend fun markPushed(sequence: Long, at: Instant): Unit = withContext(dispatcher) {
        database.syncQueries.markChangesSynced(
            syncedAt = at.toEpochMilliseconds(),
            upToSeq = sequence,
        )
    }

    override suspend fun cursor(): String? = withContext(dispatcher) {
        database.syncQueries.selectSyncState().executeAsOneOrNull()?.remote_cursor
    }

    override suspend fun setCursor(cursor: String?, at: Instant): Unit = withContext(dispatcher) {
        database.syncQueries.updateCursor(
            remoteCursor = cursor,
            lastSyncAt = at.toEpochMilliseconds(),
        )
    }

    override suspend fun observeRemoteRevision(revision: Revision) {
        revisions.observe(revision)
    }
}
