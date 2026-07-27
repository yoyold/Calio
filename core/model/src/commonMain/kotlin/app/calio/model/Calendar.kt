package app.calio.model

/**
 * A container of events that can be shown or hidden as a whole, for example Private, Work or Family.
 */
data class Calendar(
    val id: CalendarId,
    val name: String,
    val color: CalioColor,
    val audit: AuditFields,
    val isVisible: Boolean = true,
    val isDefault: Boolean = false,
    val sortOrder: Int = 0,
    val origin: CalendarOrigin = CalendarOrigin.Local,
) {
    init {
        require(name.isNotBlank()) { "calendar name must not be blank" }
    }

    val external: CalendarOrigin.External? get() = origin as? CalendarOrigin.External

    /** Whether Calio may change what is in this calendar at all. */
    val isWritable: Boolean get() = external?.isReadOnly != true

    /**
     * A mirrored calendar is not carried between Calio's own devices.
     *
     * Each device mirrors it from the provider directly. Doing both would make the same change
     * arrive twice by two routes, and the two routes disagree about what a revision means.
     */
    val takesPartInDeviceSync: Boolean get() = origin is CalendarOrigin.Local
}

/**
 * A colour label that works across calendars, such as Work, Private, Family, Study or Leisure.
 *
 * Built-in categories may be renamed and recoloured but not deleted, so that events referencing them
 * can never end up pointing at nothing.
 */
data class Category(
    val id: CategoryId,
    val name: String,
    val color: CalioColor,
    val audit: AuditFields,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
) {
    init {
        require(name.isNotBlank()) { "category name must not be blank" }
    }
}

/**
 * Resolves the colour an event is drawn in.
 *
 * The order is deliberate and applied in exactly one place: an explicit per-event colour beats the
 * category, which beats the calendar. Anything else would make the same event appear in different
 * colours in different views.
 */
fun resolveEventColor(
    colorOverride: CalioColor?,
    category: Category?,
    calendar: Calendar,
): CalioColor = colorOverride ?: category?.color ?: calendar.color
