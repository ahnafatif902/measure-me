package com.measureme.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.measureme.app.ui.components.MmCard
import com.measureme.app.ui.components.MmPrimaryButton
import com.measureme.app.ui.components.MmSecondaryButton
import com.measureme.app.ui.components.MmTopBar
import com.measureme.app.ui.components.PrivacyLine
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.MeasureMeTheme
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmSpace
import com.measureme.app.ui.theme.MmType

/** Which part of the body / frame the framing guide should outline in accent. */
enum class GuideZone {
    FullBody,
    Head,
    Feet,
    Face,
    Light,
    Tilt,
    People,
    Ground,
    Sheet,
}

/** One row of the three-item recovery checklist. */
data class Tip(val text: String, val failing: Boolean = false)

/**
 * A typed, fixable error. Copy is taken verbatim from the task's failure
 * table: title says what is wrong, fix says how to correct it.
 */
data class MeasureError(
    val title: String,
    val fix: String,
    val zone: GuideZone,
    val tips: List<Tip>,
) {
    companion object {
        private val BASE_TIPS = listOf(
            "Head to toe in the frame",
            "Face the camera and stand tall",
            "Leave a hand's width above and below",
        )

        private fun tips(failing: String): List<Tip> =
            listOf(Tip(failing, failing = true)) +
                BASE_TIPS.filter { it != failing }.map { Tip(it) }

        val NoPhoto = MeasureError(
            title = "No photo yet",
            fix = "Take a full-body photo to get started.",
            zone = GuideZone.FullBody,
            tips = tips("Head to toe in the frame"),
        )

        val FeetCropped = MeasureError(
            title = "Your feet are cut off",
            fix = "Step back or tilt the phone down until your shoes are in frame.",
            zone = GuideZone.Feet,
            tips = tips("Head to toe in the frame"),
        )

        val HeadCropped = MeasureError(
            title = "Your head is cut off",
            fix = "Step back or tilt the phone up so there is room above your head.",
            zone = GuideZone.Head,
            tips = tips("Head to toe in the frame"),
        )

        val HeadTurned = MeasureError(
            title = "Face the camera",
            fix = "Turn your head toward the lens and keep your chin level.",
            zone = GuideZone.Face,
            tips = tips("Face the camera and stand tall"),
        )

        val TooDark = MeasureError(
            title = "It's a little dark",
            fix = "Move somewhere brighter or face a window.",
            zone = GuideZone.Light,
            tips = tips("Face the camera and stand tall"),
        )

        val PhoneTilted = MeasureError(
            title = "Hold the phone level",
            fix = "Keep the phone upright and level, then try again.",
            zone = GuideZone.Tilt,
            tips = tips("Face the camera and stand tall"),
        )

        val MultiplePeople = MeasureError(
            title = "More than one person",
            fix = "Retake with only you in the frame.",
            zone = GuideZone.People,
            tips = tips("Head to toe in the frame"),
        )

        val NoPerson = MeasureError(
            title = "We couldn't find you",
            fix = "Step back so your whole body is in the frame.",
            zone = GuideZone.FullBody,
            tips = tips("Head to toe in the frame"),
        )

        val A4NotFound = MeasureError(
            title = "We couldn't see the sheet",
            fix = "Lay the A4 sheet flat beside your feet with all four corners visible.",
            zone = GuideZone.Sheet,
            tips = tips("Head to toe in the frame"),
        )

        val UnevenGround = MeasureError(
            title = "The ground isn't flat",
            fix = "Stand on a flat floor with the sheet on the same surface.",
            zone = GuideZone.Ground,
            tips = tips("Head to toe in the frame"),
        )

        /**
         * The engine currently exposes its failure as a human message. Map it
         * to the typed UI model without changing the detection layer.
         */
        fun fromMessage(message: String?): MeasureError {
            val text = message.orEmpty().lowercase()
            return when {
                text.isBlank() || "no photo" in text || "take one first" in text -> NoPhoto
                "feet" in text || "foot" in text -> FeetCropped
                "head" in text &&
                    ("cropped" in text || "cut off" in text || "missing" in text) -> HeadCropped
                "head" in text &&
                    ("clear" in text || "turn" in text || "face" in text) -> HeadTurned
                "dark" in text || "light" in text || "bright" in text ||
                    "luminance" in text -> TooDark
                "tilt" in text || "level" in text || "roll" in text ||
                    "pitch" in text || "upright" in text -> PhoneTilted
                "more than one" in text || "multiple" in text ||
                    "two people" in text -> MultiplePeople
                "a4" in text || "sheet" in text -> A4NotFound
                "ground" in text || "floor" in text || "surface" in text ||
                    "slope" in text -> UnevenGround
                else -> NoPerson
            }
        }
    }
}

