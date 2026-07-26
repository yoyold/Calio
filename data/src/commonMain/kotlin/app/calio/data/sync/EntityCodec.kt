package app.calio.data.sync

import app.calio.data.mapper.EventPatchCodec
import app.calio.data.mapper.RecurrenceRuleCodec
import app.calio.model.Attachment
import app.calio.model.AttachmentId
import app.calio.model.Attendee
import app.calio.model.AttendeeId
import app.calio.model.AttendeeRole
import app.calio.model.AuditFields
import app.calio.model.BusyStatus
import app.calio.model.Calendar
import app.calio.model.CalendarId
import app.calio.model.CalioColor
import app.calio.model.Category
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.time.Instant

/**
 * Turns entities into the payloads that travel between devices, and back.
 *
 * Written as transfer objects rather than by annotating the model, for the same reason every other
 * codec in this layer is: the wire format is a storage decision, and tying the entity to it would
 * make each format change a change to the domain.
 *
 * A payload carries the audit fields verbatim, including the revision and the tombstone. The
 * receiving device writes exactly what it was given — deciding whether to is the engine's job, and
 * rewriting the values here would destroy the ordering it depends on.
 */
internal object EntityCodec {

    private val json = Json {
        // A payload written by a newer version must not stop an older one from synchronising.
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(calendar: Calendar): String = json.encodeToString(calendar.toDto())

    fun decodeCalendar(text: String): Calendar = json.decodeFromString<CalendarDto>(text).toDomain()

    fun encode(category: Category): String = json.encodeToString(category.toDto())

    fun decodeCategory(text: String): Category = json.decodeFromString<CategoryDto>(text).toDomain()

    fun encode(event: Event, overrides: List<RecurrenceOverride>): String =
        json.encodeToString(event.toDto(overrides))

    fun decodeEvent(text: String): DecodedEvent =
        json.decodeFromString<EventDto>(text).let { dto -> DecodedEvent(dto.toDomain(), dto.overridesOf()) }

    fun encode(task: Task): String = json.encodeToString(task.toDto())

    fun decodeTask(text: String): Task = json.decodeFromString<TaskDto>(text).toDomain()
}

/** An event never travels without its exceptions: on their own they would mean nothing. */
internal data class DecodedEvent(val event: Event, val overrides: List<RecurrenceOverride>)

// region transfer objects

@Serializable
private data class AuditDto(
    val createdAt: Long,
    val updatedAt: Long,
    val revision: String,
    val originDevice: String,
    val deletedAt: Long? = null,
)

@Serializable
private data class CalendarDto(
    val id: String,
    val name: String,
    val color: Long,
    val isDefault: Boolean,
    val sortOrder: Int,
    val audit: AuditDto,
)

@Serializable
private data class CategoryDto(
    val id: String,
    val name: String,
    val color: Long,
    val isBuiltIn: Boolean,
    val sortOrder: Int,
    val audit: AuditDto,
)

@Serializable
private data class ReminderDto(
    val id: String,
    val triggerType: String,
    val leadMinutes: Int? = null,
    val absoluteUtc: Long? = null,
    val channel: String,
)

@Serializable
private data class AttendeeDto(
    val id: String,
    val name: String,
    val email: String? = null,
    val role: String,
    val response: String,
)

@Serializable
private data class AttachmentDto(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val checksum: String,
)

@Serializable
private data class OverrideDto(
    val originalStart: String,
    val type: String,
    val patch: String? = null,
)

@Serializable
private data class EventDto(
    val id: String,
    val calendarId: String,
    val categoryId: String? = null,
    val title: String,
    val description: String? = null,
    val notes: String? = null,
    val locationLabel: String? = null,
    val locationLat: Double? = null,
    val locationLon: Double? = null,
    val isAllDay: Boolean,
    val startLocal: String,
    val endLocal: String,
    val timeZoneId: String? = null,
    val recurrenceRule: String? = null,
    val colorOverride: Long? = null,
    val kind: String,
    val busyStatus: String,
    val status: String,
    val audit: AuditDto,
    val reminders: List<ReminderDto> = emptyList(),
    val attendees: List<AttendeeDto> = emptyList(),
    val attachments: List<AttachmentDto> = emptyList(),
    val overrides: List<OverrideDto> = emptyList(),
)

@Serializable
private data class TaskDto(
    val id: String,
    val parentTaskId: String? = null,
    val categoryId: String? = null,
    val title: String,
    val description: String? = null,
    val priority: String,
    val dueLocal: String? = null,
    val dueTimeZoneId: String? = null,
    val progressPercent: Int,
    val isCompleted: Boolean,
    val completedAt: Long? = null,
    val sortOrder: Int,
    val audit: AuditDto,
    val reminders: List<ReminderDto> = emptyList(),
)

// endregion

// region mapping

private fun AuditFields.toDto() = AuditDto(
    createdAt = createdAt.toEpochMilliseconds(),
    updatedAt = updatedAt.toEpochMilliseconds(),
    revision = revision.value,
    originDevice = originDevice.value,
    deletedAt = deletedAt?.toEpochMilliseconds(),
)

private fun AuditDto.toDomain() = AuditFields(
    createdAt = Instant.fromEpochMilliseconds(createdAt),
    updatedAt = Instant.fromEpochMilliseconds(updatedAt),
    revision = Revision(revision),
    originDevice = DeviceId(originDevice),
    deletedAt = deletedAt?.let(Instant::fromEpochMilliseconds),
)

/**
 * Visibility is left out on purpose.
 *
 * Which calendars a device shows is that device's business, decided at that device's screen size and
 * for that device's use. Carrying it along would let switching a calendar off on the desktop switch
 * it off on the phone.
 */
private fun Calendar.toDto() = CalendarDto(
    id = id.value,
    name = name,
    color = color.argb,
    isDefault = isDefault,
    sortOrder = sortOrder,
    audit = audit.toDto(),
)

private fun CalendarDto.toDomain() = Calendar(
    id = CalendarId(id),
    name = name,
    color = CalioColor(color),
    audit = audit.toDomain(),
    isDefault = isDefault,
    sortOrder = sortOrder,
)

private fun Category.toDto() = CategoryDto(
    id = id.value,
    name = name,
    color = color.argb,
    isBuiltIn = isBuiltIn,
    sortOrder = sortOrder,
    audit = audit.toDto(),
)

private fun CategoryDto.toDomain() = Category(
    id = CategoryId(id),
    name = name,
    color = CalioColor(color),
    audit = audit.toDomain(),
    isBuiltIn = isBuiltIn,
    sortOrder = sortOrder,
)

private fun Reminder.toDto() = ReminderDto(
    id = id.value,
    triggerType = when (trigger) {
        is ReminderTrigger.BeforeStart -> "BEFORE_START"
        is ReminderTrigger.BeforeEnd -> "BEFORE_END"
        is ReminderTrigger.Absolute -> "ABSOLUTE"
    },
    leadMinutes = when (val current = trigger) {
        is ReminderTrigger.BeforeStart -> current.leadMinutes
        is ReminderTrigger.BeforeEnd -> current.leadMinutes
        is ReminderTrigger.Absolute -> null
    },
    absoluteUtc = (trigger as? ReminderTrigger.Absolute)?.at?.toEpochMilliseconds(),
    channel = channel.name,
)

private fun ReminderDto.toDomain() = Reminder(
    id = ReminderId(id),
    trigger = when (triggerType) {
        "BEFORE_START" -> ReminderTrigger.BeforeStart(requireNotNull(leadMinutes))
        "BEFORE_END" -> ReminderTrigger.BeforeEnd(requireNotNull(leadMinutes))
        "ABSOLUTE" -> ReminderTrigger.Absolute(
            Instant.fromEpochMilliseconds(requireNotNull(absoluteUtc)),
        )

        else -> error("unknown reminder trigger: $triggerType")
    },
    channel = ReminderChannel.valueOf(channel),
)

/**
 * Attachments travel as metadata only.
 *
 * The bytes stay where they were added and are fetched by checksum when they are wanted. Putting a
 * file into an envelope would make one holiday photo hold up every appointment behind it.
 */
private fun Attachment.toDto() = AttachmentDto(
    id = id.value,
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    checksum = checksum,
)

private fun AttachmentDto.toDomain() = Attachment(
    id = AttachmentId(id),
    fileName = fileName,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    checksum = checksum,
)

private fun Event.toDto(overrides: List<RecurrenceOverride>) = EventDto(
    id = id.value,
    calendarId = calendarId.value,
    categoryId = categoryId?.value,
    title = title,
    description = description,
    notes = notes,
    locationLabel = location?.label,
    locationLat = location?.latitude,
    locationLon = location?.longitude,
    isAllDay = timeRange.isAllDay,
    startLocal = when (val range = timeRange) {
        is EventTimeRange.Zoned -> range.start.toString()
        is EventTimeRange.AllDay -> range.startDate.toString()
    },
    endLocal = when (val range = timeRange) {
        is EventTimeRange.Zoned -> range.endExclusive.toString()
        is EventTimeRange.AllDay -> range.endDateExclusive.toString()
    },
    timeZoneId = (timeRange as? EventTimeRange.Zoned)?.timeZone?.id,
    recurrenceRule = recurrence?.let(RecurrenceRuleCodec::encode),
    colorOverride = colorOverride?.argb,
    kind = kind.name,
    busyStatus = busyStatus.name,
    status = status.name,
    audit = audit.toDto(),
    reminders = reminders.map { it.toDto() },
    attendees = attendees.map {
        AttendeeDto(it.id.value, it.name, it.email, it.role.name, it.response.name)
    },
    attachments = attachments.map { it.toDto() },
    overrides = overrides.map {
        OverrideDto(
            originalStart = it.originalStart.toString(),
            type = it.type.name,
            patch = it.patch?.let(EventPatchCodec::encode),
        )
    },
)

private fun EventDto.toDomain() = Event(
    id = EventId(id),
    calendarId = CalendarId(calendarId),
    title = title,
    timeRange = if (isAllDay) {
        EventTimeRange.AllDay(LocalDate.parse(startLocal), LocalDate.parse(endLocal))
    } else {
        EventTimeRange.Zoned(
            start = LocalDateTime.parse(startLocal),
            endExclusive = LocalDateTime.parse(endLocal),
            timeZone = TimeZone.of(requireNotNull(timeZoneId) { "a zoned event needs a time zone" }),
        )
    },
    audit = audit.toDomain(),
    categoryId = categoryId?.let(::CategoryId),
    description = description,
    notes = notes,
    location = locationLabel?.let { EventLocation(it, locationLat, locationLon) },
    recurrence = recurrenceRule?.let(RecurrenceRuleCodec::decode),
    reminders = reminders.map { it.toDomain() },
    attendees = attendees.map {
        Attendee(
            id = AttendeeId(it.id),
            name = it.name,
            email = it.email,
            role = AttendeeRole.valueOf(it.role),
            response = ResponseStatus.valueOf(it.response),
        )
    },
    attachments = attachments.map { it.toDomain() },
    colorOverride = colorOverride?.let(::CalioColor),
    kind = EventKind.valueOf(kind),
    busyStatus = BusyStatus.valueOf(busyStatus),
    status = EventStatus.valueOf(status),
)

private fun EventDto.overridesOf(): List<RecurrenceOverride> = overrides.map {
    RecurrenceOverride(
        eventId = EventId(id),
        originalStart = LocalDateTime.parse(it.originalStart),
        type = OverrideType.valueOf(it.type),
        patch = it.patch?.let(EventPatchCodec::decode),
    )
}

private fun Task.toDto() = TaskDto(
    id = id.value,
    parentTaskId = parentTaskId?.value,
    categoryId = categoryId?.value,
    title = title,
    description = description,
    priority = priority.name,
    dueLocal = when (val current = due) {
        null -> null
        is TaskDue.OnDate -> current.date.toString()
        is TaskDue.AtTime -> current.dateTime.toString()
    },
    dueTimeZoneId = (due as? TaskDue.AtTime)?.timeZone?.id,
    progressPercent = progressPercent,
    isCompleted = isCompleted,
    completedAt = completedAt?.toEpochMilliseconds(),
    sortOrder = sortOrder,
    audit = audit.toDto(),
    reminders = reminders.map { it.toDto() },
)

private fun TaskDto.toDomain() = Task(
    id = TaskId(id),
    title = title,
    audit = audit.toDomain(),
    parentTaskId = parentTaskId?.let(::TaskId),
    description = description,
    priority = Priority.valueOf(priority),
    due = dueLocal?.let { local ->
        val zoneId = dueTimeZoneId
        if (zoneId == null) {
            TaskDue.OnDate(LocalDate.parse(local))
        } else {
            TaskDue.AtTime(LocalDateTime.parse(local), TimeZone.of(zoneId))
        }
    },
    categoryId = categoryId?.let(::CategoryId),
    progressPercent = progressPercent,
    isCompleted = isCompleted,
    completedAt = completedAt?.let(Instant::fromEpochMilliseconds),
    reminders = reminders.map { it.toDomain() },
    sortOrder = sortOrder,
)

// endregion
