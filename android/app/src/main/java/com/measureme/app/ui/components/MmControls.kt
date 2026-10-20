package com.measureme.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.measureme.app.pipeline.Tilt
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmElevation
import com.measureme.app.ui.theme.MmShapes
import com.measureme.app.ui.theme.MmSpace
import com.measureme.app.ui.theme.MmType
import com.measureme.app.ui.theme.MmMotion

/** Pill-shaped chip: selected = accent border, never colour alone. Press scales to 0.97. */
@Composable
fun MmChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (rememberReducedMotion()) snap() else MmMotion.Press,
        label = "chipPress",
    )

    Box(
        modifier = modifier
            .height(44.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Mm.Surface else Color.Transparent)
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) Mm.Accent else Mm.Line,
                RoundedCornerShape(16.dp),
            ),
    ) {
        Text(
            text = label,
            style = MmType.label.copy(
                color = if (selected) Mm.Accent else Mm.Ink2,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

/** Mode B camera-height stepper: 5 cm steps, bounded 60–220 cm. */
@Composable
fun MmNumberStepper(
    value: Float,
    onChange: (Float) -> Unit,
    min: Float = 60f,
    max: Float = 220f,
    step: Float = 5f,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = if (rememberReducedMotion()) snap() else MmMotion.Press,
        label = "stepperPress",
    )

    fun decrement() {
        onChange((value - step).coerceIn(min, max))
    }

    fun increment() {
        onChange((value + step).coerceIn(min, max))
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { decrement() },
                    enabled = value > min,
                )
                .clip(RoundedCornerShape(12.dp))
                .background(Mm.Surface)
                .border(1.dp, Mm.Line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "−",
                style = MmType.statValue.copy(color = Mm.Ink),
                textAlign = TextAlign.Center,
                modifier = Modifier.size(16.dp),
            )
        }

        Text(
            text = "${(value + 0.5f).toInt()} cm",
            style = MmType.statValue.copy(
                color = Mm.Ink,
                fontWeight = FontWeight.SemiBold,
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { increment() },
                    enabled = value < max,
                )
                .clip(RoundedCornerShape(12.dp))
                .background(Mm.Surface)
                .border(1.dp, Mm.Line, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "+",
                style = MmType.statValue.copy(color = Mm.Ink),
                textAlign = TextAlign.Center,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Real gravity-fed level indicator — "Level" when near-zero, "Tilt X°" otherwise. */
@Composable
fun MmLevelIndicator(
    tilt: Tilt?,
    modifier: Modifier = Modifier,
) {
    val degrees = tilt?.let { (kotlin.math.abs(it.pitchDeg) + kotlin.math.abs(it.rollDeg)) / 2.0 } ?: 0.0
    val isLevel = degrees < 1.5
    val label = if (isLevel) "Level" else "Tilt ${(degrees + 0.5).toInt()}°"

    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Mm.Surface)
            .border(1.dp, Mm.Line, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (isLevel) Mm.Accent else Mm.Ink2),
        )
        androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MmType.label.copy(color = Mm.Ink),
        )
    }
}

/** 72 dp shutter — accent disc, inner white ring, press spring 0.97 + haptic. */
@Composable
fun MmShutterButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.97f else 1f,
        animationSpec = if (rememberReducedMotion()) snap() else MmMotion.Press,
        label = "shutterPress",
    )

    Box(
        modifier = modifier
            .size(72.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .alpha(if (enabled) 1f else Mm.DisabledAlpha)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(Mm.Accent)
            .shadow(
                MmElevation.ButtonAccent, androidx.compose.foundation.shape.CircleShape,
                ambientColor = Mm.Accent.copy(alpha = 0.35f),
                spotColor = Mm.Accent.copy(alpha = 0.35f),
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .border(3.dp, Mm.OnAccent, androidx.compose.foundation.shape.CircleShape),
        )
    }
}
