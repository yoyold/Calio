package app.calio.model

import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

enum class ReminderChannel { NOTIFICATION, ALARM }

/**
 * When a reminder fires.
 *
 * Relative triggers store a non-negative lead time and say in their name what they are relative to,
 * rather than encoding "before" as a negative number — sign conventions are exactly the kind of
 * detail that gets inverted once and then silently ships.
 */
sealed interface ReminderTrigger {

    data class BeforeStart(val leadMinutes: Int) : ReminderTrigger {
        init {
            require(leadMinutes >= 0) { "lead time must not be negative" }
        }
    }

    data class BeforeEnd(val leadMinutes: Int) : ReminderTrigger {
        init {
            require(leadMinutes >= 0) { "lead time must not be negative" }
        }
    }

    data class Absolute(val at: Instant) : ReminderTrigger
}

data class Reminder(
    val id: ReminderId,
    val trigger: ReminderTrigger,
    val channel: ReminderChannel = ReminderChannel.NOTIFICATION,
)

/** Resolves the instant at which this reminder fires for the given occurrence. */
fun Reminder.fireTime(range: EventTimeRange): Instant = when (val current = trigger) {
    is ReminderTrigger.BeforeStart -> range.startUtc - current.leadMinutes.minutes
    is ReminderTrigger.BeforeEnd -> range.endUtcExclusive - current.leadMinutes.minutes
    is ReminderTrigger.Absolute -> current.at
}
