package app.calio.datetime

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * A half-open range of calendar days: [start] is included, [endExclusive] is not.
 *
 * Every range in the project is half-open. Mixing inclusive and exclusive ends is the reliable way
 * to produce off-by-one-day bugs that only show up at month boundaries, so the convention is applied
 * without exception and encoded in the property name.
 */
data class DateRange(
    val start: LocalDate,
    val endExclusive: LocalDate,
) : Iterable<LocalDate> {

    init {
        require(endExclusive >= start) { "a date range must not end before it starts" }
    }

    val dayCount: Int get() = start.daysUntil(endExclusive)

    val isEmpty: Boolean get() = start == endExclusive

    /** The last day actually contained in the range, or null when the range is empty. */
    val lastDay: LocalDate? get() = if (isEmpty) null else endExclusive.minusDays(1)

    operator fun contains(date: LocalDate): Boolean = date >= start && date < endExclusive

    fun overlaps(other: DateRange): Boolean =
        start < other.endExclusive && other.start < endExclusive

    /**
     * The instants this range covers in the given zone.
     *
     * The conversion has to happen against a zone because a calendar day is not a fixed number of
     * hours: on a daylight-saving change it is 23 or 25 hours long.
     */
    fun toInstantRange(zone: TimeZone): InstantRange = InstantRange(
        start = start.atStartOfDayIn(zone),
        endExclusive = endExclusive.atStartOfDayIn(zone),
    )

    override fun iterator(): Iterator<LocalDate> = object : Iterator<LocalDate> {
        private var next = start
        override fun hasNext(): Boolean = next < endExclusive
        override fun next(): LocalDate {
            val current = next
            next = current.plusDays(1)
            return current
        }
    }

    companion object {
        fun singleDay(date: LocalDate): DateRange = DateRange(date, date.plusDays(1))
    }
}

fun LocalDate.plusDays(days: Int): LocalDate = plus(days, DateTimeUnit.DAY)

fun LocalDate.minusDays(days: Int): LocalDate = plus(-days, DateTimeUnit.DAY)
