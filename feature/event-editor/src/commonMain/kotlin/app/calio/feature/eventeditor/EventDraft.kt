package app.calio.feature.eventeditor

import app.calio.datetime.plusDays
import app.calio.model.AuditFields
import app.calio.model.BusyStatus
import app.calio.model.CalendarId
import app.calio.model.CategoryId
import app.calio.model.Event
import app.calio.model.EventId
import app.calio.model.EventKind
import app.calio.model.EventLocation
import app.calio.model.EventStatus
import app.calio.model.EventTimeRange
import app.calio.model.Frequency
import app.calio.model.RecurrenceRule
import app.calio.model.Reminder
import app.calio.model.ReminderId
import app.calio.model.ReminderTrigger
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone

/**
 * What the editor is holding while the user types.
 *
 * A draft is not an [Event]. An event has to be valid to exist at all — a blank title is rejected by
 * its own constructor — while a draft is invalid most of the time it is being edited. Keeping the
 * two apart is what lets the entity stay strict without the editor fighting it on every keystroke.
 */
data class EventDraft(
    val eventId: EventId? = null,
    val title: String = "",
    val calendarId: CalendarId? = null,
    val categoryId: CategoryId? = null,
    val isAllDay: Boolean = false,
    val startDate: LocalDate,
    val startTime: LocalTime = LocalTime(9, 0),
    /** The last day the event covers, as the user sees it: 3 August means it ends on 3 August. */
    val endDate: LocalDate,
    val endTime: LocalTime = LocalTime(10, 0),
    val timeZone: TimeZone,
    val location: String = "",
    val description: String = "",
    val notes: String = "",
    val recurrence: RecurrencePreset = RecurrencePreset.None,
    val recurrenceInterval: Int = 1,
    val reminders: List<ReminderDraft> = emptyList(),
    val busyStatus: BusyStatus = BusyStatus.BUSY,
    val kind: EventKind = EventKind.STANDARD,
) {
    val isNew: Boolean get() = eventId == null
}

data class ReminderDraft(val id: ReminderId, val leadMinutes: Int)

enum class RecurrencePreset(val label: String) {
    None("Does not repeat"),
    Daily("Daily"),
    Weekly("Weekly"),
    Monthly("Monthly"),
    Yearly("Yearly"),
    ;

    fun toFrequency(): Frequency? = when (this) {
        None -> null
        Daily -> Frequency.DAILY
        Weekly -> Frequency.WEEKLY
        Monthly -> Frequency.MONTHLY
        Yearly -> Frequency.YEARLY
    }

    companion object {
        fun of(rule: RecurrenceRule?): RecurrencePreset = when (rule?.frequency) {
            null -> None
            Frequency.DAILY -> Daily
            Frequency.WEEKLY -> Weekly
            Frequency.MONTHLY -> Monthly
            Frequency.YEARLY -> Yearly
        }
    }
}

enum class DraftProblem(val message: String) {
    BlankTitle("Give the event a title."),
    NoCalendar("Choose a calendar."),
    EndBeforeStart("The event has to end after it starts."),
    InvalidInterval("Repeat at least every one period."),
}

/** Everything wrong with the draft right now, so the editor can say all of it at once. */
fun EventDraft.problems(): Set<DraftProblem> = buildSet {
    if (title.isBlank()) add(DraftProblem.BlankTitle)
    if (calendarId == null) add(DraftProblem.NoCalendar)
    if (recurrence != RecurrencePreset.None && recurrenceInterval < 1) {
        add(DraftProblem.InvalidInterval)
    }
    if (!endsAfterItStarts()) add(DraftProblem.EndBeforeStart)
}

val EventDraft.isValid: Boolean get() = problems().isEmpty()

private fun EventDraft.endsAfterItStarts(): Boolean = if (isAllDay) {
    endDate >= startDate
} else {
    LocalDateTime(endDate, endTime) > LocalDateTime(startDate, startTime)
}

/**
 * The stored time range for this draft.
 *
 * The editor shows the last day an all-day event covers, because that is how people describe them —
 * "the third to the fifth". Storage keeps the end exclusive so that ranges compose without
 * off-by-one days, and this is the one place the two conventions meet.
 */
fun EventDraft.toTimeRange(): EventTimeRange = if (isAllDay) {
    EventTimeRange.AllDay(startDate, endDate.plusDays(1))
} else {
    EventTimeRange.Zoned(
        start = LocalDateTime(startDate, startTime),
        endExclusive = LocalDateTime(endDate, endTime),
        timeZone = timeZone,
    )
}

fun EventDraft.toRecurrenceRule(): RecurrenceRule? = recurrence.toFrequency()?.let { frequency ->
    RecurrenceRule(frequency = frequency, interval = recurrenceInterval)
}

/**
 * Turns the draft into an event, keeping whatever the editor does not touch.
 *
 * [existing] carries the fields no field in this editor can change — attendees, attachments and the
 * creation time — so editing a title never silently drops them.
 */
fun EventDraft.toEvent(existing: Event?, id: EventId, audit: AuditFields): Event = Event(
    id = id,
    calendarId = requireNotNull(calendarId) { "a draft without a calendar cannot be saved" },
    title = title.trim(),
    timeRange = toTimeRange(),
    audit = audit,
    categoryId = categoryId,
    description = description.trim().ifBlank { null },
    notes = notes.trim().ifBlank { null },
    location = location.trim().ifBlank { null }?.let { EventLocation(it) },
    recurrence = toRecurrenceRule(),
    reminders = reminders.map { Reminder(it.id, ReminderTrigger.BeforeStart(it.leadMinutes)) },
    attendees = existing?.attendees.orEmpty(),
    attachments = existing?.attachments.orEmpty(),
    colorOverride = existing?.colorOverride,
    kind = kind,
    busyStatus = busyStatus,
    status = existing?.status ?: EventStatus.CONFIRMED,
)

/** The draft that opens when an existing event is edited. */
fun Event.toDraft(): EventDraft {
    val range = timeRange
    val start = when (range) {
        is EventTimeRange.Zoned -> range.start
        is EventTimeRange.AllDay -> LocalDateTime(range.startDate, LocalTime(0, 0))
    }
    val end = when (range) {
        is EventTimeRange.Zoned -> range.endExclusive
        // Back from the exclusive end to the last day the user actually sees.
        is EventTimeRange.AllDay -> LocalDateTime(range.endDateExclusive.plusDays(-1), LocalTime(0, 0))
    }

    return EventDraft(
        eventId = id,
        title = title,
        calendarId = calendarId,
        categoryId = categoryId,
        isAllDay = range.isAllDay,
        startDate = start.date,
        startTime = start.time,
        endDate = end.date,
        endTime = end.time,
        timeZone = (range as? EventTimeRange.Zoned)?.timeZone ?: TimeZone.UTC,
        location = location?.label.orEmpty(),
        description = description.orEmpty(),
        notes = notes.orEmpty(),
        recurrence = RecurrencePreset.of(recurrence),
        recurrenceInterval = recurrence?.interval ?: 1,
        reminders = reminders.mapNotNull { reminder ->
            (reminder.trigger as? ReminderTrigger.BeforeStart)
                ?.let { ReminderDraft(reminder.id, it.leadMinutes) }
        },
        busyStatus = busyStatus,
        kind = kind,
    )
}
