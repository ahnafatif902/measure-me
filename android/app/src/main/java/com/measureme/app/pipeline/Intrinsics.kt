package com.measureme.app.pipeline

/**
 * Camera intrinsics for the ANALYZED bitmap (upright, downscaled).
 *
 * This is what separates Modes B and C from guesswork: with the focal length
 * in pixels we can turn image angles into real-world ratios.
 *
 * Only the pinhole model is used:
 *   u = cX + fPx * (x / z)
 *   v = cY + fPx * (y / z)
 * so principal point and focal length are all we need. Distortion is left
 * uncorrected — modern phone lenses are well corrected near the centre, and
 * the resulting error is small next to the model's error budget.
 *
 * The scaling trick that makes life easy: f_px and image width scale by the
 * same factor when a photo is downscaled, so f_px can be computed directly
 * for the analyzed bitmap from the 35 mm-equivalent focal length:
 *
 *   f_px = FocalLengthIn35mmFilm / 36 mm * analyzedWidth
 *
 * (36 mm is the 35 mm format's frame width.) If the photo carries no focal
 * length, Modes B and C fall back to Proportions with a visible warning —
 * we never invent intrinsics.
 */
data class Intrinsics(
    val fPx: Double,
    val cX: Double,
    val cY: Double,
    /** Where the numbers came from — shown in debug traces. */
    val source: String,
) {
    companion object {

        /** Vertical FOV-independent f_px from an EXIF 35 mm-equivalent focal. */
        fun fromExif35mm(focalLength35mmMm: Double, imageW: Int, imageH: Int): Intrinsics? {
            if (focalLength35mmMm <= 0.0 || imageW <= 0 || imageH <= 0) return null
            return Intrinsics(
                fPx = focalLength35mmMm / 36.0 * imageW,
                cX = imageW / 2.0,
                cY = imageH / 2.0,
                source = "exif-35mm",
            )
        }

        /** Forced intrinsics for on-device verification (debug builds only). */
        fun forced(fPx: Double, imageW: Int, imageH: Int): Intrinsics =
            Intrinsics(fPx, imageW / 2.0, imageH / 2.0, "debug-forced")
    }
}
