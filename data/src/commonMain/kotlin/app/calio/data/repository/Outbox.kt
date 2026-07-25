package app.calio.data.repository

import app.calio.database.CalioDatabase
import app.calio.model.Revision
import kotlin.time.Instant

internal enum class SyncedEntity { CALENDAR, CATEGORY, EVENT, TASK }

internal enum class SyncOperation { UPSERT, DELETE }

/**
 * Records a local mutation for synchronisation.
 *
 * This must always be called inside the same transaction as the mutation it describes. Committing
 * the two together is what makes the outbox incapable of disagreeing with the data: a crash either
 * loses both or keeps both, never one of them.
 */
internal fun CalioDatabase.recordChange(
    entity: SyncedEntity,
    entityId: String,
    operation: SyncOperation,
    revision: Revision,
    at: Instant,
) {
    syncQueries.appendChange(
        entity_type = entity.name,
        entity_id = entityId,
        operation = operation.name,
        revision = revision.value,
        created_at = at.toEpochMilliseconds(),
    )
}
