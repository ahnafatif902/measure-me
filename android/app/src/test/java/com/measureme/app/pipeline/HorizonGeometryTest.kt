package com.measureme.app.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Proves HorizonGeometry returns the exact expected values.
 *
 * Method: a forward projector builds the pixels of a synthetic person under
 * the SAME pinhole model the solver inverts (camera at a known height, known
 * person height/distance, known pitch, roll and azimuth). solve() must
 * recover the planted height to within a tenth of a centimetre.
 */
class HorizonGeometryTest {

    private val f = 1000.0
    private val intrinsics = Intrinsics(f, 500.0, 500.0, "test")

    /**
     * Forward-project a standing person. Level frame: x right, y down,
     * z forward. Camera pitched down [pitchDeg]; person upright, feet on the
     * floor at [distanceCm], [azimuthDeg] off the camera axis. Roll is the
     * exact 2-D content rotation about the principal point (K·Rz·K⁻¹).
     */
    private fun projectPerson(
        cameraHeightCm: Double,
        heightCm: Double,
        distanceCm: Double,
        pitchDeg: Double,
        rollDeg: Double = 0.0,
        azimuthDeg: Double = 0.0,
    ): Pair<Px, Px> {
        val t = Math.toRadians(pitchDeg)
        val a = Math.toRadians(azimuthDeg)
        val r = Math.toRadians(rollDeg)

        val x = distanceCm * sin(a)
        val yFeet = cameraHeightCm * cos(t) - distanceCm * cos(a) * sin(t)
        val zFeet = cameraHeightCm * sin(t) + distanceCm * cos(a) * cos(t)

        // Crown = feet + H · world-up; world-up in the camera frame is
        // (0, −cosθ, −sinθ) after the pitch rotation.
        val yCrown = yFeet - heightCm * cos(t)
        val zCrown = zFeet - heightCm * sin(t)

        fun pixel(px: Double, py: Double, pz: Double): Px {
            val ux = f * px / pz
            val vy = f * py / pz
            val u = intrinsics.cX + ux * cos(r) - vy * sin(r)
            val v = intrinsics.cY + ux * sin(r) + vy * cos(r)
            return Px(u.toFloat(), v.toFloat())
        }

        return pixel(x, yFeet, zFeet) to pixel(x, yCrown, zCrown)
    }

    /** Gravity vector (device axes, |g| = 9.81) for a given pitch/roll. */
    private fun gravityFor(pitchDeg: Double, rollDeg: Double): Triple<Float, Float, Float> {
        val t = Math.toRadians(pitchDeg)
        val r = Math.toRadians(rollDeg)
        val g = 9.81
        return Triple(
            (-g * cos(t) * sin(r)).toFloat(),
            (-g * cos(t) * cos(r)).toFloat(),
            (-g * sin(t)).toFloat(),
        )
    }

    // ------------------------------------------------------------------
    // Exact recovery
    // ------------------------------------------------------------------

    @Test
    fun `hand-computed case returns exactly 175_0 cm`() {
        // Hand-computed pixels for h_c=135, H=175, d=300, θ=10°, f=1000,
        // c=(500,500): feet (500, 753.554), crown (500, 182.831).
        val sol = HorizonGeometry.solve(
            feet = Px(500f, 753.554f),
            crown = Px(500f, 182.831f),
            cameraHeightCm = 135.0,
            tilt = Tilt(pitchDeg = 10.0, rollDeg = 0.0),
            intrinsics = intrinsics,
        )
        assertNotNull(sol)
        assertEquals(175.0, sol!!.heightCm, 0.05)
    }

    @Test
    fun `level camera recovers the planted height`() {
        val (feet, crown) = projectPerson(170.0, 180.0, 400.0, pitchDeg = 0.0)
        val sol = HorizonGeometry.solve(feet, crown, 170.0, Tilt(0.0, 0.0), intrinsics)
        assertNotNull(sol)
        assertEquals(180.0, sol!!.heightCm, 0.1)
    }

