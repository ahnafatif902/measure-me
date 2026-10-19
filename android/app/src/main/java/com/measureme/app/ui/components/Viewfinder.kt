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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.measureme.app.ui.theme.Mm

/**
 * The viewfinder (UI Spec §5): cream inset surface, 3:4, four corner brackets.
 * When a photo is present the surface becomes the image and the brackets stay.
 * The idle state is deliberately EMPTY — no dummy silhouette: instructions
 * live below it as a checklist, never as drawn figures inside the frame.
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
        }
        CornerBrackets(Modifier.fillMaxSize())
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
