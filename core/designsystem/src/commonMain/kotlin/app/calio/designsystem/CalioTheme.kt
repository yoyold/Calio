package app.calio.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF565E71),
    background = Color(0xFFFDFCFF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFFDFCFF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    error = Color(0xFFB3261E),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF002B77),
    primaryContainer = Color(0xFF0B47A6),
    onPrimaryContainer = Color(0xFFD9E6FF),
    secondary = Color(0xFFBEC6DC),
    background = Color(0xFF1A1C1E),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1A1C1E),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF44474F),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474F),
    error = Color(0xFFF2B8B5),
)

private val LocalSpacing = staticCompositionLocalOf { Spacing() }
private val LocalRadius = staticCompositionLocalOf { Radius() }
private val LocalMotion = staticCompositionLocalOf { Motion() }
private val LocalCalioColors = staticCompositionLocalOf { LightCalioColors }

/**
 * The single entry point for styling.
 *
 * Both schemes are maintained as complete palettes rather than dark being derived from light by
 * inverting it. Inverted palettes are where dark mode goes wrong: the accents end up too loud and
 * the surfaces too flat, and it never quite gets fixed afterwards.
 */
@Composable
fun CalioTheme(
    useDarkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalCalioColors provides if (useDarkTheme) DarkCalioColors else LightCalioColors,
    ) {
        MaterialTheme(
            colorScheme = if (useDarkTheme) DarkScheme else LightScheme,
            typography = calioTypography(),
            content = content,
        )
    }
}

/** Access to the tokens, deliberately shaped like `MaterialTheme` so both read the same way. */
object CalioTheme {

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val radius: Radius
        @Composable @ReadOnlyComposable get() = LocalRadius.current

    val motion: Motion
        @Composable @ReadOnlyComposable get() = LocalMotion.current

    val colors: CalioColors
        @Composable @ReadOnlyComposable get() = LocalCalioColors.current
}
