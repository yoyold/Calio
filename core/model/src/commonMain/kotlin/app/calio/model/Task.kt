package app.calio.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant

enum class Priority { NONE, LOW, MEDIUM, HIGH }

/**
 * When a task is due. A plain date means "some time that day"; a date and time means a specific
 * moment and therefore needs a zone, exactly like an event.
 */
sealed interface TaskDue {

    val date: LocalDate

    /** The instant used for sorting and for the overdue check. */
    fun instant(fallbackZone: TimeZone): Instant

    data class OnDate(override val date: LocalDate) : TaskDue {
        // A date-only due date has no time of day, so it is anchored to the start of the day in the
        // zone the user is currently in. Whether the task is overdue is decided by comparing dates,
        // not this instant.
        override fun instant(fallbackZone: TimeZone): Instant = date.atStartOfDayIn(fallbackZone)
    }

    data class AtTime(val dateTime: LocalDateTime, val timeZone: TimeZone) : TaskDue {
        override val date: LocalDate get() = dateTime.date

        override fun instant(fallbackZone: TimeZone): Instant = dateTime.toInstant(timeZone)
    }
}

/**
 * A to-do item. Subtasks are tasks with a [parentTaskId]; the schema allows deeper nesting but a use
 * case restricts it to one level, because a checklist that turns into a tree stops being usable.
 */
data class Task(
    val id: TaskId,
    val title: String,
    val audit: AuditFields,
    val parentTaskId: TaskId? = null,
    val description: String? = null,
    val priority: Priority = Priority.NONE,
    val due: TaskDue? = null,
    val categoryId: CategoryId? = null,
    val progressPercent: Int = 0,
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val reminders: List<Reminder> = emptyList(),
    val sortOrder: Int = 0,
) {
    init {
        require(title.isNotBlank()) { "task title must not be blank" }
        require(progressPercent in 0..100) { "progress must be within 0..100" }
        require(parentTaskId != id) { "a task must not be its own parent" }
        require(isCompleted || completedAt == null) {
            "completedAt must only be set on a completed task"
        }
    }
}

/**
 * Progress of a task that has subtasks, as the share of completed subtasks.
 *
 * A parent's progress is derived rather than stored so it cannot go stale when a subtask changes.
 */
fun derivedProgressPercent(subtasks: List<Task>): Int {
    if (subtasks.isEmpty()) return 0
    return subtasks.count { it.isCompleted } * 100 / subtasks.size
}
