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

    // Nivel de calibración del magnetómetro:
    // 0 = UNRELIABLE, 1 = LOW, 2 = MEDIUM, 3 = HIGH
    var calibrationAccuracy: Int = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
        private set

    interface Listener {
        fun onOrientation(azimuth: Float, pitch: Float, roll: Float, mode: Mode)
        fun onCalibrationChanged(accuracy: Int) {}   // default impl → sin breaking change
    }

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val rotationVector: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val magnetometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val rotationMatrix    = FloatArray(9)
    private val remappedMatrix    = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    // Filtro EMA: sensor a 50 Hz para muchas muestras, alpha bajo para suavizado agresivo
    private val alpha = 0.05f
    private var smoothAzimuth = -1f   // -1 = no inicializado
    private var smoothPitch   = 0f
    private var smoothRoll    = 0f

    // Limitador de tasa de notificación: sensor corre a 50 Hz pero el listener
    // solo recibe actualizaciones a ~15 Hz para no redibujar la UI continuamente
    private var lastNotifyMs = 0L
    private val notifyIntervalMs = 67L   // ≈ 15 fps

    // Histéresis en la transición HORIZONTAL↔VERTICAL para evitar flip de signo cerca de 45°
    private var currentMode = Mode.HORIZONTAL

    var listener: Listener? = null

    fun start() {
        rotationVector?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        // Registrar magnetómetro SOLO para recibir accuracy callbacks
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
        if (event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // W04: defaultDisplay deprecado en API 30, lanza excepción en Android 13+ en algunos OEMs
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
        // Histéresis: entra en VERTICAL solo si supera 50°, vuelve a HORIZONTAL solo si baja de 40°
        currentMode = when {
            abs(rawPitchDeg) > 50f -> Mode.VERTICAL
            abs(rawPitchDeg) < 40f -> Mode.HORIZONTAL
            else -> currentMode   // zona de histéresis: mantener modo anterior
        }
        val mode = currentMode

        val matrixForReading = if (mode == Mode.VERTICAL) {
            SensorManager.remapCoordinateSystem(
                rotationMatrix,
                SensorManager.AXIS_X,
                SensorManager.AXIS_Z,
                remappedMatrix
            )
            remappedMatrix
        } else {
            remappedMatrix
        }

        SensorManager.getOrientation(matrixForReading, orientationAngles)

        val rawAzimuth = ((Math.toDegrees(orientationAngles[0].toDouble()) + 360.0) % 360.0).toFloat()
        val rawPitch   = Math.toDegrees(orientationAngles[1].toDouble()).toFloat()
        val rawRoll    = Math.toDegrees(orientationAngles[2].toDouble()).toFloat()

        // EMA con manejo de wrap-around en azimut (0°/360°)
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

        // Notificar al listener solo a ~15 Hz (el EMA igual corre a 50 Hz internamente)
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNotifyMs >= notifyIntervalMs) {
            lastNotifyMs = now
            listener?.onOrientation(smoothAzimuth, smoothPitch, smoothRoll, mode)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Solo reportar cambios del magnetómetro (el que afecta la brújula)
        if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD ||
            sensor?.type == Sensor.TYPE_ROTATION_VECTOR) {
            if (calibrationAccuracy != accuracy) {
                calibrationAccuracy = accuracy
                listener?.onCalibrationChanged(accuracy)
            }
        }
    }
}
