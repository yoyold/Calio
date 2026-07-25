package app.calio.model

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime

enum class Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A weekday within a recurrence, optionally qualified by position — `(TUESDAY, 2)` means the second
 * Tuesday of the period and `(FRIDAY, -1)` the last Friday.
 */
data class WeekDayOccurrence(
    val dayOfWeek: DayOfWeek,
    val position: Int? = null,
) {
    init {
        require(position == null || position in -5..5 && position != 0) {
            "position must be within -5..5 and must not be 0"
        }
    }
}

sealed interface RecurrenceEnd {
    data object Never : RecurrenceEnd

    data class OnDate(val date: LocalDate) : RecurrenceEnd

    data class AfterCount(val count: Int) : RecurrenceEnd {
        init {
            require(count >= 1) { "a recurrence must produce at least one occurrence" }
        }
    }
}

/**
 * The repetition pattern of an event, modelled closely on RFC 5545 so that import and export of ICS
 * files stay lossless.
 *
 * This is a value object without behaviour on purpose: turning a rule into concrete dates is the job
 * of the recurrence expander in the domain layer, which keeps the tricky part — leap years, month
 * ends, daylight saving — in one pure, directly testable function instead of spread over the model.
 */
data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val byWeekDays: Set<WeekDayOccurrence> = emptySet(),
    val byMonthDays: Set<Int> = emptySet(),
    val byMonths: Set<Int> = emptySet(),
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val end: RecurrenceEnd = RecurrenceEnd.Never,
) {
    init {
        require(interval >= 1) { "interval must be at least 1" }
        require(byMonthDays.all { it in -31..31 && it != 0 }) {
            "byMonthDays entries must be within -31..31 and must not be 0"
        }
        require(byMonths.all { it in 1..12 }) { "byMonths entries must be within 1..12" }
    }

    val isBounded: Boolean get() = end != RecurrenceEnd.Never
}

enum class OverrideType { CANCELLED, MODIFIED }

/**
 * A single occurrence that deviates from its series, identified by the start the rule originally
 * produced for it.
 *
 * A modified occurrence stores only the fields that differ. Keeping a patch rather than a full copy
 * means an edit to the series still reaches every field the user did not explicitly override.
 */
data class RecurrenceOverride(
    val eventId: EventId,
    val originalStart: LocalDateTime,
    val type: OverrideType,
    val patch: EventPatch? = null,
) {
    init {
        require(type == OverrideType.MODIFIED || patch == null) {
            "a cancelled occurrence must not carry a patch"
        }
    }
}

/** The subset of event fields an individual occurrence may override. */
data class EventPatch(
    val title: String? = null,
    val description: String? = null,
    val notes: String? = null,
    val location: EventLocation? = null,
    val timeRange: EventTimeRange? = null,
    val categoryId: CategoryId? = null,
    val colorOverride: CalioColor? = null,
    val busyStatus: BusyStatus? = null,
    val status: EventStatus? = null,
)
