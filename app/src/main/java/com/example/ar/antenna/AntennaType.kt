package com.example.ar.antenna

/**
 * Tipos de antena soportados por Synapse AR.
 *
 * @param label          Nombre visible en la app
 * @param emoji          Ícono representativo
 * @param needsElevation true = mostrar barra de elevación y selector de satélite
 * @param precisionDeg   Umbral de precisión para el beeper (grados)
 *                       Cuanto menor, más exigente el apuntado
 * @param description    Descripción corta para mostrar al usuario
 */
enum class AntennaType(
    val label: String,
    val emoji: String,
    val needsElevation: Boolean,
    val precisionDeg: Float,
    val description: String
) {
    SATELLITE(
        label          = "Parabólica / Satelital",
        emoji          = "📡",
        needsElevation = true,
        precisionDeg   = 2f,
        description    = "Antena de plato para TV satelital o Internet"
    ),
    WIFI_DIRECTIONAL(
        label          = "WiFi Direccional",
        emoji          = "📶",
        needsElevation = false,
        precisionDeg   = 5f,
        description    = "Panel o Yagi para enlace WiFi punto a punto"
    ),
    TDT(
        label          = "TDT / Antena de TV",
        emoji          = "📺",
        needsElevation = false,
        precisionDeg   = 10f,
        description    = "Antena terrestre para canales de TV digital"
    ),
    YAGI(
        label          = "Yagi / Directional",
        emoji          = "🔭",
        needsElevation = false,
        precisionDeg   = 5f,
        description    = "Antena Yagi para radioafición, celular o punto a punto"
    ),
    POINT_TO_POINT(
        label          = "Punto a Punto (PtP)",
        emoji          = "🔗",
        needsElevation = false,
        precisionDeg   = 1f,
        description    = "Enlace Ubiquiti, Mikrotik o similar — alta precisión"
    )
}
