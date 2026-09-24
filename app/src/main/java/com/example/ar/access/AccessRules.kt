package com.example.ar.access

/**
 * Reglas de acceso, sin dependencias de Android para poder testearlas.
 *
 * Toda la decisión de "¿puede usar esto?" vive acá. La fachada AccessManager
 * solo se encarga de conseguir los datos (estado Pro, vencimiento guardado, reloj).
 */
object AccessRules {

    /**
     * Cuánto dura el desbloqueo que otorga un anuncio recompensado.
     *
     * Se mantiene en 30 minutos a propósito. Esta app se usa arriba de un techo, con la
     * antena en la mano: cortar el desbloqueo a mitad de una orientación no empuja a
     * comprar, deja al usuario plantado en medio de un trabajo. La presión de compra la
     * hace el tope diario, no la duración.
     */
    const val UNLOCK_DURATION_MS: Long = 30L * 60L * 1000L

    /**
     * Un anuncio por función y por día. Cada función se desbloquea por separado: el que
     * mira un anuncio por el escáner WiFi sigue pudiendo mirar otro por AR el mismo día.
     * La idea es que se pueda conocer cada función, pero no usarla gratis todo el día.
     *
     * El corte es por día del calendario local, no por 24 horas corridas, porque es lo
     * que el usuario entiende ("mañana lo vuelvo a tener"). Cambiar la zona horaria del
     * teléfono permitiría estirar un desbloqueo extra; no se defiende contra eso a
     * propósito: un anuncio de más son ingresos de más, no una pérdida.
     *
     * @param lastAdDay día en que se miró el último anuncio de esta función ("yyyy-MM-dd"),
     *   o null si nunca se miró uno.
     */
    fun canWatchAdToday(lastAdDay: String?, today: String): Boolean = lastAdDay != today

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
