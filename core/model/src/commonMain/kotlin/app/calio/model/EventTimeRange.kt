package app.calio.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.toInstant
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * When an event happens.
 *
 * The wall-clock value plus its time zone is the truth; [startUtc] and [endUtcExclusive] are derived
 * on demand and exist so overlap and range checks are plain instant comparisons. Storing only UTC
 * would move a recurring 09:00 meeting by an hour when daylight saving changes; storing only local
 * time would make range queries impossible. Both are therefore kept, and the conversion lives here
 * so it cannot drift between call sites.
 *
 * The end is always exclusive.
 */
sealed interface EventTimeRange {

    val startUtc: Instant
    val endUtcExclusive: Instant
    val isAllDay: Boolean

    /** Real elapsed time, which across a daylight-saving change differs from the wall-clock span. */
    val duration: Duration get() = endUtcExclusive - startUtc

    /** Half-open overlap: ranges that merely touch at a boundary do not overlap. */
    fun overlaps(other: EventTimeRange): Boolean =
        startUtc < other.endUtcExclusive && other.startUtc < endUtcExclusive

    /** An event bound to a specific time zone, for example "14:00 in Europe/Berlin". */
    data class Zoned(
        val start: LocalDateTime,
        val endExclusive: LocalDateTime,
        val timeZone: TimeZone,
    ) : EventTimeRange {

        init {
            require(endExclusive > start) { "event end must be after its start" }
        }

        override val isAllDay: Boolean get() = false
        override val startUtc: Instant get() = start.toInstant(timeZone)
        override val endUtcExclusive: Instant get() = endExclusive.toInstant(timeZone)
    }

    /**
     * A date-based event such as "3 August", which means the same calendar day regardless of where
     * the user is. It is anchored to UTC midnight purely so it can participate in the same index and
     * overlap checks; that anchor is never converted back through a time zone for display.
     */
    data class AllDay(
        val startDate: LocalDate,
        val endDateExclusive: LocalDate,
    ) : EventTimeRange {

        init {
            require(endDateExclusive > startDate) { "all-day event must span at least one day" }
        }

        override val isAllDay: Boolean get() = true
        override val startUtc: Instant get() = startDate.atStartOfDayIn(TimeZone.UTC)
        override val endUtcExclusive: Instant get() = endDateExclusive.atStartOfDayIn(TimeZone.UTC)

        val dayCount: Int get() = startDate.daysUntil(endDateExclusive)

        companion object {
            /** Convenience for the common single-day case, which callers otherwise get wrong. */
            fun singleDay(date: LocalDate): AllDay = AllDay(date, date.plusDays())
        }
    }
}

private fun LocalDate.plusDays(days: Int = 1): LocalDate =
    LocalDate.fromEpochDays(toEpochDays() + days)
