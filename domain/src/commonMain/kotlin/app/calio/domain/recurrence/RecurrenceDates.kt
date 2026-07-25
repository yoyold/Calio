package app.calio.domain.recurrence

import app.calio.datetime.plusDays
import app.calio.datetime.startOfWeek
import app.calio.model.Frequency
import app.calio.model.RecurrenceRule
import app.calio.model.WeekDayOccurrence
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

/**
 * Turns a recurrence rule into the sequence of dates it produces, starting at [seed].
 *
 * The sequence is lazy and potentially infinite; the caller decides where to stop. It is kept
 * separate from the occurrence expander so that the date mathematics — the part with leap years,
 * short months and week starts in it — can be tested on its own, without events, zones or overrides.
 *
 * A date the rule cannot produce is skipped rather than moved. A monthly series on the 31st simply
 * does not occur in February; clamping it to the 28th would silently invent an appointment on a day
 * the user never chose.
 */
internal fun recurrenceDates(rule: RecurrenceRule, seed: LocalDate): Sequence<LocalDate> =
    when (rule.frequency) {
        Frequency.DAILY -> dailyDates(rule, seed)
        Frequency.WEEKLY -> weeklyDates(rule, seed)
        Frequency.MONTHLY -> monthlyDates(rule, seed)
        Frequency.YEARLY -> yearlyDates(rule, seed)
    }.filter { rule.byMonths.isEmpty() || it.monthOfYear in rule.byMonths }

private fun dailyDates(rule: RecurrenceRule, seed: LocalDate): Sequence<LocalDate> =
    generateSequence(seed) { it.plusDays(rule.interval) }
        .filter { rule.matchesWeekDayFilter(it) }

private fun weeklyDates(rule: RecurrenceRule, seed: LocalDate): Sequence<LocalDate> {
    val weekDays = rule.byWeekDays
        .map { it.dayOfWeek }
        .ifEmpty { listOf(seed.dayOfWeek) }
        .distinct()
        .sortedBy { daysFromWeekStart(it, rule.weekStart) }

    val firstWeek = startOfWeek(seed, rule.weekStart)

    return generateSequence(firstWeek) { it.plusDays(rule.interval * DAYS_PER_WEEK) }
        .flatMap { weekStart -> weekDays.map { weekStart.plusDays(daysFromWeekStart(it, rule.weekStart)) } }
        .dropWhile { it < seed }
}

private fun monthlyDates(rule: RecurrenceRule, seed: LocalDate): Sequence<LocalDate> {
    val firstOfMonth = LocalDate(seed.year, seed.month, 1)

    return generateSequence(firstOfMonth) { it.plus(rule.interval, DateTimeUnit.MONTH) }
        .flatMap { month -> datesWithinMonth(rule, month, fallbackDayOfMonth = seed.day) }
        .dropWhile { it < seed }
}

private fun yearlyDates(rule: RecurrenceRule, seed: LocalDate): Sequence<LocalDate> {
    val months = rule.byMonths.sorted().ifEmpty { listOf(seed.monthOfYear) }

    return generateSequence(seed.year) { it + rule.interval }
        .flatMap { year ->
            months.asSequence().flatMap { month ->
                datesWithinMonth(
                    rule = rule,
                    firstOfMonth = LocalDate(year, month, 1),
                    fallbackDayOfMonth = seed.day,
                )
            }
        }
        .dropWhile { it < seed }
}

/** Every date the rule selects inside one month, in ascending order. */
private fun datesWithinMonth(
    rule: RecurrenceRule,
    firstOfMonth: LocalDate,
    fallbackDayOfMonth: Int,
): Sequence<LocalDate> {
    val length = firstOfMonth.lengthOfMonth()

    val dates = when {
        rule.byWeekDays.isNotEmpty() -> rule.byWeekDays.flatMap { weekDaysInMonth(it, firstOfMonth) }

        rule.byMonthDays.isNotEmpty() -> rule.byMonthDays.mapNotNull { day ->
            val resolved = if (day > 0) day else length + day + 1
            if (resolved in 1..length) firstOfMonth.plusDays(resolved - 1) else null
        }

        // No further qualifier: the series keeps the day of month it started on, and skips the
        // months that are too short to contain it.
        fallbackDayOfMonth <= length -> listOf(firstOfMonth.plusDays(fallbackDayOfMonth - 1))

        else -> emptyList()
    }

    return dates.distinct().sorted().asSequence()
}

/** The dates a single [WeekDayOccurrence] selects within one month. */
private fun weekDaysInMonth(occurrence: WeekDayOccurrence, firstOfMonth: LocalDate): List<LocalDate> {
    val length = firstOfMonth.lengthOfMonth()
    val offsetToFirst = daysFromWeekStart(occurrence.dayOfWeek, firstOfMonth.dayOfWeek)
    val allInMonth = generateSequence(firstOfMonth.plusDays(offsetToFirst)) { it.plusDays(DAYS_PER_WEEK) }
        .takeWhile { it.day <= length && it.month == firstOfMonth.month }
        .toList()

    val position = occurrence.position ?: return allInMonth

    val index = if (position > 0) position - 1 else allInMonth.size + position
    return listOfNotNull(allInMonth.getOrNull(index))
}

private fun RecurrenceRule.matchesWeekDayFilter(date: LocalDate): Boolean =
    byWeekDays.isEmpty() || byWeekDays.any { it.dayOfWeek == date.dayOfWeek }

/** Distance in days from [weekStart] to [dayOfWeek], always within 0..6. */
private fun daysFromWeekStart(dayOfWeek: DayOfWeek, weekStart: DayOfWeek): Int =
    (dayOfWeek.ordinal - weekStart.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK

/** Month of the year as 1..12. Declared here so the code does not depend on a platform enum value. */
private val LocalDate.monthOfYear: Int get() = month.ordinal + 1

internal fun LocalDate.lengthOfMonth(): Int {
    val firstOfMonth = LocalDate(year, month, 1)
    return firstOfMonth.daysUntil(firstOfMonth.plus(1, DateTimeUnit.MONTH))
}

private const val DAYS_PER_WEEK = 7
