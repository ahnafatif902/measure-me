@file:OptIn(ExperimentalFoundationApi::class)

package com.measureme.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.pipeline.AppConstants
import com.measureme.app.pipeline.HeightEstimate
import com.measureme.app.pipeline.Px
import com.measureme.app.ui.components.DimensionGlyph
import com.measureme.app.ui.components.FrameMarkers
import com.measureme.app.ui.components.ManualMarker
import com.measureme.app.ui.components.MmBullets
import com.measureme.app.ui.components.MmButton
import com.measureme.app.ui.components.MmButtonSize
import com.measureme.app.ui.components.MmButtonVariant
import com.measureme.app.ui.components.MmCard
import com.measureme.app.ui.components.MmMeter
import com.measureme.app.ui.components.MmWell
import com.measureme.app.ui.components.PhotoFrame
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmType
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * The MEASURE page — the whole reference design in one scrollable screen:
 * header → dark 3:4 frame → buttons → estimate → (busy | result | failure |
 * empty) → how-card → disclaimer. Every state renders inline, exactly like
 * web/src/components/measure-studio.jsx, and every number comes from the
 * real Android pipeline (ML Kit + GeometryModule + ConfidenceModule).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeasureScreen(
    state: MeasureMeViewModel.UiState,
    onPhotoPicked: (Uri) -> Unit,
    onTakePhoto: () -> Unit,
    onSample: () -> Unit,
    onToggleManual: () -> Unit,
    onEnterManual: () -> Unit,
    onEstimate: () -> Unit,
    onManualPlace: (Px) -> Unit,
    onManualDrag: (ManualMarker, Px) -> Unit,
    onHeadCmChange: (Double) -> Unit,
    onHeadsChange: (Double) -> Unit,
) {
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> if (uri != null) onPhotoPicked(uri) }

    val scrollState = rememberScrollState()
    val resultRequester = remember { BringIntoViewRequester() }
    LaunchedEffect(state.resultNonce) {
        if (state.resultNonce > 0L) {
            // Wait one frame so the result card is laid out before scrolling.
            withFrameNanos { }
            resultRequester.bringIntoView()
        }
    }

    val bitmap = state.bitmap

    // ---- the frame's markers (manual taps, or the detected points) --------
    val manualChinDisplay: Px? = run {
        if (!state.manualInput) return@run null
        val crown = state.manualCrown ?: return@run null
        val feet = state.manualFeet ?: return@run null
        if (state.manualChinAdjusted && state.manualChin != null) state.manualChin
        else derivedChinPoint(crown, feet, state.heads)
    }
    val markers: FrameMarkers? = if (state.manualInput) {
        if (state.manualCrown == null && state.manualFeet == null && state.manualChin == null) null
        else FrameMarkers(state.manualCrown, manualChinDisplay, state.manualFeet)
    } else {
        val lm = state.landmarks
        if (lm != null && state.estimate != null) FrameMarkers(lm.headTop, lm.chin, lm.feet) else null
    }

    val debugPoints: List<Px> =
        if (state.showDebug && !state.manualInput) state.landmarks?.points?.values?.toList().orEmpty()
        else emptyList()

    val manualHint = when {
        state.manualCrown == null -> "Tap the top of the head."
        state.manualFeet == null -> "Tap the bottom of the shoes, where they meet the floor."
        state.manualChinAdjusted -> "Drag any marker to correct it. The estimate updates as you move them."
        else -> "Drag the chin marker to the bottom of the chin. It starts at 1 ÷ the canonical ratio, which is only a prior."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Mm.Bg)
            .verticalScroll(scrollState)
            .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 40.dp),
    ) {
        // ---- header --------------------------------------------------------
        Text("Proportion estimate", style = MmType.kicker)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            DimensionGlyph()
            Spacer(Modifier.width(12.dp))
            Text("MeasureMe", style = MmType.title)
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Estimate standing height from one photo. No reference object — only how many head lengths fit in the body.",
            style = MmType.lede,
        )

        Spacer(Modifier.height(24.dp))

        // ---- the photo frame ----------------------------------------------
        PhotoFrame(
            bitmap = if (bitmap != null) bitmap.asImageBitmap() else null,
            markers = markers,
            manual = state.manualInput,
            label = state.estimate?.let { "${it.heightCmRounded} cm" },
            debugPoints = debugPoints,
            onManualPlace = onManualPlace,
            onManualDrag = onManualDrag,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(12.dp))

        // ---- upload / camera ------------------------------------------------
        Row(Modifier.fillMaxWidth()) {
            MmButton(
                label = "Upload",
                variant = MmButtonVariant.Secondary,
                icon = MmIcons.Upload,
                enabled = !state.decoding,
                modifier = Modifier.weight(1f),
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            Spacer(Modifier.width(8.dp))
            MmButton(
                label = "Take photo",
                variant = MmButtonVariant.Secondary,
                icon = MmIcons.Camera,
                enabled = !state.decoding,
                modifier = Modifier.weight(1f),
                onClick = onTakePhoto,
            )
        }

        Spacer(Modifier.height(8.dp))

        // ---- ghost row ------------------------------------------------------
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MmButton(
                label = "Try a sample photo",
                variant = MmButtonVariant.Ghost,
                icon = MmIcons.ImagePlus,
                horizontalPadding = 8.dp,
                enabled = !state.decoding,
                onClick = onSample,
            )
            MmButton(
                label = if (state.manualInput) "Use automatic detection" else "Mark points yourself",
                variant = MmButtonVariant.Ghost,
                horizontalPadding = 8.dp,
                onClick = onToggleManual,
            )
        }

        if (state.manualInput) {
            Spacer(Modifier.height(12.dp))
            Text(manualHint, style = MmType.hint)
        }

        Spacer(Modifier.height(16.dp))

        // ---- the estimate action + status ----------------------------------
        MmButton(
            label = if (state.working) "Estimating…" else "Estimate height",
            size = MmButtonSize.Lg,
            icon = MmIcons.Ruler,
            enabled = bitmap != null && !state.working && !state.decoding,
            modifier = Modifier.fillMaxWidth(),
            onClick = onEstimate,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (state.decoding) "Reading the photo…"
            else "Pose detection runs on this phone. The photo is never uploaded.",
            style = MmType.status,
        )

        Spacer(Modifier.height(24.dp))

        // ---- result column (busy / result / failure / empty) ----------------
        val estimate = state.estimate
        val failure = state.failure
        Column(Modifier.fillMaxWidth().bringIntoViewRequester(resultRequester)) {
            when {
                state.working -> BusyCard()
                estimate != null -> ResultCard(estimate, state)
                failure != null -> FailureCard(failure, state.manualInput, onEnterManual)
                else -> {
                    val emptyNote = state.notice ?: when {
                        bitmap != null && state.manualInput ->
                            "Mark the crown and the feet. The estimate fills in as soon as both points are set."
                        bitmap != null ->
                            "Press Estimate height. You'll get centimeters, feet and inches, and a pose-quality score."
                        else -> "Add a full-body photo, or try the sample, then estimate."
                    }
                    EmptyCard(emptyNote)
                }
            }

            if (!state.working && estimate != null && state.notice != null) {
                Spacer(Modifier.height(12.dp))
                Text(state.notice, style = MmType.note75)
            }

            Spacer(Modifier.height(16.dp))

            // ---- how it works ----------------------------------------------
            HowCard(
                heads = state.heads,
                headCm = state.averageHeadCm,
                estimate = estimate,
                onHeadsChange = onHeadsChange,
                onHeadCmChange = onHeadCmChange,
            )

            Spacer(Modifier.height(16.dp))

            // ---- disclaimer --------------------------------------------------
            Text(
                "Not a medical or official measurement. Results are estimates. Typical error is several " +
                    "centimeters, and it can be larger with a wide-angle lens, a bent pose, or a head length " +
                    "unlike the one you set. Nothing in the photo is sent to a server.",
                style = MmType.note70,
            )
        }
    }
}

