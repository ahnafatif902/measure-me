package com.measureme.app.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for GeometryModule using fake points only — no emulator.
 * Run with:  gradlew :app:testDebugUnitTest
 *
 * Synthetic "person" used below: crown at y = 200 px, head height 180 px,
 * feet at y = 1550 px in a 1000 × 2000 image.
 *   eyes  y = 200 + 0.47·180 = 284.6   nose y = 329.6   mouth y = 351.2
 *   person_px = 1350 → heads tall = 1350 / 180 = 7.5 → height = 7.5 × 23 = 172.5 cm
 */
class GeometryModuleTest {

    private val geo = GeometryModule()

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private fun lm(name: LandmarkName, x: Float, y: Float, likelihood: Float = 0.95f) =
        MappedLandmark(name, x, y, z = 0f, likelihood = likelihood)

    /** A full synthetic standing person with exact canonical face proportions. */
    private fun syntheticPerson(
        crownY: Float = 200f,
        headPx: Float = 180f,
        footIndexY: Float = 1550f
    ): List<MappedLandmark> {
        val eyeY = crownY + (0.47 * headPx).toFloat()
        val noseY = crownY + (0.72 * headPx).toFloat()
        val mouthY = crownY + (0.84 * headPx).toFloat()
        val hipY = crownY + headPx * 2.6f
        val kneeY = crownY + headPx * 4.3f
        val ankleY = footIndexY - 20f
        val heelY = footIndexY - 10f
        return listOf(
            lm(LandmarkName.LEFT_EYE, 455f, eyeY),
            lm(LandmarkName.RIGHT_EYE, 545f, eyeY),
            lm(LandmarkName.NOSE, 500f, noseY),
            lm(LandmarkName.LEFT_MOUTH, 480f, mouthY),
            lm(LandmarkName.RIGHT_MOUTH, 520f, mouthY),
            lm(LandmarkName.LEFT_EAR, 430f, eyeY + 10f),
            lm(LandmarkName.RIGHT_EAR, 570f, eyeY + 10f),
            lm(LandmarkName.LEFT_SHOULDER, 400f, crownY + headPx * 1.3f),
            lm(LandmarkName.RIGHT_SHOULDER, 600f, crownY + headPx * 1.3f),
            lm(LandmarkName.LEFT_HIP, 440f, hipY),
            lm(LandmarkName.RIGHT_HIP, 560f, hipY),
            lm(LandmarkName.LEFT_KNEE, 440f, kneeY),
            lm(LandmarkName.RIGHT_KNEE, 560f, kneeY),
            lm(LandmarkName.LEFT_ANKLE, 460f, ankleY),
            lm(LandmarkName.RIGHT_ANKLE, 540f, ankleY),
            lm(LandmarkName.LEFT_HEEL, 455f, heelY),
            lm(LandmarkName.RIGHT_HEEL, 545f, heelY),
            lm(LandmarkName.LEFT_FOOT_INDEX, 470f, footIndexY),
            lm(LandmarkName.RIGHT_FOOT_INDEX, 530f, footIndexY)
        )
    }

    /** Minimal PoseLandmarks for estimate() tests (quality values are neutral). */
    private fun landmarks(
        headTop: Px?,
        feet: Px?,
        headHeightPx: Float?,
        headPxFromWidth: Float? = null,
        headMethod: String = if (headHeightPx != null) "face-fit" else "none",
        rollRejected: Boolean = false
    ): PoseLandmarks {
        val q = LandmarkQuality(
            visFace = 0.9f, visEars = 0.9f, visFeet = 0.9f, visShoulders = 0.9f,
            widthAgreement = 1f, fitErrorFactor = 1f, sizeFactor = 1f, spanFactor = 1f,
            marginsOk = true, multiplePeople = false
        )
        val points = HashMap<LandmarkName, Px>()
        if (headTop != null) points[LandmarkName.NOSE] = headTop
        if (feet != null) points[LandmarkName.LEFT_FOOT_INDEX] = feet
        return PoseLandmarks(
            headTop = headTop, chin = null, feet = feet,
            headHeightPx = headHeightPx, headMethod = headMethod,
            headPxFromWidth = headPxFromWidth, shoulderWidthPx = null,
            rollDeg = 0.0, leanDeg = 0.0, kneeDeg = 180.0, quality = q,
            points = points, frame = FrameInfo(1000, 2000, headTop?.y, feet?.y, rollRejected)
        )
    }

