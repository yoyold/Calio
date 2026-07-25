package app.calio.data.repository

import app.calio.data.TestEnvironment
import app.calio.data.berlin
import app.calio.data.calendar
import app.calio.data.task
import app.calio.datetime.InstantRange
import app.calio.model.CalendarId
import app.calio.model.Priority
import app.calio.model.Reminder
import app.calio.model.ReminderId
import app.calio.model.ReminderTrigger
import app.calio.model.TaskDue
import app.calio.model.TaskId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class CalendarRepositoryTest {

    private val environment = TestEnvironment()
    private val calendars = environment.calendars

    @AfterTest
    fun tearDown() = environment.close()

    @Test
    fun `a calendar survives a round trip`() = runTest {
        val original = calendar(name = "Family")

        calendars.upsert(original)

        val loaded = calendars.byId(original.id)
        assertEquals("Family", loaded?.name)
        assertEquals(original.color, loaded?.color)
        assertTrue(loaded?.isVisible == true)
    }

    @Test
    fun `visibility is a local setting and is not reported for synchronisation`() = runTest {
        // Hiding a calendar on the desktop must not hide it on the phone, so this write deliberately
        // produces neither a new revision nor an outbox entry.
        calendars.upsert(calendar())
        val before = calendars.byId(CalendarId("calendar-1"))!!.audit.revision

        calendars.setVisible(CalendarId("calendar-1"), isVisible = false)

        assertFalse(calendars.byId(CalendarId("calendar-1"))!!.isVisible)
        assertEquals(before, calendars.byId(CalendarId("calendar-1"))!!.audit.revision)
        assertEquals(1, environment.pendingChanges().count { it.entity_type == "CALENDAR" })
    }

    @Test
    fun `only visible calendars are observed as visible`() = runTest {
        calendars.upsert(calendar(id = "shown", name = "Work"))
        calendars.upsert(calendar(id = "hidden", name = "Archive", isVisible = false))

        assertEquals(2, calendars.observeAll().first().size)
        assertEquals(listOf("shown"), calendars.observeVisible().first().map { it.id.value })
    }

    @Test
    fun `making a calendar the default clears the previous one`() = runTest {
        calendars.upsert(calendar(id = "first", name = "Work").copy(isDefault = true))
        calendars.upsert(calendar(id = "second", name = "Private"))

        calendars.setDefault(CalendarId("second"))

        assertFalse(calendars.byId(CalendarId("first"))!!.isDefault)
        assertTrue(calendars.byId(CalendarId("second"))!!.isDefault)
    }

    @Test
    fun `deleting a calendar is recorded for synchronisation`() = runTest {
        calendars.upsert(calendar())

        calendars.delete(CalendarId("calendar-1"))

        assertTrue(calendars.observeAll().first().isEmpty())
        assertEquals(
            listOf("UPSERT", "DELETE"),
            environment.pendingChanges().filter { it.entity_type == "CALENDAR" }.map { it.operation },
        )
    }
}

class TaskRepositoryTest {

    private val environment = TestEnvironment()
    private val tasks = environment.tasks

    @AfterTest
    fun tearDown() = environment.close()

    @Test
    fun `a task survives a round trip with its reminders`() = runTest {
        val original = task().copy(
            description = "Two pages",
            priority = Priority.HIGH,
            due = TaskDue.OnDate(LocalDate(2026, 8, 10)),
            reminders = listOf(Reminder(ReminderId("reminder-1"), ReminderTrigger.BeforeStart(60))),
        )

        tasks.upsert(original)
        val loaded = tasks.byId(original.id)

        assertEquals(original.description, loaded?.description)
        assertEquals(Priority.HIGH, loaded?.priority)
        assertEquals(original.due, loaded?.due)
        assertEquals(original.reminders, loaded?.reminders)
    }

    @Test
    fun `a due date with a time keeps its own zone`() = runTest {
        val due = TaskDue.AtTime(LocalDateTime(2026, 8, 10, 17, 0), berlin)

        tasks.upsert(task().copy(due = due))

        assertEquals(due, tasks.byId(TaskId("task-1"))?.due)
    }

    @Test
    fun `subtasks are observed under their parent`() = runTest {
        tasks.upsert(task(id = "parent"))
        tasks.upsert(task(id = "child", title = "Draft").copy(parentTaskId = TaskId("parent")))

        assertEquals(listOf("parent"), tasks.observeTopLevel().first().map { it.id.value })
        assertEquals(listOf("child"), tasks.observeSubtasks(TaskId("parent")).first().map { it.id.value })
    }

    @Test
    fun `completing a task also completes its progress`() = runTest {
        tasks.upsert(task().copy(progressPercent = 40))

        tasks.setCompleted(TaskId("task-1"), isCompleted = true)

        val completed = tasks.byId(TaskId("task-1"))!!
        assertTrue(completed.isCompleted)
        assertEquals(100, completed.progressPercent)
        assertEquals(environment.clock.now(), completed.completedAt)
    }

    @Test
    fun `reopening a task clears its completion`() = runTest {
        tasks.upsert(task())
        tasks.setCompleted(TaskId("task-1"), isCompleted = true)

        tasks.setCompleted(TaskId("task-1"), isCompleted = false)

        val reopened = tasks.byId(TaskId("task-1"))!!
        assertFalse(reopened.isCompleted)
        assertNull(reopened.completedAt)
        assertEquals(0, reopened.progressPercent)
    }

    @Test
    fun `tasks due inside a window are observed, others are not`() = runTest {
        tasks.upsert(task(id = "soon").copy(due = TaskDue.OnDate(LocalDate(2026, 8, 10))))
        tasks.upsert(task(id = "later").copy(due = TaskDue.OnDate(LocalDate(2026, 10, 1))))
        tasks.upsert(task(id = "undated"))

        val window = InstantRange(
            Instant.parse("2026-08-01T00:00:00Z"),
            Instant.parse("2026-09-01T00:00:00Z"),
        )

        assertEquals(listOf("soon"), tasks.observeDueInRange(window).first().map { it.id.value })
    }

    @Test
    fun `deleting a task is recorded for synchronisation`() = runTest {
        tasks.upsert(task())

        tasks.delete(TaskId("task-1"))

        assertTrue(tasks.observeTopLevel().first().isEmpty())
        assertEquals(
            listOf("UPSERT", "DELETE"),
            environment.pendingChanges().filter { it.entity_type == "TASK" }.map { it.operation },
        )
    }
}
