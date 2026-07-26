package app.calio.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Spacing on a four point grid.
 *
 * Everything in the interface is a multiple of four. The value of a grid is not that four is a
 * special number, but that it removes the choice: a padding is never "about sixteen", so two screens
 * built months apart still line up.
 */
@Immutable
data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val huge: Dp = 32.dp,
)

@Immutable
data class Radius(
    val small: Dp = 6.dp,
    val medium: Dp = 10.dp,
    val large: Dp = 16.dp,
)

/**
 * Motion durations in milliseconds.
 *
 * Short and functional. Anything longer than a third of a second stops reading as a response to the
 * user and starts reading as the application taking its time.
 */
@Immutable
data class Motion(
    val fast: Int = 150,
    val standard: Int = 220,
    val emphasised: Int = 320,
)
