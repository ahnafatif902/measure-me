package com.measureme.app

import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.measureme.app.MeasureMeViewModel.Screen
import com.measureme.app.ui.screens.CameraScreen
import com.measureme.app.ui.screens.MeasureScreen
import com.measureme.app.ui.theme.MeasureMeTheme
import com.measureme.app.ui.theme.Mm

/**
 * Single activity. Two screens — MEASURE (the whole design page, all states
 * inline) and CAMERA — switched on UiState.screen. No navigation library:
 * the state machine in the ViewModel IS the navigation.
 */
class MainActivity : ComponentActivity() {

    private val vm: MeasureMeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DEBUG-ONLY automated test hook (see handleDebugIntent below).
        if (BuildConfig.DEBUG) handleDebugIntent(intent)

        setContent {
            MeasureMeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Mm.Bg) {
                    val state by vm.ui.collectAsState()
                    AppScaffold(vm, state)
                }
            }
        }
    }

    /**
     * DEBUG-ONLY: a second launch while the app is already running, e.g.
     *
     *   adb shell am start -n com.measureme.app/.MainActivity \
     *       --es debug_photo /sdcard/Android/data/com.measureme.app/files/test.jpg \
     *       --ez debug_manual true --ez debug_marks true
     *
     * triggers the automated pipeline again (used to verify states without
     * touch input). Never active in release.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (BuildConfig.DEBUG) handleDebugIntent(intent)
    }

    /** Loads a photo from a local file path and runs the pipeline. */
    private fun handleDebugIntent(intent: Intent?) {
        intent?.getStringExtra("debug_photo")?.let { path ->
            // Automated verification runs from a locked phone must still
            // light the screen and come to the front. Debug-only by
            // construction (handleDebugIntent never runs in release).
            if (Build.VERSION.SDK_INT >= 27) {
                setTurnScreenOn(true)
                setShowWhenLocked(true)
            }
            vm.loadDebugPhoto(
                path = path,
                manualMode = intent.getBooleanExtra("debug_manual", false),
                marks = intent.getBooleanExtra("debug_marks", false),
            )
        }
    }
}

@Composable
private fun AppScaffold(vm: MeasureMeViewModel, state: MeasureMeViewModel.UiState) {
    // System back: from the camera it returns to the measure page.
    BackHandler(enabled = state.screen == Screen.CAMERA) { vm.goMeasure() }

    when (state.screen) {
        Screen.MEASURE -> MeasureScreen(
            state = state,
            onPhotoPicked = vm::onPhotoPicked,
            onTakePhoto = vm::goCamera,
            onSample = vm::useSamplePhoto,
            onToggleManual = vm::toggleManual,
            onEnterManual = vm::enterManual,
            onEstimate = vm::estimate,
            onManualPlace = vm::manualPlace,
            onManualDrag = vm::manualDrag,
            onHeadCmChange = vm::setAverageHeadCm,
            onHeadsChange = vm::setHeads,
        )

        Screen.CAMERA -> CameraScreen(
            working = state.working || state.decoding,
            onPhotoPicked = vm::onPhotoPicked,
            onPhotoCaptured = vm::onPhotoCaptured,
            onBack = vm::goMeasure,
        )
    }
}
