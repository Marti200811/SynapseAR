package com.example.ar

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Bundle
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // DEBE ir antes de super.onCreate para reemplazar el splash del sistema
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val root      = findViewById<ConstraintLayout>(R.id.splashRoot)
        val pulse     = findViewById<View>(R.id.pulseRing)
        val icon      = findViewById<ImageView>(R.id.splashIcon)
        val title     = findViewById<TextView>(R.id.splashTitle)
        val divider   = findViewById<View>(R.id.splashDivider)
        val company   = findViewById<TextView>(R.id.splashCompany)
        val version   = findViewById<TextView>(R.id.splashVersion)

        // ── 1. Pulso de fondo (expand + fade out) ────────────────────────────
        val pulseScale = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(pulse, View.SCALE_X, 0.3f, 1.4f).apply { duration = 900 },
                ObjectAnimator.ofFloat(pulse, View.SCALE_Y, 0.3f, 1.4f).apply { duration = 900 },
                ObjectAnimator.ofFloat(pulse, View.ALPHA,  0.6f, 0f).apply    { duration = 900 }
            )
            interpolator = DecelerateInterpolator()
            startDelay = 100
        }

        // ── 2. Ícono: escala + fade in con overshoot ─────────────────────────
        val iconAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(icon, View.ALPHA,   0f, 1f).apply  { duration = 600 },
                ObjectAnimator.ofFloat(icon, View.SCALE_X, 0.4f, 1f).apply { duration = 700 },
                ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0.4f, 1f).apply { duration = 700 }
            )
            interpolator = OvershootInterpolator(1.2f)
            startDelay = 300
        }

        // ── 3. Título: sube desde abajo + fade in ────────────────────────────
        val titleAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(title, View.ALPHA, 0f, 1f).apply          { duration = 500 },
                ObjectAnimator.ofFloat(title, View.TRANSLATION_Y, 40f, 0f).apply { duration = 600 }
            )
            interpolator = DecelerateInterpolator(1.5f)
            startDelay = 750
        }

        // ── 4. Línea divisora: se expande de centro hacia afuera ─────────────
        val dividerAnim = AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(divider, View.ALPHA,   0f, 1f).apply   { duration = 400 },
                ObjectAnimator.ofFloat(divider, View.SCALE_X, 0f, 1f).apply   { duration = 500 }
            )
            interpolator = AccelerateDecelerateInterpolator()
            startDelay = 1100
        }

        // ── 5. Empresa: fade in ───────────────────────────────────────────────
        val companyAnim = ObjectAnimator.ofFloat(company, View.ALPHA, 0f, 1f).apply {
            duration = 600
            startDelay = 1350
            interpolator = DecelerateInterpolator()
        }

        // ── 6. Versión: fade in suave ─────────────────────────────────────────
        val versionAnim = ObjectAnimator.ofFloat(version, View.ALPHA, 0f, 0.5f).apply {
            duration = 500
            startDelay = 1600
        }

        // ── 7. Morse "VECTRIX" sincronizado con las animaciones ───────────────
        playMorseVectrix()

        // ── 8. Lanzar todas las animaciones ──────────────────────────────────
        pulseScale.start()
        iconAnim.start()
        titleAnim.start()
        dividerAnim.start()
        companyAnim.start()
        versionAnim.start()

        // ── 8. Segundo pulso + navegación a MainActivity ──────────────────────
        // C08: usar lifecycleScope.launch en lugar de postDelayed — se cancela
        // automáticamente si la Activity es destruida (swipe en Recents, etc.)
        lifecycleScope.launch {
            // Segundo pulso a los 900ms
            delay(950)
            if (!isDestroyed) {
                pulse.scaleX = 0.3f; pulse.scaleY = 0.3f; pulse.alpha = 0.6f
                AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(pulse, View.SCALE_X, 0.3f, 1.6f).apply { duration = 1000 },
                        ObjectAnimator.ofFloat(pulse, View.SCALE_Y, 0.3f, 1.6f).apply { duration = 1000 },
                        ObjectAnimator.ofFloat(pulse, View.ALPHA,  0.5f, 0f).apply    { duration = 1000 }
                    )
                    interpolator = DecelerateInterpolator()
                }.start()
            }

            // Fade out y lanzar MainActivity a los 2.8s
            delay(1850)  // 950 + 1850 = 2800ms total
            if (!isDestroyed) {
                ObjectAnimator.ofFloat(root, View.ALPHA, 1f, 0f).apply {
                    duration = 400
                    interpolator = AccelerateDecelerateInterpolator()
                    start()
                }
                delay(380)
                if (!isDestroyed) {
                    startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                    finish()
                    @Suppress("DEPRECATION")
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }
            }
        }
    }

    // Evitar que el botón atrás cierre el splash
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { /* no-op */ }

    // ── Morse "VECTRIX" — onda sinusoidal pura 700 Hz generada en tiempo real ─
    //
    // V=...-  E=.  C=-.-.  T=-  R=.-.  I=..  X=-..-
    //
    // Timing: U=40ms/unidad → 63U totales = 2520ms
    // Arranca a los 100ms para coincidir con el primer pulso del splash.
    // El buffer completo (111132 muestras ≈ 217KB) se carga en MODE_STATIC
    // para reproducción exacta y sin cortes.
    // Respeta el modo silencio/vibración.
    private fun playMorseVectrix() {
        val am = getSystemService(AUDIO_SERVICE) as AudioManager
        if (am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return

        // V=...-  E=.  C=-.-.  T=-  R=.-.  I=..  X=-..-
        val morse = listOf(
            intArrayOf(1, 1, 1, 3), // V  → 12U
            intArrayOf(1),           // E  →  4U
            intArrayOf(3, 1, 3, 1), // C  → 14U
            intArrayOf(3),           // T  →  6U
            intArrayOf(1, 3, 1),    // R  → 10U
            intArrayOf(1, 1),        // I  →  6U
            intArrayOf(3, 1, 1, 3)  // X  → 11U  (sin silencio final)
        )                            // TOTAL: 63U × 40ms = 2520ms

        lifecycleScope.launch(Dispatchers.IO) {
            delay(100L)   // sincronizar con el primer pulso visual del splash

            val sampleRate  = 44100
            val U           = 40              // ms por unidad Morse
            val freqHz      = 700             // Hz — frecuencia clásica CW
            val samplesPerU = sampleRate * U / 1000   // = 1764 muestras
            val totalSamples = samplesPerU * 63        // = 111132 muestras
            val fadeSamples  = sampleRate * 8 / 1000  // 8ms fade-in/out por elemento

            val buf = ShortArray(totalSamples)
            var pos = 0

            fun writeTone(units: Int) {
                val n = samplesPerU * units
                for (i in 0 until n) {
                    val angle = 2.0 * Math.PI * i * freqHz / sampleRate
                    val env = when {
                        i < fadeSamples         -> i.toDouble() / fadeSamples
                        i > n - fadeSamples - 1 -> (n - 1 - i).toDouble() / fadeSamples
                        else                    -> 1.0
                    }
                    buf[pos++] = (Math.sin(angle) * 32767 * 0.65 * env).toInt().toShort()
                }
            }

            fun writeSilence(units: Int) {
                repeat(samplesPerU * units) { buf[pos++] = 0 }
            }

            morse.forEachIndexed { li, letter ->
                letter.forEachIndexed { ei, units ->
                    writeTone(units)
                    when {
                        ei < letter.size - 1 -> writeSilence(1)   // gap intra-letra
                        li < morse.size - 1  -> writeSilence(3)   // gap inter-letra
                        // último elemento de la última letra: sin silencio
                    }
                }
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(buf.size * 2)
                .build()

            try {
                track.write(buf, 0, buf.size)
                track.play()
                delay(2600L)   // esperar que termine la reproducción
            } finally {
                track.stop()
                track.release()
            }
        }
    }
}
