package com.measureme.app.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.measureme.app.ui.theme.Mm

/**
 * The viewfinder (UI Spec §5): cream inset surface, 3:4, four corner brackets,
 * and a faint dashed body silhouette in the idle state. When a photo is
 * present the surface becomes the image and the brackets stay. Instructions
 * live below it as a checklist, not inside it.
 */
@Composable
fun Viewfinder(photo: Bitmap?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .mmInset(corner = 28.dp),
    ) {
        if (photo != null) {
            Image(
                bitmap = photo.asImageBitmap(),
                contentDescription = "Your photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            DashedSilhouette(Modifier.fillMaxSize())
        }
        CornerBrackets(Modifier.fillMaxSize())
    }
}

/** Faint dashed standing figure — "the person stands here". */
@Composable
private fun DashedSilhouette(modifier: Modifier) {
    Canvas(modifier) {
        val ink = Mm.Ink.copy(alpha = 0.13f)
        val stroke = 1.5.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(7.dp.toPx(), 7.dp.toPx()), 0f)

        // The figure is drawn in a 100 x 212 unit box, scaled to 58% height.
        val h = size.height * 0.58f
        val s = h / 212f
        val cx = size.width / 2f
        val top = (size.height - h) / 2f
        fun x(u: Float) = cx + (u - 50f) * s
        fun y(v: Float) = top + v * s

        // head
        drawCircle(
            color = ink,
            radius = 13f * s,
            center = Offset(x(50f), y(18f)),
            style = Stroke(width = stroke, pathEffect = dash),
        )
        // torso and hips
        drawRoundRect(
            color = ink,
            topLeft = Offset(x(31f), y(36f)),
            size = Size(38f * s, 86f * s),
            cornerRadius = CornerRadius(17f * s),
            style = Stroke(width = stroke, pathEffect = dash),
        )
        // legs
        drawLine(ink, Offset(x(43f), y(122f)), Offset(x(41f), y(196f)), stroke, StrokeCap.Round, pathEffect = dash)
        drawLine(ink, Offset(x(57f), y(122f)), Offset(x(59f), y(196f)), stroke, StrokeCap.Round, pathEffect = dash)
        // arms
        drawLine(ink, Offset(x(32f), y(52f)), Offset(x(23f), y(96f)), stroke, StrokeCap.Round, pathEffect = dash)
        drawLine(ink, Offset(x(68f), y(52f)), Offset(x(77f), y(96f)), stroke, StrokeCap.Round, pathEffect = dash)
    }
}

/** Four accent corner brackets, drawn with rounded elbows. */
@Composable
private fun CornerBrackets(modifier: Modifier) {
    Canvas(modifier) {
        val inset = 16.dp.toPx()
        val arm = 26.dp.toPx()
        val corner = 10.dp.toPx()
        val rx = size.width - inset
        val by = size.height - inset

        val path = Path().apply {
            // top-left
            moveTo(inset, inset + arm)
            lineTo(inset, inset + corner)
            quadraticBezierTo(inset, inset, inset + corner, inset)
            lineTo(inset + arm, inset)
            // top-right
            moveTo(rx - arm, inset)
            lineTo(rx - corner, inset)
            quadraticBezierTo(rx, inset, rx, inset + corner)
            lineTo(rx, inset + arm)
            // bottom-right
            moveTo(rx, by - arm)
            lineTo(rx, by - corner)
            quadraticBezierTo(rx, by, rx - corner, by)
            lineTo(rx - arm, by)
            // bottom-left
            moveTo(inset + arm, by)
            lineTo(inset + corner, by)
            quadraticBezierTo(inset, by, inset, by - corner)
            lineTo(inset, by - arm)
        }
        drawPath(
            path,
            color = Mm.Accent,
            style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