/* ------------------------------------------------------------------ */
/* Result states                                                       */
/* ------------------------------------------------------------------ */

@Composable
private fun BusyCard() {
    MmCard(Modifier.fillMaxWidth()) {
        Text("Reading the pose", style = MmType.panelTitle)
        Spacer(Modifier.height(8.dp))
        Text(
            "Finding the crown-to-feet span. The first run also finishes loading the pose model.",
            style = MmType.note75,
        )
    }
}

@Composable
private fun EmptyCard(note: String) {
    MmCard(Modifier.fillMaxWidth()) {
        Text("Estimated height", style = MmType.eyebrow)
        Spacer(Modifier.height(4.dp))
        Text("—", style = MmType.bigNumber.copy(color = Mm.Ink30))
        Spacer(Modifier.height(12.dp))
        Text(note, style = MmType.note75)
    }
}

@Composable
private fun ResultCard(estimate: HeightEstimate, state: MeasureMeViewModel.UiState) {
    val total = estimate.confidence.total
    val weakFallback = estimate.reason == "no-head-scale" || state.landmarks?.headMethod == "manual-prior"
    MmCard(Modifier.fillMaxWidth()) {
        Text("Estimated height", style = MmType.eyebrow)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(estimate.heightCmRounded.toString(), style = MmType.bigNumber)
            Spacer(Modifier.width(8.dp))
            Text("cm", style = MmType.unit, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(estimate.heightFtInText, style = MmType.imperial)

        Spacer(Modifier.height(20.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(confidenceLabel(total, weakFallback), style = MmType.qualityLabel)
            Text("Pose quality $total / 100", style = MmType.qualityScore)
        }
        Spacer(Modifier.height(8.dp))
        MmMeter(fraction = total / 100f)
        Spacer(Modifier.height(8.dp))
        Text(
            "This scores the pose — standing straight, full body in frame, camera roughly level. " +
                "It is not an accuracy percentage, and it never means certain. Typical error is several centimeters.",
            style = MmType.note70,
        )

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(Modifier.weight(1f), "Body span", estimate.personPx?.let { "${it.roundToInt()} px" } ?: "—")
            Tile(Modifier.weight(1f), "Head length", estimate.headPx?.let { "${it.roundToInt()} px" } ?: "—")
        }

        Spacer(Modifier.height(16.dp))
        Text(statureLine(estimate, state.heads), style = MmType.body14)

        Spacer(Modifier.height(12.dp))
        MmWell(Modifier.fillMaxWidth()) {
            Text(estimate.formulaText.replace(" * ", " × "), style = MmType.formulaSym)
            Spacer(Modifier.height(4.dp))
            Text(substituted(estimate, state.averageHeadCm, state.heads), style = MmType.formulaSub)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            explanation(estimate, state.landmarks?.headMethod, state.averageHeadCm, state.heads),
            style = MmType.note80,
        )

        if (estimate.notes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MmBullets(estimate.notes.map { it.text })
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, label: String, value: String) {
    MmWell(modifier) {
        Text(label, style = MmType.tileLabel)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MmType.tileValue)
    }
}

@Composable
private fun FailureCard(
    failure: MeasureMeViewModel.FailureInfo,
    manualInput: Boolean,
    onEnterManual: () -> Unit,
) {
    MmCard(Modifier.fillMaxWidth()) {
        Row {
            Icon(
                MmIcons.AlertTriangle,
                contentDescription = null,
                tint = Mm.Primary,
                modifier = Modifier.padding(top = 4.dp).size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Couldn't measure that photo", style = MmType.panelTitle)
                Spacer(Modifier.height(8.dp))
                Text(failure.message.ifBlank { failure.title }, style = MmType.body14)
            }
        }
        Spacer(Modifier.height(16.dp))
        MmBullets(failure.tips)
        if (failure.canManual && !manualInput) {
            Spacer(Modifier.height(16.dp))
            MmButton(label = "Mark points yourself", variant = MmButtonVariant.Secondary, onClick = onEnterManual)
        }
    }
}

/* ------------------------------------------------------------------ */
/* How-card (formula, sliders, bullets, debug wells)                   */
/* ------------------------------------------------------------------ */

@Composable
private fun HowCard(
    heads: Double,
    headCm: Double,
    estimate: HeightEstimate?,
    onHeadsChange: (Double) -> Unit,
    onHeadCmChange: (Double) -> Unit,
) {
    MmCard(Modifier.fillMaxWidth()) {
        Text("How the estimate is calculated", style = MmType.panelTitle)
        Spacer(Modifier.height(12.dp))
        Text("height_cm = (person_pixel_height / head_pixel_height) × average_head_cm", style = MmType.summary)
        Spacer(Modifier.height(8.dp))
        Text(
            "Standing height starts from the constant ${trimNum(heads)} × head length. Pixel stature is the " +
                "vertical distance from the crown to the lowest foot point. Head length defaults to " +
                "${trimNum(headCm)} cm. Both constants are yours to tune. The on-device pose model's 3D " +
                "coordinates are not metric, so those units are never treated as centimeters.",
            style = MmType.note80,
        )

        Spacer(Modifier.height(20.dp))

        SliderRow(
            label = "Canonical stature",
            valueLabel = "${trimNum(heads)} × head",
            value = heads.toFloat(),
            range = AppConstants.HEADS_MIN.toFloat()..AppConstants.HEADS_MAX.toFloat(),
            steps = 24,
            onChange = { onHeadsChange(it.toDouble()) },
        )
        Spacer(Modifier.height(16.dp))
        SliderRow(
            label = "Average head length",
            valueLabel = "${trimNum(headCm)} cm",
            value = headCm.toFloat(),
            range = AppConstants.HEAD_CM_MIN.toFloat()..AppConstants.HEAD_CM_MAX.toFloat(),
            steps = 31,
            onChange = { onHeadCmChange(it.toDouble()) },
        )

        Spacer(Modifier.height(16.dp))

        MmBullets(
            listOf(
                "Eyes sit at ${(AppConstants.FACE_DEPTH_EYES * 100).roundToInt()}% of head height, the nose tip " +
                    "at ${(AppConstants.FACE_DEPTH_NOSE * 100).roundToInt()}%, the mouth at " +
                    "${(AppConstants.FACE_DEPTH_MOUTH * 100).roundToInt()}% — head length is fitted to those rows.",
                "If the face fit isn't reliable, head pixels = eye-to-eye distance ÷ 0.28.",
                "If the face can't be measured at all, head pixels = ear-to-ear width ÷ 0.64.",
                "If the head still can't be measured, head pixels = body pixels ÷ ${trimNum(heads)}, and " +
                    "confidence drops. That fallback mostly repeats the average.",
                "A tilted camera is measured from the feet line and corrected before measuring; a large tilt " +
                    "lowers confidence instead.",
                "Tuned for a standing adult. Children usually need a shorter head length and fewer heads.",
            ),
        )

        Spacer(Modifier.height(16.dp))

        KnownHeightDebug(estimate, onHeadCmChange)
    }
}

@Composable
private fun SliderRow(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    onChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(label, style = MmType.body14)
            Text(valueLabel, style = MmType.tileValue)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            steps = steps,
            colors = SliderDefaults.colors(
                thumbColor = Mm.Primary,
                activeTrackColor = Mm.Primary,
                inactiveTrackColor = Mm.Ink10,
            ),
        )
    }
}

