package app.calio.data.repository

import app.calio.data.mapper.absoluteUtcOrNull
import app.calio.data.mapper.endLocalText
import app.calio.data.mapper.leadMinutesOrNull
import app.calio.data.mapper.localText
import app.calio.data.mapper.recurrenceRuleText
import app.calio.data.mapper.sortUtc
import app.calio.data.mapper.startLocalText
import app.calio.data.mapper.timeZoneIdOrNull
import app.calio.data.mapper.typeName
import app.calio.database.CalioDatabase
import app.calio.domain.recurrence.seriesEndUtc
import app.calio.model.Calendar
import app.calio.model.Category
import app.calio.model.Event
import app.calio.model.Task

/**
 * Writes an entity exactly as it is given, audit fields included.
 *
 * Two callers need this and need it to behave differently around it. A repository stamps a fresh
 * revision and appends an outbox entry; synchronisation applies a revision made elsewhere and must
 * append nothing, or two devices would echo the same change back and forth forever. What they share
 * is which columns exist and how children are replaced — so that part lives here, once.
 *
 * Every function expects to run inside a transaction opened by its caller.
 */
internal fun CalioDatabase.writeCalendar(calendar: Calendar) {
    val exists = calendarsQueries.selectById(calendar.id.value).executeAsOneOrNull() != null

    if (exists) {
        calendarsQueries.update(
            name = calendar.name,
            color = calendar.color.argb,
            isVisible = if (calendar.isVisible) 1 else 0,
            isDefault = if (calendar.isDefault) 1 else 0,
            sortOrder = calendar.sortOrder.toLong(),
            updatedAt = calendar.audit.updatedAt.toEpochMilliseconds(),
            revision = calendar.audit.revision.value,
            originDevice = calendar.audit.originDevice.value,
            id = calendar.id.value,
        )
    } else {
        calendarsQueries.insert(
            id = calendar.id.value,
            name = calendar.name,
            color = calendar.color.argb,
            is_visible = if (calendar.isVisible) 1 else 0,
            is_default = if (calendar.isDefault) 1 else 0,
            sort_order = calendar.sortOrder.toLong(),
            created_at = calendar.audit.createdAt.toEpochMilliseconds(),
            updated_at = calendar.audit.updatedAt.toEpochMilliseconds(),
            revision = calendar.audit.revision.value,
            deleted_at = calendar.audit.deletedAt?.toEpochMilliseconds(),
            origin_device = calendar.audit.originDevice.value,
        )
    }

    // The update statement has no deleted_at column, because a repository never resurrects a row
    // through it. Synchronisation does, so the tombstone state is written separately.
    calendarsQueries.setDeletedAt(
        deletedAt = calendar.audit.deletedAt?.toEpochMilliseconds(),
        id = calendar.id.value,
    )
}

internal fun CalioDatabase.writeCategory(category: Category) {
    val exists = categoriesQueries.selectById(category.id.value).executeAsOneOrNull() != null

    if (exists) {
        categoriesQueries.update(
            name = category.name,
            color = category.color.argb,
            sortOrder = category.sortOrder.toLong(),
            updatedAt = category.audit.updatedAt.toEpochMilliseconds(),
            revision = category.audit.revision.value,
            originDevice = category.audit.originDevice.value,
            id = category.id.value,
        )
    } else {
        categoriesQueries.insert(
            id = category.id.value,
            name = category.name,
            color = category.color.argb,
            is_built_in = if (category.isBuiltIn) 1 else 0,
            sort_order = category.sortOrder.toLong(),
            created_at = category.audit.createdAt.toEpochMilliseconds(),
            updated_at = category.audit.updatedAt.toEpochMilliseconds(),
            revision = category.audit.revision.value,
            deleted_at = category.audit.deletedAt?.toEpochMilliseconds(),
            origin_device = category.audit.originDevice.value,
        )
    }

    categoriesQueries.setDeletedAt(
        deletedAt = category.audit.deletedAt?.toEpochMilliseconds(),
        id = category.id.value,
    )
}

