package app.calio.database

import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The constraints that keep the data honest even if a caller gets it wrong.
 *
 * These live in the schema rather than only in Kotlin because synchronisation writes rows too, and a
 * rule that exists only in the domain layer would not protect against a malformed remote payload.
 */
class ConstraintsTest {

    private val fixture = TestDatabase()
    private val db = fixture.db

    init {
        db.putCalendar()
        db.putEvent(id = "event-1", startUtc = 1_000, endUtc = 2_000)
        db.putTask(id = "task-1")
    }

    @AfterTest
    fun tearDown() = fixture.close()

    private fun addReminder(
        id: String,
        eventId: String?,
        taskId: String?,
        triggerType: String = "BEFORE_START",
        leadMinutes: Long? = 15,
        absoluteUtc: Long? = null,
    ) = db.remindersQueries.insert(
        id = id,
        event_id = eventId,
        task_id = taskId,
        trigger_type = triggerType,
        lead_minutes = leadMinutes,
        absolute_utc = absoluteUtc,
        channel = "NOTIFICATION",
        sort_order = 0,
    )

    @Test
    fun `a reminder belongs to an event or a task`() {
        addReminder("reminder-1", eventId = "event-1", taskId = null)
        addReminder("reminder-2", eventId = null, taskId = "task-1")

        assertEquals(2, db.remindersQueries.countAll().executeAsOne())
    }

    @Test
    fun `a reminder attached to neither parent is rejected`() {
        assertFails { addReminder("orphan", eventId = null, taskId = null) }
    }

    @Test
    fun `a reminder attached to both parents is rejected`() {
        assertFails { addReminder("both", eventId = "event-1", taskId = "task-1") }
    }

    @Test
    fun `a relative reminder without a lead time is rejected`() {
        assertFails {
            addReminder("no-lead", eventId = "event-1", taskId = null, leadMinutes = null)
        }
    }

    @Test
    fun `an absolute reminder without an instant is rejected`() {
        assertFails {
            addReminder(
                id = "no-instant",
                eventId = "event-1",
                taskId = null,
                triggerType = "ABSOLUTE",
                leadMinutes = null,
                absoluteUtc = null,
            )
        }
    }

    @Test
    fun `deleting an event takes its reminders attendees attachments and overrides with it`() {
        addReminder("reminder-1", eventId = "event-1", taskId = null)
        db.participantsQueries.insertAttendee(
            id = "attendee-1",
            event_id = "event-1",
            name = "Alex",
            email = null,
            role = "REQUIRED",
            response = "NEEDS_ACTION",
            sort_order = 0,
        )
        db.participantsQueries.insertAttachment(
            id = "attachment-1",
            event_id = "event-1",
            file_name = "agenda.pdf",
            mime_type = "application/pdf",
            size_bytes = 1_024,
            checksum = "abc",
            local_path = null,
            sort_order = 0,
        )
        db.eventsQueries.insertOverride(
            event_id = "event-1",
            original_start = "2026-08-10T09:00",
            type = "CANCELLED",
            patch_json = null,
        )

        db.eventsQueries.deleteHard("event-1")

        assertEquals(0, db.remindersQueries.countAll().executeAsOne())
        assertEquals(0, db.participantsQueries.countAttendees().executeAsOne())
        assertEquals(0, db.participantsQueries.countAttachments().executeAsOne())
        assertEquals(0, db.eventsQueries.selectOverrides("event-1").executeAsList().size)
    }

    @Test
    fun `a soft deleted event keeps its children`() {
        addReminder("reminder-1", eventId = "event-1", taskId = null)

        db.eventsQueries.softDelete(deletedAt = NOW, revision = revision(), id = "event-1")

        assertEquals(1, db.remindersQueries.countAll().executeAsOne())
        assertNotNull(db.eventsQueries.selectById("event-1").executeAsOneOrNull())
    }

    @Test
    fun `deleting a task takes its subtasks with it`() {
        db.putTask(id = "subtask-1", parentTaskId = "task-1")

        db.tasksQueries.deleteHard("task-1")

        assertNull(db.tasksQueries.selectById("subtask-1").executeAsOneOrNull())
    }

    @Test
    fun `an event referencing an unknown calendar is rejected`() {
        assertFails {
            db.putEvent(id = "orphan", calendarId = "does-not-exist", startUtc = 1, endUtc = 2)
        }
    }

    @Test
    fun `an event ending before it starts is rejected`() {
        assertFails { db.putEvent(id = "backwards", startUtc = 2_000, endUtc = 1_000) }
    }

    @Test
    fun `a task progress outside the percentage range is rejected`() {
        assertFails { db.putTask(id = "impossible", progressPercent = 101) }
    }

    @Test
    fun `a completion timestamp on an open task is rejected`() {
        assertFails { db.putTask(id = "half-done", isCompleted = 0, completedAt = NOW) }
    }

    @Test
    fun `a cancelled occurrence carrying a patch is rejected`() {
        assertFails {
            db.eventsQueries.insertOverride(
                event_id = "event-1",
                original_start = "2026-08-10T09:00",
                type = "CANCELLED",
                patch_json = """{"title":"moved"}""",
            )
        }
    }

    @Test
    fun `category names are unique while they are alive`() {
        db.putCategory(id = "category-1", name = "Work")

        assertFails { db.putCategory(id = "category-2", name = "work") }
    }

    @Test
    fun `a category name becomes free again once it is deleted`() {
        db.putCategory(id = "category-1", name = "Work", deletedAt = NOW)

        db.putCategory(id = "category-2", name = "Work")

        assertEquals(1, db.categoriesQueries.countLiving().executeAsOne())
    }

    @Test
    fun `built in categories cannot be deleted`() {
        db.putCategory(id = "builtin", name = "Private", isBuiltIn = 1)

        db.categoriesQueries.softDelete(deletedAt = NOW, revision = revision(), id = "builtin")

        assertEquals(1, db.categoriesQueries.countLiving().executeAsOne())
    }
}
