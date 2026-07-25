package app.calio.model

import kotlin.jvm.JvmInline

/**
 * An opaque ARGB colour value.
 *
 * The model layer stores colours as plain numbers rather than as a UI framework type, so that
 * entities stay usable in tests and in the data layer without pulling in a rendering dependency.
 */
@JvmInline
value class CalioColor(val argb: Long) {

    val alpha: Int get() = ((argb shr 24) and 0xFF).toInt()
    val red: Int get() = ((argb shr 16) and 0xFF).toInt()
    val green: Int get() = ((argb shr 8) and 0xFF).toInt()
    val blue: Int get() = (argb and 0xFF).toInt()

    companion object {
        fun of(red: Int, green: Int, blue: Int, alpha: Int = 0xFF): CalioColor {
            require(red in 0..255 && green in 0..255 && blue in 0..255 && alpha in 0..255) {
                "colour components must be within 0..255"
            }
            return CalioColor(
                (alpha.toLong() shl 24) or (red.toLong() shl 16) or (green.toLong() shl 8) or blue.toLong(),
            )
        }
    }
}
