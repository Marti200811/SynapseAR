package com.example.ar

import android.content.Context

/**
 * Gestiona el estado Pro del usuario.
 * La fuente de verdad es Google Play Billing; esto es solo la caché local
 * para no consultar Play Store en cada pantalla.
 */
object ProManager {

    private const val PREFS = "synapse_prefs"   // mismo archivo que SettingsManager
    private const val KEY_PRO = "is_pro"

    fun isPro(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PRO, false)

    fun setPro(context: Context, value: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PRO, value).apply()
    }
}
