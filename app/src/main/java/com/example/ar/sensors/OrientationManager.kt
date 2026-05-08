
package com.example.ar.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

class OrientationManager(private val context: Context) : SensorEventListener {

    enum class Mode { HORIZONTAL, VERTICAL }

    interface Listener {
        fun onOrientation(azimuth: Float, pitch: Float, roll: Float, mode: Mode)
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    var listener: Listener? = null

    fun start() {
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val display = (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
            .defaultDisplay
        val (axisX, axisY) = when (display.rotation) {
            Surface.ROTATION_90  -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else                 -> SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)

        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val rawPitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val mode = if (abs(rawPitchDeg) > 45f) Mode.VERTICAL else Mode.HORIZONTAL

        val matrixForReading = if (mode == Mode.VERTICAL) {
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )
            remappedMatrix
        } else remappedMatrix

        SensorManager.getOrientation(matrixForReading, orientationAngles)

        val azimuth = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
        val pitch   = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val roll    = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        listener?.onOrientation(azimuth, pitch, roll, mode)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}