package com.measureme.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.cmToFtIn
import com.measureme.app.ui.components.MmCard
import com.measureme.app.ui.components.MmPrimaryButton
import com.measureme.app.ui.components.MmProgressBar
import com.measureme.app.ui.components.PrivacyLine
import com.measureme.app.ui.components.StatTile
import com.measureme.app.ui.components.rememberReducedMotion
import com.measureme.app.ui.theme.MmType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Result (UI Spec §6.3): one surface — label, the counting number with a
 * small unit, imperial value and range on the same line, the quality bar and
 * caveat, two stat tiles, one line about heads tall. Card enters with a
 * 220 ms fade and a 12 px rise; the number counts 0 → value over 1.2 s
 * (ease-out), the imperial + range line states the settled estimate and
 * fades in once the count-up finishes, and reduced motion shows the final
 * values instantly.
 */
@Composable
fun ResultScreen(
    result: MeasureMeViewModel.Result,
    onMeasureAgain: () -> Unit,
) {
    val reduced = rememberReducedMotion()

    // Card arrival: 220 ms fade with a 12 px rise.
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            appear.animateTo(1f, tween(220, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
        }
    }

    // Count-up: 0 → result over 1.2 s.
    val count = remember { Animatable(0f) }
    val countDone = remember { mutableStateOf(reduced) }
    LaunchedEffect(result.heightCm) {
        if (reduced) {
            count.snapTo(result.heightCm.toFloat())
        } else {
            count.animateTo(
                result.heightCm.toFloat(),
                tween(1200, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)),
            )
        }
        countDone.value = true
    }

    // The imperial + range line states the *settled* estimate, never the
    // animating value, so it can't disagree with the number on screen — it
    // just fades in once the count-up has finished.
    val lineAlpha = animateFloatAsState(
        targetValue = if (countDone.value) 1f else 0f,
        animationSpec = tween(220),
        label = "resultLineAlpha",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        MmCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 12.dp.toPx()
                }
                .clearAndSetSemantics {
                    // Accessibility: the result is exposed as one text value.
                    contentDescription = "Estimated height ${result.heightCm} centimetres, " +
                        "likely between ${result.rangeLowCm} and ${result.rangeHighCm}."
                },
        ) {
            Text("You're about", style = MmType.label)

            Spacer(Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = count.value.roundToInt().toString(),
                    style = MmType.resultNumber,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "cm",
                    style = MmType.unit,
                    modifier = Modifier.padding(bottom = 14.dp),
                )
            }

            Spacer(Modifier.height(6.dp))

            Text(
                "${cmToFtIn(result.heightCm.toFloat())} · " +
                    "Likely ${result.rangeLowCm}\u2013${result.rangeHighCm} cm",
                style = MmType.resultLine,
                modifier = Modifier.alpha(lineAlpha.value),
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    result.qualityLabel,
                    style = MmType.body.copy(fontWeight = FontWeight.Medium),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "Photo quality ${result.quality}/100",
                    style = MmType.qualityScore,
                )
            }

            Spacer(Modifier.height(10.dp))

            MmProgressBar(progress = result.quality / 100f)

            Spacer(Modifier.height(10.dp))

            Text(
                "This rates the photo, not the answer. Even a clean photo can be off by several centimetres.",
                style = MmType.body2,
            )

            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Head to toe",
                    value = String.format(Locale.US, "%,d px", result.personPx),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                StatTile(
                    label = "Head height",
                    value = "${result.headPx} px",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "You measure ${result.headsTall} heads tall. Classical art uses 7.5.",
                style = MmType.body2,
            )
        }

        Spacer(Modifier.height(24.dp))

        MmPrimaryButton(
            label = "Measure again",
            onClick = onMeasureAgain,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        PrivacyLine()

        Spacer(Modifier.height(28.dp))
    }
}
