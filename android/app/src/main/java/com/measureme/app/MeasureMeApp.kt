package com.measureme.app

import android.app.Application
import android.util.Log
import org.opencv.android.OpenCVLoader

/**
 * Application entry point.
 *
 * The only job here is loading OpenCV's native libraries once, at startup,
 * from the APK itself (the Maven Central AAR bundles the .so files — there
 * is no OpenCV Manager app to install). Mode C (Reference) uses OpenCV for
 * A4 sheet detection; if loading fails for any reason the app still runs —
 * [com.measureme.app.capture.A4Detector] checks availability on every call
 * and the engine falls back to the Proportions path with a visible warning.
 */
class MeasureMeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val loaded = OpenCVLoader.initLocal()
        Log.i(TAG, if (loaded) "OpenCV native libs loaded" else "OpenCV unavailable — Mode C will fall back")
    }

    private companion object {
        const val TAG = "MeasureMe"
    }
}
