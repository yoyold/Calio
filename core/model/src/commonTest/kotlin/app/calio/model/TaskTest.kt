package app.calio.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Instant

class TaskTest {

    private val berlin = TimeZone.of("Europe/Berlin")

    private fun task(
        id: String = "task-1",
        title: String = "Write the report",
        parentTaskId: TaskId? = null,
        progressPercent: Int = 0,
        isCompleted: Boolean = false,
        completedAt: Instant? = null,
    ) = Task(
        id = TaskId(id),
        title = title,
        audit = audit(),
        parentTaskId = parentTaskId,
        progressPercent = progressPercent,
        isCompleted = isCompleted,
        completedAt = completedAt,
    )

    @Test
    fun `rejects a blank title`() {
        assertFailsWith<IllegalArgumentException> { task(title = " ") }
    }

    @Test
    fun `rejects progress outside the percentage range`() {
        assertFailsWith<IllegalArgumentException> { task(progressPercent = 101) }
        assertFailsWith<IllegalArgumentException> { task(progressPercent = -1) }
    }

    @Test
    fun `rejects a task that is its own parent`() {
        assertFailsWith<IllegalArgumentException> {
            task(id = "task-1", parentTaskId = TaskId("task-1"))
        }
    }

    @Test
    fun `rejects a completion timestamp on an open task`() {
        assertFailsWith<IllegalArgumentException> {
            task(isCompleted = false, completedAt = Instant.parse("2026-07-02T08:00:00Z"))
        }
    }

    @Test
    fun `progress of a task without subtasks is zero`() {
        assertEquals(0, derivedProgressPercent(emptyList()))
    }

    @Test
    fun `progress is the share of completed subtasks`() {
        val subtasks = listOf(
            task(id = "a", isCompleted = true, completedAt = Instant.parse("2026-07-02T08:00:00Z")),
            task(id = "b", isCompleted = true, completedAt = Instant.parse("2026-07-02T09:00:00Z")),
            task(id = "c"),
            task(id = "d"),
        )

        assertEquals(50, derivedProgressPercent(subtasks))
    }

    @Test
    fun `progress reaches one hundred only when every subtask is done`() {
        val done = List(3) {
            task(id = "s$it", isCompleted = true, completedAt = Instant.parse("2026-07-02T08:00:00Z"))
        }

        assertEquals(100, derivedProgressPercent(done))
        assertEquals(66, derivedProgressPercent(done.dropLast(1) + task(id = "open")))
    }

    @Test
    fun `a date only due date resolves to the start of that day`() {
        val due = TaskDue.OnDate(LocalDate(2026, 8, 3))

        assertEquals(LocalDate(2026, 8, 3), due.date)
        assertEquals(Instant.parse("2026-08-02T22:00:00Z"), due.instant(berlin))
    }

    @Test
    fun `a timed due date uses its own zone and not the fallback`() {
        val due = TaskDue.AtTime(LocalDateTime(2026, 8, 3, 17, 0), berlin)

        assertEquals(LocalDate(2026, 8, 3), due.date)
        assertEquals(Instant.parse("2026-08-03T15:00:00Z"), due.instant(TimeZone.UTC))
    }
}
