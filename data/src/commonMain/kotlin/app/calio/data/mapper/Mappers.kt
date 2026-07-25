package app.calio.data.mapper

import app.calio.model.Attachment
import app.calio.model.AttachmentId
import app.calio.model.Attendee
import app.calio.model.AttendeeId
import app.calio.model.AttendeeRole
import app.calio.model.AuditFields
import app.calio.model.BusyStatus
import app.calio.model.CalendarId
import app.calio.model.CalioColor
import app.calio.model.CategoryId
import app.calio.model.DeviceId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.EventKind
import app.calio.model.EventLocation
import app.calio.model.EventStatus
import app.calio.model.EventTimeRange
import app.calio.model.OverrideType
import app.calio.model.Priority
import app.calio.model.RecurrenceOverride
import app.calio.model.Reminder
import app.calio.model.ReminderChannel
import app.calio.model.ReminderId
import app.calio.model.ReminderTrigger
import app.calio.model.ResponseStatus
import app.calio.model.Revision
import app.calio.model.Task
import app.calio.model.TaskDue
import app.calio.model.TaskId
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toInstant
import kotlin.time.Instant
import app.calio.database.Attachment as AttachmentRow
import app.calio.database.Attendee as AttendeeRow
import app.calio.database.Calendar as CalendarRow
import app.calio.database.Category as CategoryRow
import app.calio.database.Event as EventRow
import app.calio.database.Recurrence_override as RecurrenceOverrideRow
import app.calio.database.Reminder as ReminderRow
import app.calio.database.Task as TaskRow
import app.calio.model.Calendar as CalendarEntity
import app.calio.model.Category as CategoryEntity

/**
 * Translation between database rows and domain entities.
 *
 * The domain never sees a generated row type and the database never sees a domain type; everything
 * crosses here. Keeping the translation in one place is what allows the schema and the model to
 * evolve at different speeds, and it is the only file that has to change when a column is renamed.
 */

// region audit

internal fun audit(
    createdAt: Long,
    updatedAt: Long,
    revision: String,
    deletedAt: Long?,
    originDevice: String,
): AuditFields = AuditFields(
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    revision = Revision(revision),
    originDevice = DeviceId(originDevice),
    deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
)

// endregion

// region calendars and categories

internal fun CalendarRow.toDomain(): CalendarEntity = CalendarEntity(
    id = CalendarId(id),
    name = name,
    color = CalioColor(color),
    audit = audit(created_at, updated_at, revision, deleted_at, origin_device),
    isVisible = is_visible != 0L,
    isDefault = is_default != 0L,
    sortOrder = sort_order.toInt(),
)

internal fun CategoryRow.toDomain(): CategoryEntity = CategoryEntity(
    id = CategoryId(id),
    name = name,
    color = CalioColor(color),
    audit = audit(created_at, updated_at, revision, deleted_at, origin_device),
    isBuiltIn = is_built_in != 0L,
    sortOrder = sort_order.toInt(),
)

// endregion

// region events

internal fun EventRow.toDomain(
    reminders: List<Reminder> = emptyList(),
    attendees: List<Attendee> = emptyList(),
    attachments: List<Attachment> = emptyList(),
): Event = Event(
    id = EventId(id),
    calendarId = CalendarId(calendar_id),
    title = title,
    timeRange = timeRange(),
    audit = audit(created_at, updated_at, revision, deleted_at, origin_device),
    categoryId = category_id?.let(::CategoryId),
    description = description,
    notes = notes,
    location = location_label?.let { EventLocation(it, location_lat, location_lon) },
    recurrence = recurrence_rule?.let(RecurrenceRuleCodec::decode),
    reminders = reminders,
    attendees = attendees,
    attachments = attachments,
    colorOverride = color_override?.let(::CalioColor),
    kind = EventKind.valueOf(kind),
    busyStatus = BusyStatus.valueOf(busy_status),
    status = EventStatus.valueOf(status),
)

private fun EventRow.timeRange(): EventTimeRange = if (is_all_day != 0L) {
    EventTimeRange.AllDay(LocalDate.parse(start_local), LocalDate.parse(end_local))
} else {
    EventTimeRange.Zoned(
        start = LocalDateTime.parse(start_local),
        endExclusive = LocalDateTime.parse(end_local),
        timeZone = TimeZone.of(requireNotNull(time_zone_id) { "a zoned event needs a time zone" }),
    )
}

/**
 * The wall-clock values written to the database. They are the source of truth; the `*_utc` columns
 * are derived from them by [EventTimeRange] and exist purely so range queries stay integer
 * comparisons.
 */
internal val EventTimeRange.startLocalText: String
    get() = when (this) {
        is EventTimeRange.Zoned -> start.toString()
        is EventTimeRange.AllDay -> startDate.toString()
    }

