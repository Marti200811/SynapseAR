package com.example.ar

import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import java.text.NumberFormat
import java.util.Currency

/**
 * Gestiona la promoción de lanzamiento (precio ancla tachado).
 *
 * La promo se controla 100% desde Firebase Remote Config — sin actualizar la app:
 *   promo_active            (bool)  → true mientras la promo esté vigente
 *   promo_discount_percent  (long)  → % de descuento REAL (1-99)
 *
 * El precio ancla (tachado) se calcula a partir del precio actual de Google
 * (ya localizado en la moneda del usuario), de modo que:
 *   - solo aparece cuando hay una promo real activa (honesto / política de Play)
 *   - se muestra en la moneda local de cada usuario
 *   - se apaga remotamente cuando termina la promo
 *
 * Ejemplo: precio base $9.99, promo a $4.99 (50% off) →
 *          promo_active=true, promo_discount_percent=50 →
 *          la app calcula ancla ≈ $9.98 y la muestra tachada junto a $4.99.
 */
object PromoManager {

    private const val RC_PROMO_ACTIVE = "promo_active"
    private const val RC_PROMO_DISCOUNT = "promo_discount_percent"

    /** ¿Hay una promo válida activa? */
    fun isPromoActive(): Boolean {
        val rc = FirebaseRemoteConfig.getInstance()
        val active = rc.getBoolean(RC_PROMO_ACTIVE)
        val discount = rc.getLong(RC_PROMO_DISCOUNT)
        return active && discount in 1..99
    }

    /**
     * Calcula el precio ancla (precio "normal" tachado) en la moneda del usuario,
     * a partir del precio actual con descuento.
     *
     * @param currentMicros precio actual (con descuento) en micros
     * @param currencyCode  código ISO 4217 del precio (ej. "USD", "ARS")
     * @return texto formateado del precio ancla, o null si no se puede calcular
     */
    fun anchorPrice(currentMicros: Long, currencyCode: String?): String? {
        if (!isPromoActive() || currentMicros <= 0L || currencyCode.isNullOrBlank()) return null

        val discount = FirebaseRemoteConfig.getInstance().getLong(RC_PROMO_DISCOUNT)
        if (discount !in 1..99) return null

        // precioActual = precioAncla * (1 - descuento/100)  →  precioAncla = precioActual / (1 - d/100)
        val anchorMicros = (currentMicros * 100.0) / (100.0 - discount)
        val anchorValue = anchorMicros / 1_000_000.0

        return try {
            val nf = NumberFormat.getCurrencyInstance()
            nf.currency = Currency.getInstance(currencyCode)
            nf.format(anchorValue)
        } catch (e: IllegalArgumentException) {
            null  // código de moneda inválido
        }
    }
}
