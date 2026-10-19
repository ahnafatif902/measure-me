package com.measureme.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.measureme.app.MeasureMeViewModel.Screen
import com.measureme.app.ui.components.rememberReducedMotion
import com.measureme.app.ui.screens.CameraScreen
import com.measureme.app.ui.screens.CaptureScreen
import com.measureme.app.ui.screens.HomeScreen
import com.measureme.app.ui.screens.ResultScreen
import com.measureme.app.ui.theme.MeasureMeTheme
import com.measureme.app.ui.theme.Mm

/**
 * Single activity. The ViewModel's state machine is the navigation
 * (Home → Capture → Processing → Result); screens switch with the spec's
 * 220 ms fade and 12 px shift, snapping under reduced motion.
 */
class MainActivity : ComponentActivity() {

    private val vm: MeasureMeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DEBUG-ONLY automated test hook (see handleDebugIntent below).
        if (BuildConfig.DEBUG) handleDebugIntent(intent)

        setContent {
            MeasureMeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Mm.Paper) {
                    val state by vm.ui.collectAsState()
                    AppRoot(vm, state)
                }
            }
        }
    }

    /**
     * DEBUG-ONLY: a launch or re-launch with extras drives the app without
     * touch input (used to verify screens on a device over adb):
     *
     *   adb shell am start -n com.measureme.app/.MainActivity --es debug_screen result
     *   adb shell am start -n com.measureme.app/.MainActivity \
     *       --es debug_photo /sdcard/Android/data/com.measureme.app/files/test.jpg
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) handleDebugIntent(intent)
    }

    private fun handleDebugIntent(intent: Intent?) {
        intent ?: return
        val screen = intent.getStringExtra("debug_screen")
        val photo = intent.getStringExtra("debug_photo")
        val mode = intent.getStringExtra("debug_mode")
        val cameraHeight = intent.getFloatExtra("debug_camera_height", Float.NaN)
        val pitch = intent.getFloatExtra("debug_pitch", Float.NaN)
        val roll = intent.getFloatExtra("debug_roll", Float.NaN)
        val focal = intent.getFloatExtra("debug_focal_px", Float.NaN)
        if (screen == null && photo == null && mode == null &&
            cameraHeight.isNaN() && pitch.isNaN() && focal.isNaN()
        ) {
            return
        }
        // A phone sitting locked must still light up and show the app.
        if (Build.VERSION.SDK_INT >= 27) {
            setTurnScreenOn(true)
            setShowWhenLocked(true)
        }
        if (mode != null || !cameraHeight.isNaN() || !pitch.isNaN() || !roll.isNaN() || !focal.isNaN()) {
            vm.debugConfig(
                mode = mode,
                cameraHeightCm = cameraHeight.takeUnless { it.isNaN() },
                pitchDeg = pitch.takeUnless { it.isNaN() },
                rollDeg = roll.takeUnless { it.isNaN() },
                focalPx = focal.takeUnless { it.isNaN() },
            )
        }
        if (screen != null) vm.debugJump(screen)
        if (photo != null) vm.debugPhoto(photo)
    }
}

@Composable
private fun AppRoot(vm: MeasureMeViewModel, state: MeasureMeViewModel.UiState) {
    val reduced = rememberReducedMotion()
    val shift = with(LocalDensity.current) { 12.dp.roundToPx() }

    // System back: Camera → Capture, Capture → Home, Result → Capture.
    // While the engine is running, back does nothing (the work is real).
    BackHandler(enabled = state.screen != Screen.HOME) {
        when {
            state.measure is MeasureMeViewModel.MeasureState.Loading -> Unit
            state.screen == Screen.CAMERA -> vm.cameraBack()
            state.screen == Screen.CAPTURE -> vm.backToHome()
            state.screen == Screen.RESULT -> vm.measureAgain()
        }
    }

    AnimatedContent(
        targetState = state.screen,
        transitionSpec = {
            if (reduced) {
                fadeIn(tween(0)) togetherWith fadeOut(tween(0))
            } else {
                (fadeIn(tween(220)) + slideInVertically(tween(220)) { shift }) togetherWith
                    fadeOut(tween(220))
            }
        },
        label = "screen",
    ) { screen ->
        when (screen) {
            Screen.HOME -> HomeScreen(
                mode = state.mode,
                onSelectMode = vm::selectMode,
                onContinue = vm::continueToCapture,
            )

            Screen.CAPTURE -> CaptureScreen(
                state = state,
                onBack = vm::backToHome,
                onTakePhoto = vm::goCamera,
                onPhotoPicked = vm::onPhotoPicked,
                onSample = vm::useSamplePhoto,
                onMeasure = vm::measure,
                onSetCameraHeight = vm::setCameraHeight,
            )

            Screen.CAMERA -> CameraScreen(
                mode = state.mode,
                working = state.loading,
                onPhotoPicked = vm::onPhotoPicked,
                onPhotoCaptured = vm::onPhotoCaptured,
                onBack = vm::cameraBack,
            )

            Screen.RESULT -> ResultScreen(
                state = state.measure,
                onMeasureAgain = vm::measureAgain,
                onTryAgain = vm::measureAgain,
            )
        }
    }
}
