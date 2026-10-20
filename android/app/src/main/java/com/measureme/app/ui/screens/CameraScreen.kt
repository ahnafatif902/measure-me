package com.measureme.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview as CameraPreviewUseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import com.google.common.util.concurrent.ListenableFuture
import com.measureme.app.MeasureMeViewModel
import com.measureme.app.ui.components.MmGlassSheet
import com.measureme.app.ui.components.MmIconButton
import com.measureme.app.ui.components.MmLevelIndicator
import com.measureme.app.ui.components.MmShutterButton
import com.measureme.app.ui.icons.MmIcons
import com.measureme.app.ui.theme.MeasureMeTheme
import com.measureme.app.ui.theme.Mm
import com.measureme.app.ui.theme.MmSpace
import com.measureme.app.ui.theme.MmType
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ExecutionException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * The camera: a full-bleed CameraX preview with a floating sheet at the
 * bottom. The preview carries no drawn overlays and no inset padding; only
 * the controls know about the safe drawing area.
 *
 * Preview and capture are bound as one [UseCaseGroup] with the preview's
 * [ViewPort], so the still and the viewfinder share one field of view.
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
    val previewView = remember(context) {
        PreviewView(context).apply {
            // TextureView renders correctly inside Compose and under overlays;
            // SurfaceView can appear black when it sits under the app window.
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var capturing by remember { mutableStateOf(false) }

    LaunchedEffect(hasCameraPermission, previewView, lifecycleOwner) {
        if (!hasCameraPermission) {
            cameraProvider?.unbindAll()
            imageCapture = null
            return@LaunchedEffect
        }
        try {
            val provider = ProcessCameraProvider.getInstance(context).awaitFuture(context)
            cameraProvider = provider

            val preview = CameraPreviewUseCase.Builder().build().apply {
                setSurfaceProvider(previewView.surfaceProvider)
            }
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            // One field of view for preview and still. Wait for the PreviewView
            // to be laid out so viewPort is non-null; if it is still null, bind
            // without an explicit viewport rather than failing the camera.
            val viewPort = previewView.awaitViewPort()
            val groupBuilder = UseCaseGroup.Builder()
                .addUseCase(preview)
                .addUseCase(capture)
            if (viewPort != null) groupBuilder.setViewPort(viewPort)

            provider.unbindAll()
            provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                groupBuilder.build(),
            )
            imageCapture = capture
            cameraError = null
            capturing = false
        } catch (t: Throwable) {
            imageCapture = null
            cameraError = "Camera unavailable. You can still choose a photo from the gallery."
        }
    }
    DisposableEffect(Unit) {
        onDispose { cameraProvider?.unbindAll() }
    }

    // ---- dark preview, light sheet: opposite icon appearances -------------
    val view = LocalView.current
    val activity = context as? Activity
    DisposableEffect(activity, view) {
        val window = activity?.window
        if (window == null) {
            onDispose { }
        } else {
            val controller = WindowCompat.getInsetsController(window, view)
            val previousStatus = controller.isAppearanceLightStatusBars
            val previousNavigation = controller.isAppearanceLightNavigationBars
            // 3-button navigation draws a contrast scrim over the sheet on some
            // OEM builds, leaving a white strip under the bottom sheet. The
            // sheet is a light, opaque fill, so the scrim is not needed here.
            val previousContrast =
                if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced else null
            // Some 3-button bars are opaque and ignore the transparent window
            // colour, so tint the bar to the sheet fill. It reads as one
            // continuous sheet, and the dark icons stay legible.
            @Suppress("DEPRECATION")
            val previousNavigationColor = window.navigationBarColor
            controller.isAppearanceLightStatusBars = false
            controller.isAppearanceLightNavigationBars = true
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            @Suppress("DEPRECATION")
            window.navigationBarColor = Mm.Sheet.toArgb()
            onDispose {
                controller.isAppearanceLightStatusBars = previousStatus
                controller.isAppearanceLightNavigationBars = previousNavigation
                if (Build.VERSION.SDK_INT >= 29 && previousContrast != null) {
                    window.isNavigationBarContrastEnforced = previousContrast
                }
                @Suppress("DEPRECATION")
                window.navigationBarColor = previousNavigationColor
            }
        }
    }

    val shutter: () -> Unit = {
        val capture = imageCapture
        if (capture != null && !working && !capturing) {
            capturing = true
            capture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        if (image.width <= 0 || image.height <= 0) {
                            image.close()
                            capturing = false
                            cameraError = "The camera returned an empty photo. Try again."
                            return
                        }
                        onPhotoCaptured(image)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        capturing = false
                        cameraError = "Capture failed. Try again or choose a photo."
                    }
                },
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        CameraPreview(previewView = previewView, modifier = Modifier.fillMaxSize())

        CameraOverlay(
            mode = mode,
            cameraHeightCm = cameraHeightCm,
            working = working,
            hasCameraPermission = hasCameraPermission,
            cameraError = cameraError,
            shutterEnabled = imageCapture != null && hasCameraPermission && !working && !capturing,
            onBack = onBack,
            onPick = {
                galleryLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onShutter = shutter,
            onAllowCamera = { permissionLauncher.launch(Manifest.permission.CAMERA) },
        )
    }
}

