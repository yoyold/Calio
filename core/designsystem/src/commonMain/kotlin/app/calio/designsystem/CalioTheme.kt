package app.calio.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The surface container steps are filled in rather than left at their defaults.
 *
 * They are what gives a flat interface depth without shadows: a card is not raised, it is a shade
 * apart from what it sits on. With the defaults the steps are too close together to read, and every
 * screen then reaches for an outline instead.
 */
private val LightScheme = lightColorScheme(
    primary = Color(0xFF1B6EF3),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBE7FF),
    onPrimaryContainer = Color(0xFF00174B),
    secondary = Color(0xFF515B6E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDAE2F4),
    onSecondaryContainer = Color(0xFF0E1A2B),
    tertiary = Color(0xFF6C5A9E),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFFBFBFD),
    onBackground = Color(0xFF15171A),
    surface = Color(0xFFFBFBFD),
    onSurface = Color(0xFF15171A),
    surfaceVariant = Color(0xFFE2E4EC),
    onSurfaceVariant = Color(0xFF5A5E68),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F6FA),
    surfaceContainer = Color(0xFFEFF1F6),
    surfaceContainerHigh = Color(0xFFE9EBF2),
    surfaceContainerHighest = Color(0xFFE3E5ED),
    outline = Color(0xFF787C86),
    outlineVariant = Color(0xFFD5D8E0),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFAD9D6),
    onErrorContainer = Color(0xFF410E0B),
    inverseSurface = Color(0xFF2A2D32),
    inverseOnSurface = Color(0xFFF1F2F6),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF9FC0FF),
    onPrimary = Color(0xFF00295E),
    primaryContainer = Color(0xFF0F4192),
    onPrimaryContainer = Color(0xFFDBE7FF),
    secondary = Color(0xFFB9C4D8),
    onSecondary = Color(0xFF232E3F),
    secondaryContainer = Color(0xFF394456),
    onSecondaryContainer = Color(0xFFDAE2F4),
    tertiary = Color(0xFFCFBDFF),
    onTertiary = Color(0xFF362A63),
    background = Color(0xFF111214),
    onBackground = Color(0xFFE4E5E9),
    surface = Color(0xFF111214),
    onSurface = Color(0xFFE4E5E9),
    surfaceVariant = Color(0xFF43464D),
    onSurfaceVariant = Color(0xFFA9ADB6),
    surfaceContainerLowest = Color(0xFF0C0D0F),
    surfaceContainerLow = Color(0xFF17181B),
    surfaceContainer = Color(0xFF1B1D20),
    surfaceContainerHigh = Color(0xFF232529),
    surfaceContainerHighest = Color(0xFF2C2E33),
    outline = Color(0xFF8B8F98),
    outlineVariant = Color(0xFF33363B),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFFAD9D6),
    inverseSurface = Color(0xFFE4E5E9),
    inverseOnSurface = Color(0xFF2A2D32),
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