@Composable
private fun KnownHeightDebug(
    estimate: HeightEstimate?,
    onHeadCmChange: (Double) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var knownCm by remember { mutableStateOf("") }

    MmWell(Modifier.fillMaxWidth()) {
        DisclosureHeader("Debug: compare with a known height", expanded) { expanded = !expanded }
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Optional. This does not use a reference object, and the estimate above does not change until " +
                    "you apply a tuned head length.",
                style = MmType.note75,
            )
            Spacer(Modifier.height(12.dp))
            Text("Known height (cm)", style = MmType.note70)
            Spacer(Modifier.height(4.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mm.Surface)
                    .border(1.dp, Mm.Ink15, RoundedCornerShape(12.dp)),
            ) {
                BasicTextField(
                    value = knownCm,
                    onValueChange = { knownCm = it },
                    singleLine = true,
                    textStyle = MmType.fieldText,
                    cursorBrush = SolidColor(Mm.Primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                    decorationBox = { inner ->
                        Box {
                            if (knownCm.isEmpty()) {
                                Text("172", style = MmType.fieldText.copy(color = Mm.Ink30))
                            }
                            inner()
                        }
                    },
                )
            }

            val known = knownCm.toDoubleOrNull()
            val knownOk = known != null && known in 80.0..250.0
            val headsTall = estimate?.headsTall
            val impliedHead = if (knownOk && headsTall != null && headsTall > 0.0) known!! / headsTall else null

            if (impliedHead != null) {
                Spacer(Modifier.height(12.dp))
                val diff = estimate!!.heightCm - known!!
                Text(
                    "This estimate is ${trimNum(abs(diff))} cm ${if (diff >= 0) "above" else "below"} the known height.",
                    style = MmType.note75,
                )
                Spacer(Modifier.height(8.dp))
                Text("Implied head length for this photo: ${trimNum(impliedHead)} cm.", style = MmType.note75)
                Spacer(Modifier.height(12.dp))
                val clamped = impliedHead.coerceIn(AppConstants.HEAD_CM_MIN, AppConstants.HEAD_CM_MAX)
                MmButton(
                    label = "Tune head length to ${trimNum(clamped)} cm",
                    variant = MmButtonVariant.Secondary,
                    onClick = { onHeadCmChange((clamped * 2).roundToInt() / 2.0) },
                )
            } else if (knownCm.isNotEmpty() && !knownOk) {
                Spacer(Modifier.height(8.dp))
                Text("Enter a height between 80 and 250 cm.", style = MmType.note70)
            }
        }
    }
}

