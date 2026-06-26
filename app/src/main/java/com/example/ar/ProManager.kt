package com.example.ar

import android.content.Context
import com.example.ar.BuildConfig

/**
 * Gestiona el estado Pro del usuario.
 * La fuente de verdad es Google Play Billing; esto es solo la caché local
 * para no consultar Play Store en cada pantalla.
 */
object ProManager {

    private const val PREFS = "synapse_prefs"   // mismo archivo que SettingsManager
    private const val KEY_PRO = "is_pro"

    /**
     * SOLO PARA DESARROLLO. Ponelo en `true` para ver la app como usuario
     * GRATIS aunque estés en un build debug (probar candados, banner, los
     * 3 satélites gratis, el diálogo de upgrade, etc.).
     * Dejalo en `false` normalmente → debug = Pro (ves todas las funciones).
     */
    private const val DEBUG_FORCE_FREE = false

    /** Cambiar a false antes del lanzamiento en producción. */
    private const val TESTING_MODE = true

    fun isPro(context: Context): Boolean {
        if (BuildConfig.DEBUG) return !DEBUG_FORCE_FREE
        if (TESTING_MODE) return true
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRO, false)
    }

    fun setPro(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PRO, value).apply()
    }
}
