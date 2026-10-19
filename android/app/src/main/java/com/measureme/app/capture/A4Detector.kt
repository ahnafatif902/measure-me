package com.measureme.app.capture

import android.graphics.Bitmap
import com.measureme.app.pipeline.Px
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.core.TermCriteria
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * A4Detector — finds the four corners of an A4 sheet on the floor (Mode C).
 *
 * Pipeline: grayscale → blur → Canny edges → dilate (closes gaps on soft
 * paper edges) → contours → polygon approximation → keep convex 4-vertex
 * shapes of plausible size. The REAL validation is not here: any rectangle
 * -ish shape passes this stage, and the homography decomposition in
 * [com.measureme.app.pipeline.ReferenceGeometry] is what rejects impossible
 * perspectives, so the filters here stay deliberately loose:
 *
 *  - area ≥ 0.4% of the image (a sheet at a normal distance is far bigger;
 *    this only removes noise)
 *  - rectangle aspect (short/long of the min-area rect) ≥ 0.15 — perspective
 *    crushes the aspect ratio of a sheet seen from standing height, so a
 *    strict 1:√2 test would wrongly reject the very views we need
 *
 * Corner order: the four corners are returned sorted by atan2 around their
 * centroid in DESCENDING order (the A4's own CCW corner loop as seen from the
 * camera). ReferenceGeometry re-sorts defensively, so any cyclic order works;
 * this just makes the detector's own output deterministic.
 *
 * If OpenCV native code is unavailable, detect() returns null and the app
 * simply falls back to Proportions with a visible warning.
 */
class A4Detector {

    /** Native libs load once; every call is a no-op check afterwards. */
    private val openCvReady: Boolean by lazy {
        try {
            OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * @return the sheet's four corners in image pixels (detector order), or
     *   null when no plausible sheet is in the photo.
     */
    fun detect(bitmap: Bitmap): List<Px>? {
        if (bitmap.width < 64 || bitmap.height < 64) return null
        if (!openCvReady) return null

        var rgba: Mat? = null
        var gray: Mat? = null
        var edges: Mat? = null
        var hierarchy: Mat? = null
        var kernel: Mat? = null
        try {
            rgba = Mat()
            Utils.bitmapToMat(bitmap, rgba)
            gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

            edges = Mat()
            Imgproc.Canny(gray, edges, 60.0, 180.0)
            kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
            Imgproc.dilate(edges, edges, kernel)

            hierarchy = Mat()
            val contours = ArrayList<MatOfPoint>()
            Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)

            val minArea = bitmap.width.toDouble() * bitmap.height.toDouble() * MIN_AREA_FRACTION
            var best: List<Px>? = null
            var bestArea = 0.0

            for (contour in contours) {
                val area = abs(Imgproc.contourArea(contour))
                if (area < minArea || area <= bestArea) continue

                val pts = quadFrom(contour, gray) ?: continue
                val rect = Imgproc.minAreaRect(MatOfPoint2f(*pts))
                val shortSide = min(rect.size.width, rect.size.height)
                val longSide = max(rect.size.width, rect.size.height)
                if (longSide < 1e-3 || shortSide / longSide < MIN_ASPECT) continue

                best = orderCorners(pts)
                bestArea = area
            }
            return best
        } catch (t: Throwable) {
            return null
        } finally {
            // Native memory does not wait for the GC — release promptly.
            listOf(rgba, gray, edges, hierarchy, kernel).forEach { it?.release() }
        }
    }

    /**
     * Approximate one contour to 4 convex corners (pointing into the page
     * that produced it) → pixel corners with sub-pixel refinement.
     */
    private fun quadFrom(contour: MatOfPoint, gray: Mat): Array<org.opencv.core.Point>? {
        val c2f = MatOfPoint2f(*contour.toArray())
        val approx = MatOfPoint2f()
        try {
            val perimeter = Imgproc.arcLength(c2f, true)
            if (perimeter < 40.0) return null
            Imgproc.approxPolyDP(c2f, approx, 0.03 * perimeter, true)
            val pts = approx.toArray()
            if (pts.size != 4) return null
            if (!Imgproc.isContourConvex(MatOfPoint(*pts))) return null

            // Sub-pixel refinement sharpens the corners the homography will
            // hang off — worth it, and cheap (a handful of pixels each).
            try {
                val refined = MatOfPoint2f(*pts)
                Imgproc.cornerSubPix(
                    gray, refined, Size(5.0, 5.0), Size(-1.0, -1.0),
                    TermCriteria(TermCriteria.EPS + TermCriteria.MAX_ITER, 30, 0.1),
                )
                return refined.toArray()
            } catch (t: Throwable) {
                return pts
            }
        } finally {
            c2f.release()
            approx.release()
        }
    }

    /**
     * Contract with ReferenceGeometry: sort by atan2 around the centroid in
     * DESCENDING order — the A4's CCW corner loop as seen from the camera.
     */
    private fun orderCorners(pts: Array<org.opencv.core.Point>): List<Px> {
        val cx = pts.map { it.x }.sum() / pts.size
        val cy = pts.map { it.y }.sum() / pts.size
        return pts.sortedByDescending { atan2(it.y - cy, it.x - cx) }
            .map { Px(it.x.toFloat(), it.y.toFloat()) }
    }

    private companion object {
        /** Ignore blobs smaller than 0.4% of the image. */
        const val MIN_AREA_FRACTION = 0.004

        /** Loose aspect sanity; the homography check does the real vetting. */
        const val MIN_ASPECT = 0.15
    }
}
