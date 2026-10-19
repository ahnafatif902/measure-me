package com.measureme.app

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.measureme.app.capture.ImageCaptureModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MeasureMeViewModel — the state machine behind Home → Capture → Processing
 * → Result (PRD §5). The UI is a pure function of [UiState]; there is no
 * navigation library, the state IS the navigation.
 *
 * Photo intake is real (camera / photo picker / the bundled sample). The
 * measuring backend is stubbed for now: [measure] walks the three real pipeline
 * stages as a timer and [stubResult] returns the UI Spec's placeholder values,
 * so the whole flow runs end-to-end while the geometry lands.
 */
class MeasureMeViewModel(app: Application) : AndroidViewModel(app) {

    enum class Screen { HOME, CAPTURE, CAMERA, RESULT }

    /** The three measuring modes (PRD §4). Default: Proportions. */
    enum class Mode(val label: String) {
        PROPORTIONS("Proportions"),
        HORIZON("Horizon"),
        REFERENCE("Reference"),
    }

    /**
     * Stubbed result — mirrors the pipeline's HeightEstimate + Report shape.
     * Values are the UI Spec §11 placeholders (167 cm, 161–173, 92/100, 7.3 heads).
     */
    data class Result(
        val heightCm: Int,
        val rangeLowCm: Int,
        val rangeHighCm: Int,
        val quality: Int,
        val qualityLabel: String,
        val headsTall: Double,
        val personPx: Int,
        val headPx: Int,
        val ftInText: String,
    )

    data class UiState(
        val screen: Screen = Screen.HOME,
        val mode: Mode = Mode.PROPORTIONS,
        val photo: Bitmap? = null,
        /** A photo is being decoded (camera screen shows its own busy overlay). */
        val loading: Boolean = false,
        /** The capture screen is showing the processing view. */
        val processing: Boolean = false,
        /** Completed pipeline stages, 0..3 — drives the progress bar and step rows. */
        val step: Int = 0,
        val result: Result? = null,
        /** One specific, fixable reason when a photo cannot be used (FR-2). */
        val failReason: String? = null,
    )

    private val prefs = app.getSharedPreferences("measureme", Context.MODE_PRIVATE)
    private val capture = ImageCaptureModule(app)
    private var measureJob: Job? = null

    private val _ui = MutableStateFlow(UiState(mode = loadMode()))
    val ui: StateFlow<UiState> = _ui

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /** Mode selection persists across launches (PRD §5: stores last-used mode). */
    fun selectMode(mode: Mode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _ui.update { if (it.processing) it else it.copy(mode = mode) }
    }

    fun continueToCapture() = _ui.update {
        if (it.processing) it else it.copy(screen = Screen.CAPTURE, failReason = null)
    }

    fun backToHome() = _ui.update { if (it.processing) it else it.copy(screen = Screen.HOME) }

    fun goCamera() = _ui.update {
        if (it.processing) it else it.copy(screen = Screen.CAMERA, failReason = null)
    }

    fun cameraBack() = _ui.update { if (it.processing) it else it.copy(screen = Screen.CAPTURE) }

    /** Result → Capture with the photo kept, ready for another run. */
    fun measureAgain() = _ui.update {
        it.copy(screen = Screen.CAPTURE, result = null, step = 0)
    }

    // ------------------------------------------------------------------
    // Photo intake (real, not stubbed)
    // ------------------------------------------------------------------

    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch { load { capture.decodeUri(uri) } }
    }

    fun onPhotoCaptured(proxy: ImageProxy) {
        viewModelScope.launch {
            try {
                load { capture.fromImageProxy(proxy) }
            } finally {
                proxy.close()
            }
        }
    }

    /** "Use a sample": the app's own bundled photo. */
    fun useSamplePhoto() {
        viewModelScope.launch { load { capture.decodeSample() } }
    }

    private suspend fun load(decode: () -> Bitmap) {
        _ui.update { it.copy(loading = true, failReason = null) }
        try {
            val bmp = withContext(Dispatchers.IO) { decode() }
            _ui.update {
                it.copy(
                    photo = bmp, loading = false, processing = false, step = 0,
                    result = null, screen = Screen.CAPTURE,
                )
            }
        } catch (c: kotlinx.coroutines.CancellationException) {
            throw c // never swallow cancellation
        } catch (t: Throwable) {
            Log.w(TAG, "Photo intake failed", t)
            // FR-2: one specific, fixable reason (spec §7 error copy rules).
            _ui.update {
                it.copy(
                    loading = false,
                    failReason = "That photo could not be read. Try another one.",
                    screen = Screen.CAPTURE,
                )
            }
        }
    }

    // ------------------------------------------------------------------
    // Processing — the three PRD stages, currently a staged timer
    // ------------------------------------------------------------------

    fun measure() {
        if (_ui.value.photo == null || _ui.value.processing) return
        measureJob?.cancel()
        measureJob = viewModelScope.launch {
            _ui.update { it.copy(processing = true, step = 0, failReason = null, result = null) }

            // PRD §5: find person → locate head and feet → work out the scale,
            // under 3 s on a mid-range phone. The real pipeline replaces the
            // delays stage by stage (UI Spec §8: no fake progress).
            listOf(650L, 650L, 700L).forEachIndexed { index, ms ->
                delay(ms)
                _ui.update { it.copy(step = index + 1) }
            }
            delay(200)

            // ------------------------------------------------------------------
            // TODO: Wire up GeometryEngine — replace stubResult() with the real
            // pipeline: detector.detect(photo) → geometry.derive(...) →
            // geometry.estimate(...) → confidence.compute(...). The quality gate
            // (FR-2) and its one-line failure reasons belong here too.
            // ------------------------------------------------------------------
            val result = stubResult()
            _ui.update { it.copy(result = result, processing = false, screen = Screen.RESULT) }
        }
    }

    /** UI Spec §11 placeholders — replaced when the pipeline is wired up. */
    private fun stubResult(): Result = Result(
        heightCm = 167,
        rangeLowCm = 161,
        rangeHighCm = 173,
        quality = 92,
        qualityLabel = "Clean shot",
        headsTall = 7.3,
        personPx = 1400,
        headPx = 193,
        ftInText = "5 ft 6 in",
    )

    private fun loadMode(): Mode = runCatching {
        Mode.valueOf(prefs.getString(KEY_MODE, Mode.PROPORTIONS.name)!!)
    }.getOrDefault(Mode.PROPORTIONS)

    // ------------------------------------------------------------------
    // DEBUG-ONLY entry points for automated on-device verification:
    //
    //   adb shell am start -n com.measureme.app/.MainActivity \
    //       --es debug_screen result
    //   adb shell am start -n com.measureme.app/.MainActivity \
    //       --es debug_photo /sdcard/Android/data/com.measureme.app/files/test.jpg
    // ------------------------------------------------------------------

    fun debugJump(target: String) {
        when (target.lowercase()) {
            "home" -> _ui.update { it.copy(screen = Screen.HOME) }
            "capture" -> _ui.update { it.copy(screen = Screen.CAPTURE, processing = false) }
            "result" -> _ui.update {
                it.copy(result = stubResult(), processing = false, step = 3, screen = Screen.RESULT)
            }
        }
    }

    fun debugPhoto(path: String) {
        Log.d(TAG, "debugPhoto: $path")
        viewModelScope.launch {
            load {
                if (path == "sample") capture.decodeSample()
                else capture.decodeFile(java.io.File(path))
            }
            if (_ui.value.photo != null) measure()
        }
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val TAG = "MeasureMe"
    }
}
