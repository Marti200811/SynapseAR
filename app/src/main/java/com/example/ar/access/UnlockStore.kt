package com.example.ar.access

import android.content.Context

/**
 * Guarda hasta cuándo está desbloqueada cada función.
 *
 * Usa el mismo archivo de preferencias que ProManager y SettingsManager.
 * No hace falta limpiar los vencidos: un valor viejo simplemente falla la
 * comparación en AccessRules.
 */
internal object UnlockStore {

    private const val PREFS = "synapse_prefs"

    private fun key(feature: Feature) = "unlock_until_${feature.name}"

    /** Timestamp (epoch millis) hasta el que vale el desbloqueo. 0 = nunca se desbloqueó. */
    fun expiryMillis(context: Context, feature: Feature): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(key(feature), 0L)

    fun setExpiryMillis(context: Context, feature: Feature, millis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(key(feature), millis).apply()
    }
}