    // ------------------------------------------------------------------
    // estimate() — the primary formula
    // ------------------------------------------------------------------

    @Test
    fun `primary path implements the documented formula exactly`() {
        val est = geo.estimate(
            landmarks(headTop = Px(100f, 200f), feet = Px(100f, 1550f), headHeightPx = 180f)
        )
        assertEquals(1350f, est.personPx!!, 0.01f)
        assertEquals(7.5, est.headsTall!!, 1e-6)
        assertEquals(172.5, est.heightCm, 1e-6) // (1350 / 180) * 23
        assertFalse(est.usedFallback)
        assertEquals("height_cm = (person_px / head_px) * average_head_cm", est.formulaText)
        assertTrue(est.ok)
    }

    @Test
    fun `height uses the tunable average_head_cm`() {
        val est = geo.estimate(
            landmarks(headTop = Px(100f, 200f), feet = Px(100f, 1550f), headHeightPx = 180f),
            averageHeadCm = 25.0
        )
        assertEquals(187.5, est.heightCm, 1e-6) // 7.5 * 25
    }

    // ------------------------------------------------------------------
    // estimate() — weak paths
    // ------------------------------------------------------------------

    @Test
    fun `missing crown returns weak estimate, not a crash`() {
        val est = geo.estimate(landmarks(headTop = null, feet = Px(100f, 1550f), headHeightPx = 180f))
        assertFalse(est.ok)
        assertEquals("Missing head point", est.reason)
        assertEquals(7.5 * 23.0, est.heightCm, 1e-6) // population prior
        assertTrue(est.usedFallback)
    }

    @Test
    fun `missing feet returns weak estimate`() {
        val est = geo.estimate(landmarks(headTop = Px(100f, 200f), feet = null, headHeightPx = 180f))
        assertFalse(est.ok)
        assertEquals("Missing feet point", est.reason)
    }

    @Test
    fun `no head size at all falls back to the ratio prior and warns`() {
        val est = geo.estimate(landmarks(headTop = Px(100f, 200f), feet = Px(100f, 1550f), headHeightPx = null))
        assertTrue(est.usedFallback)
        assertEquals(172.5, est.heightCm, 1e-6) // 7.5 * 23 — no photo scale exists
        assertEquals("fallback ≈ body_to_head_ratio * average_head_cm", est.formulaText)
        assertTrue(est.notes.any { it.level == "warn" && it.text.contains("population-average") })
    }

    @Test
    fun `ear width tier derives head size and still uses the formula`() {
        val est = geo.estimate(
            landmarks(
                headTop = Px(100f, 300f), feet = Px(100f, 1500f),
                headHeightPx = null, headPxFromWidth = 160f
            )
        )
        assertEquals(1200.0 / 160.0, est.headsTall!!, 1e-4) // 7.5
        assertEquals(172.5, est.heightCm, 1e-4)
        assertTrue(est.usedFallback) // head size itself is a fallback here
        assertTrue(est.formulaText.contains("ear_width"))
        assertTrue(est.notes.any { it.text.contains("ear width") })
    }

    @Test
    fun `out-of-window heads-tall ratio adds a warning note`() {
        val est = geo.estimate(
            landmarks(headTop = Px(100f, 100f), feet = Px(100f, 2000f), headHeightPx = 200f)
        )
        assertEquals(9.5, est.headsTall!!, 1e-6) // way outside 6.4–8.3
        assertTrue(est.notes.any { it.level == "warn" && it.text.contains("body-to-head ratio") })
    }

    // ------------------------------------------------------------------
    // derive() — landmarks → head/feet points
    // ------------------------------------------------------------------

