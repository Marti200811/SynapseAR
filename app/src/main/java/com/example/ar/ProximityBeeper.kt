package com.example.ar

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Handler
import android.os.Looper

/**
 * Emite pitidos cuya frecuencia aumenta cuanto más preciso es el apuntado.
 * Mide la diferencia angular entre el rumbo al objetivo y la orientación
 * actual del teléfono (igual que el Satlink con un satélite).
 *
 * Zonas de precisión:
 *   > 45°  → silencio
 *   20–45° → pitido lento   (cada 1500 ms)
 *   10–20° → pitido medio   (cada 700 ms)
 *   3–10°  → pitido rápido  (cada 280 ms)
 *   < 3°   → tono continuo fijo (dial tone sostenido)
 */
class ProximityBeeper {

    private val handler = Handler(Looper.getMainLooper())
    private var toneGen: ToneGenerator? = null
    private var isRunning = false
    private var currentIntervalMs = -2L  // valor inicial imposible para forzar primer update
    private var continuousActive = false  // true cuando estamos en modo tono continuo

    private val beepRunnable = object : Runnable {
        override fun run() {
            if (!isRunning || continuousActive) return
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
            if (currentIntervalMs > 0) {
                handler.postDelayed(this, currentIntervalMs)
            }
        }
    }

    fun start() {
        if (isRunning) return
        // W12: ToneGenerator puede lanzar RuntimeException si el sistema de audio
        // no está disponible (llamada en curso, modo avión en algunos dispositivos).
        toneGen = try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 85)
        } catch (e: RuntimeException) {
            null  // sin audio: beeper desactivado silenciosamente
        }
        isRunning = true
        continuousActive = false
        currentIntervalMs = -2L
    }

    fun stop() {
        isRunning = false
        continuousActive = false
        handler.removeCallbacks(beepRunnable)
        toneGen?.stopTone()
        toneGen?.release()
        toneGen = null
        currentIntervalMs = -2L
    }

    /**
     * Llamar con la diferencia angular en grados (0 = apuntado perfecto).
     * angularError debe ser siempre positivo (valor absoluto de la diferencia).
     */
    fun updateAngularError(angularError: Double) {
        if (!isRunning) return

        val aligned = angularError <= 3.0

        if (aligned) {
            if (!continuousActive) {
                // Pasar de pulsos a tono continuo
                handler.removeCallbacks(beepRunnable)
                continuousActive = true
                currentIntervalMs = 0L
                // Dial tone sostenido 60 s; se detiene explícitamente al salir del estado
                toneGen?.startTone(ToneGenerator.TONE_SUP_DIAL, 60_000)
            }
            return
        }

        if (continuousActive) {
            // Salir del modo continuo y volver a pulsos
            toneGen?.stopTone()
            continuousActive = false
            currentIntervalMs = -2L  // forzar re-schedule abajo
        }

        val newInterval: Long = when {
            angularError > 45.0 -> -1L    // silencio
            angularError > 20.0 -> 1500L  // lento
            angularError > 10.0 -> 700L   // medio
            else                -> 280L   // rápido (3–10°)
        }

        if (newInterval == currentIntervalMs) return  // sin cambio, no interrumpir

        handler.removeCallbacks(beepRunnable)
        currentIntervalMs = newInterval

        if (newInterval > 0) {
            handler.post(beepRunnable)
        }
        // si newInterval == -1 → silencio, no postear nada
    }
}
