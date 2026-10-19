package com.measureme.app.pipeline

import kotlin.math.abs

/**
 * ManualFallbackModule — tap-based measurement, no ML involved.
 *
 * The interaction model is the reference design's (web/src/lib/measure.js
 * measureFromManual()): two taps — the crown and the foot line — plus a chin
 * marker that starts at 1 ÷ the canonical ratio down from the crown (a prior,
 * not a measurement) and can be dragged onto the real chin.
 *
 * Reuses GeometryModule.estimate() and ConfidenceModule exactly like the
 * automatic path, so both routes share one formula, one confidence engine and
 * one overlay. The head-method tag drives the confidence caps:
 *   manual-taps   chin dragged onto the real chin → MANUAL_MAX_CONFIDENCE (65)
 *   manual-prior  chin still at the derived prior → MANUAL_PRIOR_MAX_CONF (36)
 */
class ManualFallbackModule(
    private val geometry: GeometryModule = GeometryModule(),
    private val confidence: ConfidenceModule = ConfidenceModule(),
) {

    enum class Marker { CROWN, CHIN, FEET }

    data class Outcome(
        val estimate: HeightEstimate,
        val report: ConfidenceModule.Report,
        val landmarks: PoseLandmarks,
    )

    /** The reference's derived chin: one canonical share of the body below the crown. */
    fun derivedChin(crown: Px, feet: Px, heads: Double): Px = Px(
        crown.x,
        crown.y + ((feet.y - crown.y) / heads.coerceIn(AppConstants.HEADS_MIN, AppConstants.HEADS_MAX)).toFloat(),
    )

    /**
     * Human-readable problem with the placed points, or null when they can be
     * measured. Mirrors the reference's guards, expressed in image pixels.
     */
    fun validate(crown: Px, chinPoint: Px, feet: Px, imageH: Int): String? {
        if (feet.y <= crown.y + 0.12f * imageH) {
            return "The feet marker needs to sit well below the top of the head."
        }
        if (chinPoint.y <= crown.y + 0.01f * imageH || chinPoint.y >= feet.y - 0.02f * imageH) {
            return "Place the chin marker between the crown and the feet, on the bottom of the chin."
        }
        return null
    }

    /**
     * Points → PoseLandmarks. Produces the SAME type the ML pipeline produces,
     * which is what lets GeometryModule.estimate() and ConfidenceModule run
     * unchanged on manual input.
     */
    fun buildLandmarks(
        crown: Px,
        chinPoint: Px,
        feet: Px,
        chinAdjusted: Boolean,
        imageW: Int,
        imageH: Int,
    ): PoseLandmarks {
        // Vertical head height, consistent with person_px being a vertical span.
        val headPx = abs(chinPoint.y - crown.y)
        val personPx = abs(feet.y - crown.y)

        val points = HashMap<LandmarkName, Px>()
        points[LandmarkName.NOSE] = crown // reuse slots for overlay/debug drawing
        points[LandmarkName.LEFT_MOUTH] = chinPoint
        points[LandmarkName.LEFT_FOOT_INDEX] = feet

        val sizeFactor = ((headPx - AppConstants.MIN_HEAD_PX) / (AppConstants.GOOD_HEAD_PX - AppConstants.MIN_HEAD_PX))
            .coerceIn(0f, 1f)
        val spanFactor = ((personPx / imageH - 0.35) / 0.35).coerceIn(0.0, 1.0).toFloat()

        val quality = LandmarkQuality(
            visFace = 1f, visEars = 1f, visFeet = 1f, visShoulders = 1f,
            widthAgreement = 0.7f,  // a single hand-placed scale is never certain
            fitErrorFactor = 0.7f,  // same idea: no least-squares agreement exists
            sizeFactor = sizeFactor,
            spanFactor = spanFactor,
            marginsOk = crown.y > imageH * 0.01f && feet.y < imageH * 0.995f,
            multiplePeople = false,
        )

        return PoseLandmarks(
            headTop = crown,
            chin = chinPoint,
            feet = feet,
            headHeightPx = headPx,
            headMethod = if (chinAdjusted) "manual-taps" else "manual-prior",
            headPxFromWidth = null,
            shoulderWidthPx = null,
            rollDeg = 0.0,  // unknown by construction — no floor line observed
            leanDeg = 0.0,
            kneeDeg = 180.0,
            quality = quality,
            points = points,
            frame = FrameInfo(imageW, imageH, crown.y, feet.y, rollRejected = false),
        )
    }

    /** Full manual measurement: points → PoseLandmarks → formula + confidence. */
    fun measure(
        crown: Px,
        chin: Px?,
        chinAdjusted: Boolean,
        feet: Px,
        imageW: Int,
        imageH: Int,
        averageHeadCm: Double,
        heads: Double,
        image: ImageStats,
    ): Outcome {
        val chinPoint = if (chinAdjusted && chin != null) chin else derivedChin(crown, feet, heads)
        val landmarks = buildLandmarks(crown, chinPoint, feet, chinAdjusted, imageW, imageH)
        val estimate = geometry.estimate(landmarks, averageHeadCm, heads)
        val report = confidence.compute(landmarks, estimate, image)
        return Outcome(
            estimate = estimate.copy(
                confidence = report.breakdown,
                buckets = report.buckets,
                expectedErrorCm = report.adjustedErrorCm,
            ),
            report = report,
            landmarks = landmarks,
        )
    }
}