/**
 * Full-screen error state: framing guide, title, fix, three-row checklist,
 * then a fixed action footer. Layout is safe-area aware; the guide column
 * uses BoxWithConstraints + heightIn(min = maxHeight) because weight() does
 * not work inside a vertical scroll.
 */
@Composable
fun ErrorScreen(
    error: MeasureError,
    onBack: () -> Unit,
    onRetake: () -> Unit,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mm.Paper)
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            ),
    ) {
        MmTopBar(
            title = "",
            onBack = onBack,
            modifier = Modifier
                .height(56.dp)
                .fillMaxWidth()
                .padding(horizontal = MmSpace.Screen),
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val availableHeight = maxHeight
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = availableHeight)
                    .padding(horizontal = MmSpace.Screen)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FramingGuide(
                    zone = error.zone,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                )
                Spacer(Modifier.height(MmSpace.MD))
                Text(
                    text = error.title,
                    style = MmType.title,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(MmSpace.XS))
                Text(
                    text = error.fix,
                    style = MmType.body.copy(color = Mm.Ink2),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 320.dp),
                )
                Spacer(Modifier.height(MmSpace.MD))
                TipsCard(
                    tips = error.tips,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(MmSpace.Screen),
            verticalArrangement = Arrangement.spacedBy(MmSpace.Gap),
        ) {
            MmPrimaryButton(
                label = "Retake photo",
                onClick = onRetake,
                modifier = Modifier.fillMaxWidth(),
            )
            MmSecondaryButton(
                label = "Choose a different photo",
                onClick = onPick,
                modifier = Modifier.fillMaxWidth(),
            )
            PrivacyLine()
        }
    }
}

/** Surface card with the three recovery tips; the failing one is first and
 *  carries the accent warning icon, the rest get a neutral check. */
@Composable
private fun TipsCard(tips: List<Tip>, modifier: Modifier = Modifier) {
    MmCard(modifier = modifier) {
        tips.forEachIndexed { index, tip ->
            TipRow(tip)
            if (index < tips.lastIndex) Spacer(Modifier.height(MmSpace.Gap))
        }
    }
}

