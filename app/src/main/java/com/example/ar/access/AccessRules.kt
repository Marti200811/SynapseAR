package com.example.ar.access

/**
 * Reglas de acceso, sin dependencias de Android para poder testearlas.
 *
 * Toda la decisión de "¿puede usar esto?" vive acá. La fachada AccessManager
 * solo se encarga de conseguir los datos (estado Pro, vencimiento guardado, reloj).
 */
object AccessRules {

    /** Cuánto dura el desbloqueo que otorga un anuncio recompensado. */
    const val UNLOCK_DURATION_MS: Long = 30L * 60L * 1000L

    /**
     * Un usuario Pro siempre puede. Si no lo es, necesita un desbloqueo que
     * todavía no haya vencido. Un vencimiento exactamente igual a "ahora" ya venció.
     */
    fun canUse(isPro: Boolean, expiryMillis: Long, nowMillis: Long): Boolean =
        isPro || nowMillis < expiryMillis

    /** Milisegundos que le quedan al desbloqueo. Nunca negativo. */
    fun remaining(expiryMillis: Long, nowMillis: Long): Long =
        (expiryMillis - nowMillis).coerceAtLeast(0L)
}