internal val EventTimeRange.endLocalText: String
    get() = when (this) {
        is EventTimeRange.Zoned -> endExclusive.toString()
        is EventTimeRange.AllDay -> endDateExclusive.toString()
    }

internal val EventTimeRange.timeZoneIdOrNull: String?
    get() = (this as? EventTimeRange.Zoned)?.timeZone?.id

internal fun Event.recurrenceRuleText(): String? = recurrence?.let(RecurrenceRuleCodec::encode)

internal fun RecurrenceOverrideRow.toDomain(): RecurrenceOverride = RecurrenceOverride(
    eventId = EventId(event_id),
    originalStart = LocalDateTime.parse(original_start),
    type = OverrideType.valueOf(type),
    patch = patch_json?.let(EventPatchCodec::decode),
)

// endregion

// region reminders, attendees and attachments

internal fun ReminderRow.toDomain(): Reminder = Reminder(
    id = ReminderId(id),
    trigger = when (trigger_type) {
        "BEFORE_START" -> ReminderTrigger.BeforeStart(requireLead())
        "BEFORE_END" -> ReminderTrigger.BeforeEnd(requireLead())
        "ABSOLUTE" -> ReminderTrigger.Absolute(
            Instant.fromEpochMilliseconds(
                requireNotNull(absolute_utc) { "an absolute reminder needs an instant" },
            ),
        )

        else -> error("unknown reminder trigger: $trigger_type")
    },
    channel = ReminderChannel.valueOf(channel),
)

private fun ReminderRow.requireLead(): Int =
    requireNotNull(lead_minutes) { "a relative reminder needs a lead time" }.toInt()

internal val ReminderTrigger.typeName: String
    get() = when (this) {
        is ReminderTrigger.BeforeStart -> "BEFORE_START"
        is ReminderTrigger.BeforeEnd -> "BEFORE_END"
        is ReminderTrigger.Absolute -> "ABSOLUTE"
    }

internal val ReminderTrigger.leadMinutesOrNull: Long?
    get() = when (this) {
        is ReminderTrigger.BeforeStart -> leadMinutes.toLong()
        is ReminderTrigger.BeforeEnd -> leadMinutes.toLong()
        is ReminderTrigger.Absolute -> null
    }

internal val ReminderTrigger.absoluteUtcOrNull: Long?
    get() = (this as? ReminderTrigger.Absolute)?.at?.toEpochMilliseconds()

internal fun AttendeeRow.toDomain(): Attendee = Attendee(
    id = AttendeeId(id),
    name = name,
    email = email,
    role = AttendeeRole.valueOf(role),
    response = ResponseStatus.valueOf(response),
)

internal fun AttachmentRow.toDomain(): Attachment = Attachment(
    id = AttachmentId(id),
    fileName = file_name,
    mimeType = mime_type,
    sizeBytes = size_bytes,
    checksum = checksum,
    localPath = local_path,
)

// endregion

// region tasks

internal fun TaskRow.toDomain(reminders: List<Reminder> = emptyList()): Task = Task(
    id = TaskId(id),
    title = title,
    audit = audit(created_at, updated_at, revision, deleted_at, origin_device),
    parentTaskId = parent_task_id?.let(::TaskId),
    description = description,
    priority = Priority.valueOf(priority),
    due = due(),
    categoryId = category_id?.let(::CategoryId),
    progressPercent = progress_percent.toInt(),
    isCompleted = is_completed != 0L,
    completedAt = completed_at?.let(Instant::fromEpochMilliseconds),
    reminders = reminders,
    sortOrder = sort_order.toInt(),
)

private fun TaskRow.due(): TaskDue? {
    val local = due_local ?: return null
    val zoneId = due_time_zone_id ?: return TaskDue.OnDate(LocalDate.parse(local))
    return TaskDue.AtTime(LocalDateTime.parse(local), TimeZone.of(zoneId))
}

internal val TaskDue.localText: String
    get() = when (this) {
        is TaskDue.OnDate -> date.toString()
        is TaskDue.AtTime -> dateTime.toString()
    }

internal val TaskDue.timeZoneIdOrNull: String?
    get() = (this as? TaskDue.AtTime)?.timeZone?.id

/**
 * The instant a due date sorts by. A date without a time is anchored to UTC midnight, the same
 * convention all-day events use, so that ordering never depends on where the device happens to be.
 */
internal val TaskDue.sortUtc: Long
    get() = when (this) {
        is TaskDue.OnDate -> date.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
        is TaskDue.AtTime -> dateTime.toInstant(timeZone).toEpochMilliseconds()
    }

// endregion
