package app.calio.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * A restrained type scale.
 *
 * A calendar is read in glances, not in paragraphs, so the small sizes carry most of the work and
 * are the ones tuned here: tight line heights, slightly negative tracking on the large sizes, and no
 * weight above semi bold anywhere.
 */
@Composable
internal fun calioTypography(): Typography {
    val default = Typography()

    val trimmed = LineHeightStyle(
        alignment = LineHeightStyle.Alignment.Center,
        trim = LineHeightStyle.Trim.None,
    )

    fun TextStyle.tuned(weight: FontWeight? = null, tracking: Float? = null) = copy(
        fontWeight = weight ?: fontWeight,
        letterSpacing = tracking?.sp ?: letterSpacing,
        lineHeightStyle = trimmed,
    )

    return default.copy(
        displaySmall = default.displaySmall.tuned(FontWeight.SemiBold, -0.5f),
        headlineMedium = default.headlineMedium.tuned(FontWeight.SemiBold, -0.25f),
        headlineSmall = default.headlineSmall.tuned(FontWeight.SemiBold, -0.25f),
        titleLarge = default.titleLarge.tuned(FontWeight.SemiBold),
        titleMedium = default.titleMedium.tuned(FontWeight.Medium),
        bodyLarge = default.bodyLarge.tuned(),
        bodyMedium = default.bodyMedium.tuned(),
        // The label styles are what an event chip in a crowded month grid is drawn with.
        labelLarge = default.labelLarge.tuned(FontWeight.Medium),
        labelMedium = default.labelMedium.tuned(FontWeight.Medium),
        labelSmall = default.labelSmall.tuned(FontWeight.Medium, 0.1f),
    )
}
