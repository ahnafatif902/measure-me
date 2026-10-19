package com.measureme.app.pipeline

/**
 * Typed failures, so the UI can show a specific recovery tip instead of a
 * generic crash dialog. Thrown by the detection/capture layers, caught in the
 * ViewModel, rendered by the Result/Failure screen.
 */
sealed class MeasureMeException(message: String) : Exception(message) {
    /** ML Kit found no person at all in the photo. */
    class NoPoseFound : MeasureMeException("No person found in the photo")

    /** A pose exists, but the full body (head + feet) is not visible. */
    class PoseIncomplete(val missingFace: Boolean, val missingFeet: Boolean) :
        MeasureMeException(
            "Full body not visible" +
                when {
                    missingFace && missingFeet -> " — head and feet are both missing"
                    missingFace -> " — the head/face is missing"
                    missingFeet -> " — the feet/floor line is missing"
                    else -> ""
                }
        )

    /** Detector itself could not run (model failure, out of memory…). */
    class DetectorUnavailable(cause: Throwable?) :
        MeasureMeException("The on-device pose model could not run: ${cause?.message ?: "unknown"}")

    /** Photo could not be decoded/read. */
    class ImageUnreadable(cause: Throwable?) :
        MeasureMeException("The photo could not be read: ${cause?.message ?: "unknown"}")

    /** Detection took longer than the allowed budget. */
    class DetectTimeout : MeasureMeException("Detection took too long")
}
