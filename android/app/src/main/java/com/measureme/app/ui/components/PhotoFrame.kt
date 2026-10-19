package com.measureme.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.measureme.app.pipeline.Px
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmType
import com.measureme.app.ui.theme.SansFont
import kotlin.math.hypot
import kotlin.math.min

/**
 * PhotoFrame — the dark 3:4 frame, the reference's centrepiece.
 *
 *  - empty state: ruler glyph + "Head to feet, in frame" (exactly the design)
 *  - photo drawn object-contain inside the frame, letterboxed on the ink bg
 *  - measurement overlay drawn in FRAME coordinates via containMap(), so the
 *    rod and markers land exactly on the photo at any size (same maths as
 *    the reference's SVG viewBox)
 *  - manual mode: tap to place the crown / the feet, drag any marker
 *    (28dp hit radius — straight from the reference) to correct it
 */

/** Which draggable marker a drag gesture grabbed. */
enum class ManualMarker { CROWN, CHIN, FEET }

/** Marker points in image pixels — what the overlay draws and hit-tests. */
data class FrameMarkers(
    val crown: Px? = null,
    val chin: Px? = null,
    val feet: Px? = null,
)

/** object-contain mapping of the photo inside the frame (web: containMap()). */
fun containMap(frameW: Float, frameH: Float, imgW: Float, imgH: Float): Rect {
    val scale = min(frameW / imgW, frameH / imgH)
    val w = imgW * scale
    val h = imgH * scale
    return Rect(
        left = (frameW - w) / 2f,
        top = (frameH - h) / 2f,
        right = (frameW + w) / 2f,
        bottom = (frameH + h) / 2f,
    )
}

private fun Rect.mapPoint(p: Px, imgW: Float, imgH: Float): Offset =
    Offset(left + p.x / imgW * width, top + p.y / imgH * height)

private fun Offset.toImage(rect: Rect, imgW: Float, imgH: Float): Px = Px(
    (((x - rect.left) / rect.width).coerceIn(0f, 1f)) * imgW,
    (((y - rect.top) / rect.height).coerceIn(0f, 1f)) * imgH,
)

@Composable
fun PhotoFrame(
    bitmap: ImageBitmap?,
    markers: FrameMarkers?,
    manual: Boolean,
    label: String?,
    debugPoints: List<Px>,
    onManualPlace: (Px) -> Unit,
    onManualDrag: (ManualMarker, Px) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Box(modifier = modifier.shadow(1.dp, shape).clip(shape).background(Mm.Ink)) {
        Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
            if (bitmap == null) {
                EmptyFrameState()
            } else {
                PhotoAndOverlay(bitmap, markers, manual, label, debugPoints, onManualPlace, onManualDrag)
            }
        }
    }
}

@Composable
private fun EmptyFrameState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(MmIcons.Ruler, contentDescription = null, tint = Mm.Surface80, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(12.dp))
        Text("Head to feet, in frame", style = MmType.frameTitle, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Stand straight, face the camera, and leave a little room above your hair and below your shoes.",
            style = MmType.frameCopy,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 320.dp),
        )
    }
}