    @Test
    fun `tilted, rolled and off-axis person is recovered exactly`() {
        val (feet, crown) = projectPerson(
            cameraHeightCm = 90.0, heightCm = 168.5, distanceCm = 260.0,
            pitchDeg = 18.0, rollDeg = -7.5, azimuthDeg = 22.0,
        )
        val sol = HorizonGeometry.solve(feet, crown, 90.0, Tilt(18.0, -7.5), intrinsics)
        assertNotNull(sol)
        assertEquals(168.5, sol!!.heightCm, 0.1)
    }

    @Test
    fun `recovery holds across camera heights`() {
        for (cameraHeight in listOf(60.0, 135.0, 220.0)) {
            val (feet, crown) = projectPerson(cameraHeight, 155.0, 450.0, pitchDeg = 8.0)
            val sol = HorizonGeometry.solve(feet, crown, cameraHeight, Tilt(8.0, 0.0), intrinsics)
            assertNotNull("h_c=$cameraHeight", sol)
            assertEquals("h_c=$cameraHeight", 155.0, sol!!.heightCm, 0.1)
        }
    }

    @Test
    fun `sensor tilt and solver agree end to end`() {
        // The gravity vector for (pitch, roll) → TiltMath → solve must equal
        // solving with the planted (pitch, roll): SensorReader and
        // HorizonGeometry cannot drift apart in sign or convention.
        val (gx, gy, gz) = gravityFor(pitchDeg = 12.0, rollDeg = 5.0)
        val tilt = TiltMath.fromGravity(gx, gy, gz)
        assertNotNull(tilt)

        val (feet, crown) = projectPerson(140.0, 172.0, 320.0, pitchDeg = 12.0, rollDeg = 5.0)
        val sol = HorizonGeometry.solve(feet, crown, 140.0, tilt!!, intrinsics)
        assertNotNull(sol)
        assertEquals(172.0, sol!!.heightCm, 0.15)
    }

    @Test
    fun `roll must be de-rotated, ignoring it gives a wrong answer`() {
        val (feet, crown) = projectPerson(
            135.0, 175.0, 300.0, pitchDeg = 10.0, rollDeg = 8.0, azimuthDeg = 15.0,
        )
        val correct = HorizonGeometry.solve(feet, crown, 135.0, Tilt(10.0, 8.0), intrinsics)
        assertEquals(175.0, correct!!.heightCm, 0.1)

        val ignoringRoll = HorizonGeometry.solve(feet, crown, 135.0, Tilt(10.0, 0.0), intrinsics)
        // Whatever it returns (it may even refuse), it must NOT be the right
        // answer — the de-roll term is load-bearing.
        if (ignoringRoll != null) {
            assertTrue(
                "roll=0 result ${ignoringRoll.heightCm} should differ from 175",
                kotlin.math.abs(ignoringRoll.heightCm - 175.0) > 2.0,
            )
        }
    }

    // ------------------------------------------------------------------
    // Honest failure — null instead of a wrong number
    // ------------------------------------------------------------------

    @Test
    fun `feet above the horizon are rejected`() {
        // Camera on the floor looking up: feet pixels sit above the principal
        // point at pitch 0 → no depression to measure with.
        val sol = HorizonGeometry.solve(
            feet = Px(500f, 300f), crown = Px(500f, 100f),
            cameraHeightCm = 10.0, tilt = Tilt(0.0, 0.0), intrinsics = intrinsics,
        )
        assertNull(sol)
    }

    @Test
    fun `implausible heights are rejected, not shown`() {
        val (feet, crown) = projectPerson(135.0, 10.0, 300.0, pitchDeg = 10.0)
        val sol = HorizonGeometry.solve(feet, crown, 135.0, Tilt(10.0, 0.0), intrinsics)
        assertNull(sol) // 10 cm person → outside the hard window → fallback outside
    }

    @Test
    fun `near-overhead pitch is rejected`() {
        val sol = HorizonGeometry.solve(
            feet = Px(500f, 900f), crown = Px(500f, 300f),
            cameraHeightCm = 135.0, tilt = Tilt(70.0, 0.0), intrinsics = intrinsics,
        )
        assertNull(sol)
    }
}
