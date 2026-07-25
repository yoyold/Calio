package app.calio.data.repository

import app.calio.data.TestEnvironment
import app.calio.data.berlin
import app.calio.data.calendar
import app.calio.data.event
import app.calio.datetime.InstantRange
import app.calio.model.Attachment
import app.calio.model.AttachmentId
import app.calio.model.Attendee
import app.calio.model.AttendeeId
import app.calio.model.AttendeeRole
import app.calio.model.BusyStatus
import app.calio.model.CalendarId
import app.calio.model.CalioColor
import app.calio.model.EventId
import app.calio.model.EventKind
import app.calio.model.EventLocation
import app.calio.model.EventPatch
import app.calio.model.EventTimeRange
import app.calio.model.Frequency
import app.calio.model.OverrideType
import app.calio.model.RecurrenceEnd
import app.calio.model.RecurrenceOverride
import app.calio.model.RecurrenceRule
import app.calio.model.Reminder
import app.calio.model.ReminderId
import app.calio.model.ReminderTrigger
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class EventRepositoryTest {

    private val environment = TestEnvironment()
    private val events = environment.events

    private val window = InstantRange(
        Instant.parse("2026-08-01T00:00:00Z"),
        Instant.parse("2026-09-01T00:00:00Z"),
    )

    @AfterTest
    fun tearDown() = environment.close()

    private suspend fun givenCalendar(isVisible: Boolean = true, id: String = "calendar-1") {
        environment.calendars.upsert(calendar(id = id, isVisible = isVisible))
    }

    @Test
    fun `an event survives a round trip through the database`() = runTest {
        givenCalendar()
        val original = event(
            title = "Quarterly review",
        ).copy(
            description = "Numbers and plans",
            notes = "Bring the printout",
            location = EventLocation("Conference room", 52.52, 13.405),
            colorOverride = CalioColor(0xFF4CAF50),
            kind = EventKind.FOCUS,
            busyStatus = BusyStatus.TENTATIVE,
        )

        events.upsert(original)
        val loaded = events.byId(original.id)

        assertEquals(original.title, loaded?.title)
        assertEquals(original.description, loaded?.description)
        assertEquals(original.notes, loaded?.notes)
        assertEquals(original.location, loaded?.location)
        assertEquals(original.colorOverride, loaded?.colorOverride)
        assertEquals(original.kind, loaded?.kind)
        assertEquals(original.busyStatus, loaded?.busyStatus)
        assertEquals(original.timeRange, loaded?.timeRange)
    }

    @Test
    fun `a recurrence rule survives a round trip`() = runTest {
        givenCalendar()
        val rule = RecurrenceRule(
            frequency = Frequency.WEEKLY,
            interval = 2,
            end = RecurrenceEnd.AfterCount(8),
        )

        events.upsert(event(recurrence = rule))

        assertEquals(rule, events.byId(EventId("event-1"))?.recurrence)
    }

    @Test
    fun `reminders attendees and attachments come back with their event`() = runTest {
        givenCalendar()
        val withChildren = event().copy(
            reminders = listOf(
                Reminder(ReminderId("reminder-1"), ReminderTrigger.BeforeStart(15)),
                Reminder(ReminderId("reminder-2"), ReminderTrigger.Absolute(Instant.parse("2026-08-01T06:00:00Z"))),
            ),
            attendees = listOf(
                Attendee(AttendeeId("attendee-1"), name = "Alex", role = AttendeeRole.ORGANIZER),
            ),
            attachments = listOf(
                Attachment(AttachmentId("attachment-1"), "agenda.pdf", "application/pdf", 1_024, "abc"),
            ),
        )

        events.upsert(withChildren)
        val loaded = events.byId(withChildren.id)

        assertEquals(withChildren.reminders, loaded?.reminders)
        assertEquals(withChildren.attendees, loaded?.attendees)
        assertEquals(withChildren.attachments, loaded?.attachments)
    }

    @Test
    fun `updating an event does not duplicate its children`() = runTest {
        givenCalendar()
        val withReminder = event().copy(
            reminders = listOf(Reminder(ReminderId("reminder-1"), ReminderTrigger.BeforeStart(15))),
        )

        events.upsert(withReminder)
        events.upsert(withReminder.copy(title = "Renamed"))

        assertEquals(1, environment.database.remindersQueries.countAll().executeAsOne())
        assertEquals("Renamed", events.byId(withReminder.id)?.title)
    }

    @Test
    fun `every write stamps a fresh revision`() = runTest {
        givenCalendar()
        val original = event()

        events.upsert(original)
        val first = events.byId(original.id)?.audit?.revision
        events.upsert(original.copy(title = "Renamed"))
        val second = events.byId(original.id)?.audit?.revision

        assertNotEquals(original.audit.revision, first)
        assertTrue(first!! < second!!)
    }

    @Test
    fun `every write leaves exactly one outbox entry`() = runTest {
        givenCalendar()

        events.upsert(event())

        val changes = environment.pendingChanges().filter { it.entity_type == "EVENT" }
        assertEquals(1, changes.size)
        assertEquals("UPSERT", changes.single().operation)
        assertEquals("event-1", changes.single().entity_id)
    }

    @Test
    fun `a deleted event is gone for readers but recorded for synchronisation`() = runTest {
        givenCalendar()
        events.upsert(event())

        events.delete(EventId("event-1"))

        assertNull(events.byId(EventId("event-1")))
        assertTrue(events.observeInRange(window).first().isEmpty())
        assertEquals(
            listOf("UPSERT", "DELETE"),
            environment.pendingChanges().filter { it.entity_type == "EVENT" }.map { it.operation },
        )
    }

    @Test
    fun `the range query returns what falls inside the window`() = runTest {
        givenCalendar()
        events.upsert(event(id = "inside"))
        events.upsert(
            event(
                id = "outside",
                start = LocalDateTime(2026, 10, 1, 9, 0),
                endExclusive = LocalDateTime(2026, 10, 1, 10, 0),
            ),
        )

        assertEquals(listOf("inside"), events.observeInRange(window).first().map { it.id.value })
    }

    @Test
    fun `hidden calendars drop out of the default range query`() = runTest {
        givenCalendar(isVisible = false)
        events.upsert(event())

        assertTrue(events.observeInRange(window).first().isEmpty())
        assertEquals(
            listOf("event-1"),
            events.observeInRange(window, setOf(CalendarId("calendar-1"))).first().map { it.id.value },
        )
    }

    @Test
    fun `a series limited by a count gets an end bound it can be skipped by`() = runTest {
        givenCalendar()
        events.upsert(
            event(recurrence = RecurrenceRule(Frequency.DAILY, end = RecurrenceEnd.AfterCount(3))),
        )

        val row = environment.database.eventsQueries.selectById("event-1").executeAsOne()

        // Three daily occurrences from 3 August end on the fifth.
        assertEquals(
            Instant.parse("2026-08-05T07:30:00Z").toEpochMilliseconds(),
            row.recurrence_until_utc,
        )
    }

    @Test
    fun `an endless series has no end bound`() = runTest {
        givenCalendar()
        events.upsert(event(recurrence = RecurrenceRule(Frequency.DAILY)))

        assertNull(environment.database.eventsQueries.selectById("event-1").executeAsOne().recurrence_until_utc)
    }

    @Test
    fun `exceptions survive a round trip and are reported as a change to their event`() = runTest {
        givenCalendar()
        events.upsert(event(recurrence = RecurrenceRule(Frequency.DAILY)))
        val moved = RecurrenceOverride(
            eventId = EventId("event-1"),
            originalStart = LocalDateTime(2026, 8, 5, 9, 0),
            type = OverrideType.MODIFIED,
            patch = EventPatch(
                title = "Moved",
                timeRange = EventTimeRange.Zoned(
                    LocalDateTime(2026, 8, 5, 14, 0),
                    LocalDateTime(2026, 8, 5, 15, 0),
                    berlin,
                ),
            ),
        )

        events.upsertOverride(moved)

        assertEquals(listOf(moved), events.overridesFor(EventId("event-1")))
        assertEquals(
            mapOf(EventId("event-1") to listOf(moved)),
            events.observeAllOverrides().first(),
        )
        assertEquals(2, environment.pendingChanges().count { it.entity_type == "EVENT" })
    }

    @Test
    fun `a cancelled occurrence is stored without a patch`() = runTest {
        givenCalendar()
        events.upsert(event(recurrence = RecurrenceRule(Frequency.DAILY)))
        val cancelled = RecurrenceOverride(
            eventId = EventId("event-1"),
            originalStart = LocalDateTime(2026, 8, 5, 9, 0),
            type = OverrideType.CANCELLED,
        )

        events.upsertOverride(cancelled)

        assertEquals(listOf(cancelled), events.overridesFor(EventId("event-1")))
    }

    @Test
    fun `removing an exception takes it out of storage`() = runTest {
        givenCalendar()
        events.upsert(event(recurrence = RecurrenceRule(Frequency.DAILY)))
        val start = LocalDateTime(2026, 8, 5, 9, 0)
        events.upsertOverride(
            RecurrenceOverride(EventId("event-1"), start, OverrideType.CANCELLED),
        )

        events.removeOverride(EventId("event-1"), start)

        assertTrue(events.overridesFor(EventId("event-1")).isEmpty())
    }
}
