package com.example.ar.tdt

/**
 * Transmisor de Televisión Digital Terrestre (TDT).
 *
 * @param name      Nombre del transmisor / cerro / torre
 * @param city      Ciudad o región que cubre
 * @param country   País (código ISO 3166-1 alpha-2)
 * @param standard  Estándar digital: DVB-T2, ISDB-T, ATSC, ATSC3
 * @param lat       Latitud del transmisor
 * @param lon       Longitud del transmisor
 * @param power     Potencia ERP en kW (aproximada)
 */
data class TdtTransmitter(
    val name: String,
    val city: String,
    val country: String,
    val standard: String,
    val lat: Double,
    val lon: Double,
    val power: Double = 0.0
) {
    val countryFlag: String get() = countryToFlag(country)

    private fun countryToFlag(iso: String): String {
        // Convierte código ISO a emoji de bandera
        return iso.uppercase().map { char ->
            String(Character.toChars(char.code + 0x1F1E6 - 'A'.code))
        }.joinToString("")
    }
}
