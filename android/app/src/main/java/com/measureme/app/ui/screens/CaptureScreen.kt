package com.measureme.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.MeasureMeViewModel.Mode
import com.measureme.app.ui.components.CheckListRow
import com.measureme.app.ui.components.FailureBanner
import com.measureme.app.ui.components.MmCard
import com.measureme.app.ui.components.MmGhostButton
import com.measureme.app.ui.components.MmPrimaryButton
import com.measureme.app.ui.components.MmSecondaryButton
import com.measureme.app.ui.components.MmTopBar
import com.measureme.app.ui.components.PrivacyLine
import com.measureme.app.ui.components.Viewfinder
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.MmType
import kotlin.math.roundToInt

/**
 * Capture (UI Spec §6.2): back button and the mode name, the viewfinder, a
 * mode-specific setup block (Mode B: where the phone is held; Mode C: how to
 * place the A4 sheet; Mode A: nothing extra) and the checklist. Take photo /
 * Choose photo side by side, Use a sample as a ghost button, and Measure my
 * height disabled until a photo exists. On press, the checklist and buttons
 * cross-fade into the processing view — same screen, same top bar.
 */
@Composable
fun CaptureScreen(
    state: MeasureMeViewModel.UiState,
    onBack: () -> Unit,
    onTakePhoto: () -> Unit,
    onPhotoPicked: (Uri) -> Unit,
    onSample: () -> Unit,
    onMeasure: () -> Unit,
    onSetCameraHeight: (Float) -> Unit,
) {
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPhotoPicked(uri) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        MmTopBar(
            title = state.mode.label,
            onBack = onBack,
        )

        Spacer(Modifier.height(16.dp))

        Viewfinder(photo = state.photo)

        Spacer(Modifier.height(24.dp))

        Column {
            // ---- mode-specific setup ------------------------------------
            when (state.mode) {
                Mode.PROPORTIONS -> Unit // the checklist below is all Mode A needs
                Mode.HORIZON -> CameraHeightSetup(
                    cameraHeightCm = state.cameraHeightCm,
                    onSetCameraHeight = onSetCameraHeight,
                )
                Mode.REFERENCE -> A4Setup()
            }

            if (state.mode != Mode.PROPORTIONS) Spacer(Modifier.height(24.dp))

            // ---- checklist -------------------------------------------------
            when (state.mode) {
                Mode.PROPORTIONS -> {
                    CheckListRow(
                        title = "Head to toe in the frame",
                        detail = "Face the camera and stand tall. Leave a hand's width above and below.",
                    )
                    Spacer(Modifier.height(14.dp))
                    CheckListRow(
                        title = "Phone level, around chest height",
                        detail = "Hold it upright and still.",
                    )
                    Spacer(Modifier.height(14.dp))
                    CheckListRow(
                        title = "Even, bright light",
                        detail = "Face the light, avoid a window behind you.",
                    )
                }
                Mode.HORIZON -> {
                    CheckListRow(
                        title = "Hold the phone level and still",
                        detail = "The tilt is read from the phone at the moment you shoot.",
                    )
                    Spacer(Modifier.height(14.dp))
                    CheckListRow(
                        title = "Head to toe in the frame",
                        detail = "Leave a hand's width above and below.",
                    )
                    Spacer(Modifier.height(14.dp))
                    CheckListRow(
                        title = "Even, bright light",
                        detail = "Face the light, avoid a window behind you.",
                    )
                }
                Mode.REFERENCE -> {
                    CheckListRow(
                        title = "A4 sheet flat on the floor",
                        detail = "Beside your feet. All four corners visible.",
                    )
                    Spacer(Modifier.height(14.dp))
                    CheckListRow(
                        title = "Head to toe in the frame",
                        detail = "Your whole body and the sheet together.",
                    )
                    Spacer(Modifier.height(14.dp))
                    CheckListRow(
                        title = "Even, bright light",
                        detail = "Face the light, avoid a window behind you.",
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(Modifier.fillMaxWidth()) {
                MmSecondaryButton(
                    label = "Take photo",
                    icon = MmIcons.Camera,
                    onClick = onTakePhoto,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                MmSecondaryButton(
                    label = "Choose photo",
                    icon = MmIcons.ImagePlus,
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(6.dp))

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                MmGhostButton(label = "Use a sample", onClick = onSample)
            }

            if (state.failReason != null) {
                Spacer(Modifier.height(12.dp))
                FailureBanner(reason = state.failReason)
            }

            Spacer(Modifier.height(16.dp))

            MmPrimaryButton(
                label = "Measure my height",
                onClick = onMeasure,
                enabled = state.photo != null,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(16.dp))

            PrivacyLine()
        }

        Spacer(Modifier.height(28.dp))
    }
}

/**
 * Mode B: the horizon math needs the height the phone is held at. Presets
 * from the brief (tripod 150 / hand-held 135 / chair 90) plus a custom value.
 */
@Composable
private fun CameraHeightSetup(cameraHeightCm: Float, onSetCameraHeight: (Float) -> Unit) {
    var custom by remember { mutableStateOf(cameraHeightCm !in listOf(150f, 135f, 90f)) }
    var customText by remember { mutableStateOf(cameraHeightCm.roundToInt().toString()) }

    Text("Phone height when you shoot", style = MmType.label)
    Spacer(Modifier.height(4.dp))
    Text(
        "The horizon math measures everything from where the camera is held.",
        style = MmType.body2,
    )

    Spacer(Modifier.height(12.dp))

    Row(Modifier.fillMaxWidth()) {
        HeightChip(
            label = "Tripod · 150 cm",
            selected = !custom && cameraHeightCm == 150f,
            onClick = { custom = false; onSetCameraHeight(150f) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        HeightChip(
            label = "Hand-held · 135 cm",
            selected = !custom && cameraHeightCm == 135f,
            onClick = { custom = false; onSetCameraHeight(135f) },
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(12.dp))
    Row(Modifier.fillMaxWidth()) {
        HeightChip(
            label = "Chair · 90 cm",
            selected = !custom && cameraHeightCm == 90f,
            onClick = { custom = false; onSetCameraHeight(90f) },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        HeightChip(
            label = "Custom",
            selected = custom,
            onClick = {
                customText = cameraHeightCm.roundToInt().toString()
                custom = true
            },
            modifier = Modifier.weight(1f),
        )
    }

    if (custom) {
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = customText,
            onValueChange = { raw ->
                customText = raw.filter { it.isDigit() }.take(3)
                customText.toFloatOrNull()?.let {
                    if (it in MeasureMeViewModel.MIN_CAMERA_HEIGHT_CM..MeasureMeViewModel.MAX_CAMERA_HEIGHT_CM) {
                        onSetCameraHeight(it)
                    }
                }
            },
            label = { Text("Custom height (cm)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun HeightChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        MmPrimaryButton(label = label, onClick = onClick, modifier = modifier)
    } else {
        MmSecondaryButton(label = label, onClick = onClick, modifier = modifier)
    }
}

/** Mode C: the A4 sheet is the ruler — say clearly how to place it. */
@Composable
private fun A4Setup() {
    MmCard(Modifier.fillMaxWidth()) {
        Text("Place a plain A4 sheet on the floor", style = MmType.label)
        Spacer(Modifier.height(8.dp))
        Text(
            "Lay it flat beside your feet — nothing covering it, all four corners " +
                "visible from where the phone will be.",
            style = MmType.body2,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "The sheet pins down the camera's exact position, so the app does not " +
                "need your phone height or the tilt sensor.",
            style = MmType.body2,
        )
    }
}
