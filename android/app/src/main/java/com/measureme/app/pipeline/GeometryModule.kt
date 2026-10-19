package com.measureme.app.pipeline

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * GeometryModule — landmarks → head point + feet point → height.
 *
 * This class is the heart of the app and it is PURE KOTLIN on purpose:
 * no Android imports, so it is covered by fast JVM unit tests
 * (see src/test/java/.../GeometryModuleTest.kt with fake points).
 *
 * Two stages, mirroring the pipeline diagram in README.md:
 *   1. derive()   raw ML Kit landmarks → PoseLandmarks (crown, chin→head size,
 *                 feet, roll/lean/knee, quality signals). All in pixels.
 *   2. estimate() PoseLandmarks → HeightEstimate via the documented formula.
 *
 * The formula (shown verbatim in the UI):
 *   height_cm = (person_pixel_height / head_pixel_height) * average_head_cm
 */
class GeometryModule {

    // ------------------------------------------------------------------
    // STAGE 2 — the measurement formula (contract from the product spec)
    // ------------------------------------------------------------------

    fun estimate(
        landmarks: PoseLandmarks,
        averageHeadCm: Double = AppConstants.AVERAGE_HEAD_CM,
        heads: Double = AppConstants.BODY_TO_HEAD_RATIO
    ): HeightEstimate {
        val head = landmarks.headTop ?: return weakEstimate("Missing head point", averageHeadCm)
        val feet = landmarks.feet ?: return weakEstimate("Missing feet point", averageHeadCm)

        val personPx = abs(feet.y - head.y)
        if (personPx < 1f) return weakEstimate("Head and feet overlap", averageHeadCm)

        // Ladder of head sizes, best → weakest (the weaker the source, the lower
        // the confidence that ConfidenceModule will assign):
        //   1. headHeightPx   — crown→chin fitted from face landmarks (best)
        //   2. headPxFromWidth— head height derived from ear-to-ear width (fair)
        //   3. none           — no scale in the photo at all → population prior
        val headPx = landmarks.headHeightPx
        val headPxFromWidth = if (headPx == null) landmarks.headPxFromWidth else null
        val effectiveHeadPx = headPx ?: headPxFromWidth

        val heightCm: Double
        val formula: String
        val usedFallback: Boolean

        if (effectiveHeadPx != null && effectiveHeadPx > 1f) {
            heightCm = (personPx / effectiveHeadPx) * averageHeadCm
            formula = if (headPx != null) {
                "height_cm = (person_px / head_px) * average_head_cm"
            } else {
                "height_cm = (person_px / head_px_from_ear_width) * average_head_cm"
            }
            usedFallback = headPx == null // ear-width tier is a fallback for the head size
        } else {
            // Weaker path — lower confidence outside. No scale exists in this
            // photo, so the best we can do is the population prior. The pixel
            // measurements no longer influence the result and the UI says so.
            heightCm = heads * averageHeadCm
            formula = "fallback ≈ body_to_head_ratio * average_head_cm"
            usedFallback = true
        }

        val headsTall = if (effectiveHeadPx != null && effectiveHeadPx > 1f) {
            (personPx / effectiveHeadPx).toDouble()
        } else {
            null
        }

        // ---- Notes: honest warnings the user sees in the result card -----
        val notes = mutableListOf<Note>()
        if (headPx == null && headPxFromWidth != null) {
            notes += Note("warn", "Head size was estimated from ear width — less precise than a face fit.")
        }
        if (headPx == null && headPxFromWidth == null) {
            notes += Note(
                "warn",
                "No head scale found in the photo, so this is a population-average guess " +
                    "(${heads} heads × ${averageHeadCm} cm), not a measurement of this person."
            )
        }
        if (headsTall != null &&
            (headsTall < AppConstants.HEADS_TALL_MIN || headsTall > AppConstants.HEADS_TALL_MAX)
        ) {
            notes += Note(
                "warn",
                "The measured body-to-head ratio (${"%.1f".format(headsTall)} heads) is outside the " +
                    "usual ${AppConstants.HEADS_TALL_MIN}–${AppConstants.HEADS_TALL_MAX} range — check the photo."
            )
        }
        if (landmarks.frame.rollRejected) {
            notes += Note("warn", "The camera was tilted more than ${AppConstants.MAX_ROLL_DEG.toInt()}° — straighten the photo for a better estimate.")
        }
        if (landmarks.leanDeg > AppConstants.LEAN_BAD_DEG * 0.6) {
            notes += Note("warn", "The person appears to be leaning (${landmarks.leanDeg.roundToInt()}°) — leaning makes anyone read shorter.")
        }

        // ---- Expected error band (shown as ± cm in the UI) ----------------
        val errFrac = if (usedFallback) {
            0.15
        } else {
            0.045 +
                (1.0 - landmarks.quality.fitErrorFactor) * 0.03 +
                (1.0 - landmarks.quality.widthAgreement) * 0.02
        }
        val expectedErrorCm = max(3.0, heightCm * errFrac).roundToInt()

        // ---- Formula trace ("How this was calculated" in the UI) ----------
        val trace = listOf(
            TraceSection(
                "Pixel measurements",
                listOf(
                    "person_px (crown → feet)" to "%.0f px".format(personPx),
                    "head_px (crown → chin)" to (effectiveHeadPx?.let { "%.0f px".format(it) } ?: "not available"),
                    "head size source" to landmarks.headMethod,
                    "heads tall (person_px / head_px)" to (headsTall?.let { "%.2f".format(it) } ?: "—")
                )
            ),
            TraceSection(
                "Anthropometric formula",
                listOf(
                    "formula" to formula,
                    "average_head_cm" to "%.1f cm".format(averageHeadCm),
                    "canonical_heads (fallback only)" to "%.1f".format(heads),
                    "result" to "%.1f cm".format(heightCm)
                )
            ),
            TraceSection(
                "Quality signals",
                listOf(
                    "camera roll" to "%.1f°".format(landmarks.rollDeg),
                    "body lean" to "%.1f°".format(landmarks.leanDeg),
                    "smallest knee angle" to "%.0f°".format(landmarks.kneeDeg),
                    "face-fit residual factor" to "%.2f".format(landmarks.quality.fitErrorFactor),
                    "head height vs head width" to "%.2f".format(landmarks.quality.widthAgreement)
                )
            )
        )

        return HeightEstimate(
            heightCm = heightCm,
            confidence = ConfidenceBreakdown(0, 0, 0, 0), // fill via ConfidenceModule
            usedFallback = usedFallback,
            formulaText = formula,
            reason = if (usedFallback && headPxFromWidth == null) "no-head-scale" else null,
            headsTall = headsTall,
            personPx = personPx,
            headPx = effectiveHeadPx,
            anchorHeightsCm = mapOf("head" to heightCm),
            expectedErrorCm = expectedErrorCm,
            notes = notes,
            trace = trace
        )
    }

