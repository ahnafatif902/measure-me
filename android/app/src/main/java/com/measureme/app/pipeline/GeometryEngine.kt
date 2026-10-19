package com.measureme.app.pipeline

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * What the user asked the engine to do. Sealed so every mode is explicit and
 * the fallback policy is a compile-checked `when`, not a string flag.
 */
sealed interface GeometryInput {

    /**
     * Mode A — population proportions from the body-to-head ratio.
     * Sex and age are carried for the record but are math-inactive this round.
     */
    data class Proportions(
        val sex: String? = null,
        val age: Int? = null,
    ) : GeometryInput

    /**
     * Mode B — the horizon ruler. [tilt] is the live gravity snapshot taken at
     * the shutter; [intrinsics] is the EXIF-derived focal length. Either being
     * null is an honest, cause-specific fallback (never a guessed value).
     */
    data class Horizon(
        val cameraHeightCm: Double,
        val tilt: Tilt?,
        val intrinsics: Intrinsics?,
    ) : GeometryInput

    /**
     * Mode C — an A4 sheet on the floor pins the camera pose. The corners are
     * detected on the analyzed bitmap by the analyzer and passed alongside.
     * Works from a gallery photo too: the homography supplies the orientation.
     */
    data class Reference(
        val gravityTilt: Tilt?,
        val intrinsics: Intrinsics?,
    ) : GeometryInput
}

/**
 * GeometryEngine — the single entry point for "turn a photo into a height".
 *
 * Routing + fallback policy live here and nowhere else:
 *
 *   Proportions → the existing Mode A result, untouched.
 *   Horizon     → HorizonGeometry with the live tilt; on ANY missing or
 *                 unusable input → Mode A result with a cause-specific
 *                 warning and a 1.5× wider margin.
 *   Reference   → A4Detector corners + ReferenceGeometry → camera height,
 *                 then the same tangent-ratio step as Horizon; same fallback.
 *
 * Error margins for a successful B/C solve are fractions of the height
 * (5% / 3.5%), scaled by the same photo-quality factor ConfidenceModule uses
 * (1 + (92 − score)/92). A fallback keeps Mode A's quality-adjusted margin
 * and widens it by FALLBACK_MARGIN_SCALE. The warning is never silent.
 */
class GeometryEngine {

    /**
     * Human-readable trace of the most recent solve — pixels, tangents,
     * residuals, rejection reasons. Logged by PoseAnalyzer: deliberate
     * observability so real-photo failures can be tuned without guessing.
     */
    var diagnostics: String = ""
        private set

    private fun trace(line: String) {
        diagnostics += line + "\n"
    }

    /** Which formula actually produced the number. */
    enum class Solved { PROPORTIONS, HORIZON, REFERENCE }

    /** Mode A's result — computed by the existing, unit-tested pipeline. */
    data class ProportionsResult(
        val heightCm: Double,
        val errorMarginCm: Int,
        val formulaText: String,
    )

    data class Result(
        val heightCm: Double,
        val solved: Solved,
        /** True when B/C was requested but Proportions had to stand in. */
        val fallback: Boolean,
        /** Cause-specific sentence shown to the user, or null. */
        val warning: String?,
        val errorMarginCm: Int,
        val formulaText: String,
        /** The camera height the math used (Horizon input / Reference solution). */
        val cameraHeightCm: Double? = null,
    )

    fun solve(
        input: GeometryInput,
        pose: PoseLandmarks,
        qualityScore: Int,
        modeA: ProportionsResult,
        referenceCorners: List<Px>?,
    ): Result {
        diagnostics = ""
        return when (input) {
            is GeometryInput.Proportions -> {
                trace("Proportions: H=${modeA.heightCm} ±${modeA.errorMarginCm} cm")
                Result(
                    heightCm = modeA.heightCm,
                    solved = Solved.PROPORTIONS,
                    fallback = false,
                    warning = null,
                    errorMarginCm = modeA.errorMarginCm,
                    formulaText = modeA.formulaText,
                )
            }

            is GeometryInput.Horizon -> solveHorizon(input, pose, qualityScore, modeA)
            is GeometryInput.Reference -> solveReference(input, pose, qualityScore, modeA, referenceCorners)
        }
    }

    // ------------------------------------------------------------------