/** Full-bleed preview. No padding, no insets, no overlays. */
@Composable
private fun CameraPreview(previewView: PreviewView, modifier: Modifier = Modifier) {
    AndroidView(
        factory = { previewView },
        modifier = modifier,
    )
}

/** All camera UI that floats above the preview. Extracted so it can be
 *  previewed without CameraX. */
@Composable
private fun CameraOverlay(
    mode: MeasureMeViewModel.Mode,
    cameraHeightCm: Float,
    working: Boolean,
    hasCameraPermission: Boolean,
    cameraError: String?,
    shutterEnabled: Boolean,
    onBack: () -> Unit,
    onPick: () -> Unit,
    onShutter: () -> Unit,
    onAllowCamera: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        if (cameraError != null || !hasCameraPermission) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
                    .padding(bottom = 96.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    cameraError ?: "Camera access is off. Choose a photo or allow the camera.",
                    style = MmType.body.copy(color = Mm.Surface),
                    textAlign = TextAlign.Center,
                )
                if (!hasCameraPermission) {
                    Spacer(Modifier.height(MmSpace.Gap))
                    TextButton(onClick = onAllowCamera) {
                        Text("Allow camera", style = MmType.button.copy(color = Mm.Accent))
                    }
                }
            }
        }

        // ---- top controls: safe area + 16/12 content padding ---------------
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                    ),
                )
                .padding(horizontal = MmSpace.SM, vertical = MmSpace.Gap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MmIconButton(
                icon = MmIcons.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
                size = 48.dp,
            )
            Spacer(Modifier.width(MmSpace.XS))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Mm.Surface.copy(alpha = 0.85f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(
                    text = mode.label,
                    style = MmType.wordmark.copy(color = Mm.Ink),
                )
            }
            Spacer(Modifier.weight(1f))
        }

        // ---- bottom sheet: background BEFORE bottom inset padding ----------
        MmGlassSheet(modifier = Modifier.align(Alignment.BottomCenter)) {
            Text(
                text = cameraInstruction(mode),
                style = MmType.body,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(MmSpace.SM))

            if (mode == MeasureMeViewModel.Mode.HORIZON) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    MmLevelIndicator(tilt = null)
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "Phone held at ${cameraHeightCm.roundToInt()} cm",
                        style = MmType.label.copy(color = Mm.Ink2),
                    )
                }
                Spacer(Modifier.height(MmSpace.SM))
            }

            // Three equal slots keep the 72 dp shutter exactly centred.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    MmIconButton(
                        icon = MmIcons.ImagePlus,
                        contentDescription = "Choose photo",
                        onClick = onPick,
                        size = 48.dp,
                    )
                }
                MmShutterButton(
                    enabled = shutterEnabled,
                    onClick = onShutter,
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    Spacer(Modifier.size(48.dp))
                }
            }
        }

        // ---- working scrim covers everything ---------------------------------
        if (working) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Mm.Ink.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Mm.Accent,
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                    )
                    Spacer(Modifier.height(MmSpace.Gap))
                    Text("Reading the photo…", style = MmType.body2.copy(color = Mm.Surface))
                }
            }
        }
    }
}

/** Exact spec §7 instruction for each mode, shown in the sheet. */
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

/** Wait one layout pass for the PreviewView, then read its viewport. */
private suspend fun PreviewView.awaitViewPort(): ViewPort? =
    suspendCancellableCoroutine { cont ->
        doOnLayout {
            if (cont.isActive) cont.resume(viewPort)
        }
    }

@Preview(
    showSystemUi = true,
    name = "Camera overlay",
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun CameraOverlayPreview() {
    MeasureMeTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1C1C1E)),
        ) {
            CameraOverlay(
                mode = MeasureMeViewModel.Mode.REFERENCE,
                cameraHeightCm = 135f,
                working = false,
                hasCameraPermission = true,
                cameraError = null,
                shutterEnabled = true,
                onBack = {},
                onPick = {},
                onShutter = {},
                onAllowCamera = {},
            )
        }
    }
}
