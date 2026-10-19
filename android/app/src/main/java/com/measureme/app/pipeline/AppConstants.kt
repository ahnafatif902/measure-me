package com.measureme.app.pipeline

/**
 * Every tunable number in the app lives here, with the reason it exists.
 * See README.md § "Formula + constants" for the full justification table.
 *
 * The two headline values are the ones from the product spec:
 *   AVERAGE_HEAD_CM = 23.0      → adult crown→chin length (population average)
 *   BODY_TO_HEAD_RATIO = 7.5    → adult "heads tall" canon, used ONLY by the
 *                                 weak fallback when no head segment is available
 */
object AppConstants {

    // ---- The anthropometric priors (both live on the design's sliders) -----
    const val AVERAGE_HEAD_CM = 23.0   // adult avg; men ≈23.3, women ≈22.2
    const val BODY_TO_HEAD_RATIO = 7.5 // art/anatomy canon for adult proportions
    const val HEAD_CM_MIN = 16.0       // "Average head length" slider bounds (design)
    const val HEAD_CM_MAX = 32.0
    const val HEADS_MIN = 6.0          // "Canonical stature" slider bounds (design)
    const val HEADS_MAX = 8.5

    // ---- Absolute-size anchors used for cross-checks / weaker estimates ----
    const val HEAD_WIDTH_CM = 14.9          // ear-to-ear width, adult avg
    const val SHOULDER_JOINT_CM = 34.0      // shoulder joint centres, adult avg
    const val SOLE_TO_TOE_TIP_CM = 0.7      // toe-tip landmark floats above floor

    // ---- Canonical facial proportions (fraction of head height, crown=0) ---
    // Used to fit the head size from eyes+nose+mouth landmarks at once.
    const val FACE_DEPTH_EYES = 0.47
    const val FACE_DEPTH_NOSE = 0.72
    const val FACE_DEPTH_MOUTH = 0.84
    const val EAR_WIDTH_TO_HEAD_HEIGHT = 0.64

    // ---- Plausibility windows (drive confidence, not silent clamping) ------
    const val HEADS_TALL_MIN = 6.4
    const val HEADS_TALL_MAX = 8.3
    const val HEIGHT_MIN_CM = 130.0
    const val HEIGHT_MAX_CM = 225.0
    const val HEIGHT_HARD_MIN_CM = 120.0   // outside → treat detection as failed
    const val HEIGHT_HARD_MAX_CM = 235.0

    // ---- Modes B/C error budgets (fraction of the measured height) ---------
    const val HORIZON_ERROR_FRACTION = 0.05    // 5%: the horizon ruler, no distance needed
    const val REFERENCE_ERROR_FRACTION = 0.035 // 3.5%: the A4 pins the camera pose
    const val FALLBACK_MARGIN_SCALE = 1.5      // widened margin when B/C falls back to A

    // ---- Confidence rules ---------------------------------------------------
    const val MAX_CONFIDENCE = 92               // never claim 98–100: this method can't
    const val MANUAL_MAX_CONFIDENCE = 65        // manual taps are never as good as detection
    const val MANUAL_PRIOR_MAX_CONFIDENCE = 36  // derived chin = the prior, not a measurement
    const val MIN_HEAD_PX = 24f          // below this, face landmarks are guesswork
    const val GOOD_HEAD_PX = 56f         // at/above this, face landmarks are solid
    const val MAX_ROLL_DEG = 10.0        // camera-roll guesses beyond this rejected
    const val MAX_LEAN_CORRECTION_DEG = 10.0
    const val LEAN_GOOD_DEG = 2.0        // ≤2° counts as perfectly vertical
    const val LEAN_BAD_DEG = 15.0        // ≥15° → straightness factor at its floor
    const val KNEE_STRAIGHT_DEG = 168.0
    const val KNEE_SUSPECT_DEG = 130.0

    // ---- Landmark gates -----------------------------------------------------
    const val VIS_MIN = 0.35f   // likelihood required for face/hand-quality points
    const val VIS_LOW = 0.25f   // looser bar for feet (often partially occluded)

    // ---- Image pipeline -----------------------------------------------------
    const val MAX_ANALYSIS_DIM = 1440  // downscale long edge before detection
    const val MIN_IMAGE_DIM = 480      // smaller than this → unusable for measuring
    const val BRIGHTNESS_MIN = 40.0    // mean luminance window for "well lit"
    const val BRIGHTNESS_MAX = 235.0
}