    private fun solveHorizon(
        input: GeometryInput.Horizon,
        pose: PoseLandmarks,
        quality: Int,
        modeA: ProportionsResult,
    ): Result {
        val tilt = input.tilt ?: run {
            trace("Horizon: no live tilt (gallery photo)")
            return fallback(modeA, "Horizon needs the phone's live tilt sensor, and this photo has none. Proportions was used instead.")
        }
        val intrinsics = input.intrinsics ?: run {
            trace("Horizon: no focal length in the photo")
            return fallback(modeA, "Horizon needs the camera's focal length, which this photo doesn't carry. Proportions was used instead.")
        }
        val feet = pose.feet
        val crown = pose.headTop
        if (feet == null || crown == null) {
            trace("Horizon: missing feet/crown landmarks")
            return fallback(modeA, "Horizon needs your head and feet in frame. Proportions was used instead.")
        }

        val tans = HorizonGeometry.depressionTangents(feet, crown, tilt, intrinsics)
        trace(
            "Horizon in: h_c=${input.cameraHeightCm} tilt=(p=${tilt.pitchDeg}, r=${tilt.rollDeg}) " +
                "f=${intrinsics.fPx} feet=(${feet.x}, ${feet.y}) crown=(${crown.x}, ${crown.y}) " +
                "tanF=${tans?.first} tanC=${tans?.second}",
        )

        val solution = HorizonGeometry.solve(feet, crown, input.cameraHeightCm, tilt, intrinsics)
            ?: run {
                trace("Horizon: rejected by guards or plausibility window")
                return fallback(modeA, "Horizon geometry was unreliable on this photo. Proportions was used instead.")
            }
        trace("Horizon out: H=${solution.heightCm}")

        return measured(
            heightCm = solution.heightCm,
            solved = Solved.HORIZON,
            fraction = AppConstants.HORIZON_ERROR_FRACTION,
            quality = quality,
            formulaText = solution.formulaText,
            cameraHeightCm = input.cameraHeightCm,
        )
    }

    private fun solveReference(
        input: GeometryInput.Reference,
        pose: PoseLandmarks,
        quality: Int,
        modeA: ProportionsResult,
        corners: List<Px>?,
    ): Result {
        val intrinsics = input.intrinsics ?: run {
            trace("Reference: no focal length in the photo")
            return fallback(modeA, "Reference needs the camera's focal length, which this photo doesn't carry. Proportions was used instead.")
        }
        if (corners == null) {
            trace("Reference: no A4 quad detected")
            return fallback(
                modeA,
                "No A4 sheet found. Lay a sheet flat on the floor with all four corners visible and retake. " +
                    "Proportions was used instead.",
            )
        }

        val reference = ReferenceGeometry.solve(corners, intrinsics, input.gravityTilt?.pitchDeg)
            ?: run {
                trace("Reference: homography rejected corners=$corners gravity=${input.gravityTilt}")
                return fallback(
                    modeA,
                    "The A4 sheet's perspective could not be solved. Move the sheet closer to your feet and retake. " +
                        "Proportions was used instead.",
                )
            }
        trace(
            "Reference: h_c=${reference.cameraHeightCm} pitch=${reference.pitchDeg} " +
                "(gravity=${reference.pitchFromGravity}) roll=${reference.rollDeg} " +
                "ortho=${reference.orthoResidual} scale=${reference.scaleResidual}",
        )

        val feet = pose.feet
        val crown = pose.headTop
        if (feet == null || crown == null) {
            return fallback(modeA, "Reference needs your head and feet in frame. Proportions was used instead.")
        }

        // Gravity roll is a direct measurement and wins when present; the
        // homography's roll stands in for gallery photos.
        val roll = input.gravityTilt?.rollDeg ?: reference.rollDeg
        val solution = HorizonGeometry.solve(
            feet = feet,
            crown = crown,
            cameraHeightCm = reference.cameraHeightCm,
            tilt = Tilt(reference.pitchDeg, roll),
            intrinsics = intrinsics,
        ) ?: run {
            trace("Reference: person not measurable from the solved pose (feet=(${feet.x}, ${feet.y}) crown=(${crown.x}, ${crown.y}))")
            return fallback(
                modeA,
                "The A4 sheet fixed the camera pose, but your body was not measurable from it. Proportions was used instead.",
            )
        }
        trace("Reference out: H=${solution.heightCm}")

        return measured(
            heightCm = solution.heightCm,
            solved = Solved.REFERENCE,
            fraction = AppConstants.REFERENCE_ERROR_FRACTION,
            quality = quality,
            formulaText = "A4 homography → h_c = ${reference.cameraHeightCm.roundToInt()} cm; " +
                "H = h_c · (1 − tanφ_crown / tanφ_feet)",
            cameraHeightCm = reference.cameraHeightCm,
        )
    }

    // ------------------------------------------------------------------

    private fun measured(
        heightCm: Double,
        solved: Solved,
        fraction: Double,
        quality: Int,
        formulaText: String,
        cameraHeightCm: Double? = null,
    ): Result {
        // Same quality factor ConfidenceModule applies (photo quality, not
        // measurement quality — a clean photo earns a tighter margin).
        val errScale = 1.0 + (AppConstants.MAX_CONFIDENCE - quality).coerceAtLeast(0) / 92.0
        val margin = max(3, (heightCm * fraction * errScale).roundToInt())
        return Result(
            heightCm = heightCm,
            solved = solved,
            fallback = false,
            warning = null,
            errorMarginCm = margin,
            formulaText = formulaText,
            cameraHeightCm = cameraHeightCm,
        )
    }

    private fun fallback(modeA: ProportionsResult, cause: String): Result {
        val widened = max(3, (modeA.errorMarginCm * AppConstants.FALLBACK_MARGIN_SCALE).roundToInt())
        return Result(
            heightCm = modeA.heightCm,
            solved = Solved.PROPORTIONS,
            fallback = true,
            warning = "$cause Margin widened.",
            errorMarginCm = widened,
            formulaText = modeA.formulaText,
        )
    }
}
