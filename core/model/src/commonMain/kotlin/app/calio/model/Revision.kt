package app.calio.model

import kotlin.jvm.JvmInline

/**
 * A hybrid logical clock reading, serialised as `<physicalMillis>-<counter>-<deviceId>`.
 *
 * Wall clocks on two devices disagree, so an ordinary timestamp cannot decide which of two competing
 * edits is newer: a device with a fast clock would always win. The hybrid logical clock keeps the
 * physical component for human-readable ordering, adds a counter for edits within the same
 * millisecond, and falls back to the device id so that the ordering is *total*. Every device
 * comparing the same two revisions therefore reaches the same verdict, which is what makes
 * synchronisation converge deterministically.
 *
 * The device id may itself contain dashes, so only the first two segments are split off.
 */
@JvmInline
value class Revision(val value: String) : Comparable<Revision> {

    val physicalMillis: Long
        get() = value.substringBefore(SEPARATOR).toLong()

    val counter: Int
        get() = value.substringAfter(SEPARATOR).substringBefore(SEPARATOR).toInt()

    val deviceId: DeviceId
        get() = DeviceId(value.substringAfter(SEPARATOR).substringAfter(SEPARATOR))

    override fun compareTo(other: Revision): Int {
        physicalMillis.compareTo(other.physicalMillis).let { if (it != 0) return it }
        counter.compareTo(other.counter).let { if (it != 0) return it }
        return deviceId.value.compareTo(other.deviceId.value)
    }

    companion object {
        private const val SEPARATOR = '-'

        fun of(physicalMillis: Long, counter: Int, deviceId: DeviceId): Revision {
            require(physicalMillis >= 0) { "physicalMillis must not be negative" }
            require(counter >= 0) { "counter must not be negative" }
            return Revision("$physicalMillis$SEPARATOR$counter$SEPARATOR${deviceId.value}")
        }
    }
}