@Composable
private fun TipRow(tip: Tip) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    if (tip.failing) Mm.Accent.copy(alpha = 0.12f) else Mm.Inset,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (tip.failing) {
                Icon(
                    imageVector = MmIcons.AlertTriangle,
                    contentDescription = null,
                    tint = Mm.Accent,
                    modifier = Modifier.size(16.dp),
                )
            } else {
                Canvas(Modifier.size(16.dp)) {
                    val w = size.width
                    val check = Path().apply {
                        moveTo(w * 0.26f, w * 0.52f)
                        lineTo(w * 0.44f, w * 0.70f)
                        lineTo(w * 0.76f, w * 0.30f)
                    }
                    drawPath(
                        check,
                        color = Mm.Ink2,
                        style = Stroke(
                            width = 1.8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round,
                        ),
                    )
                }
            }
        }
        Spacer(Modifier.size(MmSpace.Gap))
        Text(
            text = tip.text,
            style = MmType.body.copy(
                color = if (tip.failing) Mm.Ink else Mm.Ink2,
                fontWeight = if (tip.failing) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

/**
 * Dashed body outline + corner brackets. The failing zone is redrawn in
 * accent (ring, floor line, sheet, second person, etc.) so the picture says
 * what the copy says.
 */
@Composable
fun FramingGuide(zone: GuideZone, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f

        val neutral = Mm.Ink2.copy(alpha = 0.34f)
        val accent = Mm.Accent
        val hairline = 1.2.dp.toPx()
        val accentStroke = 2.dp.toPx()
        val dash = PathEffect.dashPathEffect(
            floatArrayOf(6.dp.toPx(), 6.dp.toPx()),
        )
        val accentDash = PathEffect.dashPathEffect(
            floatArrayOf(10.dp.toPx(), 6.dp.toPx()),
        )

        // ---- corner brackets ------------------------------------------------
        val margin = 6.dp.toPx()
        val arm = 24.dp.toPx()
        val bracket = 2.dp.toPx()
        fun bracketCorner(x: Float, y: Float, dx: Float, dy: Float) {
            drawLine(
                color = neutral,
                start = Offset(x, y),
                end = Offset(x + arm * dx, y),
                strokeWidth = bracket,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = neutral,
                start = Offset(x, y),
                end = Offset(x, y + arm * dy),
                strokeWidth = bracket,
                cap = StrokeCap.Round,
            )
        }
        bracketCorner(margin, margin, 1f, 1f)
        bracketCorner(w - margin, margin, -1f, 1f)
        bracketCorner(margin, h - margin, 1f, -1f)
        bracketCorner(w - margin, h - margin, -1f, -1f)

        // ---- figure geometry ------------------------------------------------
        val top = h * 0.10f
        val feetY = h * 0.90f
        val headR = h * 0.055f
        val headCenter = Offset(cx, top + headR)
        val shoulderY = h * 0.28f
        val hipY = h * 0.56f
        val figureW = minOf(w * 0.50f, 150.dp.toPx())
        val shoulderHalf = figureW * 0.28f
        val hipHalf = figureW * 0.20f

        // ---- dashed body silhouette ----------------------------------------
        drawCircle(
            color = neutral,
            radius = headR,
            center = headCenter,
            style = Stroke(width = hairline, pathEffect = dash),
        )
        val body = Path().apply {
            // torso
            moveTo(cx - shoulderHalf, shoulderY)
            lineTo(cx - hipHalf, hipY)
            moveTo(cx + shoulderHalf, shoulderY)
            lineTo(cx + hipHalf, hipY)
            // legs
            moveTo(cx - hipHalf, hipY)
            lineTo(cx - hipHalf * 0.72f, feetY)
            moveTo(cx + hipHalf, hipY)
            lineTo(cx + hipHalf * 0.72f, feetY)
            // arms
            moveTo(cx - shoulderHalf, shoulderY)
            lineTo(cx - shoulderHalf * 1.32f, hipY - h * 0.06f)
            moveTo(cx + shoulderHalf, shoulderY)
            lineTo(cx + shoulderHalf * 1.32f, hipY - h * 0.06f)
            // shoulders
            moveTo(cx - shoulderHalf, shoulderY)
            lineTo(cx + shoulderHalf, shoulderY)
        }
        drawPath(
            path = body,
            color = neutral,
            style = Stroke(
                width = hairline,
                pathEffect = dash,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
        // feet
        drawLine(
            color = neutral,
            start = Offset(cx - hipHalf * 1.5f, feetY),
            end = Offset(cx - hipHalf * 0.25f, feetY),
            strokeWidth = hairline,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = neutral,
            start = Offset(cx + hipHalf * 0.25f, feetY),
            end = Offset(cx + hipHalf * 1.5f, feetY),
            strokeWidth = hairline,
            cap = StrokeCap.Round,
        )

        // ---- failing zone in accent ----------------------------------------
        when (zone) {
            GuideZone.Head -> drawCircle(
                color = accent,
                radius = headR + 5.dp.toPx(),
                center = headCenter,
                style = Stroke(width = accentStroke, pathEffect = accentDash),
            )

            GuideZone.Face -> {
                drawCircle(
                    color = accent,
                    radius = headR + 4.dp.toPx(),
                    center = headCenter,
                    style = Stroke(width = accentStroke),
                )
                drawLine(
                    color = accent,
                    start = Offset(cx - headR * 0.75f, headCenter.y + headR * 0.55f),
                    end = Offset(cx + headR * 0.75f, headCenter.y + headR * 0.55f),
                    strokeWidth = accentStroke,
                    cap = StrokeCap.Round,
                )
            }

            GuideZone.Feet -> {
                val topLeft = Offset(cx - figureW * 0.34f, feetY - h * 0.075f)
                val zoneSize = Size(figureW * 0.68f, h * 0.115f)
                drawRoundRect(
                    color = accent.copy(alpha = 0.08f),
                    topLeft = topLeft,
                    size = zoneSize,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                )
                drawRoundRect(
                    color = accent,
                    topLeft = topLeft,
                    size = zoneSize,
                    cornerRadius = CornerRadius(14.dp.toPx()),
                    style = Stroke(width = accentStroke, pathEffect = accentDash),
                )
            }

            GuideZone.Light -> {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                    size = Size(w - 8.dp.toPx(), h - 8.dp.toPx()),
                    cornerRadius = CornerRadius(20.dp.toPx()),
                    style = Stroke(width = accentStroke, pathEffect = accentDash),
                )
                drawCircle(
                    color = accent.copy(alpha = 0.10f),
                    radius = h * 0.13f,
                    center = Offset(cx, headCenter.y),
                )
            }

            GuideZone.Tilt -> {
                drawLine(
                    color = accent,
                    start = Offset(w * 0.16f, h * 0.50f),
                    end = Offset(w * 0.84f, h * 0.44f),
                    strokeWidth = accentStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = accent.copy(alpha = 0.35f),
                    start = Offset(w * 0.18f, h * 0.58f),
                    end = Offset(w * 0.82f, h * 0.58f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            GuideZone.People -> {
                val second = Offset(cx + figureW * 0.42f, top + headR * 1.6f)
                drawCircle(
                    color = accent.copy(alpha = 0.08f),
                    radius = headR * 0.85f,
                    center = second,
                )
                drawCircle(
                    color = accent,
                    radius = headR * 0.85f,
                    center = second,
                    style = Stroke(width = accentStroke),
                )
                drawLine(
                    color = accent,
                    start = Offset(second.x - headR, second.y + headR * 1.6f),
                    end = Offset(second.x + headR, second.y + headR * 1.6f),
                    strokeWidth = accentStroke,
                    cap = StrokeCap.Round,
                )
            }

            GuideZone.Ground -> {
                drawLine(
                    color = accent,
                    start = Offset(w * 0.12f, feetY),
                    end = Offset(w * 0.88f, feetY),
                    strokeWidth = accentStroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = accent.copy(alpha = 0.35f),
                    start = Offset(w * 0.18f, feetY + h * 0.035f),
                    end = Offset(w * 0.82f, feetY + h * 0.035f),
                    strokeWidth = 1.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            GuideZone.Sheet -> {
                val left = cx + hipHalf * 1.1f
                val sheet = Path().apply {
                    moveTo(left, feetY - h * 0.03f)
                    lineTo(left + w * 0.17f, feetY - h * 0.07f)
                    lineTo(left + w * 0.20f, feetY + h * 0.04f)
                    lineTo(left + w * 0.03f, feetY + h * 0.07f)
                    close()
                }
                drawPath(path = sheet, color = accent.copy(alpha = 0.10f))
                drawPath(
                    path = sheet,
                    color = accent,
                    style = Stroke(
                        width = accentStroke,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            GuideZone.FullBody -> {
                val topLeft = Offset(cx - figureW * 0.62f, top - h * 0.04f)
                val zoneSize = Size(figureW * 1.24f, feetY - top + h * 0.10f)
                drawRoundRect(
                    color = accent,
                    topLeft = topLeft,
                    size = zoneSize,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = accentStroke, pathEffect = accentDash),
                )
            }
        }
    }
}

@Preview(
    showSystemUi = true,
    name = "Error — no photo",
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun ErrorPreview() {
    MeasureMeTheme {
        ErrorScreen(
            error = MeasureError.NoPhoto,
            onBack = {},
            onRetake = {},
            onPick = {},
        )
    }
}