@Composable
private fun DisclosureHeader(text: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DisclosureTriangle(open)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MmType.summary)
    }
}

@Composable
private fun DisclosureTriangle(open: Boolean) {
    Canvas(Modifier.size(10.dp)) {
        val path = Path()
        if (open) {
            path.moveTo(0f, size.height * 0.2f)
            path.lineTo(size.width, size.height * 0.2f)
            path.lineTo(size.width / 2f, size.height * 0.9f)
        } else {
            path.moveTo(size.width * 0.2f, 0f)
            path.lineTo(size.width * 0.9f, size.height / 2f)
            path.lineTo(size.width * 0.2f, size.height)
        }
        path.close()
        drawPath(path, Mm.Ink70)
    }
}

/* ------------------------------------------------------------------ */
/* Readout helpers (mirroring web/src/lib/measure.js)                  */
/* ------------------------------------------------------------------ */

/** The reference's derived chin: one canonical share of the body below the crown. */
private fun derivedChinPoint(crown: Px, feet: Px, heads: Double): Px = Px(
    crown.x,
    crown.y + ((feet.y - crown.y) / heads.coerceIn(AppConstants.HEADS_MIN, AppConstants.HEADS_MAX)).toFloat(),
)

/** JS trimNum(): round then drop trailing zeros (23.0 → "23", 7.35 → "7.4"). */
private fun trimNum(value: Double, digits: Int = 1): String {
    val factor = 10.0.pow(digits.toDouble())
    val rounded = Math.round(value * factor) / factor
    val text = "%.${digits}f".format(rounded)
    return text.trimEnd('0').trimEnd('.')
}

