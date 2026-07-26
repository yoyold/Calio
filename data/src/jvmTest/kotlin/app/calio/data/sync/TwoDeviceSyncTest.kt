package app.calio.data.sync

import app.calio.data.TestEnvironment
import app.calio.data.calendar
import app.calio.data.event
import app.calio.data.task
import app.calio.datetime.InstantRange
import app.calio.model.CalendarId
import app.calio.model.DeviceId
import app.calio.model.EventId
import app.calio.model.Frequency
import app.calio.model.OverrideType
import app.calio.model.RecurrenceOverride
import app.calio.model.RecurrenceRule
import app.calio.model.Reminder
import app.calio.model.ReminderId
import app.calio.model.ReminderTrigger
import app.calio.model.TaskId
import app.calio.sync.InMemoryRemoteSyncSource
import app.calio.sync.PassthroughCipher
import app.calio.sync.SyncEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Two devices, two databases, one remote.
 *
 * This is the test the whole synchronisation design exists for. Everything below it — the outbox
 * written in the same transaction as the data, the hybrid logical clock, the envelope — is only
 * worth anything if two real databases end up agreeing, so here they are made to.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TwoDeviceSyncTest {

    private val remote = InMemoryRemoteSyncSource()

    private val deviceA = Device("device-a")
    private val deviceB = Device("device-b")

    private inner class Device(name: String) {
        // Distinct device ids, or the two clocks would hand out identical revisions and the
        // tiebreaker that makes the ordering total would have nothing to break the tie with.
        val environment = TestEnvironment(
            dispatcher = UnconfinedTestDispatcher(),
            deviceId = DeviceId(name),
        )
        val store = DatabaseSyncableStore(
            database = environment.database,
            revisions = environment.revisions,
            dispatcher = UnconfinedTestDispatcher(),
        )
        val engine = SyncEngine(store, remote, PassthroughCipher(), environment.clock)

        suspend fun sync() = engine.sync()

        fun close() = environment.close()
    }

    @AfterTest
    fun tearDown() {
        deviceA.close()
        deviceB.close()
    }

    private suspend fun bothSync() {
        deviceA.sync()
        deviceB.sync()
        deviceA.sync()
    }

    @Test
    fun `a calendar created on one device appears on the other`() = runTest {
        deviceA.environment.calendars.upsert(calendar(name = "Work"))

        bothSync()

        val received = deviceB.environment.calendars.observeAll().first()
        assertEquals(listOf("Work"), received.map { it.name })
    }

    @Test
    fun `an event travels with its reminders and its recurrence`() = runTest {
        deviceA.environment.calendars.upsert(calendar())
        deviceA.environment.events.upsert(
            event(recurrence = RecurrenceRule(Frequency.WEEKLY, interval = 2)).copy(
                notes = "Bring the printout",
                reminders = listOf(Reminder(ReminderId("reminder-1"), ReminderTrigger.BeforeStart(15))),
            ),
        )

        bothSync()

        val received = deviceB.environment.events.byId(EventId("event-1"))
        assertNotNull(received)
        assertEquals("Bring the printout", received.notes)
        assertEquals(RecurrenceRule(Frequency.WEEKLY, interval = 2), received.recurrence)
        assertEquals(ReminderTrigger.BeforeStart(15), received.reminders.single().trigger)
    }

    @Test
    fun `the times of an event survive the trip`() = runTest {
        deviceA.environment.calendars.upsert(calendar())
        val original = event(
            start = LocalDateTime(2026, 8, 3, 9, 0),
            endExclusive = LocalDateTime(2026, 8, 3, 10, 30),
        )
        deviceA.environment.events.upsert(original)

        bothSync()

        assertEquals(
            original.timeRange,
            deviceB.environment.events.byId(original.id)?.timeRange,
        )
    }

    @Test
    fun `an exception to a series travels with it`() = runTest {
        deviceA.environment.calendars.upsert(calendar())
        deviceA.environment.events.upsert(event(recurrence = RecurrenceRule(Frequency.DAILY)))
        deviceA.environment.events.upsertOverride(
            RecurrenceOverride(
                eventId = EventId("event-1"),
                originalStart = LocalDateTime(2026, 8, 5, 9, 0),
                type = OverrideType.CANCELLED,
            ),
        )

        bothSync()

        val received = deviceB.environment.events.overridesFor(EventId("event-1"))
        assertEquals(OverrideType.CANCELLED, received.single().type)
        assertEquals(LocalDateTime(2026, 8, 5, 9, 0), received.single().originalStart)
    }

    @Test
    fun `a task travels with its due date`() = runTest {
        deviceA.environment.tasks.upsert(
            task(title = "Write the report").copy(
                due = app.calio.model.TaskDue.OnDate(kotlinx.datetime.LocalDate(2026, 8, 12)),
            ),
        )

        bothSync()

        val received = deviceB.environment.tasks.byId(TaskId("task-1"))
        assertEquals("Write the report", received?.title)
        assertEquals(kotlinx.datetime.LocalDate(2026, 8, 12), received?.due?.date)
    }

    @Test
    fun `a deletion reaches the other device`() = runTest {
        deviceA.environment.calendars.upsert(calendar())
        deviceA.environment.events.upsert(event())
        bothSync()
        assertNotNull(deviceB.environment.events.byId(EventId("event-1")))

        deviceA.environment.events.delete(EventId("event-1"))
        bothSync()

        assertNull(deviceB.environment.events.byId(EventId("event-1")))
    }

    @Test
    fun `applying a remote change does not send it straight back`() = runTest {
        // Without this the two devices would answer each other forever.
        deviceA.environment.calendars.upsert(calendar())
        deviceA.sync()

        deviceB.sync()

        assertEquals(0, deviceB.environment.pendingChanges().size)
    }

    @Test
    fun `syncing again after everything has settled moves nothing`() = runTest {
        deviceA.environment.calendars.upsert(calendar())
        deviceA.environment.events.upsert(event())
        bothSync()
        val logSize = remote.size

        deviceA.sync()
        deviceB.sync()

        assertEquals(logSize, remote.size)
        assertEquals(0, deviceA.environment.pendingChanges().size)
        assertEquals(0, deviceB.environment.pendingChanges().size)
    }

    @Test
    fun `the later of two competing edits wins on both devices`() = runTest {
        deviceA.environment.calendars.upsert(calendar())
        deviceA.environment.events.upsert(event(title = "Original"))
        bothSync()
        deviceB.sync()

        // Both edit while apart. B's clock is moved on, so its edit is the later one.
        deviceA.environment.events.upsert(
            deviceA.environment.events.byId(EventId("event-1"))!!.copy(title = "From A"),
        )
        deviceB.environment.clock.advanceBy(10.minutes)
        deviceB.environment.events.upsert(
            deviceB.environment.events.byId(EventId("event-1"))!!.copy(title = "From B"),
        )

        deviceB.sync()
        deviceA.sync()
        deviceB.sync()

        assertEquals("From B", deviceA.environment.events.byId(EventId("event-1"))?.title)
        assertEquals("From B", deviceB.environment.events.byId(EventId("event-1"))?.title)
    }

    @Test
    fun `the edit that lost is kept so it can be recovered`() = runTest {
        deviceA.environment.calendars.upsert(calendar())
        deviceA.environment.events.upsert(event(title = "Original"))
        bothSync()
        deviceB.sync()

        deviceA.environment.events.upsert(
            deviceA.environment.events.byId(EventId("event-1"))!!.copy(title = "From A"),
        )
        deviceB.environment.clock.advanceBy(10.minutes)
        deviceB.environment.events.upsert(
            deviceB.environment.events.byId(EventId("event-1"))!!.copy(title = "From B"),
        )
        deviceB.sync()

        deviceA.sync()

        val conflicts = deviceA.environment.database.syncQueries.selectOpenConflicts().executeAsList()
        assertEquals(1, conflicts.size)
        assertTrue(conflicts.single().discarded_payload.contains("From A"))
    }

    @Test
    fun `calendar visibility stays with the device that set it`() = runTest {
        // Hiding a calendar on one screen must not hide it on another.
        deviceA.environment.calendars.upsert(calendar(name = "Work"))
        bothSync()

        deviceB.environment.calendars.setVisible(CalendarId("calendar-1"), isVisible = false)
        deviceA.environment.calendars.upsert(
            deviceA.environment.calendars.byId(CalendarId("calendar-1"))!!.copy(name = "Renamed"),
        )
        bothSync()
        deviceB.sync()

        val onB = deviceB.environment.calendars.byId(CalendarId("calendar-1"))
        assertEquals("Renamed", onB?.name)
        assertEquals(false, onB?.isVisible)
        assertEquals(true, deviceA.environment.calendars.byId(CalendarId("calendar-1"))?.isVisible)
    }

    @Test
    fun `both databases hold the same events once they have settled`() = runTest {
        deviceA.environment.calendars.upsert(calendar())
        deviceA.environment.events.upsert(event(id = "from-a", title = "A one"))
        // The calendar has to reach B before B can put an event in it: the foreign key is real, and
        // the append-only log preserves the order the two were created in.
        deviceA.sync()
        deviceB.sync()

        deviceB.environment.clock.advanceBy(1.minutes)
        deviceB.environment.events.upsert(event(id = "from-b", title = "B one"))

        deviceB.sync()
        deviceA.sync()

        val onA = deviceA.environment.events.observeInRange(wholeAugust()).first().map { it.id.value }
        val onB = deviceB.environment.events.observeInRange(wholeAugust()).first().map { it.id.value }
        assertEquals(onA.sorted(), onB.sorted())
        assertEquals(listOf("from-a", "from-b"), onA.sorted())
    }

    private fun wholeAugust() = InstantRange(
        Instant.parse("2026-08-01T00:00:00Z"),
        Instant.parse("2026-09-01T00:00:00Z"),
    )
}
