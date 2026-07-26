package app.calio.shared.ui

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

private val MONTH_NAMES = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

internal fun LocalDate.weekdayName(): String = WEEKDAY_NAMES[dayOfWeek.ordinal]

internal fun LocalDate.monthName(): String = MONTH_NAMES[month.ordinal]

/** For example `26 July 2026`. */
internal fun LocalDate.longLabel(): String = "$day ${monthName()} $year"

/** For example `26 July`, used where the year is already obvious from the context. */
internal fun LocalDate.shortLabel(): String = "$day ${monthName()}"

/** Always two digits per part, so a column of times lines up. */
internal fun LocalTime.clockLabel(): String =
    "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"
