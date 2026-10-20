package com.measureme.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.MeasureMeViewModel.Mode
import com.measureme.app.ui.components.CheckListRow
import com.measureme.app.ui.components.FailureBanner
import com.measureme.app.ui.components.MmCard
import com.measureme.app.ui.components.MmChip
import com.measureme.app.ui.components.MmGhostButton
import com.measureme.app.ui.components.MmNumberStepper
import com.measureme.app.ui.components.MmPrimaryButton
import com.measureme.app.ui.components.MmSecondaryButton
import com.measureme.app.ui.components.MmTopBar
import com.measureme.app.ui.components.PrivacyLine
import com.measureme.app.ui.components.Viewfinder
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmSpace
import com.measureme.app.ui.theme.MmType

/**
 * Capture (UI Spec §6.2): back button and the mode name, the viewfinder, a
 * card containing the mode-specific setup and the checklist, then the photo
 * actions. Measure my height is disabled until a photo exists.
 *
 * Safe areas: the root owns the top and horizontal insets, the scroll
 * viewport owns the bottom insets, and content uses the 20 dp screen padding.
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
        ) {
            MmTopBar(
                title = state.mode.label,
                onBack = onBack,
                modifier = Modifier.height(56.dp),
            )

            Spacer(Modifier.height(MmSpace.MD))

            Viewfinder(photo = state.photo)

            Spacer(Modifier.height(MmSpace.MD))

            // ---- capture sheet ---------------------------------------------
            MmCard(modifier = Modifier.fillMaxWidth()) {
                when (state.mode) {
                    Mode.PROPORTIONS -> Unit
                    Mode.HORIZON -> CameraHeightSetup(
                        cameraHeightCm = state.cameraHeightCm,
                        onSetCameraHeight = onSetCameraHeight,
                    )
                    Mode.REFERENCE -> A4Setup()
                }

                if (state.mode != Mode.PROPORTIONS) Spacer(Modifier.height(MmSpace.MD))

                when (state.mode) {
                    Mode.PROPORTIONS -> {
                        CheckListRow(
                            title = "Head to toe in the frame",
                            detail = "Face the camera and stand tall. Leave a hand's width above and below.",
                        )
                        Spacer(Modifier.height(MmSpace.SM))
                        CheckListRow(
                            title = "Phone level, around chest height",
                            detail = "Hold it upright and still.",
                        )
                        Spacer(Modifier.height(MmSpace.SM))
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
                        Spacer(Modifier.height(MmSpace.SM))
                        CheckListRow(
                            title = "Head to toe in the frame",
                            detail = "Leave a hand's width above and below.",
                        )
                        Spacer(Modifier.height(MmSpace.SM))
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
                        Spacer(Modifier.height(MmSpace.SM))
                        CheckListRow(
                            title = "Head to toe in the frame",
                            detail = "Your whole body and the sheet together.",
                        )
                        Spacer(Modifier.height(MmSpace.SM))
                        CheckListRow(
                            title = "Even, bright light",
                            detail = "Face the light, avoid a window behind you.",
                        )
                    }
                }

                Spacer(Modifier.height(MmSpace.MD))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    MmSecondaryButton(
                        label = "Take photo",
                        icon = MmIcons.Camera,
                        onClick = onTakePhoto,
                    )
                    MmSecondaryButton(
                        label = "Choose photo",
                        icon = MmIcons.ImagePlus,
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                }

                Spacer(Modifier.height(MmSpace.XS))

                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    MmGhostButton(label = "Use a sample", onClick = onSample)
                }

                if (state.failReason != null) {
                    Spacer(Modifier.height(MmSpace.SM))
                    FailureBanner(reason = state.failReason)
                }

                Spacer(Modifier.height(MmSpace.SM))

                MmPrimaryButton(
                    label = "Measure my height",
                    onClick = { if (state.photo != null) onMeasure() },
                    enabled = state.photo != null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(MmSpace.LG))

            PrivacyLine()

            Spacer(Modifier.height(MmSpace.LG))
        }
    }
}

/** Mode B: the horizon math needs the height the phone is held at. Presets
 * (tripod 150 / hand-held 135 / chair 90) plus a stepper for custom values. */
@Composable
private fun CameraHeightSetup(cameraHeightCm: Float, onSetCameraHeight: (Float) -> Unit) {
    Text("Phone height when you shoot", style = MmType.label)
    Spacer(Modifier.height(MmSpace.XS))
    Text(
        "The horizon math measures everything from where the camera is held.",
        style = MmType.body2,
    )

    Spacer(Modifier.height(MmSpace.SM))

    MmNumberStepper(
        value = cameraHeightCm,
        onChange = onSetCameraHeight,
    )

    Spacer(Modifier.height(MmSpace.SM))

    Row(modifier = Modifier.fillMaxWidth()) {
        val presets = listOf("Tripod · 150 cm" to 150f, "Hand-held · 135 cm" to 135f, "Chair · 90 cm" to 90f)
        presets.forEachIndexed { index, (label, cm) ->
            MmChip(
                label = label,
                selected = cameraHeightCm == cm,
                onClick = { onSetCameraHeight(cm) },
                modifier = Modifier.weight(1f),
            )
            if (index < presets.lastIndex) Spacer(Modifier.width(MmSpace.XS))
        }
    }
}

/** Mode C: the A4 sheet is the ruler — say clearly how to place it. */
@Composable
private fun A4Setup() {
    Text("Place a plain A4 sheet on the floor", style = MmType.label)
    Spacer(Modifier.height(MmSpace.XS))
    Text(
        "Lay it flat beside your feet — nothing covering it, all four corners " +
            "visible from where the phone will be.",
        style = MmType.body2,
    )
    Spacer(Modifier.height(MmSpace.XS))
    Text(
        "The sheet pins down the camera's exact position, so the app does not " +
            "need your phone height or the tilt sensor.",
        style = MmType.body2,
    )
}
