package app.calio.data.repository

import app.calio.data.mapper.absoluteUtcOrNull
import app.calio.data.mapper.leadMinutesOrNull
import app.calio.data.mapper.localText
import app.calio.data.mapper.sortUtc
import app.calio.data.mapper.timeZoneIdOrNull
import app.calio.data.mapper.toDomain
import app.calio.data.mapper.typeName
import app.calio.database.CalioDatabase
import app.calio.datetime.InstantRange
import app.calio.domain.repository.TaskRepository
import app.calio.domain.sync.RevisionSource
import app.calio.model.Task
import app.calio.model.TaskId
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class TaskRepositoryImpl(
    private val database: CalioDatabase,
    private val revisions: RevisionSource,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.System,
) : TaskRepository {

    private val tasks = database.tasksQueries

    override fun observeAll(): Flow<List<Task>> =
        tasks.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeTopLevel(): Flow<List<Task>> =
        tasks.selectTopLevel().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeSubtasks(parentId: TaskId): Flow<List<Task>> =
        tasks.selectSubtasks(parentId.value).asFlow().mapToList(dispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeDueInRange(window: InstantRange): Flow<List<Task>> =
        tasks.selectDueInRange(
            rangeStartUtc = window.start.toEpochMilliseconds(),
            rangeEndUtc = window.endExclusive.toEpochMilliseconds(),
        ).asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(id: TaskId): Task? = withContext(dispatcher) {
        tasks.selectById(id.value).executeAsOneOrNull()?.let { row ->
            row.toDomain(
                reminders = database.remindersQueries.selectForTask(id.value)
                    .executeAsList()
                    .map { it.toDomain() },
            )
        }
    }

    override suspend fun upsert(task: Task): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            val exists = tasks.selectById(task.id.value).executeAsOneOrNull() != null
            if (exists) {
                tasks.update(
                    parentTaskId = task.parentTaskId?.value,
                    categoryId = task.categoryId?.value,
                    title = task.title,
                    description = task.description,
                    priority = task.priority.name,
                    dueLocal = task.due?.localText,
                    dueTimeZoneId = task.due?.timeZoneIdOrNull,
                    dueUtc = task.due?.sortUtc,
                    progressPercent = task.progressPercent.toLong(),
                    isCompleted = if (task.isCompleted) 1 else 0,
                    completedAt = task.completedAt?.toEpochMilliseconds(),
                    sortOrder = task.sortOrder.toLong(),
                    updatedAt = now.toEpochMilliseconds(),
                    revision = revision.value,
                    originDevice = revision.deviceId.value,
                    id = task.id.value,
                )
            } else {
                tasks.insert(
                    id = task.id.value,
                    parent_task_id = task.parentTaskId?.value,
                    category_id = task.categoryId?.value,
                    title = task.title,
                    description = task.description,
                    priority = task.priority.name,
                    due_local = task.due?.localText,
                    due_time_zone_id = task.due?.timeZoneIdOrNull,
                    due_utc = task.due?.sortUtc,
                    progress_percent = task.progressPercent.toLong(),
                    is_completed = if (task.isCompleted) 1 else 0,
                    completed_at = task.completedAt?.toEpochMilliseconds(),
                    sort_order = task.sortOrder.toLong(),
                    created_at = task.audit.createdAt.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                    revision = revision.value,
                    deleted_at = task.audit.deletedAt?.toEpochMilliseconds(),
                    origin_device = revision.deviceId.value,
                )
            }

            database.remindersQueries.deleteForTask(task.id.value)
            task.reminders.forEachIndexed { index, reminder ->
                database.remindersQueries.insert(
                    id = reminder.id.value,
                    event_id = null,
                    task_id = task.id.value,
                    trigger_type = reminder.trigger.typeName,
                    lead_minutes = reminder.trigger.leadMinutesOrNull,
                    absolute_utc = reminder.trigger.absoluteUtcOrNull,
                    channel = reminder.channel.name,
                    sort_order = index.toLong(),
                )
            }

            database.recordChange(
                entity = SyncedEntity.TASK,
                entityId = task.id.value,
                operation = SyncOperation.UPSERT,
                revision = revision,
                at = now,
            )
        }
    }

    /**
     * Completing a task sets its progress to the matching extreme, so the two can never contradict
     * each other in the list.
     */
    override suspend fun setCompleted(id: TaskId, isCompleted: Boolean): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            tasks.setCompleted(
                isCompleted = if (isCompleted) 1 else 0,
                completedAt = if (isCompleted) now.toEpochMilliseconds() else null,
                progressPercent = if (isCompleted) 100 else 0,
                updatedAt = now.toEpochMilliseconds(),
                revision = revision.value,
                id = id.value,
            )
            database.recordChange(
                entity = SyncedEntity.TASK,
                entityId = id.value,
                operation = SyncOperation.UPSERT,
                revision = revision,
                at = now,
            )
        }
    }

    override suspend fun delete(id: TaskId): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            tasks.softDelete(
                deletedAt = now.toEpochMilliseconds(),
                revision = revision.value,
                id = id.value,
            )
            database.recordChange(
                entity = SyncedEntity.TASK,
                entityId = id.value,
                operation = SyncOperation.DELETE,
                revision = revision,
                at = now,
            )
        }
    }
}
