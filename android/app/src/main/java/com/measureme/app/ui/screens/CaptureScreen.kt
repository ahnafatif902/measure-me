package com.measureme.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.ui.components.CheckListRow
import com.measureme.app.ui.components.FailureBanner
import com.measureme.app.ui.components.MmGhostButton
import com.measureme.app.ui.components.MmPrimaryButton
import com.measureme.app.ui.components.MmSecondaryButton
import com.measureme.app.ui.components.MmTopBar
import com.measureme.app.ui.components.PrivacyLine
import com.measureme.app.ui.components.Viewfinder
import com.measureme.app.ui.icons.MmIcons

/**
 * Capture (UI Spec §6.2): back button (hidden while processing) and the mode
 * name, the viewfinder, then the three-item checklist. Take photo / Choose
 * photo side by side, Use a sample as a ghost button, and Measure my height
 * disabled until a photo exists. On press, the checklist and buttons
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
            onBack = if (state.processing) null else onBack,
        )

        Spacer(Modifier.height(16.dp))

        Viewfinder(photo = state.photo)

        Spacer(Modifier.height(24.dp))

        Crossfade(
            targetState = state.processing,
            animationSpec = tween(220),
            label = "captureMode",
        ) { processing ->
            if (processing) {
                ProcessingScreen(step = state.step)
            } else {
                Column {
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
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
