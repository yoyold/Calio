package app.calio.feature.tasks

import app.calio.model.Priority
import app.calio.model.Task
import app.calio.model.TaskDue
import app.calio.model.TaskId
import app.calio.testing.FakeCategoryRepository
import app.calio.testing.FakeTaskRepository
import app.calio.testing.FixedClock
import app.calio.testing.testCategory
import app.calio.testing.testTask
import app.calio.testing.testZone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModelTest {

    // Wednesday, 5 August 2026 in Berlin.
    private val clock = FixedClock(Instant.parse("2026-08-05T08:30:00Z"))
    private val today = LocalDate(2026, 8, 5)

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.tasks(
        initial: List<Task> = emptyList(),
    ): Pair<TasksViewModel, FakeTaskRepository> {
        val repository = FakeTaskRepository(initial)
        val viewModel = TasksViewModel(
            tasks = repository,
            categories = FakeCategoryRepository(listOf(testCategory)),
            zone = testZone,
            clock = clock,
        )
        backgroundScope.launch { viewModel.state.collect { } }
        return viewModel to repository
    }

    private fun task(
        id: String,
        title: String = "Task $id",
        parent: TaskId? = null,
        due: LocalDate? = null,
        completed: Boolean = false,
        priority: Priority = Priority.NONE,
    ) = testTask(id = id, title = title).copy(
        parentTaskId = parent,
        due = due?.let(TaskDue::OnDate),
        isCompleted = completed,
        completedAt = if (completed) clock.now() else null,
        progressPercent = if (completed) 100 else 0,
        priority = priority,
    )

    @Test
    fun `quick add creates a top level task`() = runTest {
        val (viewModel, repository) = tasks()
        viewModel.state.first()

        viewModel.onEvent(TasksUiEvent.QuickAdd("Write the report"))

        val state = viewModel.state.first { it.items.isNotEmpty() }
        assertEquals("Write the report", state.items.single().task.title)
        assertEquals(null, repository.stored.single().parentTaskId)
    }

    @Test
    fun `a blank quick add is ignored`() = runTest {
        val (viewModel, repository) = tasks()
        viewModel.state.first()

        viewModel.onEvent(TasksUiEvent.QuickAdd("   "))
        testScheduler.runCurrent()

        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun `subtasks are grouped under their parent and not listed on their own`() = runTest {
        val (viewModel, _) = tasks(
            listOf(task("parent"), task("child", parent = TaskId("parent"))),
        )

        val state = viewModel.state.first { it.items.isNotEmpty() }

        assertEquals(1, state.items.size)
        assertEquals(listOf("child"), state.items.single().subtasks.map { it.id.value })
    }

    @Test
    fun `a parent's progress follows its subtasks`() = runTest {
        val (viewModel, _) = tasks(
            listOf(
                task("parent"),
                task("a", parent = TaskId("parent"), completed = true),
                task("b", parent = TaskId("parent")),
                task("c", parent = TaskId("parent")),
                task("d", parent = TaskId("parent")),
            ),
        )

        val item = viewModel.state.first { it.items.isNotEmpty() }.items.single()

        assertEquals(25, item.progressPercent)
        assertEquals(1, item.completedSubtasks)
    }

    @Test
    fun `a task without subtasks is complete or not at all`() = runTest {
        val (viewModel, _) = tasks(listOf(task("done", completed = true), task("open")))
        viewModel.state.first { it.items.isNotEmpty() }

        viewModel.onEvent(TasksUiEvent.SelectFilter(TaskFilter.All))
        val items = viewModel.state.first { it.filter == TaskFilter.All && it.items.size == 2 }.items

        assertEquals(100, items.single { it.task.id.value == "done" }.progressPercent)
        assertEquals(0, items.single { it.task.id.value == "open" }.progressPercent)
    }

    @Test
    fun `an unfinished task with a past due date is overdue`() = runTest {
        val (viewModel, _) = tasks(
            listOf(
                task("late", due = LocalDate(2026, 8, 1)),
                task("today", due = today),
                task("done-late", due = LocalDate(2026, 8, 1), completed = true),
            ),
        )

        val state = viewModel.state.first { it.items.size == 2 }
        val overdue = state.items.filter { it.isOverdue }

        assertEquals(listOf("late"), overdue.map { it.task.id.value })
    }

    @Test
    fun `the open filter hides completed tasks`() = runTest {
        val (viewModel, _) = tasks(listOf(task("open"), task("done", completed = true)))

        val state = viewModel.state.first { it.items.isNotEmpty() }

        assertEquals(listOf("open"), state.items.map { it.task.id.value })
    }

    @Test
    fun `each filter shows what it promises`() = runTest {
        val (viewModel, _) = tasks(
            listOf(
                task("open"),
                task("due-today", due = today),
                task("late", due = LocalDate(2026, 8, 1)),
                task("done", completed = true),
            ),
        )
        viewModel.state.first { it.items.isNotEmpty() }

        suspend fun idsFor(filter: TaskFilter): List<String> {
            viewModel.onEvent(TasksUiEvent.SelectFilter(filter))
            return viewModel.state.first { it.filter == filter }.items.map { it.task.id.value }
        }

        assertEquals(listOf("done"), idsFor(TaskFilter.Completed))
        assertEquals(listOf("due-today"), idsFor(TaskFilter.Today))
        assertEquals(listOf("late"), idsFor(TaskFilter.Overdue))
        assertEquals(4, idsFor(TaskFilter.All).size)
    }

    @Test
    fun `the counts describe every filter, not only the visible one`() = runTest {
        val (viewModel, _) = tasks(
            listOf(task("open"), task("late", due = LocalDate(2026, 8, 1)), task("done", completed = true)),
        )

        val state = viewModel.state.first { it.items.isNotEmpty() }

        assertEquals(2, state.counts[TaskFilter.Open])
        assertEquals(1, state.counts[TaskFilter.Overdue])
        assertEquals(1, state.counts[TaskFilter.Completed])
        assertEquals(3, state.counts[TaskFilter.All])
    }

    @Test
    fun `dated tasks come before undated ones`() = runTest {
        val (viewModel, _) = tasks(
            listOf(
                task("no-date", title = "A task without a date"),
                task("later", due = LocalDate(2026, 8, 20)),
                task("soon", due = LocalDate(2026, 8, 6)),
            ),
        )

        val state = viewModel.state.first { it.items.size == 3 }

        assertEquals(listOf("soon", "later", "no-date"), state.items.map { it.task.id.value })
    }

    @Test
    fun `completing a task moves it out of the open list`() = runTest {
        val (viewModel, repository) = tasks(listOf(task("open")))
        viewModel.state.first { it.items.isNotEmpty() }

        viewModel.onEvent(TasksUiEvent.ToggleCompleted(TaskId("open"), isCompleted = true))

        viewModel.state.first { it.items.isEmpty() }
        assertTrue(repository.stored.single().isCompleted)
        assertEquals(100, repository.stored.single().progressPercent)
    }

    @Test
    fun `a subtask is added under its parent and opens it`() = runTest {
        val (viewModel, repository) = tasks(listOf(task("parent")))
        viewModel.state.first { it.items.isNotEmpty() }

        viewModel.onEvent(TasksUiEvent.AddSubtask(TaskId("parent"), "Draft the outline"))

        val state = viewModel.state.first { it.items.single().hasSubtasks }
        assertEquals("Draft the outline", state.items.single().subtasks.single().title)
        assertTrue(TaskId("parent") in state.expanded)
        assertEquals(2, repository.stored.size)
    }

    @Test
    fun `deleting a parent takes its subtasks with it`() = runTest {
        val (viewModel, repository) = tasks(
            listOf(task("parent"), task("child", parent = TaskId("parent"))),
        )
        viewModel.state.first { it.items.isNotEmpty() }

        viewModel.onEvent(TasksUiEvent.Delete(TaskId("parent")))

        viewModel.state.first { it.items.isEmpty() }
        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun `editing loads the task and writes the changes back`() = runTest {
        val (viewModel, repository) = tasks(listOf(task("task-1", title = "Rough note")))
        viewModel.state.first { it.items.isNotEmpty() }

        viewModel.onEvent(TasksUiEvent.StartEditing(TaskId("task-1")))
        val editing = viewModel.state.first { it.editing != null }
        assertEquals("Rough note", editing.editing?.title)

        viewModel.onEvent(
            TasksUiEvent.DraftChanged(
                editing.editing!!.copy(
                    title = "Write the report",
                    priority = Priority.HIGH,
                    dueDate = LocalDate(2026, 8, 12),
                    categoryId = testCategory.id,
                ),
            ),
        )
        viewModel.onEvent(TasksUiEvent.SaveDraft)

        val saved = viewModel.state.first { it.editing == null && it.items.isNotEmpty() }
        val item = saved.items.single()
        assertEquals("Write the report", item.task.title)
        assertEquals(Priority.HIGH, item.task.priority)
        assertEquals(LocalDate(2026, 8, 12), item.task.due?.date)
        assertEquals(testCategory, item.category)
        assertEquals(1, repository.stored.size)
    }

    @Test
    fun `an empty title blocks saving the draft`() = runTest {
        val (viewModel, repository) = tasks(listOf(task("task-1", title = "Rough note")))
        viewModel.state.first { it.items.isNotEmpty() }
        viewModel.onEvent(TasksUiEvent.StartEditing(TaskId("task-1")))
        val editing = viewModel.state.first { it.editing != null }

        viewModel.onEvent(TasksUiEvent.DraftChanged(editing.editing!!.copy(title = "  ")))
        val blocked = viewModel.state.first { it.editing?.title == "  " }

        assertFalse(blocked.canSaveDraft)

        viewModel.onEvent(TasksUiEvent.SaveDraft)
        testScheduler.runCurrent()

        assertEquals("Rough note", repository.stored.single().title)
    }

    @Test
    fun `cancelling leaves the task untouched`() = runTest {
        val (viewModel, repository) = tasks(listOf(task("task-1", title = "Rough note")))
        viewModel.state.first { it.items.isNotEmpty() }
        viewModel.onEvent(TasksUiEvent.StartEditing(TaskId("task-1")))
        val editing = viewModel.state.first { it.editing != null }

        viewModel.onEvent(TasksUiEvent.DraftChanged(editing.editing!!.copy(title = "Changed")))
        viewModel.onEvent(TasksUiEvent.CancelEditing)

        viewModel.state.first { it.editing == null }
        assertEquals("Rough note", repository.stored.single().title)
    }
}
