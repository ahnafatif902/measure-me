package com.measureme.app.detect

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseDetectorOptionsBase
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.measureme.app.pipeline.LandmarkName
import com.measureme.app.pipeline.MappedLandmark
import com.measureme.app.pipeline.MeasureMeException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * PoseDetectionModule — on-device pose estimation with ML Kit's BlazePose.
 *
 * The "pose-detection" artifact bundles the model INSIDE the app, so after
 * install everything runs offline: no Play Services download, no network,
 * no cloud vision API. This is the module the product spec calls for.
 *
 * Output: landmark list in IMAGE PIXELS (origin top-left), ready for
 * GeometryModule.derive(). The module is deliberately thin — mapping + error
 * classification + a run-once-at-a-time guard.
 */
class PoseDetectionModule {

    private var detector: PoseDetector? = null
    private val lock = Mutex()
    private val detectBudgetMs = 20_000L // generous: low-end phones on 12 MP photos

    /** ML Kit landmark type → our LandmarkName. Explicit table > magic numbers. */
    private val landmarkTable: Map<Int, LandmarkName> = mapOf(
        PoseLandmark.NOSE to LandmarkName.NOSE,
        PoseLandmark.LEFT_EYE to LandmarkName.LEFT_EYE,
        PoseLandmark.RIGHT_EYE to LandmarkName.RIGHT_EYE,
        PoseLandmark.LEFT_EAR to LandmarkName.LEFT_EAR,
        PoseLandmark.RIGHT_EAR to LandmarkName.RIGHT_EAR,
        PoseLandmark.LEFT_MOUTH to LandmarkName.LEFT_MOUTH,
        PoseLandmark.RIGHT_MOUTH to LandmarkName.RIGHT_MOUTH,
        PoseLandmark.LEFT_SHOULDER to LandmarkName.LEFT_SHOULDER,
        PoseLandmark.RIGHT_SHOULDER to LandmarkName.RIGHT_SHOULDER,
        PoseLandmark.LEFT_HIP to LandmarkName.LEFT_HIP,
        PoseLandmark.RIGHT_HIP to LandmarkName.RIGHT_HIP,
        PoseLandmark.LEFT_KNEE to LandmarkName.LEFT_KNEE,
        PoseLandmark.RIGHT_KNEE to LandmarkName.RIGHT_KNEE,
        PoseLandmark.LEFT_ANKLE to LandmarkName.LEFT_ANKLE,
        PoseLandmark.RIGHT_ANKLE to LandmarkName.RIGHT_ANKLE,
        PoseLandmark.LEFT_HEEL to LandmarkName.LEFT_HEEL,
        PoseLandmark.RIGHT_HEEL to LandmarkName.RIGHT_HEEL,
        PoseLandmark.LEFT_FOOT_INDEX to LandmarkName.LEFT_FOOT_INDEX,
        PoseLandmark.RIGHT_FOOT_INDEX to LandmarkName.RIGHT_FOOT_INDEX
    )

    data class DetectionResult(
        val landmarks: List<MappedLandmark>,
        val hasFace: Boolean,
        val hasFeet: Boolean
    )

    /** Lazily create the client: keeps app start-up instant. */
    private fun client(): PoseDetector = detector ?: PoseDetection
        .getClient(
            PoseDetectorOptions.Builder()
                .setDetectorMode(PoseDetectorOptionsBase.SINGLE_IMAGE_MODE)
                .build()
        )
        .also { detector = it }

    /**
     * Run pose detection on one photo.
     * @throws MeasureMeException.NoPoseFound when ML Kit sees no person
     * @throws MeasureMeException.DetectorUnavailable on model failure
     * @throws MeasureMeException.DetectTimeout when the budget is exceeded
     */
    suspend fun detect(bitmap: Bitmap): DetectionResult = lock.withLock {
        withContext(Dispatchers.Default) {
            val (pose, mapped) = try {
                val image = InputImage.fromBitmap(bitmap, 0)
                val pose = withTimeout(detectBudgetMs) { client().process(image).awaitTask() }
                pose to mapLandmarks(pose)
            } catch (t: TimeoutCancellationException) {
                throw MeasureMeException.DetectTimeout()
            } catch (t: MeasureMeException) {
                throw t
            } catch (t: Throwable) {
                throw MeasureMeException.DetectorUnavailable(t)
            }

            if (pose.allPoseLandmarks.isEmpty() || mapped.isEmpty()) {
                throw MeasureMeException.NoPoseFound()
            }

            // Which body ends are actually visible? Drives the recovery tips.
            val hasFace = mapped.any {
                it.name in FACE_NAMES && it.likelihood >= 0.35f
            }
            val hasFeet = mapped.any {
                it.name in FEET_NAMES && it.likelihood >= 0.25f
            }
            DetectionResult(mapped, hasFace, hasFeet)
        }
    }

    private fun mapLandmarks(pose: Pose): List<MappedLandmark> {
        val out = ArrayList<MappedLandmark>(32)
        for (lm in pose.allPoseLandmarks) {
            val name = landmarkTable[lm.landmarkType] ?: continue
            // Image mode: getPosition() is already in image pixels (top-left origin).
            val loc = lm.position
            out += MappedLandmark(
                name = name,
                x = loc.x,
                y = loc.y,
                z = lm.position3D.z,
                likelihood = lm.inFrameLikelihood
            )
        }
        return out
    }

    fun close() {
        detector?.close()
        detector = null
    }

    private companion object {
        val FACE_NAMES = setOf(
            LandmarkName.NOSE, LandmarkName.LEFT_EYE, LandmarkName.RIGHT_EYE,
            LandmarkName.LEFT_MOUTH, LandmarkName.RIGHT_MOUTH
        )
        val FEET_NAMES = setOf(
            LandmarkName.LEFT_ANKLE, LandmarkName.RIGHT_ANKLE,
            LandmarkName.LEFT_HEEL, LandmarkName.RIGHT_HEEL,
            LandmarkName.LEFT_FOOT_INDEX, LandmarkName.RIGHT_FOOT_INDEX
        )
    }
}

/**
 * Bridge ML Kit's Task API into coroutines without adding a dependency
 * (kotlinx-coroutines-play-services). Suspends until success/failure/cancel.
 */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnSuccessListener { value -> if (cont.isActive) cont.resume(value) }
        addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
        addOnCanceledListener { cont.cancel() }
    }
