package com.measureme.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
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
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmMotion
import com.measureme.app.ui.theme.MmSpace
import com.measureme.app.ui.theme.MmType
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Result (UI Spec §6.3) — one screen, three states:
 *
 *  - Loading: the processing animation (progress bar + the three real steps).
 *  - Error:   a warning card with the specific reason and a "Try again" way
 *             out, instead of a number we cannot stand behind.
 *  - Success: a floating surface card — label, the counting number with a
 *             small unit, the imperial conversion plus metadata below it,
 *             the quality bar and caveat, two stat tiles, one line about
 *             heads tall.
 *
 * The card enters with a spring fade + 12 dp rise; the number counts up
 * with [MmMotion.Fluid]; reduced motion shows final values instantly.
 */
@Composable
fun ResultScreen(
    state: MeasureMeViewModel.MeasureState,
    onMeasureAgain: () -> Unit,
    onRetake: () -> Unit,
    onPick: () -> Unit,
) {
    when (state) {
        is MeasureMeViewModel.MeasureState.Idle -> IdleView()
        is MeasureMeViewModel.MeasureState.Loading -> LoadingView(state.step)
        is MeasureMeViewModel.MeasureState.Error -> ErrorScreen(
            error = MeasureError.fromMessage(state.message),
            onBack = onMeasureAgain,
            onRetake = onRetake,
            onPick = onPick,
        )
        is MeasureMeViewModel.MeasureState.Success -> SuccessView(state.result, onMeasureAgain)
    }
}

/** Shared safe-area scaffold: background, then insets, then content padding.
 *  The scroll viewport stops above the navigation bar so nothing rests under
 *  it; long result content can still scroll. */
@Composable
private fun ResultScaffold(
    content: @Composable ColumnScope.() -> Unit,
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
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                    ),
                )
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MmSpace.Screen),
            content = content,
        )
    }
}

/** Nothing measured yet — a quiet placeholder instead of a blank screen. */
@Composable
private fun IdleView() {
    ResultScaffold {
        Spacer(Modifier.height(MmSpace.MD))
        MmCard(modifier = Modifier.fillMaxWidth()) {
            Text("Nothing measured yet", style = MmType.label)
            Spacer(Modifier.height(MmSpace.XS))
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
            appear.animateTo(1f, spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    ResultScaffold {
        Spacer(Modifier.height(MmSpace.MD))

        MmCard(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 12.dp.toPx()
                },
        ) {
            Text("Working on it", style = MmType.label)
            Spacer(Modifier.height(MmSpace.SM))
            ProcessingScreen(step = step)
        }

        Spacer(Modifier.height(MmSpace.MD))
        PrivacyLine()
        Spacer(Modifier.height(MmSpace.LG))
    }
}

/** The measurement: the number, its conversions and the run's metadata. */
@Composable
private fun SuccessView(result: MeasureMeViewModel.Result, onMeasureAgain: () -> Unit) {
    val reduced = rememberReducedMotion()

    // Card arrival: spring fade with a 12 dp rise.
    val appear = remember { Animatable(if (reduced) 1f else 0f) }
    LaunchedEffect(Unit) {
        if (!reduced) {
            appear.animateTo(1f, spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow))
        }
    }

    // Count-up: 0 → result with MmMotion.Fluid spring.
    val count = remember { Animatable(0f) }
    val countDone = remember { mutableStateOf(reduced) }
    LaunchedEffect(result.heightCm) {
        if (reduced) {
            count.snapTo(result.heightCm.toFloat())
        } else {
            count.animateTo(
                result.heightCm.toFloat(),
                spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
            )
        }
        countDone.value = true
    }

    // The imperial + metadata line states the *settled* estimate — fades in
    // once the count-up has finished so it can't disagree with the number.
    val lineAlpha = animateFloatAsState(
        targetValue = if (countDone.value) 1f else 0f,
        animationSpec = spring<Float>(dampingRatio = 0.75f, stiffness = Spring.StiffnessMediumLow),
        label = "resultLineAlpha",
    )

    ResultScaffold {
        Spacer(Modifier.height(MmSpace.SM))

        // Floating result card: 24 dp total side margin (20 screen + 4 card).
        MmCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MmSpace.XXS)
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 12.dp.toPx()
                }
                .clearAndSetSemantics {
                    contentDescription = "Estimated height ${result.heightCm} centimetres, " +
                        "${cmToFtIn(result.heightCm.toFloat())}. ${result.metadataString}. " +
                        (result.warning?.let { "$it " } ?: "") +
                        "Photo quality ${result.quality} out of 100."
                },
        ) {
            Text("You're about", style = MmType.label)

            Spacer(Modifier.height(MmSpace.XS))

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

            Spacer(Modifier.height(MmSpace.XS))

            Text(
                text = "${cmToFtIn(result.heightCm.toFloat())} · ${result.metadataString}",
                style = MmType.resultLine,
                modifier = Modifier.alpha(lineAlpha.value),
            )

            // Mode B/C fallback: say exactly why Proportions stood in. Never silent.
            result.warning?.let { warning ->
                Spacer(Modifier.height(MmSpace.XS))
                Text(
                    text = warning,
                    style = MmType.body2.copy(color = Mm.Accent),
                    modifier = Modifier.alpha(lineAlpha.value),
                )
            }

            Spacer(Modifier.height(MmSpace.MD))

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

            Spacer(Modifier.height(MmSpace.XS))

            MmProgressBar(progress = result.quality / 100f)

            Spacer(Modifier.height(MmSpace.MD))

            Text(
                "This rates the photo, not the answer. Even a clean photo can be off by several centimetres.",
                style = MmType.body2,
            )

            Spacer(Modifier.height(MmSpace.MD))

            Row(Modifier.fillMaxWidth()) {
                StatTile(
                    label = "Head to toe",
                    value = String.format(Locale.US, "%,d px", result.personPx),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(MmSpace.SM))
                StatTile(
                    label = "Head height",
                    value = "${result.headPx} px",
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(MmSpace.SM))

            Text(
                "You measure ${"%.1f".format(result.headsTall)} heads tall. Classical art uses 7.5.",
                style = MmType.body2,
            )
        }

        Spacer(Modifier.height(MmSpace.LG))

        MmPrimaryButton(
            label = "Measure again",
            onClick = onMeasureAgain,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(MmSpace.SM))

        PrivacyLine()

        Spacer(Modifier.height(MmSpace.LG))
    }
}