    @Test
    fun `face fit recovers crown and head height from canonical proportions`() {
        val pl = geo.derive(syntheticPerson(), imageW = 1000, imageH = 2000)
        assertEquals("face-fit", pl.headMethod)
        assertNotNull(pl.headTop)
        assertNotNull(pl.headHeightPx)
        assertEquals(200f, pl.headTop!!.y, 2f)      // crown
        assertEquals(180f, pl.headHeightPx!!, 2f)   // head height
    }

    @Test
    fun `feet point is the lowest visible foot landmark`() {
        val pl = geo.derive(syntheticPerson(), imageW = 1000, imageH = 2000)
        assertEquals(1550f, pl.feet!!.y, 0.01f) // foot index, not the ankle or heel
    }

    @Test
    fun `end-to-end derive plus estimate gives the expected height`() {
        val pl = geo.derive(syntheticPerson(), imageW = 1000, imageH = 2000)
        val est = geo.estimate(pl)
        assertEquals(172.5, est.heightCm, 1.0) // ±1 cm for float rounding
        assertFalse(est.usedFallback)
    }

    @Test
    fun `missing face landmarks fall back to eye spacing`() {
        val raw = syntheticPerson().filter {
            it.name in setOf(LandmarkName.LEFT_EYE, LandmarkName.RIGHT_EYE) ||
                it.name.index >= LandmarkName.LEFT_ANKLE.index
        }
        val pl = geo.derive(raw, imageW = 1000, imageH = 2000)
        assertEquals("eye-spacing", pl.headMethod)
        assertNotNull(pl.headHeightPx)
    }

    @Test
    fun `small camera roll is measured and deskewed`() {
        // Heel line: dx = 90 px, dy = 7.87 px → atan(7.87/90) ≈ 5.0° of roll.
        val raw = syntheticPerson().map {
            when (it.name) {
                LandmarkName.LEFT_HEEL -> it.copy(y = 1540f)
                LandmarkName.RIGHT_HEEL -> it.copy(y = 1547.87f)
                else -> it
            }
        }
        val pl = geo.derive(raw, imageW = 1000, imageH = 2000)
        assertEquals(5.0, pl.rollDeg, 0.3)
        assertFalse(pl.frame.rollRejected)
    }

    @Test
    fun `large camera roll is rejected instead of corrected`() {
        // Heel line tilted ~20°: dy = 36.4 px over dx = 100 px.
        val raw = syntheticPerson().map {
            when (it.name) {
                LandmarkName.LEFT_HEEL -> it.copy(y = 1540f)
                LandmarkName.RIGHT_HEEL -> it.copy(y = 1576.4f)
                else -> it
            }
        }
        val pl = geo.derive(raw, imageW = 1000, imageH = 2000)
        assertTrue(pl.frame.rollRejected)
    }

    @Test
    fun `bent knee is detected from hip-knee-ankle angle`() {
        val raw = syntheticPerson().map {
            when (it.name) {
                LandmarkName.LEFT_KNEE -> it.copy(x = 560f, y = 1060f) // knee pushed forward
                else -> it
            }
        }
        val pl = geo.derive(raw, imageW = 1000, imageH = 2000)
        assertTrue("expected a bent knee, got ${pl.kneeDeg}", pl.kneeDeg < 165.0)
    }

    @Test
    fun `straight legs report near-180 degree knees`() {
        val pl = geo.derive(syntheticPerson(), imageW = 1000, imageH = 2000)
        assertTrue(pl.kneeDeg > 170.0)
    }

    @Test
    fun `ear width cross-check is derived when ears are visible`() {
        val pl = geo.derive(syntheticPerson(), imageW = 1000, imageH = 2000)
        assertNotNull(pl.headPxFromWidth) // 140 px ear span / 0.64 ≈ 219 px head
        assertEquals(140f / 0.64f, pl.headPxFromWidth!!, 1f)
    }

    // ------------------------------------------------------------------
    // Formatting
    // ------------------------------------------------------------------

    @Test
    fun `height renders to feet and inches`() {
        val est = geo.estimate(
            landmarks(headTop = Px(0f, 0f), feet = Px(0f, 1470f), headHeightPx = 180f)
        )
        assertEquals(187.8, est.heightCm, 0.2) // 8.166 heads * 23
        assertEquals("6 ft 2 in", est.heightFtInText)
    }
}
