package com.example.ar

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
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

        // ── 7. Lanzar todas las animaciones ──────────────────────────────────
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
}
