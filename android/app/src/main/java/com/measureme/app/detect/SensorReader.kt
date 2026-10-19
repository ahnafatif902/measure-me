package com.measureme.app.detect

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.measureme.app.pipeline.Tilt
import com.measureme.app.pipeline.TiltMath

/**
 * SensorReader — the phone's real orientation, read live from the fused
 * gravity sensor while the camera screen is open, and snapshotted at the
 * shutter moment for Mode B (Horizon).
 *
 * TYPE_GRAVITY is used instead of TYPE_ACCELEROMETER on purpose: it is the
 * platform-fused gravity vector, already filtered from hand-shake noise, so
 * a single sample is stable enough for a still photo.
 *
 * If the device has no gravity sensor (or no sample has arrived yet),
 * [snapshot] returns null and the engine falls back to Proportions with a
 * cause-specific warning — never a guessed tilt.
 */
class SensorReader(context: Context) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val gravity = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)

    @Volatile
    private var latest: Tilt? = null

    /** Whether this device can report gravity at all. */
    val isAvailable: Boolean get() = gravity != null

    fun start() {
        val m = manager ?: return
        val s = gravity ?: return
        latest = null
        // SENSOR_DELAY_UI ≈ 16 Hz: plenty for a still photo, negligible cost.
        m.registerListener(this, s, SensorManager.SENSOR_DELAY_UI)
    }

    fun stop() {
        manager?.unregisterListener(this)
    }

    /** The orientation right now — null until the first sample arrives. */
    fun snapshot(): Tilt? = latest

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GRAVITY) return
        latest = TiltMath.fromGravity(event.values[0], event.values[1], event.values[2])
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
