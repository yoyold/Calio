package app.calio.model

import kotlin.jvm.JvmInline

/**
 * Typed identifiers.
 *
 * Wrapping the raw string costs nothing at runtime but makes it impossible to pass a [TaskId] where
 * an [EventId] is expected, which is the single most common class of mix-up in a data layer that
 * moves many opaque strings around.
 */
@JvmInline
value class CalendarId(val value: String) {
    init {
        require(value.isNotBlank()) { "CalendarId must not be blank" }
    }
}

@JvmInline
value class CategoryId(val value: String) {
    init {
        require(value.isNotBlank()) { "CategoryId must not be blank" }
    }
}

@JvmInline
value class EventId(val value: String) {
    init {
        require(value.isNotBlank()) { "EventId must not be blank" }
    }
}

@JvmInline
value class TaskId(val value: String) {
    init {
        require(value.isNotBlank()) { "TaskId must not be blank" }
    }
}

@JvmInline
value class ReminderId(val value: String) {
    init {
        require(value.isNotBlank()) { "ReminderId must not be blank" }
    }
}

@JvmInline
value class AttendeeId(val value: String) {
    init {
        require(value.isNotBlank()) { "AttendeeId must not be blank" }
    }
}

@JvmInline
value class AttachmentId(val value: String) {
    init {
        require(value.isNotBlank()) { "AttachmentId must not be blank" }
    }
}

/** Stable identifier of one installation. Used as the tiebreaker when ordering revisions. */
@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeviceId must not be blank" }
    }
}
