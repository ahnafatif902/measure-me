package com.measureme.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmShapes
import com.measureme.app.ui.theme.MmSpace
import com.measureme.app.ui.theme.MmType

/*
 * The MeasureMe component kit — UI Design Specification v1.0, section 5.
 * Every component answers to a spec row; motion constants come from section 8
 * (framer springs mapped to Compose per section 10):
 *   press: scale 0.97, spring stiffness 500, damping 30  → ratio 0.67
 *   mode:  scale 0.98, same spring
 *   progress: spring stiffness 140, damping 22           → ratio 0.93
 * Reduced motion (system setting) snaps everything.
 */

/** Press spring: stiffness 500, damping 30 → damping ratio 30 / (2·√500) ≈ 0.67. */
private val PressSpring = spring<Float>(stiffness = 500f, dampingRatio = 0.67f)

/** Progress spring: stiffness 140, damping 22 → damping ratio 22 / (2·√140) ≈ 0.93. */
private val ProgressSpring = spring<Float>(stiffness = 140f, dampingRatio = 0.93f)

/** True when the user has animations switched off (spec §8: reduced motion). */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE, 1f,
            ) == 0f
        }.getOrDefault(false)
    }
}

// ---------------------------------------------------------------------------
// Modifier decorations
// ---------------------------------------------------------------------------

/** Card: hairline border + 1 px white inner top highlight + ambient shadow (spec §4). */
private fun Modifier.mmCardDecoration(): Modifier = this
    .shadow(10.dp, MmShapes.Card, clip = false, ambientColor = Mm.Shadow, spotColor = Mm.Shadow)
    .clip(MmShapes.Card)
    .background(Mm.Surface)
    .border(1.dp, Mm.Line, MmShapes.Card)
    .mmTopHighlight(24.dp)

/** The 1 px white inner top highlight that makes surfaces read as physical. */
internal fun Modifier.mmTopHighlight(corner: Dp, alpha: Float = 0.65f): Modifier = drawBehind {
    val r = corner.toPx()
    drawLine(
        color = Color.White.copy(alpha = alpha),
        start = Offset(r + 6.dp.toPx(), 1.dp.toPx()),
        end = Offset(size.width - r - 6.dp.toPx(), 1.dp.toPx()),
        strokeWidth = 1.dp.toPx(),
    )
}

/** Recessed surface: paper tone + inner top shadow (spec §4: inset 0 2px 6px ink 7%). */
internal fun Modifier.mmInset(corner: Dp, depth: Dp = 6.dp): Modifier = this
    .clip(RoundedCornerShape(corner))
    .background(Mm.Inset)
    .drawBehind {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Mm.Ink.copy(alpha = 0.07f), Color.Transparent),
                startY = 0f,
                endY = depth.toPx(),
            ),
        )
    }

/** Visible keyboard focus ring: 2 px accent, 3 px offset (spec §9). */
private fun Modifier.mmFocusRing(focused: Boolean, corner: Dp): Modifier = drawBehind {
    if (!focused) return@drawBehind
    val offset = 3.dp.toPx()
    drawRoundRect(
        color = Mm.Accent,
        topLeft = Offset(-offset, -offset),
        size = Size(size.width + offset * 2, size.height + offset * 2),
        cornerRadius = CornerRadius(corner.toPx() + offset),
        style = Stroke(width = 2.dp.toPx()),
    )
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

/** Primary button: accent fill, 48 high, 16 radius, white label (spec §5). */
@Composable
fun MmPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MmButtonCore(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = Mm.Accent,
        pressedBackground = Mm.AccentPress,
        contentColor = Mm.OnAccent,
        accentShadow = true,
        topHighlight = true,
    )
}

/** Secondary button: surface fill, hairline border, ink label. */
@Composable
fun MmSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    MmButtonCore(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = Mm.Surface,
        pressedBackground = Mm.Inset,
        contentColor = Mm.Ink,
        icon = icon,
        border = Mm.Line,
    )
}

/** Ghost button: no fill, ink-2 label. */
@Composable
fun MmGhostButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    MmButtonCore(
        label = label,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = Color.Transparent,
        pressedBackground = Mm.Ink.copy(alpha = 0.05f),
        contentColor = Mm.Ink2,
        height = 44.dp,
    )
}

@Composable
private fun MmButtonCore(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    background: Color,
    pressedBackground: Color,
    contentColor: Color,
    icon: ImageVector? = null,
    border: Color? = null,
    height: Dp = 48.dp,
    accentShadow: Boolean = false,
    topHighlight: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = if (reduced) snap() else PressSpring,
        label = "pressScale",
    )
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pressed) {
        if (pressed && enabled) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    var focused by remember { mutableStateOf(false) }

    val shape = MmShapes.Button
    Row(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else Mm.DisabledAlpha)
            .onFocusChanged { focused = it.isFocused }
            .mmFocusRing(focused, 16.dp)
            .then(
                if (accentShadow) {
                    Modifier.shadow(
                        4.dp, shape,
                        ambientColor = Mm.Accent.copy(alpha = 0.35f),
                        spotColor = Mm.Accent.copy(alpha = 0.35f),
                    )
                } else Modifier,
            )
            .clip(shape)
            .background(if (pressed && enabled) pressedBackground else background)
            .then(if (border != null) Modifier.border(1.dp, border, shape) else Modifier)
            .then(if (topHighlight) Modifier.mmTopHighlight(16.dp, alpha = 0.30f) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            )
            .height(height)
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(label, style = MmType.button.copy(color = contentColor))
    }
}

