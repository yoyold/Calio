package app.calio.datetime

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/** The number of days from Monday, so Monday is 0 and Sunday is 6. */
private val DayOfWeek.indexFromMonday: Int get() = ordinal

/**
 * The first day of the week [date] belongs to.
 *
 * The week start is a parameter rather than a constant because it is a user setting: large parts of
 * the world start the week on Sunday, and getting this wrong shifts every week and month view by a
 * day.
 */
fun startOfWeek(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): LocalDate {
    val offset = (date.dayOfWeek.indexFromMonday - weekStart.indexFromMonday + DAYS_PER_WEEK) % DAYS_PER_WEEK
    return date.minusDays(offset)
}

/** The week [date] belongs to, as a half-open range of seven days. */
fun weekOf(date: LocalDate, weekStart: DayOfWeek = DayOfWeek.MONDAY): DateRange {
    val first = startOfWeek(date, weekStart)
    return DateRange(first, first.plusDays(DAYS_PER_WEEK))
}

fun firstDayOfMonth(date: LocalDate): LocalDate = LocalDate(date.year, date.month, 1)

fun firstDayOfNextMonth(date: LocalDate): LocalDate =
    firstDayOfMonth(date).plus(1, DateTimeUnit.MONTH)

/** The month [date] belongs to, as a half-open range of days. */
fun monthOf(date: LocalDate): DateRange = DateRange(firstDayOfMonth(date), firstDayOfNextMonth(date))

fun firstDayOfYear(date: LocalDate): LocalDate = LocalDate(date.year, 1, 1)

/** The year [date] belongs to, as a half-open range of days. */
fun yearOf(date: LocalDate): DateRange =
    DateRange(firstDayOfYear(date), LocalDate(date.year + 1, 1, 1))

/**
 * The ISO-8601 week number of [date], between 1 and 53.
 *
 * ISO weeks always start on Monday and week 1 is the week containing the first Thursday of the year.
 * The consequence is that early January can belong to the last week of the previous year and late
 * December to week 1 of the next — which is why [isoWeekYear] exists and why the week number alone
 * must never be used as a key.
 *
 * The implementation locates the Thursday of the week and reads the answer off that day, which
 * handles every boundary case without special-casing any of them.
 */
fun isoWeekNumber(date: LocalDate): Int = (thursdayOfIsoWeek(date).dayOfYear - 1) / DAYS_PER_WEEK + 1

/** The year the ISO week of [date] belongs to, which is not always `date.year`. */
fun isoWeekYear(date: LocalDate): Int = thursdayOfIsoWeek(date).year

private fun thursdayOfIsoWeek(date: LocalDate): LocalDate =
    date.plusDays(THURSDAY_INDEX - date.dayOfWeek.indexFromMonday)

private const val DAYS_PER_WEEK = 7
private const val THURSDAY_INDEX = 3
