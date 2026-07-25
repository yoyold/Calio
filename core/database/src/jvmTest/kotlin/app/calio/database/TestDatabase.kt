package app.calio.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.util.Properties

/**
 * A throwaway database for one test.
 *
 * The tests run against the real schema in memory rather than against mocks, so they verify the
 * actual SQL — constraints, cascades, indices and triggers included — and still finish in
 * milliseconds. The driver runs the same statements the desktop build uses; the Android driver is a
 * different code path and is covered separately by instrumentation tests.
 */
internal class TestDatabase : AutoCloseable {

    private val driver = JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties(),
        schema = CalioDatabase.Schema,
    )

    val db: CalioDatabase = CalioDatabase(driver)

    init {
        driver.enableForeignKeys()
    }

    override fun close() {
        driver.close()
    }
}

internal const val TEST_DEVICE = "test-device"
internal const val NOW = 1_753_400_000_000L

internal fun revision(millis: Long = NOW, counter: Long = 0): String = "$millis-$counter-$TEST_DEVICE"

internal fun CalioDatabase.putCalendar(
    id: String = "calendar-1",
    name: String = "Work",
    isVisible: Long = 1,
    isDefault: Long = 0,
    deletedAt: Long? = null,
) {
    calendarsQueries.insert(
        id = id,
        name = name,
        color = 0xFF2196F3,
        is_visible = isVisible,
        is_default = isDefault,
        sort_order = 0,
        created_at = NOW,
        updated_at = NOW,
        revision = revision(),
        deleted_at = deletedAt,
        origin_device = TEST_DEVICE,
    )
}

internal fun CalioDatabase.putCategory(
    id: String = "category-1",
    name: String = "Study",
    isBuiltIn: Long = 0,
    deletedAt: Long? = null,
) {
    categoriesQueries.insert(
        id = id,
        name = name,
        color = 0xFFE91E63,
        is_built_in = isBuiltIn,
        sort_order = 0,
        created_at = NOW,
        updated_at = NOW,
        revision = revision(),
        deleted_at = deletedAt,
        origin_device = TEST_DEVICE,
    )
}

@Suppress("LongParameterList")
internal fun CalioDatabase.putEvent(
    id: String = "event-1",
    calendarId: String = "calendar-1",
    title: String = "Meeting",
    startUtc: Long,
    endUtc: Long,
    description: String? = null,
    notes: String? = null,
    location: String? = null,
    categoryId: String? = null,
    recurrenceRule: String? = null,
    recurrenceUntilUtc: Long? = null,
    deletedAt: Long? = null,
) {
    eventsQueries.insert(
        id = id,
        calendar_id = calendarId,
        category_id = categoryId,
        title = title,
        description = description,
        notes = notes,
        location_label = location,
        location_lat = null,
        location_lon = null,
        is_all_day = 0,
        start_local = "2026-08-03T09:00",
        end_local = "2026-08-03T10:00",
        time_zone_id = "Europe/Berlin",
        start_utc = startUtc,
        end_utc = endUtc,
        recurrence_rule = recurrenceRule,
        recurrence_until_utc = recurrenceUntilUtc,
        color_override = null,
        kind = "STANDARD",
        busy_status = "BUSY",
        status = "CONFIRMED",
        created_at = NOW,
        updated_at = NOW,
        revision = revision(),
        deleted_at = deletedAt,
        origin_device = TEST_DEVICE,
    )
}

internal fun CalioDatabase.putTask(
    id: String = "task-1",
    title: String = "Write the report",
    parentTaskId: String? = null,
    description: String? = null,
    dueUtc: Long? = null,
    progressPercent: Long = 0,
    isCompleted: Long = 0,
    completedAt: Long? = null,
    deletedAt: Long? = null,
) {
    tasksQueries.insert(
        id = id,
        parent_task_id = parentTaskId,
        category_id = null,
        title = title,
        description = description,
        priority = "NONE",
        due_local = null,
        due_time_zone_id = null,
        due_utc = dueUtc,
        progress_percent = progressPercent,
        is_completed = isCompleted,
        completed_at = completedAt,
        sort_order = 0,
        created_at = NOW,
        updated_at = NOW,
        revision = revision(),
        deleted_at = deletedAt,
        origin_device = TEST_DEVICE,
    )
}
