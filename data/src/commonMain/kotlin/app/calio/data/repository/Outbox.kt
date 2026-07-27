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
/**
 * Records a change to an event, in whichever outbox it belongs to.
 *
 * A mirrored calendar answers to its provider, and each device mirrors it for itself. Putting such a
 * change into Calio's own outbox as well would deliver it twice by two routes — and the two routes
 * disagree about what a revision means, so the second delivery could not even be recognised as a
 * duplicate.
 */
internal fun CalioDatabase.recordEventChange(
    calendarId: String,
    eventId: String,
    operation: SyncOperation,
    revision: Revision,
    at: Instant,
) {
    if (isMirroredCalendar(calendarId)) {
        accountsQueries.appendExternalChange(
            calendar_id = calendarId,
            event_id = eventId,
            operation = operation.name,
            created_at = at.toEpochMilliseconds(),
        )
    } else {
        recordChange(SyncedEntity.EVENT, eventId, operation, revision, at)
    }
}

internal fun CalioDatabase.isMirroredCalendar(calendarId: String): Boolean =
    calendarsQueries.selectById(calendarId).executeAsOneOrNull()?.external_account_id != null

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
