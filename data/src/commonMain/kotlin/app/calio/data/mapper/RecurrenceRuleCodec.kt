package app.calio.data.mapper

import app.calio.model.Frequency
import app.calio.model.RecurrenceEnd
import app.calio.model.RecurrenceRule
import app.calio.model.WeekDayOccurrence
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * Reads and writes recurrence rules in the RFC 5545 `RRULE` format.
 *
 * A dedicated format would have been simpler, but this one is what ICS files carry. Storing the
 * standard text means import and export are a copy rather than a translation, and nothing is lost in
 * a round trip through another calendar application.
 */
internal object RecurrenceRuleCodec {

    fun encode(rule: RecurrenceRule): String = buildList {
        add("FREQ=${rule.frequency.name}")
        if (rule.interval != 1) add("INTERVAL=${rule.interval}")
        if (rule.byWeekDays.isNotEmpty()) {
            add("BYDAY=" + rule.byWeekDays.sortedBy { it.dayOfWeek.ordinal }.joinToString(",", transform = ::encodeWeekDay))
        }
        if (rule.byMonthDays.isNotEmpty()) add("BYMONTHDAY=" + rule.byMonthDays.sorted().joinToString(","))
        if (rule.byMonths.isNotEmpty()) add("BYMONTH=" + rule.byMonths.sorted().joinToString(","))
        if (rule.weekStart != DayOfWeek.MONDAY) add("WKST=" + WEEK_DAY_CODES[rule.weekStart.ordinal])
        when (val end = rule.end) {
            is RecurrenceEnd.Never -> Unit
            is RecurrenceEnd.OnDate -> add("UNTIL=" + encodeDate(end.date))
            is RecurrenceEnd.AfterCount -> add("COUNT=${end.count}")
        }
    }.joinToString(";")

    fun decode(text: String): RecurrenceRule {
        val parts = text.split(';')
            .filter { it.isNotBlank() }
            .associate { part ->
                val separator = part.indexOf('=')
                require(separator > 0) { "malformed recurrence rule part: $part" }
                part.substring(0, separator).uppercase() to part.substring(separator + 1)
            }

        val frequency = parts["FREQ"]?.let { Frequency.valueOf(it.uppercase()) }
            ?: error("recurrence rule without a frequency: $text")

        return RecurrenceRule(
            frequency = frequency,
            interval = parts["INTERVAL"]?.toInt() ?: 1,
            byWeekDays = parts["BYDAY"].orEmpty().splitValues().map(::decodeWeekDay).toSet(),
            byMonthDays = parts["BYMONTHDAY"].orEmpty().splitValues().map(String::toInt).toSet(),
            byMonths = parts["BYMONTH"].orEmpty().splitValues().map(String::toInt).toSet(),
            weekStart = parts["WKST"]?.let { decodeWeekDayCode(it) } ?: DayOfWeek.MONDAY,
            end = when {
                parts.containsKey("UNTIL") -> RecurrenceEnd.OnDate(decodeDate(parts.getValue("UNTIL")))
                parts.containsKey("COUNT") -> RecurrenceEnd.AfterCount(parts.getValue("COUNT").toInt())
                else -> RecurrenceEnd.Never
            },
        )
    }

    private fun String.splitValues(): List<String> =
        split(',').map { it.trim() }.filter { it.isNotEmpty() }

    private fun encodeWeekDay(occurrence: WeekDayOccurrence): String {
        val code = WEEK_DAY_CODES[occurrence.dayOfWeek.ordinal]
        return occurrence.position?.let { "$it$code" } ?: code
    }

    private fun decodeWeekDay(value: String): WeekDayOccurrence {
        val code = value.takeLast(2).uppercase()
        val position = value.dropLast(2).takeIf { it.isNotEmpty() }?.toInt()
        return WeekDayOccurrence(decodeWeekDayCode(code), position)
    }

    private fun decodeWeekDayCode(code: String): DayOfWeek {
        val index = WEEK_DAY_CODES.indexOf(code.uppercase())
        require(index >= 0) { "unknown weekday code: $code" }
        return WEEK_DAYS[index]
    }

    // RFC 5545 writes a date as an unseparated year, month and day.
    private fun encodeDate(date: LocalDate): String =
        date.year.toString().padStart(4, '0') +
            (date.month.ordinal + 1).toString().padStart(2, '0') +
            date.day.toString().padStart(2, '0')

    private fun decodeDate(value: String): LocalDate {
        val digits = value.takeWhile { it.isDigit() }
        require(digits.length >= 8) { "malformed recurrence end date: $value" }
        return LocalDate(
            digits.substring(0, 4).toInt(),
            digits.substring(4, 6).toInt(),
            digits.substring(6, 8).toInt(),
        )
    }

    /** Both lists are indexed by `DayOfWeek.ordinal`, which starts at Monday. */
    private val WEEK_DAY_CODES = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

    private val WEEK_DAYS = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    )
}