/** Small circular icon button — used for back navigation (44 x 44 target). */
@Composable
fun MmIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduced = rememberReducedMotion()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = if (reduced) snap() else PressSpring,
        label = "iconPress",
    )
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(pressed) {
        if (pressed) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(CircleShape)
            .background(Mm.Surface)
            .border(1.dp, Mm.Line, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Mm.Ink, modifier = Modifier.size(18.dp))
    }
}

// ---------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------

/** The one surface everything sits on: label, number, stats… (spec §5 result card). */
@Composable
fun MmCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .mmCardDecoration()
            .padding(MmSpace.MD),
        content = content,
    )
}

/** Top bar: leading ruler icon + wordmark (home) or back button + title (capture). */
@Composable
fun MmTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    leadingIcon: ImageVector? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            MmIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.width(MmSpace.SM))
        } else if (leadingIcon != null) {
            Icon(leadingIcon, contentDescription = null, tint = Mm.Ink, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(MmSpace.XS))
        }
        Text(title, style = MmType.wordmark)
    }
}

// ---------------------------------------------------------------------------
// Checklist, steps, progress
// ---------------------------------------------------------------------------

/** One checklist row: accent check glyph, title, one line of detail (spec §6.2). */
@Composable
fun CheckListRow(title: String, detail: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth()) {
        Canvas(Modifier.size(20.dp).padding(top = 1.dp)) {
            drawCircle(color = Mm.Accent.copy(alpha = 0.14f), radius = size.minDimension / 2f)
            val w = size.width
            val check = Path().apply {
                moveTo(w * 0.28f, w * 0.53f)
                lineTo(w * 0.44f, w * 0.69f)
                lineTo(w * 0.74f, w * 0.33f)
            }
            drawPath(
                check,
                color = Mm.Accent,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MmType.body.copy(fontWeight = FontWeight.Medium))
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MmType.body2)
        }
    }
}

enum class StepState { Pending, Active, Done }

/** One processing step row; the circle becomes an accent check on completion. */
@Composable
fun StepRow(label: String, state: StepState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 2f
            val w = size.width
            when (state) {
                StepState.Pending -> drawCircle(
                    color = Mm.LineStrong,
                    radius = r - 1.dp.toPx(),
                    style = Stroke(width = 1.5.dp.toPx()),
                )
                StepState.Active -> {
                    drawCircle(
                        color = Mm.Accent,
                        radius = r - 1.dp.toPx(),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawCircle(color = Mm.Accent, radius = r * 0.32f)
                }
                StepState.Done -> {
                    drawCircle(color = Mm.Accent, radius = r)
                    val check = Path().apply {
                        moveTo(w * 0.28f, w * 0.52f)
                        lineTo(w * 0.44f, w * 0.68f)
                        lineTo(w * 0.73f, w * 0.33f)
                    }
                    drawPath(
                        check,
                        color = Mm.OnAccent,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                    )
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MmType.body.copy(
                color = if (state == StepState.Pending) Mm.Ink2 else Mm.Ink,
                fontWeight = if (state == StepState.Done) FontWeight.Medium else FontWeight.Normal,
            ),
        )
    }
}

/** Progress bar: 8 high, inset track, accent fill, spring width (spec §5, §8). */
@Composable
fun MmProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val reduced = rememberReducedMotion()
    val value by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = if (reduced) snap() else ProgressSpring,
        label = "progress",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .mmInset(corner = 4.dp)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(value, 0f..1f) },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(value)
                .clip(RoundedCornerShape(4.dp))
                .background(Mm.Accent),
        )
    }
}

// ---------------------------------------------------------------------------
// Result bits
// ---------------------------------------------------------------------------

/** Stat tile: label + tabular value on a recessed surface (spec §5). */
@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .mmInset(corner = 16.dp)
            .padding(MmSpace.MD),
    ) {
        Text(label, style = MmType.label)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MmType.statValue)
    }
}

/** One specific, fixable reason — errors say what went wrong and how to fix it. */
@Composable
fun FailureBanner(reason: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Mm.Accent.copy(alpha = 0.07f))
            .border(1.dp, Mm.Accent.copy(alpha = 0.30f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            MmIcons.AlertTriangle,
            contentDescription = null,
            tint = Mm.Accent,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            reason,
            style = MmType.body2.copy(color = Mm.Ink),
            modifier = Modifier.weight(1f),
        )
    }
}

/** The privacy line, exact copy from the spec §7. */
@Composable
fun PrivacyLine(modifier: Modifier = Modifier) {
    Text(
        "Processed on your device. Your photo never leaves your phone.",
        style = MmType.caption,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}