    /**
     * Weak estimate used when the detection cannot produce both points.
     * Returns the population prior (BODY_TO_HEAD_RATIO * average_head_cm) with
     * ok = false so the UI shows the failure state + recovery tips.
     */
    private fun weakEstimate(reason: String, averageHeadCm: Double): HeightEstimate {
        val prior = AppConstants.BODY_TO_HEAD_RATIO * averageHeadCm
        return HeightEstimate(
            heightCm = prior,
            confidence = ConfidenceBreakdown(0, 0, 0, 0),
            usedFallback = true,
            formulaText = "fallback ≈ body_to_head_ratio * average_head_cm",
            ok = false,
            reason = reason
        )
    }

    // ------------------------------------------------------------------
    // STAGE 1 — raw landmarks → PoseLandmarks
    // ------------------------------------------------------------------

    /**
     * Convert ML Kit landmarks (already in image pixels, origin top-left) into
     * the primitives the formula needs, plus the quality signals that feed
     * ConfidenceModule. Everything here is plain arithmetic — each block is
     * commented so it can be re-implemented from scratch.
     */
    fun derive(
        raw: List<MappedLandmark>,
        imageW: Int,
        imageH: Int,
        multiplePeople: Boolean = false
    ): PoseLandmarks {
        // 0. Name → landmark lookup (keep the highest-likelihood sample per name).
        val byName = HashMap<LandmarkName, MappedLandmark>()
        for (lm in raw) {
            val prev = byName[lm.name]
            if (prev == null || lm.likelihood > prev.likelihood) byName[lm.name] = lm
        }

        // 1. Camera roll: angle of the heel line (fallback: ankle line).
        //    The person stands vertically on the ground; if the ground line in
        //    the image is tilted, the camera was rolled. We measure that tilt
        //    using the two heels/ankles and correct the points if it is small.
        var rollDeg = 0.0
        val heelL = byName[LandmarkName.LEFT_HEEL]
        val heelR = byName[LandmarkName.RIGHT_HEEL]
        val ankL = byName[LandmarkName.LEFT_ANKLE]
        val ankR = byName[LandmarkName.RIGHT_ANKLE]
        val groundPair = if (heelL != null && heelR != null) heelL to heelR
        else if (ankL != null && ankR != null) ankL to ankR
        else null
        val rollRejected: Boolean
        if (groundPair != null) {
            val (a, b) = groundPair
            val dx = (b.x - a.x).toDouble()
            val dy = (b.y - a.y).toDouble()
            rollDeg = Math.toDegrees(atan2(dy, dx))
            if (abs(rollDeg) > 90) rollDeg -= Math.signum(rollDeg) * 180 // keep angle horizontal-ish
            rollRejected = abs(rollDeg) > AppConstants.MAX_ROLL_DEG
        } else {
            rollRejected = false
        }

        // Deskew: rotate every point by -roll around the ankles' midpoint so
        // the measure axis becomes truly vertical. Skipped when the tilt is
        // too large to trust (rollRejected) — confidence pays for it instead.
        val pivot = when {
            ankL != null && ankR != null -> Px((ankL.x + ankR.x) / 2f, (ankL.y + ankR.y) / 2f)
            heelL != null && heelR != null -> Px((heelL.x + heelR.x) / 2f, (heelL.y + heelR.y) / 2f)
            else -> null
        }
        val theta = Math.toRadians(-rollDeg)
        val ct = cos(theta)
        val st = sin(theta)
        val points = HashMap<LandmarkName, Px>()
        for ((name, lm) in byName) {
            points[name] = if (pivot != null && !rollRejected && rollDeg != 0.0) {
                val dx = lm.x - pivot.x
                val dy = lm.y - pivot.y
                Px(
                    x = pivot.x + (dx * ct - dy * st).toFloat(),
                    y = pivot.y + (dx * st + dy * ct).toFloat()
                )
            } else {
                Px(lm.x, lm.y)
            }
        }

        // 2. Feet = lowest visible ground-contact point among ankles/heels/toe
        //    tips. The toe-tip landmark is the actual front of the shoe, so
        //    taking the max y is the best "standing on the floor" estimate.
        val footCandidates = listOf(
            LandmarkName.LEFT_ANKLE, LandmarkName.RIGHT_ANKLE,
            LandmarkName.LEFT_HEEL, LandmarkName.RIGHT_HEEL,
            LandmarkName.LEFT_FOOT_INDEX, LandmarkName.RIGHT_FOOT_INDEX
        ).mapNotNull { name ->
            val lm = byName[name]?.let { points[name] }
            if (lm != null && byName[name]!!.likelihood >= AppConstants.VIS_LOW) name to lm else null
        }
        val feet = footCandidates.maxByOrNull { it.second.y }?.second

        // 3. Head size. Primary method: least-squares fit of the canonical face
        //    proportions. For a head of pixel height h with the crown at y0:
        //        eyes  ≈ y0 + 0.47 h      nose ≈ y0 + 0.72 h      mouth ≈ y0 + 0.84 h
        //    With ≥2 observed feature rows we solve for (y0, h) by linear
        //    regression of y on the canonical depth. This uses nose + eyes +
        //    mouth together, which is far more stable than any single feature.
        var headPx: Float? = null
        var crown: Px? = null
        var chin: Px? = null
        var headMethod = "none"
        var fitErrorFactor = 1.0f

        val faceSamples = mutableListOf<Pair<Double, Double>>() // (canonical depth, y px)
        val faceWeights = mutableListOf<Float>()
        fun addFace(name: LandmarkName, depth: Double) {
            val lm = byName[name] ?: return
            if (lm.likelihood < AppConstants.VIS_MIN) return
            val p = points[name]!!
            faceSamples += depth to p.y.toDouble()
            faceWeights += lm.likelihood
        }
        addFace(LandmarkName.LEFT_EYE, AppConstants.FACE_DEPTH_EYES)
        addFace(LandmarkName.RIGHT_EYE, AppConstants.FACE_DEPTH_EYES)
        addFace(LandmarkName.NOSE, AppConstants.FACE_DEPTH_NOSE)
        addFace(LandmarkName.LEFT_MOUTH, AppConstants.FACE_DEPTH_MOUTH)
        addFace(LandmarkName.RIGHT_MOUTH, AppConstants.FACE_DEPTH_MOUTH)

        if (faceSamples.size >= 2 && faceSamples.map { it.first }.distinct().size >= 2) {
            // Simple unweighted least squares on the 5 (or fewer) samples:
            // slope = Σ(d-d̄)(y-ȳ) / Σ(d-d̄)²  → headPx
            // intercept = ȳ - slope·d̄           → crownY
            val n = faceSamples.size
            val dBar = faceSamples.sumOf { it.first } / n
            val yBar = faceSamples.sumOf { it.second } / n
            var sxy = 0.0
            var sxx = 0.0
            for ((d, y) in faceSamples) {
                sxy += (d - dBar) * (y - yBar)
                sxx += (d - dBar) * (d - dBar)
            }
            if (sxx > 1e-6) {
                val slope = sxy / sxx          // = head height in px
                val intercept = yBar - slope * dBar // = crown y in px
                if (slope > 1f) {
                    headPx = slope.toFloat()
                    val noseX = points[LandmarkName.NOSE]?.x
                        ?: points[LandmarkName.LEFT_EYE]?.x
                        ?: points[LandmarkName.RIGHT_EYE]?.x
                        ?: 0f
                    crown = Px(noseX, intercept.toFloat())
                    chin = Px(noseX, (intercept + slope).toFloat())
                    headMethod = "face-fit"
                    // Residual → how well the face matches canonical proportions.
                    var rms = 0.0
                    for ((d, y) in faceSamples) {
                        val predicted = intercept + slope * d
                        rms += (y - predicted) * (y - predicted)
                    }
                    rms = sqrt(rms / n)
                    fitErrorFactor = (1.0 - (rms / slope) / 0.07).coerceIn(0.25, 1.0).toFloat()
                }
            }
        }

        // 3b. Fallback ladder for the head size if the face fit failed:
        //     interpupillary distance ≈ 0.28 of head height (rough adult canon).
        if (headPx == null) {
            val eyeL = byName[LandmarkName.LEFT_EYE]
            val eyeR = byName[LandmarkName.RIGHT_EYE]
            if (eyeL != null && eyeR != null &&
                eyeL.likelihood >= AppConstants.VIS_MIN && eyeR.likelihood >= AppConstants.VIS_MIN
            ) {
                val eyeSpacing = hypot((eyeL.x - eyeR.x).toDouble(), (eyeL.y - eyeR.y).toDouble())
                val h = eyeSpacing / 0.28
                headPx = h.toFloat()
                val midX = (eyeL.x + eyeR.x) / 2f
                val midY = (eyeL.y + eyeR.y) / 2f
                crown = Px(midX, (midY - AppConstants.FACE_DEPTH_EYES * h).toFloat())
                chin = Px(midX, (midY + (1.0 - AppConstants.FACE_DEPTH_EYES) * h).toFloat())
                headMethod = "eye-spacing"
                fitErrorFactor = 0.7f
            }
        }

        // 4. Cross-check head size from ear-to-ear width (outer pinnae).
        //    Adult ears span ≈ 64% of the head height → width / 0.64 ≈ height.
        val earL = byName[LandmarkName.LEFT_EAR]
        val earR = byName[LandmarkName.RIGHT_EAR]
        var headPxFromWidth: Float? = null
        if (earL != null && earR != null &&
            earL.likelihood >= AppConstants.VIS_MIN && earR.likelihood >= AppConstants.VIS_MIN
        ) {
            val earWidth = hypot((earL.x - earR.x).toDouble(), (earL.y - earR.y).toDouble())
            headPxFromWidth = (earWidth / AppConstants.EAR_WIDTH_TO_HEAD_HEIGHT).toFloat()
        }

        // 5. Shoulder width (joint centres) — informational + confidence signal.
        var shoulderWidthPx: Float? = null
        val shL = byName[LandmarkName.LEFT_SHOULDER]
        val shR = byName[LandmarkName.RIGHT_SHOULDER]
        if (shL != null && shR != null && shL.likelihood >= AppConstants.VIS_MIN && shR.likelihood >= AppConstants.VIS_MIN) {
            shoulderWidthPx = hypot((shL.x - shR.x).toDouble(), (shL.y - shR.y).toDouble()).toFloat()
        }

        // 6. Body lean: angle of the ankle-mid → crown axis away from vertical.
        val leanDeg = if (crown != null && ankL != null && ankR != null) {
            val ankleMidX = (points[LandmarkName.LEFT_ANKLE]!!.x + points[LandmarkName.RIGHT_ANKLE]!!.x) / 2.0
            val ankleMidY = (points[LandmarkName.LEFT_ANKLE]!!.y + points[LandmarkName.RIGHT_ANKLE]!!.y) / 2.0
            val dx = crown.x - ankleMidX
            val dy = crown.y - ankleMidY
            abs(Math.toDegrees(atan2(dx, abs(dy))))
        } else {
            0.0
        }

        // 7. Knee angle: 180° = perfectly straight legs. We report the smaller
        //    of the two knees so a single bent leg lowers confidence.
        fun kneeAngle(hip: LandmarkName, knee: LandmarkName, ankle: LandmarkName): Double? {
            val h = points[hip] ?: return null
            val k = points[knee] ?: return null
            val a = points[ankle] ?: return null
            val v1x = (h.x - k.x).toDouble(); val v1y = (h.y - k.y).toDouble()
            val v2x = (a.x - k.x).toDouble(); val v2y = (a.y - k.y).toDouble()
            val n1 = hypot(v1x, v1y); val n2 = hypot(v2x, v2y)
            if (n1 < 1e-3 || n2 < 1e-3) return null
            val cosine = ((v1x * v2x + v1y * v2y) / (n1 * n2)).coerceIn(-1.0, 1.0)
            return Math.toDegrees(acos(cosine))
        }
        val kneeDeg = listOfNotNull(
            kneeAngle(LandmarkName.LEFT_HIP, LandmarkName.LEFT_KNEE, LandmarkName.LEFT_ANKLE),
            kneeAngle(LandmarkName.RIGHT_HIP, LandmarkName.RIGHT_KNEE, LandmarkName.RIGHT_ANKLE)
        ).minOrNull() ?: 180.0

        // 8. Quality signals → ConfidenceModule consumes these purely as data.
        fun visibilityOf(vararg names: LandmarkName): Float {
            val vals = names.mapNotNull { byName[it]?.likelihood }
            return if (vals.isEmpty()) 0f else vals.average().toFloat()
        }
        val personPx = if (crown != null && feet != null) abs(feet.y - crown.y) else null
        val widthAgreement = if (headPx != null && headPxFromWidth != null) {
            (min(headPx, headPxFromWidth) / max(headPx, headPxFromWidth))
        } else {
            0.75f // neutral: with a single head source there is nothing to compare
        }
        val sizeFactor = headPx?.let {
            ((it - AppConstants.MIN_HEAD_PX) / (AppConstants.GOOD_HEAD_PX - AppConstants.MIN_HEAD_PX)).coerceIn(0f, 1f)
        } ?: 0f
        val spanFactor = personPx?.let {
            ((it / imageH - 0.35) / (0.7 - 0.35)).coerceIn(0.0, 1.0).toFloat()
        } ?: 0f
        val marginsOk = crown != null && feet != null &&
            crown.y > imageH * 0.01f && crown.y < imageH * 0.9f &&
            feet.y < imageH * 0.995f && feet.y > imageH * 0.2f

        val quality = LandmarkQuality(
            visFace = visibilityOf(
                LandmarkName.NOSE, LandmarkName.LEFT_EYE, LandmarkName.RIGHT_EYE,
                LandmarkName.LEFT_MOUTH, LandmarkName.RIGHT_MOUTH
            ),
            visEars = visibilityOf(LandmarkName.LEFT_EAR, LandmarkName.RIGHT_EAR),
            visFeet = visibilityOf(
                LandmarkName.LEFT_ANKLE, LandmarkName.RIGHT_ANKLE,
                LandmarkName.LEFT_HEEL, LandmarkName.RIGHT_HEEL,
                LandmarkName.LEFT_FOOT_INDEX, LandmarkName.RIGHT_FOOT_INDEX
            ),
            visShoulders = visibilityOf(LandmarkName.LEFT_SHOULDER, LandmarkName.RIGHT_SHOULDER),
            widthAgreement = widthAgreement,
            fitErrorFactor = fitErrorFactor,
            sizeFactor = sizeFactor,
            spanFactor = spanFactor,
            marginsOk = marginsOk,
            multiplePeople = multiplePeople
        )

        return PoseLandmarks(
            headTop = crown,
            chin = chin,
            feet = feet,
            headHeightPx = headPx,
            headMethod = headMethod,
            headPxFromWidth = headPxFromWidth,
            shoulderWidthPx = shoulderWidthPx,
            rollDeg = rollDeg,
            leanDeg = leanDeg,
            kneeDeg = kneeDeg,
            quality = quality,
            points = points,
            frame = FrameInfo(imageW, imageH, crown?.y, feet?.y, rollRejected)
        )
    }
}
