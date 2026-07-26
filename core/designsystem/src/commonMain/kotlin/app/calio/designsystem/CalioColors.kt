package app.calio.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * The colour roles the calendar needs on top of the standard palette.
 *
 * They live here rather than being picked at each call site so that a focus block looks the same in
 * the day grid, in the month grid and in a search result. Everything else in the interface takes its
 * colour from the user's calendars and categories, not from this file.
 */
@Immutable
data class CalioColors(
    val focusBlock: Color,
    val bufferBlock: Color,
    val conflict: Color,
    val nonWorkingHours: Color,
    val nowIndicator: Color,
    val todayHighlight: Color,
    val dropTarget: Color,
)

internal val LightCalioColors = CalioColors(
    focusBlock = Color(0xFF6750A4),
    bufferBlock = Color(0xFF7D7A88),
    conflict = Color(0xFFB3261E),
    nonWorkingHours = Color(0x0A000000),
    nowIndicator = Color(0xFFE8452C),
    todayHighlight = Color(0xFF1B6EF3),
    dropTarget = Color(0x331B6EF3),
)

internal val DarkCalioColors = CalioColors(
    focusBlock = Color(0xFFD0BCFF),
    bufferBlock = Color(0xFFC9C5D0),
    conflict = Color(0xFFF2B8B5),
    nonWorkingHours = Color(0x14FFFFFF),
    nowIndicator = Color(0xFFFF8A73),
    todayHighlight = Color(0xFF8AB4FF),
    dropTarget = Color(0x338AB4FF),
)

/**
 * Text and icon colour that stays readable on [background].
 *
 * Event colours come from the user, so no palette can guarantee contrast in advance. Deciding it
 * from the perceived brightness of the actual colour is what keeps a pale yellow category readable
 * in light mode and a deep blue one readable in dark mode.
 *
 * The threshold sits slightly above the midpoint because dark text on a mid tone reads better than
 * light text on the same tone.
 */
fun readableContentColor(
    background: Color,
    onLight: Color = Color(0xFF1A1A1A),
    onDark: Color = Color(0xFFFFFFFF),
): Color = if (background.luminance() > READABILITY_THRESHOLD) onLight else onDark

internal const val READABILITY_THRESHOLD = 0.45f
