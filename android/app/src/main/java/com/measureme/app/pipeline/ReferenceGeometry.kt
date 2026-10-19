package com.measureme.app.pipeline

import kotlin.math.abs
import kotlin.math.acos

/**
 * ReferenceGeometry — Mode C: height from an A4 sheet on the floor.
 *
 * The sheet is a known-size planar reference (21.0 × 29.7 cm). A 4-point
 * homography from paper centimetres to image pixels encodes the camera's
 * full pose — so from the sheet alone we get the camera height h_c AND the
 * pitch, with no sensors and no distance measurement.
 *
 * Decomposition (standard planar-target pose recovery):
 *   1. H = DLT(paper corners → image corners), h33 fixed to 1
 *   2. Hn = K⁻¹·H — with K = diag(fPx, fPx, 1) + principal point
 *   3. scale λ = 2 / (‖hn1‖ + ‖hn2‖), then r1 = λ·hn1, r2 = λ·hn2, t = λ·hn3
 *   4. orthonormalize (Gram–Schmidt) → R = [r1 r2 r1×r2]; camera centre
 *      C = −Rᵗt in paper centimetres → h_c = C_z
 *   5. sign fix: the camera must be on the +z side of the floor (t·r3 < 0,
 *      which makes C_z > 0)
 *
 * Corner ORDER does not matter: corners are re-sorted here by atan2 around
 * their centroid (descending), which corresponds to the A4's own CCW corner
 * loop, and (0,0)/(21,0)/(21,29.7)/(0,29.7) are assigned in that loop. Any
 * cyclic shift of the detected corners is therefore absorbed; a mirrored
 * detection cannot survive the plausibility guards.
 *
 * Pitch is computed as θ = 90° − acos(−R[2][2]); when a gravity pitch is
 * supplied (camera capture) it takes precedence — it is a direct physical
 * measurement, so it wins over the homography's noisier estimate.
 *
 * Pure Kotlin — no Android imports — fully exercised by JVM tests.
 */
object ReferenceGeometry {

    const val A4_SHORT_CM = 21.0
    const val A4_LONG_CM = 29.7

    /** Camera-height window outside which the solve is not trusted. */
    const val MIN_CAMERA_HEIGHT_CM = 40.0
    const val MAX_CAMERA_HEIGHT_CM = 260.0

    data class Pose(
        /** Camera height above the floor, in cm. */
        val cameraHeightCm: Double,
        /** Pitch used downstream: gravity when supplied, else homography. */
        val pitchDeg: Double,
        /** In-image rotation of the world-vertical (same convention as [Tilt]). */
        val rollDeg: Double,
        val pitchFromGravity: Boolean,
        /** |cos| between the un-normalized r1,r2 — 0 is perfect. */
        val orthoResidual: Double,
        /** | |r1|/|r2| − 1 | — 0 is perfect. */
        val scaleResidual: Double,
    )

