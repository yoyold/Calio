package app.calio.database

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The outbox and the synchronisation bookkeeping.
 *
 * The outbox is only trustworthy if it is written in the same transaction as the mutation it
 * describes; the transaction test below is the one that proves the two can never disagree.
 */
class SyncTablesTest {

    private val fixture = TestDatabase()
    private val db = fixture.db

    @AfterTest
    fun tearDown() = fixture.close()

    private fun appendChange(entityId: String, operation: String = "UPSERT", counter: Long = 0) =
        db.syncQueries.appendChange(
            entity_type = "EVENT",
            entity_id = entityId,
            operation = operation,
            revision = revision(counter = counter),
            created_at = NOW + counter,
        )

    @Test
    fun `pending changes come back in the order they were written`() {
        appendChange("event-1", counter = 0)
        appendChange("event-2", counter = 1)
        appendChange("event-3", counter = 2)

        val pending = db.syncQueries.selectPendingChanges(limit = 10).executeAsList()

        assertEquals(listOf("event-1", "event-2", "event-3"), pending.map { it.entity_id })
        assertEquals(3, db.syncQueries.countPendingChanges().executeAsOne())
    }

    @Test
    fun `marking a batch synced leaves the rest pending`() {
        appendChange("event-1", counter = 0)
        appendChange("event-2", counter = 1)
        appendChange("event-3", counter = 2)
        val batch = db.syncQueries.selectPendingChanges(limit = 2).executeAsList()

        db.syncQueries.markChangesSynced(syncedAt = NOW, upToSeq = batch.last().seq)

        assertEquals(1, db.syncQueries.countPendingChanges().executeAsOne())
        assertEquals(
            listOf("event-3"),
            db.syncQueries.selectPendingChanges(limit = 10).executeAsList().map { it.entity_id },
        )
    }

    @Test
    fun `acknowledged changes are pruned by age`() {
        appendChange("event-1")
        db.syncQueries.markChangesSynced(syncedAt = NOW, upToSeq = Long.MAX_VALUE)

        db.syncQueries.pruneSyncedChanges(olderThan = NOW + 1)

        assertEquals(0, db.syncQueries.countPendingChanges().executeAsOne())
        assertTrue(db.syncQueries.selectPendingChanges(limit = 10).executeAsList().isEmpty())
    }

    @Test
    fun `an unknown operation is rejected`() {
        assertFails { appendChange("event-1", operation = "PATCH") }
    }

    @Test
    fun `a mutation and its outbox entry are committed together`() {
        db.putCalendar()

        db.transaction {
            db.putEvent(id = "event-1", startUtc = 1, endUtc = 2)
            appendChange("event-1")
        }

        assertEquals(1, db.syncQueries.countPendingChanges().executeAsOne())
        assertEquals(1, db.eventsQueries.selectByCalendar("calendar-1").executeAsList().size)
    }

    @Test
    fun `a failed transaction leaves neither the row nor the outbox entry behind`() {
        db.putCalendar()

        assertFails {
            db.transaction {
                db.putEvent(id = "event-1", startUtc = 1, endUtc = 2)
                appendChange("event-1")
                // A constraint violation stands in for any failure later in the same unit of work.
                db.putEvent(id = "broken", startUtc = 2, endUtc = 1)
            }
        }

        assertEquals(0, db.syncQueries.countPendingChanges().executeAsOne())
        assertEquals(0, db.eventsQueries.selectByCalendar("calendar-1").executeAsList().size)
    }

    @Test
    fun `the sync state is created once and stays a single row`() {
        db.syncQueries.initialiseSyncState(TEST_DEVICE)
        db.syncQueries.initialiseSyncState("another-device")

        val state = db.syncQueries.selectSyncState().executeAsOne()

        assertEquals(TEST_DEVICE, state.device_id)
        assertNull(state.remote_cursor)
    }

    @Test
    fun `the cursor and the logical clock are stored independently`() {
        db.syncQueries.initialiseSyncState(TEST_DEVICE)

        db.syncQueries.updateCursor(remoteCursor = "cursor-42", lastSyncAt = NOW)
        db.syncQueries.updateClock(clockMillis = NOW, clockCounter = 7)

        val state = db.syncQueries.selectSyncState().executeAsOne()

        assertEquals("cursor-42", state.remote_cursor)
        assertEquals(NOW, state.last_sync_at)
        assertEquals(NOW, state.clock_millis)
        assertEquals(7, state.clock_counter)
    }

    @Test
    fun `a resolved conflict keeps the discarded version for recovery`() {
        db.syncQueries.recordConflict(
            id = "conflict-1",
            entity_type = "EVENT",
            entity_id = "event-1",
            local_revision = revision(counter = 2),
            remote_revision = revision(counter = 1),
            discarded_payload = """{"title":"remote version"}""",
            detected_at = NOW,
        )

        val open = db.syncQueries.selectOpenConflicts().executeAsList()
        assertEquals(1, open.size)
        assertEquals("""{"title":"remote version"}""", open.single().discarded_payload)

        db.syncQueries.resolveConflict(resolvedAt = NOW + 1, id = "conflict-1")

        assertTrue(db.syncQueries.selectOpenConflicts().executeAsList().isEmpty())
    }
}
