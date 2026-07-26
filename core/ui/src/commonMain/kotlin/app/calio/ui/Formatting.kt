package app.calio.ui

import app.calio.datetime.DateRange
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * Date and time wording for the interface.
 *
 * The names are spelled out here rather than taken from the platform so that both builds read
 * identically and a screenshot from one is a screenshot from the other. Translating them is a
 * separate concern and will replace this file wholesale rather than growing it.
 */

private val WEEKDAY_NAMES = listOf(
    "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
)

private val WEEKDAY_ABBREVIATIONS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun LocalDate.weekdayName(): String = WEEKDAY_NAMES[dayOfWeek.ordinal]

fun LocalDate.weekdayAbbreviation(): String = WEEKDAY_ABBREVIATIONS[dayOfWeek.ordinal]

fun LocalDate.monthName(): String = MONTH_NAMES[month.ordinal]

/** For example `26 July 2026`. */
fun LocalDate.longLabel(): String = "$day ${monthName()} $year"

/** For example `26 July`, used where the year is obvious from the context. */
fun LocalDate.shortLabel(): String = "$day ${monthName()}"

/** Always two digits per part, so a column of times lines up. */
fun LocalTime.clockLabel(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

/**
 * A span of days as one heading, repeating only what changes.
 *
 * A week inside one month reads `3 – 9 August 2026`, one crossing a month `31 July – 6 August 2026`
 * and one crossing a year with both years spelled out. Repeating the month on both sides of every
 * dash would make the heading twice as long for no extra information.
 */
fun DateRange.headingLabel(): String {
    val last = lastDay ?: return start.longLabel()
    if (start == last) return start.longLabel()

    return when {
        start.year != last.year -> "${start.longLabel()} – ${last.longLabel()}"
        start.month != last.month -> "${start.shortLabel()} – ${last.longLabel()}"
        else -> "${start.day} – ${last.longLabel()}"
    }
}
