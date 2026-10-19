package com.measureme.app.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import java.io.ByteArrayInputStream
import java.io.File
import androidx.exifinterface.media.ExifInterface
import com.measureme.app.R
import com.measureme.app.pipeline.AppConstants
import com.measureme.app.pipeline.ImageStats
import com.measureme.app.pipeline.MeasureMeException
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * ImageCaptureModule — everything about turning photos into a clean Bitmap.
 *
 *  - Gallery import  : decodeUri() (EXIF rotation + downscale, memory-safe)
 *  - Camera capture  : fromImageProxy() (CameraX JPEG → Bitmap)
 *  - EXIF optics     : the 35 mm-equivalent focal length rides along with
 *                      every decode (DecodedPhoto) for Modes B and C
 *  - Image quality   : stats() (mean luminance / contrast, in one cheap pass)
 *
 * Downscaling to MAX_ANALYSIS_DIM matters on low/mid-range phones: BlazePose
 * input is far smaller than a 12 MP photo, so decoding at full size only
 * wastes memory and time.
 */
class ImageCaptureModule(private val context: Context) {

    /**
     * A decoded photo plus whatever its header says about the camera's
     * optics. Modes B and C need the focal length to convert pixels into
     * real-world geometry; null means "this photo doesn't carry one" and the
     * engine will fall back to Proportions with a visible warning.
     */
    data class DecodedPhoto(val bitmap: Bitmap, val focalLength35mmMm: Double?)

    /** Decode a gallery/content Uri the right way up and downscaled. */
    fun decodeUri(uri: Uri, maxDim: Int = AppConstants.MAX_ANALYSIS_DIM): DecodedPhoto {
        try {
            // Pass 1: bounds only (no pixels decoded).
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: throw MeasureMeException.ImageUnreadable(null)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw MeasureMeException.ImageUnreadable(null)
            }

            // EXIF orientation + optics in one pass: orientation keeps portrait
            // photos upright; the 35 mm-equivalent focal length feeds f_px for
            // Modes B/C. One stream open serves both.
            var f35mm = 0.0
            val orientation = context.contentResolver.openInputStream(uri)?.use { stream ->
                val exif = ExifInterface(stream)
                f35mm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0.0)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: ExifInterface.ORIENTATION_NORMAL

            // Pass 2: decode at a sample size that lands just under maxDim.
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: throw MeasureMeException.ImageUnreadable(null)

            return DecodedPhoto(applyExif(decoded, orientation), f35mm.takeIf { it > 0.0 })
        } catch (t: MeasureMeException) {
            throw t
        } catch (t: Throwable) {
            throw MeasureMeException.ImageUnreadable(t)
        }
    }

    /**
     * Decode straight from a File — same pipeline as decodeUri() but without
     * going through ContentResolver. Used by the debug_photo verification hook
     * (MIUI privacy layers can veto file:// URIs presented to the resolver).
     */
    fun decodeFile(file: File, maxDim: Int = AppConstants.MAX_ANALYSIS_DIM): DecodedPhoto {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            file.inputStream().use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw MeasureMeException.ImageUnreadable(null)
            }

            var f35mm = 0.0
            val orientation = file.inputStream().use { stream ->
                val exif = ExifInterface(stream)
                f35mm = exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0.0)
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }

            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
            val decoded = file.inputStream().use {
                BitmapFactory.decodeStream(
                    it, null, BitmapFactory.Options().apply { inSampleSize = sample }
                )
            } ?: throw MeasureMeException.ImageUnreadable(null)

            return DecodedPhoto(applyExif(decoded, orientation), f35mm.takeIf { it > 0.0 })
        } catch (t: MeasureMeException) {
            throw t
        } catch (t: Throwable) {
            throw MeasureMeException.ImageUnreadable(t)
        }
    }

    /** CameraX still capture → JPEG bytes → upright, downscaled Bitmap. */
    fun fromImageProxy(proxy: ImageProxy, maxDim: Int = AppConstants.MAX_ANALYSIS_DIM): DecodedPhoto {
        try {
            val buffer = proxy.planes[0].buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
            val decoded = BitmapFactory.decodeByteArray(
                bytes, 0, bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: throw MeasureMeException.ImageUnreadable(null)

            // CameraX tells us how to rotate the JPEG; EXIF is only needed for
            // the focal length (Modes B/C). Reading it costs nothing — the
            // bytes are already in hand.
            val f35mm = try {
                ExifInterface(ByteArrayInputStream(bytes))
                    .getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0.0)
            } catch (t: Throwable) {
                0.0
            }

            // Rotate upright with CameraX's rotation hint, then hand back the
            // bitmap together with the optics.
            return DecodedPhoto(
                rotate(decoded, proxy.imageInfo.rotationDegrees),
                f35mm.takeIf { it > 0.0 },
            )
        } catch (t: MeasureMeException) {
            throw t
        } catch (t: Throwable) {
            throw MeasureMeException.ImageUnreadable(t)
        }
    }

    /**
     * The bundled reference sample photo ("Try a sample photo").
     * It is upright and EXIF-free by construction, so this is the plain
     * resource decode + the same powers-of-two downscale as decodeUri().
     */
    fun decodeSample(maxDim: Int = AppConstants.MAX_ANALYSIS_DIM): DecodedPhoto {
        try {
            val res = context.resources
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(res, R.drawable.sample_photo, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                throw MeasureMeException.ImageUnreadable(null)
            }
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / sample > maxDim) sample *= 2
            val decoded = BitmapFactory.decodeResource(
                res, R.drawable.sample_photo,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: throw MeasureMeException.ImageUnreadable(null)
            return DecodedPhoto(decoded, null)
        } catch (t: MeasureMeException) {
            throw t
        } catch (t: Throwable) {
            throw MeasureMeException.ImageUnreadable(t)
        }
    }

    /**
     * Cheap image-quality statistics for the confidence breakdown:
     * sample ~128×128 grid points, compute mean luminance + std dev
     * (contrast proxy). No heavy image processing on the UI thread —
     * call from a background dispatcher.
     */
    fun stats(bitmap: Bitmap, croppedHeadPx: Float? = null): ImageStats {
        val step = max(1, min(bitmap.width, bitmap.height) / 128)
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                // getPixel returns a packed ARGB int — unpack with bit shifts
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luma = 0.299 * r + 0.587 * g + 0.114 * b
                sum += luma
                sumSq += luma * luma
                n++
                x += step
            }
            y += step
        }
        if (n == 0) return ImageStats(bitmap.width, bitmap.height, 0.0, 0.0, croppedHeadPx)
        val mean = sum / n
        val variance = (sumSq / n - mean * mean).coerceAtLeast(0.0)
        return ImageStats(bitmap.width, bitmap.height, mean, sqrt(variance), croppedHeadPx)
    }

    // ------------------------------------------------------------------

    private fun applyExif(bitmap: Bitmap, orientation: Int): Bitmap = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> rotate(bitmap, 90)
        ExifInterface.ORIENTATION_ROTATE_180 -> rotate(bitmap, 180)
        ExifInterface.ORIENTATION_ROTATE_270 -> rotate(bitmap, 270)
        // Mirrored variants are rare in practice; treating them as upright is
        // acceptable for MVP and keeps the code readable.
        else -> bitmap
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        return rotated
    }
}
