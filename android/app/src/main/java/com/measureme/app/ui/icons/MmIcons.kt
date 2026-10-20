package com.measureme.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The five lucide icons the reference design uses (lucide-static v0.462.0,
 * ISC licence), converted 1:1 to stroked ImageVectors:
 * 24×24 viewport, stroke-width 2, round caps and joins, no fill.
 * `circle`/`polyline`/`line` elements are expressed as path data with the
 * same coordinates.
 */
object MmIcons {

    val Ruler = icon(
        "Ruler",
        "M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.41 2.41 0 0 1 0-3.4l2.6-2.6a2.41 2.41 0 0 1 3.4 0Z",
        "m14.5 12.5 2-2",
        "m11.5 9.5 2-2",
        "m8.5 6.5 2-2",
        "m17.5 15.5 2-2",
    )

    val Upload = icon(
        "Upload",
        "M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4",
        "M17 8 12 3 7 8",
        "M12 3 12 15",
    )

    val Camera = icon(
        "Camera",
        "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3l-2.5-3z",
        "M9 13a3 3 0 1 0 6 0a3 3 0 1 0-6 0",
    )

    val ImagePlus = icon(
        "ImagePlus",
        "M16 5h6",
        "M19 2v6",
        "M21 11.5V19a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h7.5",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
        "M7 9a2 2 0 1 0 4 0a2 2 0 1 0-4 0",
    )

    val AlertTriangle = icon(
        "AlertTriangle",
        "m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z",
        "M12 9v4",
        "M12 17h.01",
    )

    /** Mode A — Proportions: a standing figure. */
    val Person = icon(
        "Person",
        "M14 4.8a2 2 0 1 0-4 0 2 2 0 1 0 4 0",
        "M12 6.8v7.2",
        "M12 9.3 8.5 12.3",
        "M12 9.3l3.5 3",
        "M12 14 9.5 19.5",
        "M12 14l2.5 5.5",
    )

    /** Mode B — Horizon: a phone against a horizon line. */
    val PhoneTilt = icon(
        "PhoneTilt",
        "M9.5 2.5h5A1.5 1.5 0 0 1 16 4v16a1.5 1.5 0 0 1-1.5 1.5h-5A1.5 1.5 0 0 1 8 20V4a1.5 1.5 0 0 1 1.5-1.5z",
        "M3.5 12h17",
    )

    /** Mode C — Reference: an A4 sheet with a folded corner. */
    val Page = icon(
        "Page",
        "M14.5 2.5H7A1.5 1.5 0 0 0 5.5 4v16A1.5 1.5 0 0 0 7 21.5h10a1.5 1.5 0 0 0 1.5-1.5V7z",
        "M14.5 2.5V7h4",
    )

    /** Back arrow for navigation (lucide arrow-left). */
    val ArrowBack = icon(
        "ArrowBack",
        "M19 12H5",
        "m12 19-7-7 7-7",
    )

    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
}
