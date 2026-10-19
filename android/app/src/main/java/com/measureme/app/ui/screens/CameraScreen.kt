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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.ui.components.MmIconButton
import com.measureme.app.ui.components.MmSecondaryButton
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmShapes
import com.measureme.app.ui.theme.MmType
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * The camera: back camera preview with CameraX, MINIMIZE_LATENCY stills, and
 * the Photo Picker for the gallery. The frame stays completely clean — live
 * feed only, with a mode-specific caption line instead of any drawn overlay.
 */
@Composable
fun CameraScreen(
    mode: MeasureMeViewModel.Mode,
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(16.dp))

        // ---- top bar -------------------------------------------------------
        Row(verticalAlignment = Alignment.CenterVertically) {
            MmIconButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Spacer(Modifier.width(14.dp))
            Column {
                Text("Take a photo", style = MmType.wordmark)
                Text(cameraSubtitle(mode), style = MmType.caption)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- the dark preview frame ---------------------------------------
        Box(
            Modifier
                .fillMaxWidth()
                .shadow(10.dp, MmShapes.Viewfinder, clip = false, ambientColor = Mm.Shadow, spotColor = Mm.Shadow)
                .clip(MmShapes.Viewfinder)
                .background(Mm.Ink),
        ) {
            Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                if (hasCameraPermission && cameraError == null) {
                    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            cameraError ?: "Camera permission needed to take a photo.",
                            style = MmType.body2.copy(color = Mm.Surface),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        if (!hasCameraPermission) {
                            MmSecondaryButton(
                                label = "Allow camera",
                                onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            )
                        }
                    }
                }

                // decoding overlay — shown while the captured photo is read
                if (working) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Mm.Ink.copy(alpha = 0.6f)),
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
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- controls: choose photo · shutter ------------------------------
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            MmSecondaryButton(
                label = "Choose photo",
                icon = MmIcons.ImagePlus,
                enabled = !working,
                onClick = {
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(16.dp))
            ShutterButton(enabled = imageCapture != null && !working, onClick = shutter)
        }

        Spacer(Modifier.height(16.dp))

        Text(
            when (mode) {
                MeasureMeViewModel.Mode.PROPORTIONS ->
                    "Head to feet in frame, a little margin, even light, and hold the phone level."
                MeasureMeViewModel.Mode.HORIZON ->
                    "Hold the phone level at the height you picked — the tilt is read when you shoot."
                MeasureMeViewModel.Mode.REFERENCE ->
                    "Lay an A4 sheet flat beside your feet, all four corners visible, then shoot."
            },
            style = MmType.caption,
        )
    }
}

/** One caption line under "Take a photo", per mode — the only overlay there is. */
private fun cameraSubtitle(mode: MeasureMeViewModel.Mode): String = when (mode) {
    MeasureMeViewModel.Mode.PROPORTIONS -> "Head to feet in frame"
    MeasureMeViewModel.Mode.HORIZON -> "Hold level · full body in frame"
    MeasureMeViewModel.Mode.REFERENCE -> "A4 on the floor · corners visible"
}

@Composable
private fun ShutterButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .alpha(if (enabled) 1f else Mm.DisabledAlpha)
            .clip(CircleShape)
            .background(Mm.Accent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(28.dp).border(3.dp, Mm.OnAccent, CircleShape))
    }
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
