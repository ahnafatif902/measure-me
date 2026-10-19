package com.measureme.app.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Pins the gravity → pitch/roll sign conventions used by Mode B. */
class TiltMathTest {

    @Test
    fun `vertical phone is level - zero pitch, zero roll`() {
        val t = TiltMath.fromGravity(0f, -9.81f, 0f)
        assertNotNull(t)
        assertEquals(0.0, t!!.pitchDeg, 0.01)
        assertEquals(0.0, t.rollDeg, 0.01)
    }

    @Test
    fun `flat, screen-up phone points the camera straight down - 90 degrees`() {
        val t = TiltMath.fromGravity(0f, 0f, -9.81f)
        assertEquals(90.0, t!!.pitchDeg, 0.01)
    }

    @Test
    fun `flat, screen-down phone points the camera straight up - minus 90 degrees`() {
        val t = TiltMath.fromGravity(0f, 0f, 9.81f)
        assertEquals(-90.0, t!!.pitchDeg, 0.01)
    }

    @Test
    fun `pure pitch keeps roll at zero`() {
        // Gravity for a camera pitched down 10° (no roll), |g| = 9.81.
        val t = TiltMath.fromGravity(0f, (-9.81 * 0.98480775).toFloat(), (-9.81 * 0.17364818).toFloat())
        assertEquals(10.0, t!!.pitchDeg, 0.01)
        assertEquals(0.0, t.rollDeg, 0.01)
    }

    @Test
    fun `roll is recovered from the in-plane gravity component`() {
        val r = Math.toRadians(30.0)
        val t = TiltMath.fromGravity(
            (-9.81 * Math.sin(r)).toFloat(),
            (-9.81 * Math.cos(r)).toFloat(),
            0f,
        )
        assertEquals(30.0, t!!.rollDeg, 0.01)
    }

    @Test
    fun `pitch and roll combine independently`() {
        // g_device = (−|g|cosθ·sinρ, −|g|cosθ·cosρ, −|g|sinθ) for pitch θ, roll ρ.
        val theta = Math.toRadians(18.0)
        val rho = Math.toRadians(-7.5)
        val t = TiltMath.fromGravity(
            (-9.81 * Math.cos(theta) * Math.sin(rho)).toFloat(),
            (-9.81 * Math.cos(theta) * Math.cos(rho)).toFloat(),
            (-9.81 * Math.sin(theta)).toFloat(),
        )
        assertEquals(18.0, t!!.pitchDeg, 0.01)
        assertEquals(-7.5, t.rollDeg, 0.01)
    }

    @Test
    fun `zero gravity has no direction - null`() {
        assertNull(TiltMath.fromGravity(0f, 0f, 0f))
    }
}
