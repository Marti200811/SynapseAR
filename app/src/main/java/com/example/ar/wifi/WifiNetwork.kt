package com.example.ar.wifi

/**
 * Red WiFi detectada por el escáner.
 *
 * @param ssid       Nombre de la red
 * @param bssid      MAC del punto de acceso (identificador único)
 * @param rssi       Señal en dBm (ej: -45 es muy buena, -90 es muy mala)
 * @param frequency  Frecuencia en MHz (2412–2484 = 2.4GHz, 5180–5825 = 5GHz)
 * @param channel    Canal WiFi calculado
 */
data class WifiNetwork(
    val ssid: String,
    val bssid: String,
    val rssi: Int,
    val frequency: Int
) {
    val channel: Int get() = when {
        frequency in 2412..2484 -> (frequency - 2412) / 5 + 1
        frequency == 2484       -> 14
        frequency in 5170..5825 -> (frequency - 5000) / 5
        else                    -> 0
    }

    val band: String get() = when {
        frequency < 3000 -> "2.4 GHz"
        frequency < 6000 -> "5 GHz"
        else             -> "6 GHz"
    }

    /** Señal en porcentaje 0–100 para mostrar en barras */
    val signalPercent: Int get() = when {
        rssi >= -50 -> 100
        rssi <= -100 -> 0
        else -> ((rssi + 100) * 2).coerceIn(0, 100)
    }

    /** Etiqueta de calidad de señal */
    val signalLabel: String get() = when {
        rssi >= -50 -> "Excelente"
        rssi >= -60 -> "Buena"
        rssi >= -70 -> "Regular"
        rssi >= -80 -> "Débil"
        else        -> "Muy débil"
    }

    /**
     * Distancia estimada al router usando log-distance path loss (n=3 para interiores).
     * Referencia a 1m: -40 dBm en 2.4 GHz, -47 dBm en 5/6 GHz.
     */
    val estimatedDistance: String get() {
        val refDbm = if (frequency < 3000) -40 else -47
        val distM  = Math.pow(10.0, (refDbm - rssi) / 30.0)   // /30 = 10*n, n=3
        return when {
            distM <  2  -> "~1m"
            distM <  10 -> "~${distM.toInt()}m"
            distM <  50 -> "~${((distM / 5).toInt() * 5)}m"
            distM < 100 -> "~${((distM / 10).toInt() * 10)}m"
            else        -> ">100m"
        }
    }
}
