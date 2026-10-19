package com.measureme.app

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.measureme.app.capture.ImageCaptureModule
import com.measureme.app.detect.PoseDetectionModule
import com.measureme.app.pipeline.AppConstants
import com.measureme.app.pipeline.ConfidenceModule
import com.measureme.app.pipeline.GeometryModule
import com.measureme.app.pipeline.HeightEstimate
import com.measureme.app.pipeline.ImageStats
import com.measureme.app.pipeline.ManualFallbackModule
import com.measureme.app.pipeline.MeasureMeException
import com.measureme.app.pipeline.PoseLandmarks
import com.measureme.app.pipeline.Px
import com.measureme.app.ui.components.ManualMarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * MeasureMeViewModel — orchestrates the pipeline:
 *
 *   photo → ImageCaptureModule → PoseDetectionModule → GeometryModule.derive
 *         → GeometryModule.estimate → ConfidenceModule → UiState → screen
 *
 * The UI is a pure function of UiState; the single design page renders every
 * state inline (photo, manual marks, result, failure), exactly like the
 * reference layout. No database, no accounts: state dies with the process.
 */
class MeasureMeViewModel(app: Application) : AndroidViewModel(app) {

    enum class Screen { MEASURE, CAMERA }

    data class FailureInfo(
        val title: String,
        val message: String,
        val tips: List<String>,
        val canManual: Boolean,
    )

    data class UiState(
        val screen: Screen = Screen.MEASURE,
        val bitmap: Bitmap? = null,
        val imageStats: ImageStats? = null,
        /** The detector is running (busy card). */
        val working: Boolean = false,
        /** A photo is being loaded/decoded (the reference's separate decoding flag). */
        val decoding: Boolean = false,
        val estimate: HeightEstimate? = null,
        val landmarks: PoseLandmarks? = null,
        val fromManual: Boolean = false,
        val failure: FailureInfo? = null,
        /** Small inline note near the estimate button (the reference's runError). */
        val notice: String? = null,
        val averageHeadCm: Double = AppConstants.AVERAGE_HEAD_CM,
        val heads: Double = AppConstants.BODY_TO_HEAD_RATIO,
        val showDebug: Boolean = false,
        // --- manual marking: inline on the frame, reference-style ---
        val manualInput: Boolean = false,
        val manualCrown: Px? = null,
        val manualChin: Px? = null,
        val manualFeet: Px? = null,
        val manualChinAdjusted: Boolean = false,
        /** Bumped on every press of "Estimate height"; the page scrolls the result into view. */
        val resultNonce: Long = 0L,
    )

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui

    private val geometry = GeometryModule()
    private val confidence = ConfidenceModule()
    private val detector = PoseDetectionModule()
    private val capture = ImageCaptureModule(app)
    private val manual = ManualFallbackModule(geometry, confidence)

    // ------------------------------------------------------------------
    // Photo intake
    // ------------------------------------------------------------------

