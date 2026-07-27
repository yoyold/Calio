package app.calio.data.repository

import app.calio.data.mapper.EventPatchCodec
import app.calio.data.mapper.absoluteUtcOrNull
import app.calio.data.mapper.endLocalText
import app.calio.data.mapper.leadMinutesOrNull
import app.calio.data.mapper.recurrenceRuleText
import app.calio.data.mapper.startLocalText
import app.calio.data.mapper.timeZoneIdOrNull
import app.calio.data.mapper.toDomain
import app.calio.data.mapper.typeName
import app.calio.database.CalioDatabase
import app.calio.datetime.InstantRange
import app.calio.domain.recurrence.seriesEndUtc
import app.calio.domain.repository.EventRepository
import app.calio.domain.sync.RevisionSource
import app.calio.model.CalendarId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.RecurrenceOverride
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import kotlin.time.Clock

class EventRepositoryImpl(
    private val database: CalioDatabase,
    private val revisions: RevisionSource,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.System,
) : EventRepository {

    private val events = database.eventsQueries

    /**
     * Events in a window are returned without their reminders, attendees and attachments.
     *
     * A month view asks for hundreds of events and draws none of those details, so loading them
     * would mean hundreds of extra queries for data nobody looks at. The full event, children
     * included, is what [observeById] and [byId] return.
     */
    override fun observeInRange(window: InstantRange, calendarIds: Set<CalendarId>?): Flow<List<Event>> {
        val query = if (calendarIds == null) {
            events.selectInRangeForVisibleCalendars(
                rangeStartUtc = window.start.toEpochMilliseconds(),
                rangeEndUtc = window.endExclusive.toEpochMilliseconds(),
            )
        } else {
            events.selectInRange(
                rangeStartUtc = window.start.toEpochMilliseconds(),
                rangeEndUtc = window.endExclusive.toEpochMilliseconds(),
            )
        }

        val wanted = calendarIds?.mapTo(mutableSetOf()) { it.value }

        return query.asFlow().mapToList(dispatcher).map { rows ->
            rows.filter { wanted == null || it.calendar_id in wanted }.map { it.toDomain() }
        }
    }

    // A soft deleted row is a tombstone kept for synchronisation, not an event. Callers asking for
    // an event by id get nothing, exactly as if it had been removed.
    override fun observeById(id: EventId): Flow<Event?> =
        events.selectById(id.value).asFlow().mapToOneOrNull(dispatcher).map { row ->
            row?.takeIf { it.deleted_at == null }?.let { loadChildren(it.toDomain(), id) }
        }

    override suspend fun byId(id: EventId): Event? = withContext(dispatcher) {
        events.selectById(id.value).executeAsOneOrNull()
            ?.takeIf { it.deleted_at == null }
            ?.let { loadChildren(it.toDomain(), id) }
    }

    private fun loadChildren(event: Event, id: EventId): Event = event.copy(
        reminders = database.remindersQueries.selectForEvent(id.value).executeAsList().map { it.toDomain() },
        attendees = database.participantsQueries.selectAttendees(id.value).executeAsList().map { it.toDomain() },
        attachments = database.participantsQueries.selectAttachments(id.value).executeAsList().map { it.toDomain() },
    )

    override suspend fun upsert(event: Event): Unit = withContext(dispatcher) {
        // The revision is taken before the transaction opens, because handing one out is itself a
        // suspending operation and a transaction block must not suspend.
        val revision = revisions.next()
        val now = clock.now()
        val stamped = event.copy(
            audit = event.audit.copy(
                updatedAt = now,
                revision = revision,
                originDevice = revision.deviceId,
            ),
        )

        database.transaction {
            database.writeEvent(stamped)
            database.recordEventChange(
                calendarId = stamped.calendarId.value,
                eventId = stamped.id.value,
                operation = SyncOperation.UPSERT,
                revision = revision,
                at = now,
            )
        }
    }

    override suspend fun delete(id: EventId): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            // Which calendar it lived in has to be read before the row becomes a tombstone, because
            // that is what decides where the deletion has to be reported.
            val calendarId = events.selectById(id.value).executeAsOneOrNull()?.calendar_id

            events.softDelete(
                deletedAt = now.toEpochMilliseconds(),
                revision = revision.value,
                id = id.value,
            )

            if (calendarId != null) {
                database.recordEventChange(
                    calendarId = calendarId,
                    eventId = id.value,
                    operation = SyncOperation.DELETE,
                    revision = revision,
                    at = now,
                )
            }
        }
    }

    override suspend fun overridesFor(id: EventId): List<RecurrenceOverride> = withContext(dispatcher) {
        events.selectOverrides(id.value).executeAsList().map { it.toDomain() }
    }

    override fun observeAllOverrides(): Flow<Map<EventId, List<RecurrenceOverride>>> =
        events.selectAllOverrides().asFlow().mapToList(dispatcher).map { rows ->
            rows.map { it.toDomain() }.groupBy { it.eventId }
        }

    /**
     * An exception changes the series, so it is reported to synchronisation as a change to the
     * event. Exceptions travel with their event rather than as entities of their own; on their own
     * they would be meaningless to another device.
     */
    override suspend fun upsertOverride(override: RecurrenceOverride): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            events.insertOverride(
                event_id = override.eventId.value,
                original_start = override.originalStart.toString(),
                type = override.type.name,
                patch_json = override.patch?.let(EventPatchCodec::encode),
            )
            database.recordChange(
                entity = SyncedEntity.EVENT,
                entityId = override.eventId.value,
                operation = SyncOperation.UPSERT,
                revision = revision,
                at = now,
            )
        }
    }

    override suspend fun removeOverride(id: EventId, originalStart: LocalDateTime): Unit =
        withContext(dispatcher) {
            val revision = revisions.next()
            val now = clock.now()

            database.transaction {
                events.deleteOverride(eventId = id.value, originalStart = originalStart.toString())
                database.recordChange(
                    entity = SyncedEntity.EVENT,
                    entityId = id.value,
                    operation = SyncOperation.UPSERT,
                    revision = revision,
                    at = now,
                )
            }
        }
}