internal fun CalioDatabase.writeEvent(event: Event) {
    val range = event.timeRange
    val exists = eventsQueries.selectById(event.id.value).executeAsOneOrNull() != null

    if (exists) {
        eventsQueries.update(
            calendarId = event.calendarId.value,
            categoryId = event.categoryId?.value,
            title = event.title,
            description = event.description,
            notes = event.notes,
            locationLabel = event.location?.label,
            locationLat = event.location?.latitude,
            locationLon = event.location?.longitude,
            isAllDay = if (range.isAllDay) 1 else 0,
            startLocal = range.startLocalText,
            endLocal = range.endLocalText,
            timeZoneId = range.timeZoneIdOrNull,
            startUtc = range.startUtc.toEpochMilliseconds(),
            endUtc = range.endUtcExclusive.toEpochMilliseconds(),
            recurrenceRule = event.recurrenceRuleText(),
            recurrenceUntilUtc = seriesEndUtc(event)?.toEpochMilliseconds(),
            colorOverride = event.colorOverride?.argb,
            kind = event.kind.name,
            busyStatus = event.busyStatus.name,
            status = event.status.name,
            updatedAt = event.audit.updatedAt.toEpochMilliseconds(),
            revision = event.audit.revision.value,
            originDevice = event.audit.originDevice.value,
            id = event.id.value,
        )
    } else {
        eventsQueries.insert(
            id = event.id.value,
            calendar_id = event.calendarId.value,
            category_id = event.categoryId?.value,
            title = event.title,
            description = event.description,
            notes = event.notes,
            location_label = event.location?.label,
            location_lat = event.location?.latitude,
            location_lon = event.location?.longitude,
            is_all_day = if (range.isAllDay) 1 else 0,
            start_local = range.startLocalText,
            end_local = range.endLocalText,
            time_zone_id = range.timeZoneIdOrNull,
            start_utc = range.startUtc.toEpochMilliseconds(),
            end_utc = range.endUtcExclusive.toEpochMilliseconds(),
            recurrence_rule = event.recurrenceRuleText(),
            recurrence_until_utc = seriesEndUtc(event)?.toEpochMilliseconds(),
            color_override = event.colorOverride?.argb,
            kind = event.kind.name,
            busy_status = event.busyStatus.name,
            status = event.status.name,
            created_at = event.audit.createdAt.toEpochMilliseconds(),
            updated_at = event.audit.updatedAt.toEpochMilliseconds(),
            revision = event.audit.revision.value,
            deleted_at = event.audit.deletedAt?.toEpochMilliseconds(),
            origin_device = event.audit.originDevice.value,
        )
    }

    eventsQueries.setDeletedAt(
        deletedAt = event.audit.deletedAt?.toEpochMilliseconds(),
        id = event.id.value,
    )
    writeEventChildren(event)
}

/**
 * Children are replaced wholesale rather than diffed. They are few, they always arrive together with
 * their event, and a diff would add a class of bug for no measurable gain.
 */
internal fun CalioDatabase.writeEventChildren(event: Event) {
    remindersQueries.deleteForEvent(event.id.value)
    event.reminders.forEachIndexed { index, reminder ->
        remindersQueries.insert(
            id = reminder.id.value,
            event_id = event.id.value,
            task_id = null,
            trigger_type = reminder.trigger.typeName,
            lead_minutes = reminder.trigger.leadMinutesOrNull,
            absolute_utc = reminder.trigger.absoluteUtcOrNull,
            channel = reminder.channel.name,
            sort_order = index.toLong(),
        )
    }

    participantsQueries.deleteAttendeesForEvent(event.id.value)
    event.attendees.forEachIndexed { index, attendee ->
        participantsQueries.insertAttendee(
            id = attendee.id.value,
            event_id = event.id.value,
            name = attendee.name,
            email = attendee.email,
            role = attendee.role.name,
            response = attendee.response.name,
            sort_order = index.toLong(),
        )
    }

    participantsQueries.deleteAttachmentsForEvent(event.id.value)
    event.attachments.forEachIndexed { index, attachment ->
        participantsQueries.insertAttachment(
            id = attachment.id.value,
            event_id = event.id.value,
            file_name = attachment.fileName,
            mime_type = attachment.mimeType,
            size_bytes = attachment.sizeBytes,
            checksum = attachment.checksum,
            local_path = attachment.localPath,
            sort_order = index.toLong(),
        )
    }
}

internal fun CalioDatabase.writeTask(task: Task) {
    val exists = tasksQueries.selectById(task.id.value).executeAsOneOrNull() != null

    if (exists) {
        tasksQueries.update(
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
            updatedAt = task.audit.updatedAt.toEpochMilliseconds(),
            revision = task.audit.revision.value,
            originDevice = task.audit.originDevice.value,
            id = task.id.value,
        )
    } else {
        tasksQueries.insert(
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
            updated_at = task.audit.updatedAt.toEpochMilliseconds(),
            revision = task.audit.revision.value,
            deleted_at = task.audit.deletedAt?.toEpochMilliseconds(),
            origin_device = task.audit.originDevice.value,
        )
    }

    tasksQueries.setDeletedAt(
        deletedAt = task.audit.deletedAt?.toEpochMilliseconds(),
        id = task.id.value,
    )
    writeTaskReminders(task)
}

internal fun CalioDatabase.writeTaskReminders(task: Task) {
    remindersQueries.deleteForTask(task.id.value)
    task.reminders.forEachIndexed { index, reminder ->
        remindersQueries.insert(
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
}
