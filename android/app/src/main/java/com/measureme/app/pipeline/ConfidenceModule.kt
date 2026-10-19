package com.measureme.app.pipeline

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ConfidenceModule — transparent, rule-based, explainable.
 *
 * Exactly the 4-bucket additive scheme from the product spec, each up to +25:
 *   A  full body visible / landmarks present        +25
 *   B  person roughly vertical (not leaning)        +25
 *   C  pose landmark quality / geometry reliability +25
 *   D  image quality (resolution, lighting)         +25
 *
 * The total is clamped to MAX_CONFIDENCE (92) so the app never claims 98–100.
 * Manual taps are further capped at MANUAL_MAX_CONFIDENCE (65). When the cap
 * bites, all four buckets are scaled proportionally so the breakdown still
 * adds up to the headline number — no unexplained gap for the user.
 *
 * Every bucket carries a human-readable reason → this IS the UI breakdown.
 */
class ConfidenceModule {

    data class Report(
        val breakdown: ConfidenceBreakdown,
        val buckets: List<ConfidenceBucket>,
        val adjustedErrorCm: Int
    )

    fun compute(
        landmarks: PoseLandmarks,
        estimate: HeightEstimate,
        image: ImageStats
    ): Report {
        val isManual = landmarks.headMethod == "manual-taps"
        val isManualPrior = landmarks.headMethod == "manual-prior"
        val isTapMode = isManual || isManualPrior

        // ---------------- A. Full body visible / landmarks present --------
        val presence = if (estimate.headPx != null && landmarks.feet != null && estimate.personPx != null) 1.0 else 0.0
        var a = 25.0 * (0.55 * presence + 0.45 * landmarks.quality.spanFactor)
        if (!landmarks.quality.marginsOk) a *= 0.6
        if (landmarks.quality.multiplePeople) a *= 0.5
        val aReason = when {
            presence == 0.0 -> "Head or feet point could not be located"
            !landmarks.quality.marginsOk -> "A body point sits at the very edge of the frame"
            else -> "Crown and feet located; the body spans ${(landmarks.quality.spanFactor * 100).roundToInt()}% of the ideal frame height"
        }

        // ---------------- B. Roughly vertical ------------------------------
        val b: Double
        val bReason: String
        if (isTapMode) {
            // Hand-placed taps carry no floor line or knee landmarks, so the
            // straightness part of the budget is only partially granted.
            // (Same for the derived chin: tap mode still cannot judge posture.)
            b = 25.0 * (0.7 * 0.75 + 0.3 * 0.8)
            bReason = "Tap mode cannot verify posture — assuming the person stood straight"
        } else {
            val rollFactor = if (landmarks.frame.rollRejected) {
                0.45
            } else {
                1.0 - (min(abs(landmarks.rollDeg), AppConstants.MAX_ROLL_DEG) / AppConstants.MAX_ROLL_DEG) * 0.25
            }
            val leanFactor = 1.0 - ((landmarks.leanDeg - AppConstants.LEAN_GOOD_DEG) /
                (AppConstants.LEAN_BAD_DEG - AppConstants.LEAN_GOOD_DEG)).coerceIn(0.0, 1.0)
            val kneeFactor = 0.35 + 0.65 * ((landmarks.kneeDeg - AppConstants.KNEE_SUSPECT_DEG) /
                (AppConstants.KNEE_STRAIGHT_DEG - AppConstants.KNEE_SUSPECT_DEG)).coerceIn(0.0, 1.0)
            b = 25.0 * (0.4 * rollFactor + 0.35 * leanFactor + 0.25 * kneeFactor)
            val issues = mutableListOf<String>()
            if (landmarks.frame.rollRejected) issues += "camera tilted >${AppConstants.MAX_ROLL_DEG.roundToInt()}°"
            if (landmarks.leanDeg > AppConstants.LEAN_GOOD_DEG) issues += "leaning ${landmarks.leanDeg.roundToInt()}°"
            if (landmarks.kneeDeg < AppConstants.KNEE_STRAIGHT_DEG) issues += "knees bent to ${landmarks.kneeDeg.roundToInt()}°"
            bReason = if (issues.isEmpty()) "Standing straight, camera level" else "Posture issues: " + issues.joinToString(", ")
        }

        // ---------------- C. Landmark / geometry quality -------------------
        val agreement = landmarks.quality.widthAgreement * 0.5 + landmarks.quality.fitErrorFactor * 0.5
        val meanVis = (landmarks.quality.visFace + landmarks.quality.visFeet) / 2.0
        var c = if (isTapMode) {
            25.0 * (0.45 * 0.75 + 0.2 * agreement + 0.35 * landmarks.quality.sizeFactor)
        } else {
            25.0 * (0.45 * meanVis + 0.2 * agreement + 0.35 * landmarks.quality.sizeFactor)
        }
        if (estimate.usedFallback && estimate.headPx != null) c *= 0.75  // ear-width tier
        if (estimate.reason == "no-head-scale") c *= 0.35                // population prior
        if (estimate.headsTall != null &&
            (estimate.headsTall < AppConstants.HEADS_TALL_MIN || estimate.headsTall > AppConstants.HEADS_TALL_MAX)
        ) c *= 0.6
        val cReason = when {
            estimate.reason == "no-head-scale" ->
                "No head scale in the photo — this is a population guess, not a measurement"
            estimate.headPx == null -> "Head size unreliable"
            estimate.headsTall != null &&
                (estimate.headsTall < AppConstants.HEADS_TALL_MIN || estimate.headsTall > AppConstants.HEADS_TALL_MAX) ->
                "Body-to-head ratio (${"%.1f".format(estimate.headsTall)} heads) looks unusual"
            isManualPrior -> "The chin marker is still the default prior — drag it onto the chin"
            isManual -> "Hand-placed points — precision depends on your taps"
            else -> "Face landmarks clear; head size agrees with ear width (${(agreement * 100).roundToInt()}%)"
        }

        // ---------------- D. Image quality ---------------------------------
        val maxDim = max(image.width, image.height)
        val resFactor = ((maxDim - AppConstants.MIN_IMAGE_DIM) / 1120.0).coerceIn(0.25, 1.0)
        val brightnessFactor = if (image.meanLuma < AppConstants.BRIGHTNESS_MIN ||
            image.meanLuma > AppConstants.BRIGHTNESS_MAX
        ) {
            0.15
        } else {
            1.0 - (abs(image.meanLuma - 137.0) / 98.0) * 0.5
        }
        val contrastFactor = (image.lumaStdDev / 25.0).coerceIn(0.2, 1.0)
        val d = 25.0 * (0.4 * resFactor + 0.35 * brightnessFactor + 0.25 * contrastFactor)
        val dReason = buildString {
            append(
                if (maxDim >= 1600) "High-resolution photo"
                else "Photo is ${maxDim}px — ${if (maxDim < 720) "low" else "moderate"} detail"
            )
            if (brightnessFactor < 0.7) {
                append("; lighting is ${if (image.meanLuma < AppConstants.BRIGHTNESS_MIN) "too dark" else "too bright"}")
            }
            if (contrastFactor < 0.7) append("; low contrast")
        }

        // ---------------- Combine + cap ------------------------------------
        val raw = listOf(a.roundToInt(), b.roundToInt(), c.roundToInt(), d.roundToInt())
            .map { it.coerceIn(0, 25) }
        val cap = when {
            isManualPrior -> AppConstants.MANUAL_PRIOR_MAX_CONFIDENCE
            isManual -> AppConstants.MANUAL_MAX_CONFIDENCE
            else -> AppConstants.MAX_CONFIDENCE
        }
        val rawSum = raw.sum()
        val capped = min(cap, rawSum)

        // Proportional scaling when the cap bites, with rounding drift fixed
        // on the last bucket so the four numbers add up to exactly `capped`.
        val scores: List<Int> = if (rawSum <= capped) {
            raw
        } else {
            val scale = capped.toDouble() / rawSum
            val scaled = raw.map { (it * scale).roundToInt() }.toMutableList()
            var drift = capped - scaled.sum()
            var i = scaled.lastIndex
            while (drift != 0) {
                val step = if (drift > 0) 1 else -1
                if (scaled[i] + step in 0..25) { scaled[i] += step; drift -= step }
                else { i = (i - 1 + scaled.size) % scaled.size }
            }
            scaled
        }

        val breakdown = ConfidenceBreakdown(scores[0], scores[1], scores[2], scores[3])
        val cappedNote = if (rawSum > capped) " (scaled down to the $cap% ceiling)" else ""
        val buckets = listOf(
            ConfidenceBucket("fullBody", "Full body visible", scores[0], 25, aReason + cappedNote),
            ConfidenceBucket("vertical", "Standing straight", scores[1], 25, bReason + cappedNote),
            ConfidenceBucket("landmarks", "Landmark quality", scores[2], 25, cReason + cappedNote),
            ConfidenceBucket("image", "Image quality", scores[3], 25, dReason + cappedNote)
        )

        // Wider error bar when confidence is lower — one honest number.
        val errScale = 1.0 + (AppConstants.MAX_CONFIDENCE - capped) / 92.0
        val adjustedError = max(
            if (isTapMode) 5 else 3,
            (estimate.expectedErrorCm * errScale).roundToInt()
        )

        return Report(breakdown = breakdown, buckets = buckets, adjustedErrorCm = adjustedError)
    }
}
