package app.calio.designsystem.icon

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The icon set, drawn as vector paths.
 *
 * A general purpose icon library would bring several thousand paths into the build for the handful
 * used here, in a visual language that does not match a deliberately quiet calendar. These are
 * defined on a 24 unit grid with a single stroke weight, so they stay sharp at any size and take
 * their colour from the theme through the usual tint.
 */
object CalioIcons {

    val Calendar: ImageVector by lazy {
        strokeIcon("Calendar") {
            roundedRect(x = 3f, y = 5f, width = 18f, height = 16f, radius = 3f)
            moveTo(8f, 2.8f)
            lineTo(8f, 7.2f)
            moveTo(16f, 2.8f)
            lineTo(16f, 7.2f)
            moveTo(3f, 10f)
            lineTo(21f, 10f)
        }
    }

    val Tasks: ImageVector by lazy {
        strokeIcon("Tasks") {
            moveTo(3.5f, 7.6f)
            lineTo(6f, 10.1f)
            lineTo(10.6f, 5.5f)
            moveTo(13.5f, 8f)
            lineTo(20.5f, 8f)
            moveTo(3.5f, 16.6f)
            lineTo(6f, 19.1f)
            lineTo(10.6f, 14.5f)
            moveTo(13.5f, 17f)
            lineTo(20.5f, 17f)
        }
    }

    val Search: ImageVector by lazy {
        strokeIcon("Search") {
            circle(centreX = 10.5f, centreY = 10.5f, radius = 6.5f)
            moveTo(15.4f, 15.4f)
            lineTo(20.5f, 20.5f)
        }
    }

    val Settings: ImageVector by lazy {
        strokeIcon("Settings") {
            moveTo(3.5f, 6.5f)
            lineTo(20.5f, 6.5f)
            moveTo(3.5f, 12f)
            lineTo(20.5f, 12f)
            moveTo(3.5f, 17.5f)
            lineTo(20.5f, 17.5f)
            circle(centreX = 9f, centreY = 6.5f, radius = 2.1f)
            circle(centreX = 15.5f, centreY = 12f, radius = 2.1f)
            circle(centreX = 11f, centreY = 17.5f, radius = 2.1f)
        }
    }

    val Plus: ImageVector by lazy {
        strokeIcon("Plus") {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(5f, 12f)
            lineTo(19f, 12f)
        }
    }

    val ChevronLeft: ImageVector by lazy {
        strokeIcon("ChevronLeft") {
            moveTo(14.5f, 5.5f)
            lineTo(8f, 12f)
            lineTo(14.5f, 18.5f)
        }
    }

    val ChevronRight: ImageVector by lazy {
        strokeIcon("ChevronRight") {
            moveTo(9.5f, 5.5f)
            lineTo(16f, 12f)
            lineTo(9.5f, 18.5f)
        }
    }

    val Clock: ImageVector by lazy {
        strokeIcon("Clock") {
            circle(centreX = 12f, centreY = 12f, radius = 8f)
            moveTo(12f, 7.2f)
            lineTo(12f, 12.4f)
            lineTo(15.6f, 14.4f)
        }
    }

    val CheckCircle: ImageVector by lazy {
        strokeIcon("CheckCircle") {
            circle(centreX = 12f, centreY = 12f, radius = 8f)
            moveTo(8.2f, 12.2f)
            lineTo(10.9f, 14.9f)
            lineTo(15.9f, 9.4f)
        }
    }

    val Layers: ImageVector by lazy {
        strokeIcon("Layers") {
            moveTo(12f, 3.4f)
            lineTo(21f, 8f)
            lineTo(12f, 12.6f)
            lineTo(3f, 8f)
            close()
            moveTo(3f, 12.6f)
            lineTo(12f, 17.2f)
            lineTo(21f, 12.6f)
            moveTo(3f, 16.6f)
            lineTo(12f, 21.2f)
            lineTo(21f, 16.6f)
        }
    }
}

private const val STROKE_WIDTH = 1.7f

private fun strokeIcon(name: String, outline: PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            // The colour is irrelevant: an Icon recolours the whole vector with its tint, which is
            // what lets one definition serve both themes.
            stroke = SolidColor(Color.Black),
            strokeLineWidth = STROKE_WIDTH,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = outline,
        )
    }.build()

private fun PathBuilder.roundedRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    radius: Float,
) {
    moveTo(x + radius, y)
    lineTo(x + width - radius, y)
    quadTo(x + width, y, x + width, y + radius)
    lineTo(x + width, y + height - radius)
    quadTo(x + width, y + height, x + width - radius, y + height)
    lineTo(x + radius, y + height)
    quadTo(x, y + height, x, y + height - radius)
    lineTo(x, y + radius)
    quadTo(x, y, x + radius, y)
    close()
}

private fun PathBuilder.circle(centreX: Float, centreY: Float, radius: Float) {
    moveTo(centreX - radius, centreY)
    arcToRelative(radius, radius, 0f, true, true, radius * 2, 0f)
    arcToRelative(radius, radius, 0f, true, true, -radius * 2, 0f)
    close()
}
