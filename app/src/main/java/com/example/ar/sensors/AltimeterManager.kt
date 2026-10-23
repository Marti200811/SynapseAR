package com.example.ar.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import kotlin.math.pow

/**
 * Altura sobre el nivel del mar. Con barómetro: la presión da una lectura suave y
 * rápida, y el GPS la ancla (la presión sola deriva con el clima). Sin barómetro,
 * se usa la altitud del GPS directamente.
 */
class AltimeterManager(context: Context) : SensorEventListener {

    interface Listener {
        fun onAltitude(altitudeM: Double, pressureHpa: Float?, fromBarometer: Boolean)
    }

    var listener: Listener? = null

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val barometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val hasBarometer: Boolean get() = barometer != null

    private var pressureHpa: Float? = null
    private var seaLevelHpa: Float? = null     // presión reducida al nivel del mar, anclada por GPS
    private var gpsAltitudeM: Double? = null
    private var lastNotifyMs = 0L

    fun start() {
        barometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        lastNotifyMs = 0L
    }

    fun updateLocation(location: Location) {
        if (!location.hasAltitude()) return
        gpsAltitudeM = location.altitude
        val p = pressureHpa
        if (p != null) {
            // Presión al nivel del mar que haría coherente la lectura con la altitud del GPS.
            val estimate = p / (1.0 - location.altitude / 44330.0).pow(5.255).toFloat()
            // Ajuste lento: el GPS corrige la deriva sin meterle su ruido a la lectura.
            seaLevelHpa = seaLevelHpa?.let { it + 0.1f * (estimate - it) } ?: estimate
        }
        notifyNow()
    }

    override fun onSensorChanged(event: SensorEvent) {
        val raw = event.values[0]
        pressureHpa = pressureHpa?.let { it + 0.15f * (raw - it) } ?: raw
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastNotifyMs >= 500L) notifyNow()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun notifyNow() {
        lastNotifyMs = android.os.SystemClock.elapsedRealtime()
        val p = pressureHpa
        val sea = seaLevelHpa
        if (p != null && sea != null) {
            val alt = 44330.0 * (1.0 - (p / sea).toDouble().pow(1.0 / 5.255))
            listener?.onAltitude(alt, p, true)
        } else {
            gpsAltitudeM?.let { listener?.onAltitude(it, p, false) }
        }
    }
}
