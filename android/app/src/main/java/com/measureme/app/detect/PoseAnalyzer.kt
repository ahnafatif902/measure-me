package com.measureme.app.detect

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.measureme.app.capture.A4Detector
import com.measureme.app.capture.ImageCaptureModule
import com.measureme.app.pipeline.AppConstants
import com.measureme.app.pipeline.ConfidenceModule
import com.measureme.app.pipeline.GeometryEngine
import com.measureme.app.pipeline.GeometryInput
import com.measureme.app.pipeline.GeometryModule
import com.measureme.app.pipeline.LandmarkName
import com.measureme.app.pipeline.MappedLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * PoseAnalyzer — the real measuring engine (all three modes).
 *
 * 1. MediaPipe Pose Landmarker runs on the photo (IMAGE mode, bundled model,
 *    fully on-device — no network, no Play Services download).
 * 2. The 33 landmarks are converted to image pixels and mapped onto the
 *    repo's [MappedLandmark] type. MediaPipe's BlazePose topology and the
 *    repo's [LandmarkName] indices are the same numbering, so the mapping is
 *    a straight index lookup.
 * 3. Mode A maths: nose → feet in pixels, head length proxied by the
 *    ear-to-ear distance, heads-tall = pixels / head length, height =
 *    heads × the average adult head length (23 cm).
 * 4. The same landmarks are also handed to the repo's real pipeline
 *    ([GeometryModule] → [ConfidenceModule]) to produce the crown-based
 *    estimate, the 0–92 quality score and the honest error margin.
 * 5. [GeometryEngine] then routes by the requested [GeometryInput]: Mode A
 *    uses the numbers above; Mode B solves the horizon geometry with the
 *    shutter-time tilt; Mode C detects an A4 sheet and solves the camera
 *    pose from it. B/C fall back to A — with a visible cause-specific
 *    warning — whenever their inputs are missing or unusable.
 *
 * Nothing here is stubbed: every number the UI shows comes from the photo.
 */
class PoseAnalyzer(private val context: Context) {

    /** What the analyzer can return. */
    sealed interface Outcome {

        /** A person was found and measured. */
        data class Success(
            /** The height to show, in cm. */
            val estimatedHeightCm: Float,
            /** Nose → feet distance in pixels (the Mode A pixel span). */
            val pixelHeight: Int,
            /** Head length in pixels (ear distance × 1.4). */
            val headLengthPx: Int,
            /** Body length measured in head lengths. */
            val nHeads: Double,
            /** Crown → feet distance in pixels (the stat tile). */
            val personPx: Int,
            /** Fitted head height in pixels (the stat tile). */
            val headPx: Int,
            /** Confidence score, 0–92 (the pipeline never claims 98–100). */
            val qualityScore: Int,
            /** Human label for the quality score. */
            val qualityLabel: String,
            /** Expected error in cm — the honest margin of the estimate. */
            val errorMarginCm: Int,
            /** Which formula produced the number (shown in the trace). */
            val formulaText: String,
            /** Which model actually answered: PROPORTIONS / HORIZON / REFERENCE. */
            val solved: GeometryEngine.Solved,
            /** True when Mode B/C was requested but Proportions had to stand in. */
            val fallback: Boolean,
            /** Cause-specific fallback sentence, or null. Always shown if set. */
            val warning: String? = null,
            /** Camera height the math used (Mode B input or Mode C solution). */
            val cameraHeightCm: Double? = null,
        ) : Outcome

        /** Why the photo could not be measured. */
        sealed interface Failure : Outcome {
            /** No person in the frame at all. */
            data object NoPerson : Failure

            /** A person, but not enough of one to measure. */
            data class Unmeasurable(val message: String) : Failure
        }
    }

    private var landmarker: PoseLandmarker? = null
    private val lock = Mutex()
    private val geometry = GeometryModule()
    private val engine = GeometryEngine()
    private val confidence = ConfidenceModule()
    private val imageStats = ImageCaptureModule(context)
    private val a4 = A4Detector()

