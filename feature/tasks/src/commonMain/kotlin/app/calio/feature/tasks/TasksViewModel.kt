package app.calio.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.calio.datetime.today
import app.calio.domain.repository.CategoryRepository
import app.calio.domain.repository.TaskRepository
import app.calio.model.AuditFields
import app.calio.model.Category
import app.calio.model.DeviceId
import app.calio.model.Task
import app.calio.model.TaskId
import app.calio.model.derivedProgressPercent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The task list, its filters and the small editor behind it.
 *
 * Editing lives in the same view model as the list because a task editor has nothing to coordinate:
 * no conflicts, no recurrence, no second screen. A separate view model would be a second object to
 * wire for one dialog.
 */
@OptIn(ExperimentalUuidApi::class)
class TasksViewModel(
    private val tasks: TaskRepository,
    private val categories: CategoryRepository,
    private val zone: TimeZone,
    private val clock: Clock = Clock.System,
) : ViewModel() {

    private val filter = MutableStateFlow(TaskFilter.Open)
    private val expanded = MutableStateFlow(emptySet<TaskId>())
    private val editing = MutableStateFlow<TaskDraft?>(null)

    val state: StateFlow<TasksUiState> = combine(
        tasks.observeAll(),
        categories.observeAll(),
        filter,
        expanded,
        editing,
    ) { allTasks, allCategories, currentFilter, currentExpanded, currentDraft ->
        buildState(allTasks, allCategories, currentFilter, currentExpanded, currentDraft)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
        initialValue = TasksUiState(today = clock.today(zone)),
    )

    fun onEvent(event: TasksUiEvent) {
        when (event) {
            is TasksUiEvent.SelectFilter -> filter.value = event.filter

            is TasksUiEvent.ToggleExpanded -> expanded.update { current ->
                if (event.id in current) current - event.id else current + event.id
            }

            is TasksUiEvent.QuickAdd -> viewModelScope.launch { add(event.title, parent = null) }

            is TasksUiEvent.AddSubtask -> viewModelScope.launch {
                add(event.title, parent = event.parentId)
                expanded.update { it + event.parentId }
            }

            is TasksUiEvent.ToggleCompleted -> viewModelScope.launch {
                tasks.setCompleted(event.id, event.isCompleted)
            }

            is TasksUiEvent.Delete -> viewModelScope.launch { tasks.delete(event.id) }

            is TasksUiEvent.StartEditing -> viewModelScope.launch {
                editing.value = tasks.byId(event.id)?.toDraft()
            }

            is TasksUiEvent.DraftChanged -> editing.value = event.draft

            TasksUiEvent.SaveDraft -> viewModelScope.launch { saveDraft() }

            TasksUiEvent.CancelEditing -> editing.value = null
        }
    }

    private suspend fun add(title: String, parent: TaskId?) {
        if (title.isBlank()) return

        tasks.upsert(
            TaskDraft(title = title, parentTaskId = parent).toTask(
                existing = null,
                id = TaskId(Uuid.random().toString()),
                audit = AuditFields.forNewEntity(clock.now(), DEVICE_PLACEHOLDER),
            ),
        )
    }

    private suspend fun saveDraft() {
        val draft = editing.value ?: return
        if (draft.problems().isNotEmpty()) return

        val existing = draft.taskId?.let { tasks.byId(it) }
        val audit = existing?.audit?.copy(updatedAt = clock.now())
            ?: AuditFields.forNewEntity(clock.now(), DEVICE_PLACEHOLDER)

        tasks.upsert(
            draft.toTask(
                existing = existing,
                id = draft.taskId ?: TaskId(Uuid.random().toString()),
                audit = audit,
            ),
        )
        editing.value = null
    }

    private fun buildState(
        allTasks: List<Task>,
        allCategories: List<Category>,
        currentFilter: TaskFilter,
        currentExpanded: Set<TaskId>,
        currentDraft: TaskDraft?,
    ): TasksUiState {
        val today = clock.today(zone)
        val categoriesById = allCategories.associateBy { it.id }
        val subtasksByParent = allTasks.filter { it.parentTaskId != null }.groupBy { it.parentTaskId }

        val items = allTasks
            .filter { it.parentTaskId == null }
            .map { task ->
                val subtasks = subtasksByParent[task.id].orEmpty()
                TaskItem(
                    task = task,
                    subtasks = subtasks,
                    // A parent's progress follows its subtasks rather than its own field, so the two
                    // can never contradict each other in the list.
                    progressPercent = if (subtasks.isEmpty()) {
                        if (task.isCompleted) COMPLETE else task.progressPercent
                    } else {
                        derivedProgressPercent(subtasks)
                    },
                    isOverdue = task.isOverdueOn(today),
                    category = task.categoryId?.let(categoriesById::get),
                )
            }

        return TasksUiState(
            today = today,
            filter = currentFilter,
            items = items.filter { currentFilter.accepts(it, today) }.sortedWith(listOrder),
            categories = allCategories,
            counts = TaskFilter.entries.associateWith { candidate ->
                items.count { candidate.accepts(it, today) }
            },
            expanded = currentExpanded,
            editing = currentDraft,
            editorProblems = currentDraft?.problems().orEmpty(),
        )
    }

    private companion object {
        const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        const val COMPLETE = 100

        /** The audit device is a placeholder; the repository replaces it when it writes. */
        val DEVICE_PLACEHOLDER = DeviceId("pending")

        /**
         * Open before done, then by due date with undated tasks last, then by priority.
         *
         * Undated tasks sort last rather than first: a task nobody put a date on is by definition
         * the one that can wait.
         */
        val listOrder = compareBy<TaskItem>(
            { it.task.isCompleted },
            { it.task.due?.date ?: LocalDate(9999, 12, 31) },
            { -it.task.priority.ordinal },
            { it.task.title.lowercase() },
        )
    }
}

private fun Task.isOverdueOn(today: LocalDate): Boolean {
    val dueDate = due?.date ?: return false
    return !isCompleted && dueDate < today
}

private fun TaskFilter.accepts(item: TaskItem, today: LocalDate): Boolean = when (this) {
    TaskFilter.All -> true
    TaskFilter.Open -> !item.task.isCompleted
    TaskFilter.Completed -> item.task.isCompleted
    TaskFilter.Today -> !item.task.isCompleted && item.task.due?.date == today
    TaskFilter.Overdue -> item.isOverdue
}
