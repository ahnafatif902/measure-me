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
import com.measureme.app.detect.PoseAnalyzer
import com.measureme.app.detect.SensorReader
import com.measureme.app.pipeline.GeometryEngine
import com.measureme.app.pipeline.GeometryInput
import com.measureme.app.pipeline.Intrinsics
import com.measureme.app.pipeline.Tilt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * MeasureMeViewModel — the state machine behind Home → Capture → Result
 * (PRD §5). The UI is a pure function of [UiState]; there is no navigation
 * library, the state IS the navigation.
 *
 * Photo intake is real (camera / photo picker / the bundled sample) and the
 * measuring is real too: [analyzeImage] hands the photo to [PoseAnalyzer],
 * which runs MediaPipe's Pose Landmarker and the Mode A (Proportions)
 * geometry. There is no stub data left anywhere in the app.
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
     * The measurement outcome. The Result screen renders exactly this:
     * Loading → the processing animation, Success → the number,
     * Error → a warning card with a way out.
     */
    sealed interface MeasureState {
        /** Nothing measured yet. */
        data object Idle : MeasureState

        /** The engine is running; [step] is 0..3 real pipeline stages done. */
        data class Loading(val step: Int = 0) : MeasureState

        /** A measurement — every field comes from the photo. */
        data class Success(val result: Result) : MeasureState

        /** The photo could not be measured; [message] says why. */
        data class Error(val message: String) : MeasureState
    }

    /** What the Result screen shows after a successful measurement. */
    data class Result(
        val heightCm: Int,
        /** Authentic run metadata: the model that ran + its error margin. */
        val metadataString: String = "Proportions Model · Margin ±5 cm",
        val quality: Int,
        val qualityLabel: String,
        val headsTall: Double,
        val personPx: Int,
        val headPx: Int,
        /** Set when Mode B/C fell back to Proportions — cause-specific. */
        val warning: String? = null,
    )

    data class UiState(
        val screen: Screen = Screen.HOME,
        val mode: Mode = Mode.PROPORTIONS,
        val photo: Bitmap? = null,
        /** A photo is being decoded (the camera screen shows its own overlay). */
        val loading: Boolean = false,
        /** The measurement outcome — drives the whole Result screen. */
        val measure: MeasureState = MeasureState.Idle,
        /** Set when the photo itself could not be read. */
        val failReason: String? = null,
        /** Height the phone is held at for Mode B, in cm (persisted). */
        val cameraHeightCm: Float = 135f,
        /** Profile hints — stored for a future anthropometric pass, math-inactive today. */
        val sex: String? = null,
        val age: Int? = null,
    )

    private val prefs = app.getSharedPreferences("measureme", Context.MODE_PRIVATE)
    private val capture = ImageCaptureModule(app)
    private val analyzer = PoseAnalyzer(app)
    private val sensors = SensorReader(app)
    private var analyzeJob: Job? = null

    /** Shutter-time context for Modes B/C; refreshed by every photo intake. */
    private var captureTilt: Tilt? = null
    private var captureIntrinsics: Intrinsics? = null

    /** Debug-only overrides (see debugConfig) — never set in normal use. */
    private var debugPitchDeg: Float? = null
    private var debugRollDeg: Float = 0f
    private var debugFocalPx: Float? = null

    private val _ui = MutableStateFlow(
        UiState(
            mode = loadMode(),
            cameraHeightCm = loadCameraHeight(),
            sex = prefs.getString(KEY_SEX, null),
            age = prefs.getInt(KEY_AGE, 0).takeIf { it > 0 },
        ),
    )
    val ui: StateFlow<UiState> = _ui

    // ------------------------------------------------------------------
    // Navigation
    // ------------------------------------------------------------------

    /** Mode selection persists across launches (PRD §5: stores last-used mode). */
    fun selectMode(mode: Mode) {
        prefs.edit().putString(KEY_MODE, mode.name).apply()
        _ui.update { if (isLoading) it else it.copy(mode = mode) }
    }

    /** Camera height for Mode B — persisted like the mode, bounded to sane values. */
    fun setCameraHeight(cm: Float) {
        val bounded = cm.coerceIn(MIN_CAMERA_HEIGHT_CM, MAX_CAMERA_HEIGHT_CM)
        prefs.edit().putFloat(KEY_CAMERA_HEIGHT, bounded).apply()
        _ui.update { if (isLoading) it else it.copy(cameraHeightCm = bounded) }
    }

    /** Stored profile hints; not used by today's math (approved scope). */
    fun setSex(sex: String?) {
        prefs.edit().putString(KEY_SEX, sex).apply()
        _ui.update { if (isLoading) it else it.copy(sex = sex) }
    }

    fun setAge(age: Int?) {
        val bounded = age?.coerceIn(1, 120)
        prefs.edit().putInt(KEY_AGE, bounded ?: 0).apply()
        _ui.update { if (isLoading) it else it.copy(age = bounded) }
    }

    fun continueToCapture() = _ui.update {
        if (isLoading) it else it.copy(screen = Screen.CAPTURE, failReason = null)
    }

    fun backToHome() = _ui.update { if (isLoading) it else it.copy(screen = Screen.HOME) }

    fun goCamera() {
        sensors.start() // Mode B needs the tilt at the shutter
        _ui.update { if (isLoading) it else it.copy(screen = Screen.CAMERA, failReason = null) }
    }

    fun cameraBack() {
        sensors.stop()
        _ui.update { if (isLoading) it else it.copy(screen = Screen.CAPTURE) }
    }

    /** Result → Capture with the photo kept, ready for another run. */
    fun measureAgain() = _ui.update {
        if (isLoading) it
        else it.copy(screen = Screen.CAPTURE, measure = MeasureState.Idle, failReason = null)
    }

    private val isLoading: Boolean
        get() = _ui.value.measure is MeasureState.Loading

    /** The last-used mode, restored on launch (PRD §5). */
    private fun loadMode(): Mode = runCatching {
        Mode.valueOf(prefs.getString(KEY_MODE, Mode.PROPORTIONS.name)!!)
    }.getOrDefault(Mode.PROPORTIONS)

    private fun loadCameraHeight(): Float =
        prefs.getFloat(KEY_CAMERA_HEIGHT, DEFAULT_CAMERA_HEIGHT_CM)
            .coerceIn(MIN_CAMERA_HEIGHT_CM, MAX_CAMERA_HEIGHT_CM)

    // ------------------------------------------------------------------
    // Photo intake (real)
    // ------------------------------------------------------------------

    fun onPhotoPicked(uri: Uri) {
        sensors.stop()
        viewModelScope.launch {
            load {
                captureTilt = null // a gallery photo carries no live shutter tilt
                capture.decodeUri(uri)
            }
        }
    }

    fun onPhotoCaptured(proxy: ImageProxy) {
        // Snapshot gravity AT THE SHUTTER, before the JPEG decode takes time.
        val shutterTilt = sensors.snapshot()
        sensors.stop()
        Log.d(TAG, "shutter: tilt=$shutterTilt")
        viewModelScope.launch {
            try {
                load {
                    captureTilt = shutterTilt
                    capture.fromImageProxy(proxy)
                }
            } finally {
                proxy.close()
            }
        }
    }

    /** "Use a sample": the app's own bundled photo. */
    fun useSamplePhoto() {
        sensors.stop()
        viewModelScope.launch {
            load {
                captureTilt = null
                capture.decodeSample()
            }
        }
    }

    private suspend fun load(decode: () -> ImageCaptureModule.DecodedPhoto) {
        _ui.update { it.copy(loading = true, failReason = null) }
        try {
            val decoded = withContext(Dispatchers.IO) { decode() }

            // Intrinsics for the ANALYZED bitmap: both f_px and width scale by
            // the same downscale factor, so f_px = f35/36 · analyzedWidth.
            captureIntrinsics = decoded.focalLength35mmMm?.let {
                Intrinsics.fromExif35mm(it, decoded.bitmap.width, decoded.bitmap.height)
            }

            _ui.update {
                it.copy(
                    photo = decoded.bitmap, loading = false, measure = MeasureState.Idle,
                    screen = Screen.CAPTURE,
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
    // Measurement — the real engine
    // ------------------------------------------------------------------

    /** Measure the photo currently on screen. */
    fun measure() {
        val photo = _ui.value.photo ?: return
        analyzeImage(photo)
    }

    /**
     * Run the real measurement on [bitmap].
     *
     * The screen moves to Result immediately and the Result screen renders
     * the processing animation while the engine works; it then shows either
     * the number or a specific reason the photo could not be measured.
     */
    fun analyzeImage(bitmap: Bitmap) {
        if (analyzeJob?.isActive == true) return
        val input = buildGeometryInput(bitmap)
        analyzeJob = viewModelScope.launch {
            _ui.update {
                it.copy(
                    screen = Screen.RESULT,
                    measure = MeasureState.Loading(0),
                    failReason = null,
                )
            }

            val outcome = analyzer.analyze(bitmap, input) { step ->
                _ui.update { s ->
                    if (s.measure is MeasureState.Loading) {
                        s.copy(measure = MeasureState.Loading(step))
                    } else {
                        s
                    }
                }
            }

            _ui.update { s ->
                when (outcome) {
                    is PoseAnalyzer.Outcome.Success ->
                        s.copy(measure = MeasureState.Success(toResult(outcome)))

                    is PoseAnalyzer.Outcome.Failure.NoPerson ->
                        s.copy(measure = MeasureState.Error(NO_PERSON_MESSAGE))

                    is PoseAnalyzer.Outcome.Failure.Unmeasurable ->
                        s.copy(measure = MeasureState.Error(outcome.message))
                }
            }
        }
    }

    /** Snapshot the mode + shutter-time context into a pure engine input. */
    private fun buildGeometryInput(bitmap: Bitmap): GeometryInput {
        val st = _ui.value
        val tilt = debugPitchDeg?.let { Tilt(it.toDouble(), debugRollDeg.toDouble()) } ?: captureTilt
        val intrinsics = debugFocalPx?.let {
            Intrinsics.forced(it.toDouble(), bitmap.width, bitmap.height)
        } ?: captureIntrinsics

        return when (st.mode) {
            Mode.PROPORTIONS -> GeometryInput.Proportions(sex = st.sex, age = st.age)
            Mode.HORIZON -> GeometryInput.Horizon(
                cameraHeightCm = st.cameraHeightCm.toDouble(),
                tilt = tilt,
                intrinsics = intrinsics,
            )
            Mode.REFERENCE -> GeometryInput.Reference(
                gravityTilt = tilt,
                intrinsics = intrinsics,
            )
        }
    }

    /** Build the display result from a real measurement. */
    private fun toResult(o: PoseAnalyzer.Outcome.Success): Result {
        val label = when (o.solved) {
            GeometryEngine.Solved.HORIZON ->
                "Horizon Model" + (o.cameraHeightCm?.let { " · phone ${it.roundToInt()} cm" } ?: "")
            GeometryEngine.Solved.REFERENCE ->
                "Reference Model (A4)" + (o.cameraHeightCm?.let { " · camera ${it.roundToInt()} cm" } ?: "")
            GeometryEngine.Solved.PROPORTIONS -> "Proportions Model"
        }
        return Result(
            heightCm = o.estimatedHeightCm.roundToInt(),
            metadataString = "$label · Margin ±${o.errorMarginCm} cm",
            quality = o.qualityScore,
            qualityLabel = o.qualityLabel,
            headsTall = o.nHeads,
            personPx = o.personPx,
            headPx = o.headPx,
            warning = o.warning,
        )
    }

    // ------------------------------------------------------------------
    // DEBUG-ONLY entry points for automated on-device verification:
    //
    //   adb shell am start -n com.measureme.app/.MainActivity \
    //       --es debug_screen result
    //   adb shell am start -n com.measureme.app/.MainActivity \
    //       --es debug_photo /data/data/com.measureme.app/cache/photo1.jpg
    // ------------------------------------------------------------------

    fun debugJump(target: String) {
        when (target.lowercase()) {
            "home" -> _ui.update { it.copy(screen = Screen.HOME) }
            "capture" -> _ui.update { it.copy(screen = Screen.CAPTURE) }
            "camera" -> goCamera()
            "result" -> {
                val photo = _ui.value.photo
                if (photo != null) {
                    analyzeImage(photo) // the real engine, end to end
                } else {
                    _ui.update {
                        it.copy(
                            screen = Screen.RESULT,
                            measure = MeasureState.Error("No photo yet — take one first."),
                        )
                    }
                }
            }
        }
    }

    fun debugPhoto(path: String) {
        Log.d(TAG, "debugPhoto: $path")
        viewModelScope.launch {
            load {
                if (path == "sample") {
                    capture.decodeSample()
                } else {
                    captureTilt = null
                    capture.decodeFile(java.io.File(path))
                }
            }
            _ui.value.photo?.let { analyzeImage(it) }
        }
    }

    /**
     * DEBUG-ONLY: force the Mode B/C inputs so the math can be exercised on
     * a device with an EXIF-free test photo:
     *
     *   --es debug_mode horizon --ef debug_camera_height 135 \
     *   --ef debug_pitch 10 --ef debug_roll 0 --ef debug_focal_px 3200
     */
    fun debugConfig(
        mode: String?,
        cameraHeightCm: Float?,
        pitchDeg: Float?,
        rollDeg: Float?,
        focalPx: Float?,
    ) {
        mode?.let { raw ->
            runCatching { Mode.valueOf(raw.uppercase()) }.getOrNull()?.let(::selectMode)
        }
        cameraHeightCm?.let(::setCameraHeight)
        debugPitchDeg = pitchDeg
        if (rollDeg != null) debugRollDeg = rollDeg
        debugFocalPx = focalPx
        Log.d(
            TAG,
            "debugConfig: mode=$mode h=$cameraHeightCm pitch=$pitchDeg roll=$rollDeg f=$focalPx",
        )
    }

    override fun onCleared() {
        sensors.stop()
        analyzer.close()
        super.onCleared()
    }

    companion object {
        private const val KEY_MODE = "mode"
        private const val KEY_CAMERA_HEIGHT = "cameraHeightCm"
        private const val KEY_SEX = "sex"
        private const val KEY_AGE = "age"
        private const val TAG = "MeasureMe"

        /** Mode B defaults: hand-held at chest height, the brief's middle preset. */
        const val DEFAULT_CAMERA_HEIGHT_CM = 135f
        const val MIN_CAMERA_HEIGHT_CM = 60f
        const val MAX_CAMERA_HEIGHT_CM = 220f

        private const val NO_PERSON_MESSAGE =
            "No person identified. Please ensure your full body is in the frame."
    }
}

/**
 * Centimetres → feet and inches, rounded to the nearest whole inch
 * (159f → "5 ft 3 in"). Inches that round up to 12 carry into the next
 * foot (182.7f → "6 ft 0 in").
 */
fun cmToFtIn(cm: Float): String {
    val totalInches = cm / 2.54f
    var feet = floor(totalInches / 12).toInt()
    var inches = round(totalInches % 12).toInt()
    if (inches == 12) {
        feet += 1
        inches = 0
    }
    return "$feet ft $inches in"
}