private fun confidenceLabel(total: Int, weakFallback: Boolean): String = when {
    weakFallback -> "Weak fallback"
    total >= 70 -> "Good pose"
    total >= 45 -> "Fair pose"
    else -> "Rough pose"
}

private fun statureLine(estimate: HeightEstimate, heads: Double): String {
    val headsTall = estimate.headsTall
    return if (headsTall != null) {
        "Measured stature is ${trimNum(headsTall)} heads. The canonical constant is ${trimNum(heads)}."
    } else {
        "No head scale was measured, so stature repeats the canonical ${trimNum(heads)} heads."
    }
}

private fun substituted(estimate: HeightEstimate, headCm: Double, heads: Double): String {
    val personPx = estimate.personPx
    val headPx = estimate.headPx
    return if (personPx != null && headPx != null && headPx > 1f) {
        "(${personPx.roundToInt()} / ${headPx.roundToInt()}) × ${trimNum(headCm)} = ${trimNum(estimate.heightCm)}"
    } else {
        "≈ ${trimNum(heads)} × ${trimNum(headCm)} = ${trimNum(estimate.heightCm)}"
    }
}

/** Method-specific explanation, adapted from the reference to the Android pipeline. */
private fun explanation(estimate: HeightEstimate, headMethod: String?, headCm: Double, heads: Double): String {
    val canonical = trimNum(heads)
    return when {
        headMethod == "manual-taps" ->
            "You marked the crown, the chin, and the feet. Head length is the vertical distance between " +
                "crown and chin. Pose straightness was not scored by the detector."
        headMethod == "manual-prior" ->
            "The chin marker is still one share of the canonical ratio down from the crown (body ÷ $canonical). " +
                "Until you drag it onto the chin, height is $canonical × the average head length " +
                "(${trimNum(headCm)} cm), not a head measured in this photo."
        headMethod == "face-fit" ->
            "Head length is a least-squares fit of the eyes, nose and mouth against their canonical rows: " +
                "eyes at ${(AppConstants.FACE_DEPTH_EYES * 100).roundToInt()}% of head height, the nose tip " +
                "at ${(AppConstants.FACE_DEPTH_NOSE * 100).roundToInt()}%, the mouth at " +
                "${(AppConstants.FACE_DEPTH_MOUTH * 100).roundToInt()}%. The fit also places the crown."
        headMethod == "eye-spacing" ->
            "The full face fit wasn't available, so head length is the eye-to-eye distance ÷ 0.28. That " +
                "spacing is only a rough adult canon — a weaker read than the face fit."
        estimate.reason == "no-head-scale" ->
            "Head length could not be measured from this photo, so the estimate uses the canonical fallback: " +
                "stature = $canonical × head length. The number is mostly $canonical × ${trimNum(headCm)} cm. " +
                "It is a weak estimate, and confidence is capped."
        estimate.usedFallback ->
            "Head length comes from the ear-to-ear width ÷ 0.64 — adult ears span roughly 64% of head " +
                "height. That ignores the face entirely, so confidence stays lower."
        else ->
            "Head length defaults to the average (${trimNum(headCm)} cm) — no face scale was measurable in " +
                "this photo."
    }
}
