package com.example.ar.satellite

import kotlin.math.*

/**
 * Calcula azimut y elevación para apuntar a un satélite geoestacionario
 * desde la posición GPS del usuario.
 */
object SatelliteCalculator {

    private const val EARTH_RADIUS_KM = 6378.137
    private const val ORBIT_RADIUS_KM = 42164.2   // radio órbita geoestacionaria

    data class LookAngles(
        val azimuthDeg: Double,    // 0–360° desde el Norte, sentido horario
        val elevationDeg: Double,  // positivo = sobre el horizonte
        val visible: Boolean       // true si elevación > 0°
    )

    /**
     * @param obsLat   Latitud del observador (grados, + Norte / - Sur)
     * @param obsLon   Longitud del observador (grados, + Este / - Oeste)
     * @param satLon   Longitud orbital del satélite (grados, + Este / - Oeste)
     */
    fun calculate(obsLat: Double, obsLon: Double, satLon: Double): LookAngles {

        val latRad = Math.toRadians(obsLat)
        val B      = Math.toRadians(satLon - obsLon)   // diferencia de longitudes

        // Coseno del ángulo central entre observador y satélite
        val cosCenter = cos(latRad) * cos(B)

        // ── Elevación ────────────────────────────────────────────────
        val ratio = EARTH_RADIUS_KM / ORBIT_RADIUS_KM   // ≈ 0.15127
        val el = atan2(cosCenter - ratio, sqrt(1.0 - cosCenter * cosCenter))
        val elDeg = Math.toDegrees(el)

        // ── Azimut ───────────────────────────────────────────────────
        // Ángulo base (hacia el satélite desde el Ecuador)
        val azBase = atan2(tan(B), sin(latRad))
        var azDeg  = Math.toDegrees(azBase)

        // Corrección de cuadrante
        azDeg = when {
            obsLat >= 0 && satLon >= obsLon -> 180.0 + azDeg   // Norte, satélite al Este
            obsLat >= 0 && satLon <  obsLon -> 180.0 + azDeg   // Norte, satélite al Oeste
            obsLat <  0 && satLon >= obsLon -> azDeg            // Sur, satélite al Este
            else                            -> 360.0 + azDeg   // Sur, satélite al Oeste
        }
        azDeg = ((azDeg % 360.0) + 360.0) % 360.0

        return LookAngles(
            azimuthDeg   = azDeg,
            elevationDeg = elDeg,
            visible      = elDeg > 0.0
        )
    }
}
