package com.example.ar.util

import android.content.Context
import com.example.ar.SettingsManager
import com.example.ar.UnitSystem
import java.util.Locale
import kotlin.math.*

object GeoUtils {

    private const val EARTH_RADIUS_M = 6_371_000.0

    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        val theta = atan2(y, x)
        return (Math.toDegrees(theta) + 360.0) % 360.0
    }

    /**
     * Formatea la distancia usando el sistema de unidades del dispositivo.
     * EEUU → millas/pies. Resto del mundo → km/m.
     */
    fun formatDistance(meters: Double, context: Context? = null): String {
        return if (usesImperial(context)) {
            val feet = meters * 3.28084
            if (feet >= 5280) "%.2f mi".format(feet / 5280.0)
            else "%.0f ft".format(feet)
        } else {
            if (meters >= 1000) "%.2f km".format(meters / 1000.0)
            else "%.0f m".format(meters)
        }
    }

    /**
     * Devuelve true si corresponde usar el sistema imperial.
     * Prioridad: preferencia guardada → locale del dispositivo.
     */
    fun usesImperial(context: Context? = null): Boolean {
        if (context != null) {
            return when (SettingsManager.getUnitSystem(context)) {
                UnitSystem.METRIC   -> false
                UnitSystem.IMPERIAL -> true
                UnitSystem.AUTO     -> localeIsImperial(context)
            }
        }
        val country = Locale.getDefault().country.uppercase()
        return country in setOf("US", "MM", "LR")
    }

    private fun localeIsImperial(context: Context): Boolean {
        val country = context.resources.configuration.locales.get(0).country.uppercase()
        return country in setOf("US", "MM", "LR")
    }
}
