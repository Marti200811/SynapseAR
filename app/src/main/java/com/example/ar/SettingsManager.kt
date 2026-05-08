package com.example.ar

import android.content.Context

enum class UnitSystem { AUTO, METRIC, IMPERIAL }

object SettingsManager {

    private const val PREFS_NAME = "synapse_prefs"
    private const val KEY_UNITS  = "unit_system"

    fun getUnitSystem(context: Context): UnitSystem {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_UNITS, UnitSystem.AUTO.name)
        return try { UnitSystem.valueOf(raw ?: UnitSystem.AUTO.name) }
        catch (_: Exception) { UnitSystem.AUTO }
    }

    fun setUnitSystem(context: Context, system: UnitSystem) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_UNITS, system.name).apply()
    }
}
