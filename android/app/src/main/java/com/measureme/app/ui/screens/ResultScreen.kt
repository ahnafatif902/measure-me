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
import com.measureme.app.ui.components.FailureBanner
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
 * Result (UI Spec §6.3) — one screen, three states:
 *
 *  - Loading: the processing animation (progress bar + the three real steps).
 *  - Error:   a warning card with the specific reason and a "Try again" way
 *             out, instead of a number we cannot stand behind.
 *  - Success: one surface — label, the counting number with a small unit,
 *             the exact imperial conversion plus the run metadata on the
 *             same line, the quality bar and caveat, two stat tiles, one
 *             line about heads tall.
 *
 * The card enters with a 220 ms fade and a 12 px rise; the number counts
 * 0 → value over 1.2 s (ease-out) and the metadata line states the settled
 * estimate, fading in once the count-up finishes. Reduced motion shows the
 * final values instantly.
 */
@Composable
fun ResultScreen(
    state: MeasureMeViewModel.MeasureState,
    onMeasureAgain: () -> Unit,
    onTryAgain: () -> Unit,
) {
    when (state) {
        is MeasureMeViewModel.MeasureState.Idle -> IdleView()
        is MeasureMeViewModel.MeasureState.Loading -> LoadingView(state.step)
        is MeasureMeViewModel.MeasureState.Error -> ErrorView(state.message, onTryAgain)
        is MeasureMeViewModel.MeasureState.Success -> SuccessView(state.result, onMeasureAgain)
    }
}

/** Nothing measured yet — a quiet placeholder instead of a blank screen. */
@Composable
private fun IdleView() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        MmCard(modifier = Modifier.fillMaxWidth()) {
            Text("Nothing measured yet", style = MmType.label)
            Spacer(Modifier.height(8.dp))
            Text(
                "Take a photo and Measure my height to see your estimate.",
                style = MmType.body2,
            )
        }
    }
}

/** The engine is running: real progress, real step names. */
@Composable
private fun LoadingView(step: Int) {
    val reduced = rememberReducedMotion()

    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            appear.animateTo(1f, tween(220, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
        }
    }

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
                },
        ) {
            Text("Working on it", style = MmType.label)
            Spacer(Modifier.height(20.dp))
            ProcessingScreen(step = step)
        }

        Spacer(Modifier.height(24.dp))
        PrivacyLine()
        Spacer(Modifier.height(28.dp))
    }
}

/**
 * The photo could not be measured. One specific, fixable reason and a way
 * out — never a number we cannot stand behind.
 */
@Composable
private fun ErrorView(message: String, onTryAgain: () -> Unit) {
    val reduced = rememberReducedMotion()

    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            appear.animateTo(1f, tween(220, easing = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)))
        }
    }

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
                },
        ) {
            Text("Couldn't measure that", style = MmType.label)
            Spacer(Modifier.height(16.dp))
            FailureBanner(reason = message)
            Spacer(Modifier.height(20.dp))
            Text(
                "A full-body shot with your face, head and feet in frame gives the " +
                    "best result. Keep the phone level and step back until you fit.",
                style = MmType.body2,
            )
        }

        Spacer(Modifier.height(20.dp))

        MmPrimaryButton(
            label = "Try again",
            onClick = onTryAgain,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(16.dp))

        PrivacyLine()

        Spacer(Modifier.height(28.dp))
    }
}

/** The measurement: the number, its conversions and the run's metadata. */
@Composable
private fun SuccessView(result: MeasureMeViewModel.Result, onMeasureAgain: () -> Unit) {
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

    // The imperial + metadata line states the *settled* estimate, never the
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
                        "${cmToFtIn(result.heightCm.toFloat())}. ${result.metadataString}. " +
                        "Photo quality ${result.quality} out of 100."
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
                text = "${cmToFtIn(result.heightCm.toFloat())} · ${result.metadataString}",
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
