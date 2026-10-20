package com.measureme.app.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.ui.components.MmGlassSheet
import com.measureme.app.ui.components.MmIconButton
import com.measureme.app.ui.components.MmLevelIndicator
import com.measureme.app.ui.components.MmShutterButton
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmSpace
import com.measureme.app.ui.theme.MmType
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * The camera: full-bleed back-camera preview (CameraX, MINIMIZE_LATENCY stills)
 * with a floating translucent glass sheet at the bottom.
 *
 * The preview carries ZERO drawn overlays — no brackets, no silhouette, no
 * grid. All guidance lives in the glass sheet as exact spec §7 copy.
 */
@Composable
fun CameraScreen(
    mode: MeasureMeViewModel.Mode,
    cameraHeightCm: Float,
    working: Boolean,
    onPhotoPicked: (Uri) -> Unit,
    onPhotoCaptured: (ImageProxy) -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current

    // ---- camera permission ------------------------------------------------
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // ---- gallery picker (Photo Picker: no storage permission needed) ------
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onPhotoPicked(uri) }

    // ---- CameraX wiring ----------------------------------------------------
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission) {
            cameraProvider?.unbindAll()
            return@LaunchedEffect
        }
        try {
            val provider = ProcessCameraProvider.getInstance(context).awaitFuture(context)
            cameraProvider = provider
            val preview = Preview.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture
            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture,
            )
        } catch (t: Throwable) {
            cameraError = "Camera unavailable — you can still choose a photo from the gallery."
        }
    }
    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    val shutter: () -> Unit = {
        val capture = imageCapture
        if (capture != null && !working) {
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) = onPhotoCaptured(image)
                    override fun onError(exception: ImageCaptureException) {
                        cameraError = "Capture failed: ${exception.message}"
                    }
                },
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ---- full-bleed preview -------------------------------------------
        if (hasCameraPermission && cameraError == null) {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            
            ) {
                Text(
                    cameraError ?: "Camera permission needed to take a photo.",
                    style = MmType.body2.copy(color = Mm.Surface),
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(12.dp))
                if (!hasCameraPermission) {
                    TextButton(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                    ) {
                        Text("Allow camera", style = MmType.button.copy(color = Mm.Accent))
                    }
                }
            }
        }

        // ---- floating top bar ---------------------------------------------
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, start = 16.dp, end = 16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),

            ) {
                MmIconButton(
                    icon = MmIcons.ArrowBack,
                    contentDescription = "Back",
                    onClick = onBack,
                )
                Spacer(Modifier.width(MmSpace.SM))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Mm.Surface.copy(alpha = 0.85f))
                        .border(1.dp, Mm.Line, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = mode.label,
                        style = MmType.wordmark.copy(color = Mm.Ink),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // ---- working overlay ----------------------------------------------
        if (working) {
            Box(
                modifier = Modifier.fillMaxSize().background(Mm.Ink.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Mm.Accent,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Reading the photo…", style = MmType.body2.copy(color = Mm.Surface))
                }
            }
        }

        // ---- floating glass bottom sheet ----------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp),
        ) {
            MmGlassSheet {
                Text(
                    text = cameraInstruction(mode),
                    style = MmType.body,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(MmSpace.SM))

                // Mode B height + level indicator
                if (mode == MeasureMeViewModel.Mode.HORIZON) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
        
                    ) {
                        MmLevelIndicator(tilt = null)
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = "Phone held at ${cameraHeightCm.roundToInt()} cm",
                            style = MmType.label.copy(color = Mm.Ink2),
                        )
                    }
                    Spacer(Modifier.height(MmSpace.SM))
                }

                Spacer(modifier = Modifier.height(MmSpace.SM))

                Row(
                    modifier = Modifier.fillMaxWidth(),
    
                ) {
                    MmIconButton(
                        icon = MmIcons.ImagePlus,
                        contentDescription = "Choose photo from gallery",
                        onClick = {
                            galleryLauncher.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly,
                                ),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(MmSpace.SM))
                    MmShutterButton(
                        enabled = imageCapture != null && !working,
                        onClick = shutter,
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Exact spec §7 instruction for each mode, shown in the glass sheet. */
private fun cameraInstruction(mode: MeasureMeViewModel.Mode): String = when (mode) {
    MeasureMeViewModel.Mode.PROPORTIONS,
    MeasureMeViewModel.Mode.HORIZON ->
        "Face the camera and stand tall. Leave a hand's width above and below."
    MeasureMeViewModel.Mode.REFERENCE ->
        "Place the A4 sheet flat beside your feet, all four corners visible."
}

/** ListenableFuture → coroutine, without adding guava-coroutines to the deps. */
private suspend fun <T> ListenableFuture<T>.awaitFuture(context: Context): T =
    suspendCancellableCoroutine { cont ->
        addListener(
            {
                try {
                    cont.resume(get())
                } catch (e: ExecutionException) {
                    cont.resumeWithException(e.cause ?: e)
                } catch (t: Throwable) {
                    cont.resumeWithException(t)
                }
            },
            ContextCompat.getMainExecutor(context),
        )
    }
