package com.example.ar.satellite

/**
 * Representa un satélite geoestacionario.
 * @param name        Nombre comercial (ej: "Hispasat 30W-6")
 * @param orbitalLon  Longitud orbital en grados. Positivo = Este, Negativo = Oeste.
 *                    Ej: -30.0 para 30°Oeste, 19.2 para 19.2°Este
 * @param region      Región de cobertura principal
 * @param use         Uso principal (TV, Internet, etc.)
 */
data class Satellite(
    val name: String,
    val orbitalLon: Double,
    val region: String,
    val use: String
) {
    /** Texto de posición legible, ej: "30.0°O" o "19.2°E" */
    val positionLabel: String get() =
        if (orbitalLon < 0) "${"%.1f".format(-orbitalLon)}°O"
        else "${"%.1f".format(orbitalLon)}°E"
}
