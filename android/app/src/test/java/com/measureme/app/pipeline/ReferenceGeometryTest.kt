package com.measureme.app.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

/**
 * Round-trips ReferenceGeometry through a forward projection of a real A4
 * sheet (21 × 29.7 cm) lying on a floor: plant a camera height and pitch,
 * project the sheet's corners, hand the pixels to solve() — scrambled
 * order, exactly like a detector would emit — and demand the planted
 * values back.
 */
class ReferenceGeometryTest {

    private val f = 1200.0
    private val intrinsics = Intrinsics(f, 540.0, 720.0, "test")

    /**
     * World frame: x east, y north, z up. Camera at (0, 0, heightCm), yaw
     * [yawDeg] (0 = facing north), pitched down [pitchDeg], rolled
     * [rollDeg]. Paper centre at ([eastCm], [northCm], 0), yaw [paperYawDeg].
     */
    private fun projectPaper(
        heightCm: Double,
        pitchDeg: Double,
        rollDeg: Double = 0.0,
        yawDeg: Double = 0.0,
        eastCm: Double,
        northCm: Double,
        paperYawDeg: Double,
    ): List<Px> {
        val pitch = Math.toRadians(pitchDeg)
        val roll = Math.toRadians(rollDeg)
        val yaw = Math.toRadians(yawDeg)
        val pYaw = Math.toRadians(paperYawDeg)

        val forward = doubleArrayOf(sin(yaw) * cos(pitch), cos(yaw) * cos(pitch), -sin(pitch))
        val right0 = doubleArrayOf(cos(yaw), -sin(yaw), 0.0)
        val down0 = cross(forward, right0)
        val right = doubleArrayOf(
            right0[0] * cos(roll) + down0[0] * sin(roll),
            right0[1] * cos(roll) + down0[1] * sin(roll),
            right0[2] * cos(roll) + down0[2] * sin(roll),
        )
        val down = doubleArrayOf(
            -right0[0] * sin(roll) + down0[0] * cos(roll),
            -right0[1] * sin(roll) + down0[1] * cos(roll),
            -right0[2] * sin(roll) + down0[2] * cos(roll),
        )

        val paper = listOf(
            Pair(0.0, 0.0),
            Pair(ReferenceGeometry.A4_SHORT_CM, 0.0),
            Pair(ReferenceGeometry.A4_SHORT_CM, ReferenceGeometry.A4_LONG_CM),
            Pair(0.0, ReferenceGeometry.A4_LONG_CM),
        )

        return paper.map { (px, py) ->
            val wx = eastCm + px * cos(pYaw) - py * sin(pYaw)
            val wy = northCm + px * sin(pYaw) + py * cos(pYaw)
            val rel = doubleArrayOf(wx, wy, -heightCm)
            val x = dot(rel, right)
            val y = dot(rel, down)
            val z = dot(rel, forward)
            Px((intrinsics.cX + f * x / z).toFloat(), (intrinsics.cY + f * y / z).toFloat())
        }
    }

    // ------------------------------------------------------------------

    @Test
    fun `straight view recovers camera height and pitch`() {
        val corners = projectPaper(
            heightCm = 150.0, pitchDeg = 20.0,
            eastCm = 0.0, northCm = 250.0, paperYawDeg = 0.0,
        )
        val pose = ReferenceGeometry.solve(corners, intrinsics)
        assertNotNull(pose)
        assertEquals(150.0, pose!!.cameraHeightCm, 1.5)
        assertEquals(20.0, pose.pitchDeg, 1.0)
        assertTrue(pose.orthoResidual < 0.05)
    }

    @Test
    fun `off-axis rolled view with scrambled detector order still recovers`() {
        val c = projectPaper(
            heightCm = 165.0, pitchDeg = 12.0, rollDeg = -5.0, yawDeg = 15.0,
            eastCm = 60.0, northCm = 220.0, paperYawDeg = 120.0,
        )
        // Detectors emit corners in cyclic order but with no fixed start.
        val scrambled = listOf(c[2], c[0], c[3], c[1])
        val pose = ReferenceGeometry.solve(scrambled, intrinsics)
        assertNotNull(pose)
        assertEquals(165.0, pose!!.cameraHeightCm, 1.5)
        assertEquals(12.0, pose.pitchDeg, 1.0)
        // Generator roll −5° rotates the basis one way; the Tilt convention
        // describes the world-vertical's rotation in the image, hence +5°.
        assertEquals(5.0, pose.rollDeg, 0.5)
    }

    @Test
    fun `gravity pitch wins over the homography estimate`() {
        val corners = projectPaper(
            heightCm = 150.0, pitchDeg = 20.0,
            eastCm = 0.0, northCm = 250.0, paperYawDeg = 0.0,
        )
        val pose = ReferenceGeometry.solve(corners, intrinsics, gravityPitchDeg = 19.6)
        assertNotNull(pose)
        assertEquals(150.0, pose!!.cameraHeightCm, 1.5)
        assertEquals(19.6, pose.pitchDeg, 0.001)
        assertTrue(pose.pitchFromGravity)
    }

    @Test
    fun `grazing view still recovers within a riding margin`() {
        // Shallow view: the sheet is 4.2 m away, seen at 65° incidence — the
        // hardest realistic geometry for a 4-point decomposition.
        val corners = projectPaper(
            heightCm = 190.0, pitchDeg = 8.0,
            eastCm = 0.0, northCm = 420.0, paperYawDeg = 30.0,
        )
        val pose = ReferenceGeometry.solve(corners, intrinsics)
        assertNotNull(pose)
        assertEquals(190.0, pose!!.cameraHeightCm, 5.0)
    }

    @Test
    fun `cyclic rotations of the corner list give identical results`() {
        val c = projectPaper(
            heightCm = 140.0, pitchDeg = 25.0,
            eastCm = -40.0, northCm = 200.0, paperYawDeg = 200.0,
        )
        val a = ReferenceGeometry.solve(c, intrinsics)!!
        val b = ReferenceGeometry.solve(listOf(c[3], c[1], c[0], c[2]), intrinsics)!!
        assertEquals(a.cameraHeightCm, b.cameraHeightCm, 1e-6)
        assertEquals(a.pitchDeg, b.pitchDeg, 1e-6)
    }

    @Test
    fun `degenerate collinear corners fail honestly`() {
        val line = listOf(Px(100f, 100f), Px(200f, 150f), Px(300f, 200f), Px(400f, 250f))
        assertNull(ReferenceGeometry.solve(line, intrinsics))
    }

    // tiny helpers for the projector

    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
}