    /**
     * Analyse one photo.
     *
     * @param input which model to run — Proportions, Horizon or Reference.
     *   Mode B/C always fall back to Proportions (with a warning) when their
     *   inputs are missing or their geometry is unusable.
     * @param onStage called with 1..3 as the real pipeline advances, so the
     *   progress UI reflects work that actually happened.
     */
    suspend fun analyze(
        bitmap: Bitmap,
        input: GeometryInput,
        onStage: (Int) -> Unit = {},
    ): Outcome = lock.withLock {
        withContext(Dispatchers.Default) {
            // ---- 1. Run the pose landmarker ------------------------------
            val poses = try {
                val mpImage = BitmapImageBuilder(bitmap).build()
                landmarker().detect(mpImage).landmarks()
            } catch (t: Throwable) {
                return@withContext Outcome.Failure.Unmeasurable(
                    "The pose model could not run on this photo. Try another one."
                )
            }

            // Biggest pose wins (a photo may contain several people).
            val landmarks = poses.maxByOrNull { it.size } ?: emptyList()
            if (landmarks.isEmpty()) {
                return@withContext Outcome.Failure.NoPerson
            }
            onStage(1) // "Find person" is done

            // ---- 2. Landmarks → pixels → our MappedLandmark type ---------
            val w = bitmap.width.toFloat()
            val h = bitmap.height.toFloat()
            val mapped = ArrayList<MappedLandmark>(landmarks.size)
            for (index in landmarks.indices) {
                val name = MP_TO_LANDMARK[index] ?: continue
                val lm = landmarks[index]
                mapped += MappedLandmark(
                    name = name,
                    x = lm.x() * w,
                    y = lm.y() * h,
                    z = lm.z(),
                    likelihood = likelihoodOf(lm),
                )
            }

            // ---- 3. Mode A: the proportions maths ------------------------
            fun xAt(index: Int): Float? {
                val lm = landmarks.getOrNull(index) ?: return null
                if (likelihoodOf(lm) < VIS_MIN) return null
                return lm.x() * w
            }

            fun yAt(index: Int): Float? {
                val lm = landmarks.getOrNull(index) ?: return null
                if (likelihoodOf(lm) < VIS_MIN) return null
                return lm.y() * h
            }

            val noseY = yAt(NOSE)
                ?: return@withContext Outcome.Failure.Unmeasurable(
                    "Your face could not be found in the photo. Face the camera and try again."
                )

            // The lowest visible ground-contact point wins: ankles, heels, toes.
            val footY = FOOT_INDICES.mapNotNull { yAt(it) }.maxOrNull()
                ?: return@withContext Outcome.Failure.Unmeasurable(
                    "Your feet are not in the frame. Step back so your full body fits."
                )

            val pixelHeight = footY - noseY
            if (pixelHeight <= 1f) {
                return@withContext Outcome.Failure.Unmeasurable(
                    "The photo does not show a full body. Step back and try again."
                )
            }

            val earLX = xAt(LEFT_EAR)
            val earLY = yAt(LEFT_EAR)
            val earRX = xAt(RIGHT_EAR)
            val earRY = yAt(RIGHT_EAR)
            val earDistance = if (earLX != null && earLY != null && earRX != null && earRY != null) {
                hypot(earLX - earRX, earLY - earRY)
            } else {
                0f
            }
            // Head length proxy: ear-to-ear width is ~1.4× the head's length.
            val headLengthPx = earDistance * 1.4f
            if (headLengthPx <= 1f) {
                return@withContext Outcome.Failure.Unmeasurable(
                    "Your head is not clear enough to measure. Face the camera in better light."
                )
            }

            val nHeads = pixelHeight / headLengthPx
            val proportionsHeightCm = (nHeads * AppConstants.AVERAGE_HEAD_CM).toFloat()

            onStage(2) // "Locate head and feet" is done

            // ---- 4. The repo's real geometry + confidence pipeline ---------
            // The Mode A span above runs nose→feet; the pipeline measures
            // crown→feet with a face-fitted head, which is the canonical
            // "heads tall" measure. Prefer it when it succeeded, and fall
            // back to the Mode A figure when the photo has no usable head.
            val poseLandmarks = geometry.derive(mapped, bitmap.width, bitmap.height)
            val estimate = geometry.estimate(poseLandmarks)
            val stats = imageStats.stats(bitmap)
            val report = confidence.compute(poseLandmarks, estimate, stats)

            onStage(3) // "Work out the scale" is done

            // ---- 5. Hand the mode decision to GeometryEngine ----------------
            // The Mode A number is exactly what shipped before: the crown-based
            // face-fit estimate when usable, the nose→feet proportions figure
            // otherwise. GeometryEngine reuses it untouched for Mode A and for
            // every B/C fallback, so the old path cannot regress.
            val pipelineUsable = estimate.ok && estimate.headPx != null && estimate.personPx != null
            val modeAHeightCm = if (pipelineUsable) estimate.heightCm else proportionsHeightCm.toDouble()
            val modeAFormula = if (pipelineUsable) {
                estimate.formulaText
            } else {
                "proportions = nose_to_feet_px / head_length_px * 23 cm"
            }
            val modeA = GeometryEngine.ProportionsResult(
                heightCm = modeAHeightCm,
                errorMarginCm = report.adjustedErrorCm,
                formulaText = modeAFormula,
            )

            val solution = engine.solve(
                input = input,
                pose = poseLandmarks,
                qualityScore = report.breakdown.total,
                modeA = modeA,
                referenceCorners = if (input is GeometryInput.Reference) a4.detect(bitmap) else null,
            )

            Log.d(
                "MeasureMe",
                "engine ⇒ solved=${solution.solved} fallback=${solution.fallback} " +
                    "H=${solution.heightCm} ±${solution.errorMarginCm} warning=${solution.warning}\n" +
                    engine.diagnostics,
            )

            Outcome.Success(
                estimatedHeightCm = solution.heightCm.toFloat(),
                pixelHeight = pixelHeight.roundToInt(),
                headLengthPx = headLengthPx.roundToInt(),
                nHeads = estimate.headsTall ?: nHeads.toDouble(),
                personPx = (estimate.personPx ?: pixelHeight).roundToInt(),
                headPx = (estimate.headPx ?: headLengthPx).roundToInt(),
                qualityScore = report.breakdown.total,
                qualityLabel = qualityLabel(report.breakdown.total),
                errorMarginCm = solution.errorMarginCm,
                formulaText = solution.formulaText,
                solved = solution.solved,
                fallback = solution.fallback,
                warning = solution.warning,
                cameraHeightCm = solution.cameraHeightCm,
            )
        }
    }

