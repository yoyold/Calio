package app.calio.model

/** What a block of time is for. Focus and buffer blocks are events so they inherit storage,
 *  synchronisation, export and rendering instead of duplicating all of it. */
enum class EventKind { STANDARD, FOCUS, BUFFER }

/** Whether the event occupies the slot. Only [BUSY] takes part in conflict detection. */
enum class BusyStatus { BUSY, FREE, TENTATIVE }

enum class EventStatus { CONFIRMED, TENTATIVE, CANCELLED }

data class EventLocation(
    val label: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    init {
        require(label.isNotBlank()) { "location label must not be blank" }
        require((latitude == null) == (longitude == null)) {
            "latitude and longitude must be given together"
        }
        require(latitude == null || latitude in -90.0..90.0) { "latitude out of range" }
        require(longitude == null || longitude in -180.0..180.0) { "longitude out of range" }
    }
}

/**
 * One entry in a calendar. A recurring event is a single [Event] carrying a [recurrence]; the
 * individual occurrences are never stored, they are expanded for the visible range on read.
 */
data class Event(
    val id: EventId,
    val calendarId: CalendarId,
    val title: String,
    val timeRange: EventTimeRange,
    val audit: AuditFields,
    val categoryId: CategoryId? = null,
    val description: String? = null,
    val notes: String? = null,
    val location: EventLocation? = null,
    val recurrence: RecurrenceRule? = null,
    val reminders: List<Reminder> = emptyList(),
    val attendees: List<Attendee> = emptyList(),
    val attachments: List<Attachment> = emptyList(),
    val colorOverride: CalioColor? = null,
    val kind: EventKind = EventKind.STANDARD,
    val busyStatus: BusyStatus = BusyStatus.BUSY,
    val status: EventStatus = EventStatus.CONFIRMED,
) {
    init {
        require(title.isNotBlank()) { "event title must not be blank" }
    }

    val isRecurring: Boolean get() = recurrence != null

    /** Whether this event should be considered when detecting conflicts and free slots. */
    val occupiesTime: Boolean
        get() = busyStatus == BusyStatus.BUSY && status != EventStatus.CANCELLED && !audit.isDeleted
}
