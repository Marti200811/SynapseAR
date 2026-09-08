package com.example.ar.access

/**
 * Funciones que en la versión gratuita están bloqueadas y se pueden desbloquear
 * temporalmente mirando un anuncio recompensado.
 *
 * OJO: SATELLITE es UNA función, no una por satélite. Un anuncio desbloquea
 * todos los satélites de pago a la vez — ver el spec del 2026-09-08.
 */
enum class Feature {
    AR,
    COMPASS_THEMES,
    WIFI_SCANNER,
    TDT_PICKER,
    SATELLITE
}
