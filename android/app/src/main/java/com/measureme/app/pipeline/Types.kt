package com.measureme.app.pipeline

import kotlin.math.roundToInt

/**
 * A point in image pixels (origin top-left).
 * Deliberately NOT android.graphics.PointF: the whole pipeline package is pure
 * Kotlin, so GeometryModule/ConfidenceModule can be unit-tested on the JVM
 * with fake points — no emulator, no Robolectric.
 */
data class Px(val x: Float, val y: Float)

/** Landmarks we actually use. Values match ML Kit PoseLandmark indices. */
enum class LandmarkName(val index: Int) {
    NOSE(0),
    LEFT_EYE(2), RIGHT_EYE(5),
    LEFT_EAR(7), RIGHT_EAR(8),
    LEFT_MOUTH(9), RIGHT_MOUTH(10),
    LEFT_SHOULDER(11), RIGHT_SHOULDER(12),
    LEFT_HIP(23), RIGHT_HIP(24),
    LEFT_KNEE(25), RIGHT_KNEE(26),
    LEFT_ANKLE(27), RIGHT_ANKLE(28),
    LEFT_HEEL(29), RIGHT_HEEL(30),
    LEFT_FOOT_INDEX(31), RIGHT_FOOT_INDEX(32)
}

/** One pose landmark, already converted to image pixels (origin top-left). */
data class MappedLandmark(
    val name: LandmarkName,
    val x: Float,
    val y: Float,
    val z: Float,
    val likelihood: Float
)

/**
 * The geometry primitives the rest of the app works with — this is the
 * "hand-off" from detection to measurement. Everything in PIXELS.
 */
data class PoseLandmarks(
    val headTop: Px?,              // estimated crown of the head
    val chin: Px?,                 // used to size the head; null in manual 2-point mode
    val feet: Px?,                 // lowest visible ground-contact point
    val headHeightPx: Float?,      // crown→chin distance; null = unreliable/absent
    val headMethod: String,        // "face-fit" | "eye-spacing" | "manual-taps" | "none"
    val headPxFromWidth: Float?,   // ear-width cross-check for the head
    val shoulderWidthPx: Float?,
    val rollDeg: Double,           // camera roll detected from the feet line
    val leanDeg: Double,           // body lean from vertical after deskew
    val kneeDeg: Double,           // min knee angle (180 = straight legs)
    val quality: LandmarkQuality,
    val points: Map<LandmarkName, Px>, // for the overlay + debug drawing
    val frame: FrameInfo
)

/** Everything the ConfidenceModule needs to know about pose reliability. */
data class LandmarkQuality(
    val visFace: Float,
    val visEars: Float,
    val visFeet: Float,
    val visShoulders: Float,
    val widthAgreement: Float,   // head height vs head width (0..1)
    val fitErrorFactor: Float,   // facial least-squares residual (0..1)
    val sizeFactor: Float,       // head pixel size vs thresholds (0..1)
    val spanFactor: Float,       // person span vs image height (0..1)
    val marginsOk: Boolean,      // head & feet inside frame with breathing room
    val multiplePeople: Boolean
)

data class FrameInfo(
    val imageW: Int,
    val imageH: Int,
    val crownY: Float?,
    val footY: Float?,
    val rollRejected: Boolean    // tilt > MAX_ROLL_DEG was seen and not corrected
)

/** Non-pose image statistics measured from the downscaled bitmap. */
data class ImageStats(
    val width: Int,
    val height: Int,
    val meanLuma: Double,     // 0..255
    val lumaStdDev: Double,   // contrast proxy
    val croppedHeadPx: Float? // head region size, for the quality bucket
)

data class ConfidenceBucket(
    val key: String,
    val label: String,
    val score: Int,
    val max: Int,
    val reason: String
)

/**
 * The 4-bucket additive confidence from the product spec (each up to +25).
 * Total is clamped to MAX_CONFIDENCE (92) so 98–100 is never claimed.
 */
data class ConfidenceBreakdown(
    val fullBodyVisible: Int,  // /25  landmarks present, head+feet in frame
    val verticalStanding: Int, // /25  not leaning / camera not rolled / legs straight
    val landmarkQuality: Int,  // /25  pose landmark confidence + head-fit agreement
    val imageQuality: Int      // /25  resolution enough + reasonable brightness
) {
    val total: Int
        get() = minOf(
            AppConstants.MAX_CONFIDENCE,
            fullBodyVisible + verticalStanding + landmarkQuality + imageQuality
        )
}

data class Note(val level: String, val text: String)              // "info" | "warn"
data class TraceSection(val title: String, val rows: List<Pair<String, String>>)

/**
 * Result of GeometryModule.estimate(). The first four fields are the contract
 * from the spec; the rest feeds the UI (breakdown, formula trace, error bar).
 */
data class HeightEstimate(
    val heightCm: Double,
    val confidence: ConfidenceBreakdown, // filled by ConfidenceModule
    val usedFallback: Boolean,
    val formulaText: String,
    val ok: Boolean = true,
    val reason: String? = null,          // short machine reason when weak/failed
    val headsTall: Double? = null,       // observed (person_px / head_px)
    val personPx: Float? = null,
    val headPx: Float? = null,
    val anchorHeightsCm: Map<String, Double> = emptyMap(), // per-anchor estimates
    val buckets: List<ConfidenceBucket> = emptyList(),
    val expectedErrorCm: Int = 0,
    val notes: List<Note> = emptyList(),
    val trace: List<TraceSection> = emptyList()
) {
    val heightFtInText: String
        get() {
            val totalIn = (heightCm / 2.54).roundToInt() // round ONCE, then split
            val ft = totalIn / 12
            val inch = totalIn % 12
            return "$ft ft $inch in"
        }

    val heightCmRounded: Int get() = heightCm.roundToInt()
}
