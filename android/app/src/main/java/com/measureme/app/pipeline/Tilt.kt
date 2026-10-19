package com.measureme.app.pipeline

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The phone's orientation at the moment of capture, in exactly the convention
 * the Horizon math needs:
 *
 *  - pitchDeg: depression of the optical axis below the horizon.
 *              + = camera tilted down. 0 = perfectly level.
 *  - rollDeg:  rotation of the world-vertical in the image (deg).
 *              + = the world appears rotated the same way as a standard 2-D
 *              rotation R(roll) applied to image coordinates.
 *
 * Both signs are pinned by JVM unit tests (TiltMathTest) so the sensor data
 * can never quietly disagree with HorizonGeometry.
 */
data class Tilt(val pitchDeg: Double, val rollDeg: Double)

object TiltMath {

    /** Below this magnitude the gravity vector carries no direction. */
    private const val EPS = 1e-3

    /**
     * TYPE_GRAVITY (m/s², device axes) → [Tilt].
     *
     * Derivation for the portrait-locked back camera (device X = image right,
     * device −Y = image down, device −Z = optical axis):
     *
     *  - pitch: θ = asin(−gz / |g|). Vertical phone → g=(0,−9.81,0) → 0°.
     *    Screen-up flat (camera pointing at the floor) → g=(0,0,−9.81) → +90°.
     *  - roll: the world-up direction seen by the camera is u = −g/|g|; its
     *    image-plane components evaluate to (sinβ, −cosβ) for a device roll
     *    β, which is R(β)·(0,−1) — so ρ = atan2(−gx, −gy).
     *
     * Returns null when gravity is (nearly) zero — no usable orientation.
     */
    fun fromGravity(gx: Float, gy: Float, gz: Float): Tilt? {
        val n = sqrt((gx * gx + gy * gy + gz * gz).toDouble())
        if (n < EPS) return null
        val pitch = Math.toDegrees(asin((-gz / n).coerceIn(-1.0, 1.0)))
        val roll = Math.toDegrees(atan2(-gx.toDouble(), -gy.toDouble()))
        return Tilt(pitchDeg = pitch, rollDeg = roll)
    }
}
