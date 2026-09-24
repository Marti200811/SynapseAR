package com.example.ar.access

import android.content.Context
import com.example.ar.ProManager

/**
 * Única pregunta que deben hacer los call sites: ¿puede usar esta función?
 *
 * Existe para que la condición "es Pro O tiene desbloqueo vigente" viva en un
 * solo lugar. Repetirla en cada pantalla es donde aparecen los agujeros: en el
 * proyecto hermano Oráculo, una condición armada a mano en un call site permitía
 * consultas ilimitadas gratis.
 */
object AccessManager {

    fun canUse(context: Context, feature: Feature): Boolean =
        AccessRules.canUse(
            isPro = ProManager.isPro(context),
            expiryMillis = UnlockStore.expiryMillis(context, feature),
            nowMillis = System.currentTimeMillis()
        )

    /**
     * `true` si todavía le queda el anuncio del día para esta función. Lo consulta el
     * diálogo antes de ofrecer el botón: si ya lo usó, no se le muestra una opción que
     * no va a funcionar.
     */
    fun canWatchAdFor(context: Context, feature: Feature): Boolean =
        AccessRules.canWatchAdToday(
            lastAdDay = UnlockStore.lastAdDay(context, feature),
            today = today()
        )

    /**
     * Otorga acceso temporal a [feature]. Se llama al ganar la recompensa de un anuncio.
     * Deja registrado el día para que no se pueda repetir hasta mañana.
     */
    fun grantTemporary(context: Context, feature: Feature) {
        UnlockStore.setExpiryMillis(
            context,
            feature,
            System.currentTimeMillis() + AccessRules.UNLOCK_DURATION_MS
        )
        UnlockStore.setLastAdDay(context, feature, today())
    }

    private fun today(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis()))

    /** Milisegundos que le quedan al desbloqueo temporal. 0 si no hay o ya venció. */
    fun remainingMillis(context: Context, feature: Feature): Long =
        AccessRules.remaining(
            expiryMillis = UnlockStore.expiryMillis(context, feature),
            nowMillis = System.currentTimeMillis()
        )
}