    /** Lazily create the landmarker — keeps app start-up instant. */
    private fun landmarker(): PoseLandmarker = landmarker ?: synchronized(this) {
        landmarker ?: PoseLandmarker.createFromOptions(
            context,
            PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .build()
                )
                .setRunningMode(RunningMode.IMAGE)
                .setNumPoses(1)
                .build(),
        ).also { landmarker = it }
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }

    private fun qualityLabel(score: Int): String = when {
        score >= 80 -> "Clean shot"
        score >= 60 -> "Usable shot"
        score >= 40 -> "Rough shot"
        else -> "Low quality"
    }

    /**
     * MediaPipe does not always populate visibility in IMAGE mode; fall back
     * to presence, and to full confidence when neither is reported.
     */
    private fun likelihoodOf(lm: NormalizedLandmark): Float {
        val visibility = lm.visibility().orElse(0f)
        if (visibility > 0f) return visibility
        val presence = lm.presence().orElse(0f)
        if (presence > 0f) return presence
        return 1f
    }

    private companion object {
        const val MODEL_ASSET = "pose_landmarker_full.task"

        // MediaPipe BlazePose landmark indices.
        const val NOSE = 0
        const val LEFT_EAR = 7
        const val RIGHT_EAR = 8

        /** Ankles, heels and toe tips — the ground-contact points. */
        val FOOT_INDICES = listOf(27, 28, 29, 30, 31, 32)

        /** Below this confidence a landmark is treated as not visible. */
        const val VIS_MIN = 0.35f

        /** MediaPipe index → the repo's landmark name (same topology). */
        val MP_TO_LANDMARK: Map<Int, LandmarkName> = mapOf(
            0 to LandmarkName.NOSE,
            2 to LandmarkName.LEFT_EYE,
            5 to LandmarkName.RIGHT_EYE,
            7 to LandmarkName.LEFT_EAR,
            8 to LandmarkName.RIGHT_EAR,
            9 to LandmarkName.LEFT_MOUTH,
            10 to LandmarkName.RIGHT_MOUTH,
            11 to LandmarkName.LEFT_SHOULDER,
            12 to LandmarkName.RIGHT_SHOULDER,
            23 to LandmarkName.LEFT_HIP,
            24 to LandmarkName.RIGHT_HIP,
            25 to LandmarkName.LEFT_KNEE,
            26 to LandmarkName.RIGHT_KNEE,
            27 to LandmarkName.LEFT_ANKLE,
            28 to LandmarkName.RIGHT_ANKLE,
            29 to LandmarkName.LEFT_HEEL,
            30 to LandmarkName.RIGHT_HEEL,
            31 to LandmarkName.LEFT_FOOT_INDEX,
            32 to LandmarkName.RIGHT_FOOT_INDEX,
        )
    }
}