    /**
     * @param corners four A4 corners in image pixels (any cyclic order)
     * @param intrinsics f_px + principal point of the analyzed bitmap
     * @param gravityPitchDeg pitch from the gravity sensor, when available
     * @return null when the perspective is unusable (caller falls back)
     */
    fun solve(
        corners: List<Px>,
        intrinsics: Intrinsics,
        gravityPitchDeg: Double? = null,
    ): Pose? {
        if (corners.size != 4 || intrinsics.fPx <= 0.0) return null

        val ordered = orderCorners(corners)
        val paper = listOf(
            Pair(0.0, 0.0),
            Pair(A4_SHORT_CM, 0.0),
            Pair(A4_SHORT_CM, A4_LONG_CM),
            Pair(0.0, A4_LONG_CM),
        )
        val h = solveHomography(paper, ordered) ?: return null

        // ---- Step 2: Hn = K⁻¹ H -------------------------------------------
        val f = intrinsics.fPx
        val cx = intrinsics.cX
        val cy = intrinsics.cY
        // K⁻¹ = [[1/f, 0, −cx/f], [0, 1/f, −cy/f], [0, 0, 1]]
        // (h is row-major: h[row·3 + col])
        fun hn(row: Int, col: Int): Double = when (row) {
            0 -> (h[col] - cx * h[6 + col]) / f
            1 -> (h[3 + col] - cy * h[6 + col]) / f
            else -> h[6 + col]
        }
        val hn1 = doubleArrayOf(hn(0, 0), hn(1, 0), hn(2, 0))
        val hn2 = doubleArrayOf(hn(0, 1), hn(1, 1), hn(2, 1))
        val hn3 = doubleArrayOf(hn(0, 2), hn(1, 2), hn(2, 2))

        // ---- Step 3: scale from the pair of orthonormal columns ------------
        val n1 = norm(hn1)
        val n2 = norm(hn2)
        if (n1 < 1e-9 || n2 < 1e-9) return null
        val lambda = 2.0 / (n1 + n2)

        val r1 = scale(hn1, lambda)
        val r2 = scale(hn2, lambda)
        val t = scale(hn3, lambda)

        // Residuals BEFORE orthonormalization: they measure how well the
        // fitted homography behaves like a true rotation — noisy corners and
        // wrong intrinsics show up here.
        val orthoResidual = abs(dot(r1, r2)) / (norm(r1) * norm(r2))
        val scaleResidual = abs(norm(r1) / norm(r2) - 1.0)

        // ---- Step 4: orthonormalize ---------------------------------------
        val r1n = normalize(r1)
        val r2raw = sub(r2, scale(r1n, dot(r1n, r2)))
        val r2n = normalize(r2raw)
        var r3n = cross(r1n, r2n)

        // ---- Step 5: the camera must be above the floor --------------------
        if (dot(t, r3n) > 0.0) {
            // Flip the (r1, r2, t) triple; r3 = r1×r2 is unchanged, and the
            // camera centre C_z = −(r3·t) becomes positive.
            negateInPlace(r1n)
            negateInPlace(r2n)
            negateInPlace(t)
            r3n = cross(r1n, r2n)
        }

        val cameraHeight = -dot(r3n, t)
        if (cameraHeight !in MIN_CAMERA_HEIGHT_CM..MAX_CAMERA_HEIGHT_CM) return null

        // The paper origin must be in front of the camera.
        if (t[2] <= 1e-3) return null

        // Residual gates: reject perspectives that cannot be a true pose.
        if (orthoResidual > MAX_ORTHO_RESIDUAL) return null
        if (scaleResidual > MAX_SCALE_RESIDUAL) return null

        // ---- Pitch: gravity wins when present ------------------------------
        val pitchFromHomography = 90.0 - Math.toDegrees(acos((-r3n[2]).coerceIn(-1.0, 1.0)))
        val pitch = gravityPitchDeg ?: pitchFromHomography

        // In-image rotation of the world-vertical: r3n is the paper's z (up)
        // axis in camera coordinates, so its image-plane components are
        // (r3n.x, r3n.y); up appears at (sinρ, −cosρ) in Tilt convention.
        val rollDeg = Math.toDegrees(kotlin.math.atan2(r3n[0], -r3n[1]))

        return Pose(
            cameraHeightCm = cameraHeight,
            pitchDeg = pitch,
            rollDeg = rollDeg,
            pitchFromGravity = gravityPitchDeg != null,
            orthoResidual = orthoResidual,
            scaleResidual = scaleResidual,
        )
    }

    // ------------------------------------------------------------------
    // 4-point DLT with h33 = 1 → 8×8 linear solve (Gauss–Jordan + pivoting)
    // ------------------------------------------------------------------

    fun solveHomography(
        paper: List<Pair<Double, Double>>,
        image: List<Px>,
    ): DoubleArray? {
        if (paper.size != 4 || image.size != 4) return null

        // Unknowns: h11 h12 h13 h21 h22 h23 h31 h32 (h33 = 1).
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = paper[i].first
            val y = paper[i].second
            val u = image[i].x.toDouble()
            val v = image[i].y.toDouble()
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }

        for (col in 0 until 8) {
            var pivot = col
            for (r in col + 1 until 8) if (abs(a[r][col]) > abs(a[pivot][col])) pivot = r
            if (abs(a[pivot][col]) < 1e-9) return null // degenerate quadrilateral
            val swap = a[col]; a[col] = a[pivot]; a[pivot] = swap
            for (r in 0 until 8) {
                if (r == col) continue
                val fq = a[r][col] / a[col][col]
                if (fq == 0.0) continue
                for (c in col..8) a[r][c] -= fq * a[col][c]
            }
        }

        val h = DoubleArray(8) { a[it][8] / a[it][it] }
        return doubleArrayOf(
            h[0], h[1], h[2],
            h[3], h[4], h[5],
            h[6], h[7], 1.0,
        )
    }

    /**
     * Same convention as A4Detector: descending atan2 around the centroid =
     * the A4's CCW corner loop as seen from the camera.
     */
    fun orderCorners(corners: List<Px>): List<Px> {
        val cx = corners.sumOf { it.x.toDouble() } / corners.size
        val cy = corners.sumOf { it.y.toDouble() } / corners.size
        return corners.sortedByDescending {
            kotlin.math.atan2(it.y - cy, it.x - cx)
        }
    }

    // ------------------------------------------------------------------
    // tiny vector helpers (DoubleArray, size 3)
    // ------------------------------------------------------------------

    private fun norm(v: DoubleArray) = kotlin.math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

    private fun normalize(v: DoubleArray): DoubleArray {
        val n = norm(v)
        return if (n < 1e-12) doubleArrayOf(0.0, 0.0, 0.0) else scale(v, 1.0 / n)
    }

    private fun scale(v: DoubleArray, s: Double) = doubleArrayOf(v[0] * s, v[1] * s, v[2] * s)

    private fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    private fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )

    private fun negateInPlace(v: DoubleArray) {
        v[0] = -v[0]; v[1] = -v[1]; v[2] = -v[2]
    }

    // Residual gates (generous: corner noise on real photos is real, but a
    // mirrored or degenerate fit blows far past these).
    private const val MAX_ORTHO_RESIDUAL = 0.25
    private const val MAX_SCALE_RESIDUAL = 0.30
}