@Composable
private fun PhotoAndOverlay(
    bitmap: ImageBitmap,
    markers: FrameMarkers?,
    manual: Boolean,
    label: String?,
    debugPoints: List<Px>,
    onManualPlace: (Px) -> Unit,
    onManualDrag: (ManualMarker, Px) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val markerFill = remember(density) {
        TextStyle(fontFamily = SansFont, fontSize = 12.sp, color = Mm.Surface)
    }
    val markerOutline = remember(density) {
        TextStyle(
            fontFamily = SansFont, fontSize = 12.sp, color = Mm.Ink,
            drawStyle = Stroke(width = with(density) { 3.dp.toPx() }),
        )
    }
    val rodFill = remember(density) {
        TextStyle(fontFamily = SansFont, fontSize = 13.sp, color = Mm.Surface)
    }
    val rodOutline = remember(density) {
        TextStyle(
            fontFamily = SansFont, fontSize = 13.sp, color = Mm.Ink,
            drawStyle = Stroke(width = with(density) { 4.dp.toPx() }),
        )
    }

    // Callbacks + markers are read through rememberUpdatedState so the gesture
    // loop survives recomposition triggered by its own callbacks (a drag
    // updates state on every move — that must not restart pointerInput).
    val currentMarkers by rememberUpdatedState(markers)
    val currentPlace by rememberUpdatedState(onManualPlace)
    val currentDrag by rememberUpdatedState(onManualDrag)

    val gesture = if (manual) {
        Modifier.pointerInput(bitmap) {
            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val box = containMap(size.width.toFloat(), size.height.toFloat(), imgW, imgH)
                val hit = hitMarker(down.position, box, currentMarkers, imgW, imgH, 28.dp.toPx())
                down.consume()
                if (hit != null) {
                    currentDrag(hit, down.position.toImage(box, imgW, imgH))
                    var change = down
                    while (change.pressed) {
                        val event = awaitPointerEvent()
                        change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.positionChanged()) {
                            change.consume()
                            currentDrag(hit, change.position.toImage(box, imgW, imgH))
                        }
                    }
                } else {
                    currentPlace(down.position.toImage(box, imgW, imgH))
                }
            }
        }
    } else {
        Modifier
    }

    Box(Modifier.fillMaxSize()) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
        )
        Canvas(Modifier.fillMaxSize().then(gesture)) {
            val imgW = bitmap.width.toFloat()
            val imgH = bitmap.height.toFloat()
            val box = containMap(size.width, size.height, imgW, imgH)

            debugPoints.forEach { p ->
                drawCircle(Mm.Primary.copy(alpha = 0.5f), 2.5.dp.toPx(), box.mapPoint(p, imgW, imgH))
            }

            val m = markers
            if (m?.crown != null && m.feet != null) {
                val crown = box.mapPoint(m.crown, imgW, imgH)
                val feet = box.mapPoint(m.feet, imgW, imgH)
                val x = (crown.x + feet.x) / 2f

                // The rod: surface under-stroke + primary over-stroke, end caps.
                drawLine(Mm.Surface, Offset(x, crown.y), Offset(x, feet.y), 6.dp.toPx(), StrokeCap.Round)
                drawLine(Mm.Primary, Offset(x, crown.y), Offset(x, feet.y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(Mm.Primary, Offset(x - 12.dp.toPx(), crown.y), Offset(x + 12.dp.toPx(), crown.y), 2.dp.toPx(), StrokeCap.Round)
                drawLine(Mm.Primary, Offset(x - 12.dp.toPx(), feet.y), Offset(x + 12.dp.toPx(), feet.y), 2.dp.toPx(), StrokeCap.Round)

                // The head bracket, offset from the chin like the reference.
                if (m.chin != null) {
                    val chin = box.mapPoint(m.chin, imgW, imgH)
                    val hx = chin.x + 22.dp.toPx()
                    drawLine(Mm.Surface, Offset(hx, crown.y), Offset(hx, chin.y), 5.dp.toPx())
                    drawLine(Mm.Primary, Offset(hx, crown.y), Offset(hx, chin.y), 2.dp.toPx())
                    drawLine(Mm.Primary, Offset(hx - 8.dp.toPx(), crown.y), Offset(hx + 8.dp.toPx(), crown.y), 2.dp.toPx())
                    drawLine(Mm.Primary, Offset(hx - 8.dp.toPx(), chin.y), Offset(hx + 8.dp.toPx(), chin.y), 2.dp.toPx())
                }

                if (label != null) {
                    drawOutlinedText(
                        textMeasurer, label,
                        left = x + 16.dp.toPx(), baselineY = (crown.y + feet.y) / 2f,
                        fill = rodFill, outline = rodOutline,
                    )
                }
            }

            m?.let {
                if (it.crown != null) {
                    drawMarker(textMeasurer, box.mapPoint(it.crown, imgW, imgH), "Head", markerFill, markerOutline)
                }
                if (it.chin != null) {
                    drawMarker(textMeasurer, box.mapPoint(it.chin, imgW, imgH), "Chin", markerFill, markerOutline)
                }
                if (it.feet != null) {
                    drawMarker(textMeasurer, box.mapPoint(it.feet, imgW, imgH), "Feet", markerFill, markerOutline)
                }
            }
        }
    }
}

private fun hitMarker(
    pos: Offset,
    box: Rect,
    markers: FrameMarkers?,
    imgW: Float,
    imgH: Float,
    radius: Float,
): ManualMarker? {
    if (markers == null) return null
    fun hit(p: Px?): Boolean {
        if (p == null) return false
        val o = box.mapPoint(p, imgW, imgH)
        return hypot(pos.x - o.x, pos.y - o.y) <= radius
    }
    return when {
        hit(markers.chin) -> ManualMarker.CHIN
        hit(markers.crown) -> ManualMarker.CROWN
        hit(markers.feet) -> ManualMarker.FEET
        else -> null
    }
}

private fun DrawScope.drawMarker(
    textMeasurer: TextMeasurer,
    center: Offset,
    label: String,
    fill: TextStyle,
    outline: TextStyle,
) {
    drawCircle(Mm.Primary, 11.dp.toPx(), center)
    drawCircle(Mm.Surface, 11.dp.toPx(), center, style = Stroke(width = 3.dp.toPx()))
    drawOutlinedText(
        textMeasurer, label,
        left = center.x + 16.dp.toPx(), baselineY = center.y + 4.dp.toPx(),
        fill = fill, outline = outline,
    )
}

/** SVG-style text with paint-order: stroke — an ink outline under a surface fill. */
private fun DrawScope.drawOutlinedText(
    textMeasurer: TextMeasurer,
    text: String,
    left: Float,
    baselineY: Float,
    fill: TextStyle,
    outline: TextStyle,
) {
    val outlineLayout = textMeasurer.measure(text, outline)
    drawText(outlineLayout, topLeft = Offset(left, baselineY - outlineLayout.firstBaseline))
    val fillLayout = textMeasurer.measure(text, fill)
    drawText(fillLayout, topLeft = Offset(left, baselineY - fillLayout.firstBaseline))
}