    /** Gallery import (Photo Picker / OpenDocument Uri). */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch { loadPhoto { capture.decodeUri(uri) } }
    }

    /** CameraX still capture. */
    fun onPhotoCaptured(proxy: ImageProxy) {
        viewModelScope.launch {
            try {
                loadPhoto { capture.fromImageProxy(proxy) }
            } finally {
                proxy.close()
            }
        }
    }

    /** "Try a sample photo": the reference's own bundled photo. */
    fun useSamplePhoto() {
        viewModelScope.launch { loadPhoto { capture.decodeSample() } }
    }

    private suspend fun loadPhoto(decode: () -> Bitmap) {
        _ui.update { it.copy(decoding = true, failure = null) }
        try {
            val bmp = withContext(Dispatchers.IO) { decode() }
            val stats = withContext(Dispatchers.IO) { capture.stats(bmp) }
            _ui.update {
                it.copy(
                    bitmap = bmp, imageStats = stats, decoding = false,
                    screen = Screen.MEASURE,
                    estimate = null, landmarks = null, fromManual = false,
                    failure = null, notice = null,
                    manualCrown = null, manualChin = null, manualFeet = null,
                    manualChinAdjusted = false,
                )
            }
        } catch (e: MeasureMeException) {
            fail(e)
        }
    }

    // ------------------------------------------------------------------
    // The Estimate button (routes to the automatic or the manual path)
    // ------------------------------------------------------------------

    fun estimate() {
        if (_ui.value.manualInput) estimateManual() else estimateAuto()
    }

    private fun estimateManual() {
        val state = _ui.value
        if (state.bitmap == null) return
        if (state.manualCrown == null || state.manualFeet == null) {
            _ui.update { it.copy(notice = "Tap the top of the head, then the bottom of the feet.") }
            return
        }
        recomputeManual(scroll = true)
    }

    private fun estimateAuto() {
        val bmp = _ui.value.bitmap ?: return
        viewModelScope.launch {
            _ui.update {
                it.copy(working = true, failure = null, estimate = null, landmarks = null, notice = null)
            }
            try {
                val stats = _ui.value.imageStats
                    ?: withContext(Dispatchers.IO) { capture.stats(bmp) }
                if (_ui.value.imageStats == null) _ui.update { it.copy(imageStats = stats) }

                // 1. on-device pose detection
                val detection = detector.detect(bmp)

                // 2. landmarks → head point, chin, feet point
                val landmarks = geometry.derive(detection.landmarks, bmp.width, bmp.height)
                if (landmarks.headTop == null || landmarks.feet == null) {
                    throw MeasureMeException.PoseIncomplete(
                        missingFace = landmarks.headTop == null,
                        missingFeet = landmarks.feet == null,
                    )
                }

                // 3. the formula
                val raw = geometry.estimate(landmarks, _ui.value.averageHeadCm, _ui.value.heads)

                // 4. sanity: an "impossible" height means the photo is not
                //    measurable — treat it as a failure with recovery tips.
                if (!raw.ok || raw.heightCm < AppConstants.HEIGHT_HARD_MIN_CM ||
                    raw.heightCm > AppConstants.HEIGHT_HARD_MAX_CM
                ) {
                    fail(
                        MeasureMeException.PoseIncomplete(missingFace = false, missingFeet = false),
                        overrideMessage = "The proportions in this photo don't add up " +
                            "(≈${raw.heightCm.toInt()} cm). The person may be too small in frame, " +
                            "cut off, or not standing straight.",
                    )
                    return@launch
                }

                // 5. transparent confidence
                val report = confidence.compute(landmarks, raw, stats)
                _ui.update {
                    it.copy(
                        estimate = raw.copy(
                            confidence = report.breakdown,
                            buckets = report.buckets,
                            expectedErrorCm = report.adjustedErrorCm,
                        ),
                        landmarks = landmarks,
                        fromManual = false,
                        working = false,
                        resultNonce = it.resultNonce + 1,
                    )
                }
            } catch (e: MeasureMeException) {
                fail(e)
            } catch (t: Throwable) {
                fail(MeasureMeException.DetectorUnavailable(t))
            } finally {
                _ui.update { it.copy(working = false) }
            }
        }
    }

    // ------------------------------------------------------------------
    // Manual marking (crown + feet taps, draggable derived chin)
    // ------------------------------------------------------------------

    fun toggleManual() {
        _ui.update { state ->
            state.copy(
                manualInput = !state.manualInput,
                estimate = null, landmarks = null, failure = null,
                fromManual = false, notice = null,
            )
        }
    }

    /** The failure card's "Mark points yourself" — always turns manual ON. */
    fun enterManual() {
        _ui.update { state ->
            state.copy(
                manualInput = true,
                estimate = null, landmarks = null, failure = null,
                fromManual = false, notice = null,
            )
        }
    }

    fun manualPlace(p: Px) {
        _ui.update { state ->
            if (!state.manualInput) return@update state
            when {
                state.manualCrown == null -> state.copy(manualCrown = p, notice = null)
                state.manualFeet == null -> state.copy(manualFeet = p, notice = null)
                else -> state
            }
        }
        recomputeManual(scroll = false)
    }

    fun manualDrag(marker: ManualMarker, p: Px) {
        _ui.update { state ->
            if (!state.manualInput) return@update state
            when (marker) {
                ManualMarker.CROWN -> state.copy(manualCrown = p, notice = null)
                ManualMarker.FEET -> state.copy(manualFeet = p, notice = null)
                ManualMarker.CHIN -> state.copy(manualChin = p, manualChinAdjusted = true, notice = null)
            }
        }
        recomputeManual(scroll = false)
    }

    /**
     * Live manual measurement — pure maths, so it runs on every tap/drag and
     * the estimate updates as the user moves the markers (like the reference).
     */
    private fun recomputeManual(scroll: Boolean) {
        val state = _ui.value
        val bmp = state.bitmap ?: return
        val stats = state.imageStats ?: return
        val crown = state.manualCrown
        val feet = state.manualFeet

        if (crown == null || feet == null) {
            _ui.update { it.copy(estimate = null, landmarks = null, failure = null) }
            return
        }

        val chinPoint = if (state.manualChinAdjusted && state.manualChin != null) {
            state.manualChin
        } else {
            manual.derivedChin(crown, feet, state.heads)
        }
        val problem = manual.validate(crown, chinPoint, feet, bmp.height)
        if (problem != null) {
            _ui.update {
                it.copy(
                    estimate = null, landmarks = null,
                    failure = FailureInfo(
                        title = "Couldn't measure that photo",
                        message = problem,
                        tips = TIPS,
                        canManual = false,
                    ),
                )
            }
            return
        }

        val outcome = manual.measure(
            crown = crown,
            chin = state.manualChin,
            chinAdjusted = state.manualChinAdjusted,
            feet = feet,
            imageW = bmp.width,
            imageH = bmp.height,
            averageHeadCm = state.averageHeadCm,
            heads = state.heads,
            image = stats,
        )
        _ui.update {
            it.copy(
                estimate = outcome.estimate,
                landmarks = outcome.landmarks,
                fromManual = true,
                failure = null,
                notice = null,
                resultNonce = if (scroll) it.resultNonce + 1 else it.resultNonce,
            )
        }
    }

    // ------------------------------------------------------------------
    // The two anthropometric constants (both live on the design's sliders)
    // ------------------------------------------------------------------

    fun setAverageHeadCm(value: Double) {
        _ui.update { it.copy(averageHeadCm = value.coerceIn(AppConstants.HEAD_CM_MIN, AppConstants.HEAD_CM_MAX)) }
        retune()
    }

    fun setHeads(value: Double) {
        _ui.update { it.copy(heads = value.coerceIn(AppConstants.HEADS_MIN, AppConstants.HEADS_MAX)) }
        retune()
    }

    /**
     * Re-runs the formula on the stored points (not the whole detection), so
     * the effect is instant — and it works for both paths.
     */
    private fun retune() {
        if (_ui.value.manualInput) {
            recomputeManual(scroll = false)
            return
        }
        val state = _ui.value
        val landmarks = state.landmarks ?: return
        val stats = state.imageStats ?: return
        if (state.estimate == null) return
        val raw = geometry.estimate(landmarks, state.averageHeadCm, state.heads)
        val report = confidence.compute(landmarks, raw, stats)
        _ui.update {
            it.copy(
                estimate = raw.copy(
                    confidence = report.breakdown,
                    buckets = report.buckets,
                    expectedErrorCm = report.adjustedErrorCm,
                )
            )
        }
    }

    fun setShowDebug(enabled: Boolean) {
        _ui.update { it.copy(showDebug = enabled) }
    }

    // ------------------------------------------------------------------
    // Navigation helpers
    // ------------------------------------------------------------------

    fun goCamera() = _ui.update { if (it.working || it.decoding) it else it.copy(screen = Screen.CAMERA) }

    /** Leaving the camera without taking a photo (system back / the back arrow). */
    fun goMeasure() = _ui.update { if (it.working || it.decoding) it else it.copy(screen = Screen.MEASURE) }

    // ------------------------------------------------------------------

    /**
     * DEBUG-ONLY entry point for automated on-device verification (no UI taps):
     *
     *   adb shell am start -n com.measureme.app/.MainActivity \
     *       --es debug_photo /sdcard/Android/data/com.measureme.app/files/test.jpg
     *
     * Loads the photo and runs the complete automatic pipeline so the result
     * can be screenshotted from CI or a terminal. `debug_manual` additionally
     * opens manual marking; `debug_marks` places the reference sample's own
     * tap points so the overlay can be compared against the design captures.
     * Wired up only when BuildConfig.DEBUG is true (see MainActivity).
     */
    fun loadDebugPhoto(path: String, manualMode: Boolean = false, marks: Boolean = false) {
        viewModelScope.launch {
            loadPhoto { capture.decodeUri(Uri.fromFile(java.io.File(path))) }
            if (_ui.value.bitmap == null) return@launch // decode failed → failure card
            if (manualMode) {
                _ui.update { it.copy(manualInput = true) }
                if (marks) placeDebugMarks()
            } else {
                estimateAuto()
            }
        }
    }

    /** The reference sample photo's own crown/chin/feet points (1152×1728 space). */
    private fun placeDebugMarks() {
        val bmp = _ui.value.bitmap ?: return
        val sx = bmp.width / 1152f
        val sy = bmp.height / 1728f
        _ui.update {
            it.copy(
                manualCrown = Px(565f * sx, 137.4f * sy),
                manualChin = Px(565f * sx, 330.4f * sy),
                manualFeet = Px(662f * sx, 1537.8f * sy),
                manualChinAdjusted = true,
            )
        }
        recomputeManual(scroll = false)
    }

    private fun fail(e: MeasureMeException, overrideMessage: String? = null) {
        val info = when (e) {
            is MeasureMeException.NoPoseFound -> FailureInfo(
                title = "No person detected",
                message = "The on-device model could not find a full body in this photo.",
                tips = listOf(
                    "Stand farther back so your whole body fits in the frame",
                    "Face the camera with even lighting",
                    "Avoid busy backgrounds and heavy shadow",
                ),
                canManual = true,
            )
            is MeasureMeException.PoseIncomplete -> FailureInfo(
                title = "Full body not visible",
                message = overrideMessage ?: e.message.orEmpty(),
                tips = buildList {
                    add("Head to feet in frame, with a little margin")
                    if (e.missingFeet) {
                        add("Stand farther back or tilt the phone slightly down to catch your feet")
                        add("Hold the phone around waist height")
                    }
                    if (e.missingFace) {
                        add("Face the camera — profile shots hide the face")
                        add("Remove hats/hoods that cover the head")
                    }
                    add("Brighter, even lighting")
                    add("Stand straight, arms relaxed")
                },
                canManual = true,
            )
            is MeasureMeException.DetectTimeout -> FailureInfo(
                title = "Detection timed out",
                message = "Your phone took too long to analyse this photo.",
                tips = listOf(
                    "Close other apps and retry",
                    "Try a smaller/simpler photo",
                    "Or mark the points manually — it works without the model",
                ),
                canManual = true,
            )
            is MeasureMeException.DetectorUnavailable -> FailureInfo(
                title = "Pose model failed",
                message = "The on-device model could not run on this photo.",
                tips = listOf(
                    "Retry once — this is often transient",
                    "Restart the app if it keeps happening",
                    "Manual marking works without the model",
                ),
                canManual = true,
            )
            is MeasureMeException.ImageUnreadable -> FailureInfo(
                title = "Photo could not be read",
                message = e.message.orEmpty(),
                tips = listOf(
                    "Pick a different photo (JPG or PNG)",
                    "Or take a new one with the camera",
                ),
                canManual = false,
            )
        }
        _ui.update {
            it.copy(
                failure = info, screen = Screen.MEASURE, working = false, decoding = false,
                estimate = null, landmarks = null, fromManual = false,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        detector.close()
    }

    companion object {
        /** Recovery tips for a hand-marked measurement that cannot be measured (the reference's TIPS). */
        val TIPS: List<String> = listOf(
            "Stand farther back so the top of your head and both shoes are fully in frame.",
            "Face the camera, arms a little away from your sides.",
            "Stand up straight on a level floor.",
            "Use even light. Avoid a bright window behind you.",
            "Hold the camera roughly level, not tilted.",
        )
    }
}
