package app.calio.data.repository

import app.calio.data.mapper.toDomain
import app.calio.database.CalioDatabase
import app.calio.domain.repository.CalendarRepository
import app.calio.domain.repository.CategoryRepository
import app.calio.domain.sync.RevisionSource
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.Category
import app.calio.model.CategoryId
import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class CalendarRepositoryImpl(
    private val database: CalioDatabase,
    private val revisions: RevisionSource,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.System,
) : CalendarRepository {

    private val calendars = database.calendarsQueries

    override fun observeAll(): Flow<List<Calendar>> =
        calendars.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override fun observeVisible(): Flow<List<Calendar>> =
        calendars.selectVisible().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(id: CalendarId): Calendar? = withContext(dispatcher) {
        calendars.selectById(id.value).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(calendar: Calendar): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            val exists = calendars.selectById(calendar.id.value).executeAsOneOrNull() != null
            if (exists) {
                calendars.update(
                    name = calendar.name,
                    color = calendar.color.argb,
                    isVisible = if (calendar.isVisible) 1 else 0,
                    isDefault = if (calendar.isDefault) 1 else 0,
                    sortOrder = calendar.sortOrder.toLong(),
                    updatedAt = now.toEpochMilliseconds(),
                    revision = revision.value,
                    originDevice = revision.deviceId.value,
                    id = calendar.id.value,
                )
            } else {
                calendars.insert(
                    id = calendar.id.value,
                    name = calendar.name,
                    color = calendar.color.argb,
                    is_visible = if (calendar.isVisible) 1 else 0,
                    is_default = if (calendar.isDefault) 1 else 0,
                    sort_order = calendar.sortOrder.toLong(),
                    created_at = calendar.audit.createdAt.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                    revision = revision.value,
                    deleted_at = calendar.audit.deletedAt?.toEpochMilliseconds(),
                    origin_device = revision.deviceId.value,
                )
            }
            database.recordChange(
                entity = SyncedEntity.CALENDAR,
                entityId = calendar.id.value,
                operation = SyncOperation.UPSERT,
                revision = revision,
                at = now,
            )
        }
    }

    /**
     * Visibility is a local view setting rather than shared data, so it is written without a new
     * revision and without an outbox entry. Hiding a calendar on the desktop must not hide it on the
     * phone.
     */
    override suspend fun setVisible(id: CalendarId, isVisible: Boolean): Unit = withContext(dispatcher) {
        val existing = calendars.selectById(id.value).executeAsOneOrNull() ?: return@withContext
        calendars.setVisibility(
            isVisible = if (isVisible) 1 else 0,
            updatedAt = clock.now().toEpochMilliseconds(),
            revision = existing.revision,
            id = id.value,
        )
    }

    override suspend fun setDefault(id: CalendarId): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            val target = calendars.selectById(id.value).executeAsOneOrNull() ?: return@transaction
            calendars.clearDefault()
            calendars.update(
                name = target.name,
                color = target.color,
                isVisible = target.is_visible,
                isDefault = 1,
                sortOrder = target.sort_order,
                updatedAt = now.toEpochMilliseconds(),
                revision = revision.value,
                originDevice = revision.deviceId.value,
                id = id.value,
            )
            database.recordChange(
                entity = SyncedEntity.CALENDAR,
                entityId = id.value,
                operation = SyncOperation.UPSERT,
                revision = revision,
                at = now,
            )
        }
    }

    override suspend fun delete(id: CalendarId): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            calendars.softDelete(
                deletedAt = now.toEpochMilliseconds(),
                revision = revision.value,
                id = id.value,
            )
            database.recordChange(
                entity = SyncedEntity.CALENDAR,
                entityId = id.value,
                operation = SyncOperation.DELETE,
                revision = revision,
                at = now,
            )
        }
    }
}

class CategoryRepositoryImpl(
    private val database: CalioDatabase,
    private val revisions: RevisionSource,
    private val dispatcher: CoroutineDispatcher,
    private val clock: Clock = Clock.System,
) : CategoryRepository {

    private val categories = database.categoriesQueries

    override fun observeAll(): Flow<List<Category>> =
        categories.selectAll().asFlow().mapToList(dispatcher).map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(id: CategoryId): Category? = withContext(dispatcher) {
        categories.selectById(id.value).executeAsOneOrNull()?.toDomain()
    }

    override suspend fun upsert(category: Category): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            val exists = categories.selectById(category.id.value).executeAsOneOrNull() != null
            if (exists) {
                categories.update(
                    name = category.name,
                    color = category.color.argb,
                    sortOrder = category.sortOrder.toLong(),
                    updatedAt = now.toEpochMilliseconds(),
                    revision = revision.value,
                    originDevice = revision.deviceId.value,
                    id = category.id.value,
                )
            } else {
                categories.insert(
                    id = category.id.value,
                    name = category.name,
                    color = category.color.argb,
                    is_built_in = if (category.isBuiltIn) 1 else 0,
                    sort_order = category.sortOrder.toLong(),
                    created_at = category.audit.createdAt.toEpochMilliseconds(),
                    updated_at = now.toEpochMilliseconds(),
                    revision = revision.value,
                    deleted_at = category.audit.deletedAt?.toEpochMilliseconds(),
                    origin_device = revision.deviceId.value,
                )
            }
            database.recordChange(
                entity = SyncedEntity.CATEGORY,
                entityId = category.id.value,
                operation = SyncOperation.UPSERT,
                revision = revision,
                at = now,
            )
        }
    }

    override suspend fun delete(id: CategoryId): Unit = withContext(dispatcher) {
        val revision = revisions.next()
        val now = clock.now()

        database.transaction {
            categories.softDelete(
                deletedAt = now.toEpochMilliseconds(),
                revision = revision.value,
                id = id.value,
            )
            database.recordChange(
                entity = SyncedEntity.CATEGORY,
                entityId = id.value,
                operation = SyncOperation.DELETE,
                revision = revision,
                at = now,
            )
        }
    }
}
