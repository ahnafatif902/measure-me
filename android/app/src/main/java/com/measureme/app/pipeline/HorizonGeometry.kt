package com.measureme.app.pipeline

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * HorizonGeometry — Mode B: real height from ONE photo, using the horizon
 * as the ruler.
 *
 * The idea: a camera at known height h_c looking slightly down sees the
 * floor at a known depression angle. A person standing on that floor spans
 * from their feet (depression angle φ_feet) to their crown (φ_crown); the
 * ratio of those angles gives the ratio of their height to the camera's,
 * with NO distance measurement:
 *
 *     H = h_c · (1 − tanφ_crown / tanφ_feet)
 *
 * Pitch and roll of the phone (from the gravity sensor at the shutter
 * moment) are de-rotated exactly — the formula above is the exact tangent
 * form, not a small-angle or cross-ratio approximation. Derivation:
 *
 *  1. De-roll the image: dx = (x−cX)·cosρ + (y−cY)·sinρ
 *                       dv = −(x−cX)·sinρ + (y−cY)·cosρ
 *  2. For a point whose de-rolled offset from the principal point is
 *     (dx, dv), its depression below the camera axis satisfies
 *
 *         tanφ = (dv·cosθ + fPx·sinθ) / ‖(dx, dv·sinθ − fPx·cosθ)‖
 *
 *     (θ = camera pitch at capture; + = tilted down).
 *  3. The height follows from the ratio of step 2 at the feet and crown.
 *
 * Pure Kotlin — no Android imports — so the JVM tests can forward-project
 * synthetic people and check the recovery to fractions of a centimetre.
 */
object HorizonGeometry {

    /**
     * Beyond this pitch the subject is met near-overhead: the feet and crown
     * angles blow up and the estimate becomes meaningless. Callers fall
     * back to Proportions with a warning.
     */
    const val MAX_PITCH_DEG = 60.0

    data class Solution(
        val heightCm: Double,
        val tanPhiFeet: Double,
        val tanPhiCrown: Double,
        val formulaText: String,
    )

    /**
     * @param feet   ground-contact pixel of the person
     * @param crown  crown-of-head pixel
     * @param cameraHeightCm the height the phone was held at
     * @param tilt   live pitch/roll at the shutter moment
     * @param intrinsics f_px + principal point of the analyzed bitmap
     * @return null when the geometry is unusable (caller falls back)
     */
    fun solve(
        feet: Px,
        crown: Px,
        cameraHeightCm: Double,
        tilt: Tilt,
        intrinsics: Intrinsics,
    ): Solution? {
        if (cameraHeightCm <= 0.0) return null

        val (tanFeet, tanCrown) = depressionTangents(feet, crown, tilt, intrinsics) ?: return null

        // The feet must be genuinely below the horizon (positive depression)
        // and the crown must sit at a smaller depression than the feet —
        // otherwise head/feet are swapped, upside down, or on the horizon.
        if (tanFeet <= 1e-3) return null
        if (tanCrown >= tanFeet) return null

        val height = cameraHeightCm * (1.0 - tanCrown / tanFeet)

        // Outside the hard plausibility window the geometry is telling us the
        // photo (or the camera height) was wrong — fall back rather than show it.
        if (height !in AppConstants.HEIGHT_HARD_MIN_CM..AppConstants.HEIGHT_HARD_MAX_CM) return null

        return Solution(
            heightCm = height,
            tanPhiFeet = tanFeet,
            tanPhiCrown = tanCrown,
            formulaText = "H = h_c · (1 − tanφ_crown / tanφ_feet), pitch & roll de-rotated",
        )
    }

    /**
     * Exact depression tangents of the feet and crown in the capture frame —
     * the core of Mode B, exposed separately so a solve can be diagnosed
     * (rejected inputs, fallback reasons) without duplicating the math.
     */
    fun depressionTangents(
        feet: Px,
        crown: Px,
        tilt: Tilt,
        intrinsics: Intrinsics,
    ): Pair<Double, Double>? {
        if (intrinsics.fPx <= 0.0) return null
        if (abs(tilt.pitchDeg) > MAX_PITCH_DEG) return null

        val theta = Math.toRadians(tilt.pitchDeg)
        val rho = Math.toRadians(tilt.rollDeg)
        val f = intrinsics.fPx

        fun tanPhi(p: Px): Double {
            val ox = p.x - intrinsics.cX
            val oy = p.y - intrinsics.cY
            // Step 1 — de-roll so the world-vertical is the image's y axis.
            val dx = ox * cos(rho) + oy * sin(rho)
            val dv = -ox * sin(rho) + oy * cos(rho)
            // Step 2 — exact depression tangent, pitch removed.
            val num = dv * cos(theta) + f * sin(theta)
            val den = hypot(dx, dv * sin(theta) - f * cos(theta))
            return num / den
        }

        return tanPhi(feet) to tanPhi(crown)
    }
}
