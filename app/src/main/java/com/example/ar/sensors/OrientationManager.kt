package com.example.ar.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import kotlin.math.abs

class OrientationManager(private val context: Context) : SensorEventListener {

    enum class Mode { HORIZONTAL, VERTICAL }

    var calibrationAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        private set

    interface Listener {
        fun onOrientation(azimuth: Float, pitch: Float, roll: Float, mode: Mode)
        fun onCalibrationChanged(accuracy: Int) {}
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // TYPE_GEOMAGNETIC_ROTATION_VECTOR: sin giroscopio (no deriva) + filtrado interno de Android (no tiembla)
    // Fallback a TYPE_ROTATION_VECTOR si no está disponible en el dispositivo
    private val geoRotVector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR)
    private val rotVector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnetometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix    = FloatArray(9)
    private val remappedMatrix    = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // alpha=0.20: responsive sin jitter para movimientos de antena
    private val alpha = 0.20f
    private var smoothAzimuth = -1f
    private var smoothPitch   = 0f
    private var smoothRoll    = 0f

    private var lastNotifyMs = 0L
    private val notifyIntervalMs = 33L   // 30 Hz

    private var currentMode = Mode.HORIZONTAL

    var listener: Listener? = null

    fun start() {
        val primary = geoRotVector ?: rotVector
        primary?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        smoothAzimuth = -1f
        lastNotifyMs  = 0L
        currentMode   = Mode.HORIZONTAL
    }

    override fun onSensorChanged(event: SensorEvent) {
        val isGeoVec = event.sensor.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR
        val isRotVec = event.sensor.type == Sensor.TYPE_ROTATION_VECTOR
        if (!isGeoVec && !isRotVec) return
        // Si tenemos GEOMAGNETIC, ignorar eventos del ROTATION_VECTOR normal
        if (geoRotVector != null && isRotVec) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation
        }

        val (axisX, axisY) = when (rotation) {
            Surface.ROTATION_90  -> SensorManager.AXIS_Y  to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else                 -> SensorManager.AXIS_X  to SensorManager.AXIS_Y
        }
        SensorManager.remapCoordinateSystem(rotationMatrix, axisX, axisY, remappedMatrix)

        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        val rawPitchDeg = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        currentMode = when {
            abs(rawPitchDeg) > 50f -> Mode.VERTICAL
            abs(rawPitchDeg) < 40f -> Mode.HORIZONTAL
            else -> currentMode
        }

        val matrixForReading = if (currentMode == Mode.VERTICAL) {
            val vm = FloatArray(9)
            SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, vm)
            vm
        } else {
            remappedMatrix
        }

        SensorManager.getOrientation(matrixForReading, orientationAngles)
        val rawAzimuth = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
        val rawPitch   = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rawRoll    = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        if (smoothAzimuth < 0f) {
            smoothAzimuth = rawAzimuth
            smoothPitch   = rawPitch
            smoothRoll    = rawRoll
        } else {
            var azDiff = rawAzimuth - smoothAzimuth
            if (azDiff > 180f)  azDiff -= 360f
            if (azDiff < -180f) azDiff += 360f
            smoothAzimuth = ((smoothAzimuth + alpha * azDiff) + 360f) % 360f
            smoothPitch   += alpha * (rawPitch - smoothPitch)
            smoothRoll    += alpha * (rawRoll  - smoothRoll)
        }

        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNotifyMs >= notifyIntervalMs) {
            lastNotifyMs = now
            listener?.onOrientation(smoothAzimuth, smoothPitch, smoothRoll, currentMode)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
            sensor?.type == Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR ||
            sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            if (calibrationAccuracy != accuracy) {
                calibrationAccuracy = accuracy
                listener?.onCalibrationChanged(accuracy)
            }
        }
    }
}
