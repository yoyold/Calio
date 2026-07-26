package app.calio.feature.tasks

import app.calio.model.AuditFields
import app.calio.model.Category
import app.calio.model.CategoryId
import app.calio.model.Priority
import app.calio.model.Task
import app.calio.model.TaskDue
import app.calio.model.TaskId
import kotlinx.datetime.LocalDate

/** Which tasks the list is showing. */
enum class TaskFilter(val label: String) {
    Open("Open"),
    Today("Today"),
    Overdue("Overdue"),
    Completed("Done"),
    All("All"),
}

/**
 * A task with everything the row needs, worked out once.
 *
 * Progress, overdue state and the category are resolved here rather than while drawing, so a long
 * list does not repeat the same lookups on every frame of a scroll.
 */
data class TaskItem(
    val task: Task,
    val subtasks: List<Task> = emptyList(),
    val progressPercent: Int = 0,
    val isOverdue: Boolean = false,
    val category: Category? = null,
) {
    val hasSubtasks: Boolean get() = subtasks.isNotEmpty()
    val completedSubtasks: Int get() = subtasks.count { it.isCompleted }
}

data class TasksUiState(
    val today: LocalDate,
    val filter: TaskFilter = TaskFilter.Open,
    val items: List<TaskItem> = emptyList(),
    val categories: List<Category> = emptyList(),
    val counts: Map<TaskFilter, Int> = emptyMap(),
    val expanded: Set<TaskId> = emptySet(),
    val editing: TaskDraft? = null,
    val editorProblems: Set<TaskDraftProblem> = emptySet(),
) {
    val isEmpty: Boolean get() = items.isEmpty()
    val canSaveDraft: Boolean get() = editing != null && editorProblems.isEmpty()
}

sealed interface TasksUiEvent {
    data class SelectFilter(val filter: TaskFilter) : TasksUiEvent
    data class QuickAdd(val title: String) : TasksUiEvent
    data class ToggleCompleted(val id: TaskId, val isCompleted: Boolean) : TasksUiEvent
    data class ToggleExpanded(val id: TaskId) : TasksUiEvent
    data class AddSubtask(val parentId: TaskId, val title: String) : TasksUiEvent
    data class Delete(val id: TaskId) : TasksUiEvent

    data class StartEditing(val id: TaskId) : TasksUiEvent
    data class DraftChanged(val draft: TaskDraft) : TasksUiEvent
    data object SaveDraft : TasksUiEvent
    data object CancelEditing : TasksUiEvent
}

/**
 * What the task editor holds while the user types.
 *
 * Kept apart from [Task] for the same reason the event editor keeps a draft: an entity has to be
 * valid to exist, a draft is invalid for most of the time it is being edited.
 */
data class TaskDraft(
    val taskId: TaskId? = null,
    val parentTaskId: TaskId? = null,
    val title: String = "",
    val description: String = "",
    val priority: Priority = Priority.NONE,
    val dueDate: LocalDate? = null,
    val categoryId: CategoryId? = null,
)

enum class TaskDraftProblem(val message: String) {
    BlankTitle("Give the task a title."),
}

fun TaskDraft.problems(): Set<TaskDraftProblem> = buildSet {
    if (title.isBlank()) add(TaskDraftProblem.BlankTitle)
}

fun Task.toDraft(): TaskDraft = TaskDraft(
    taskId = id,
    parentTaskId = parentTaskId,
    title = title,
    description = description.orEmpty(),
    priority = priority,
    dueDate = due?.date,
    categoryId = categoryId,
)

/**
 * Turns the draft into a task, keeping what the editor does not touch.
 *
 * Due dates are entered as plain dates. A task is due on a day, not at a moment: giving it a time
 * would suggest a precision the list has no way to act on.
 */
fun TaskDraft.toTask(existing: Task?, id: TaskId, audit: AuditFields): Task = Task(
    id = id,
    title = title.trim(),
    audit = audit,
    parentTaskId = parentTaskId,
    description = description.trim().ifBlank { null },
    priority = priority,
    due = dueDate?.let(TaskDue::OnDate),
    categoryId = categoryId,
    progressPercent = existing?.progressPercent ?: 0,
    isCompleted = existing?.isCompleted ?: false,
    completedAt = existing?.completedAt,
    reminders = existing?.reminders.orEmpty(),
    sortOrder = existing?.sortOrder ?: 0,
)
