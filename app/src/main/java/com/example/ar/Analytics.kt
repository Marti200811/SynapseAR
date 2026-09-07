package com.example.ar

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Embudo de compra.
 *
 * Objetivo: medir la INTENCIÓN de compra sin depender de que la compra se
 * complete. Con esto se puede decidir si vale la pena el trámite fiscal para
 * cobrar ANTES de hacerlo: si nadie aprieta el botón, no hay demanda que
 * monetizar y no tiene sentido inscribirse en nada.
 *
 * El evento se registra en el momento del tap, antes de que Google Play
 * intervenga — así que sirve aunque el flujo de compra esté roto.
 *
 * Dónde verlo: Firebase Console → proyecto `synapsear-f606d` → Analytics →
 * Events. Los eventos nuevos tardan hasta 24 h en aparecer la primera vez;
 * para verlos al instante usar DebugView.
 */
object Analytics {

    // Desde qué función bloqueada se le ofreció Pro al usuario.
    // Comparar los totales por `source` responde: ¿qué función vende?
    const val SRC_AR_TAB           = "ar_tab"
    const val SRC_COMPASS_THEME    = "compass_theme"
    const val SRC_WIFI_SCANNER     = "wifi_scanner"
    const val SRC_TDT_PICKER       = "tdt_picker"
    const val SRC_SATELLITE_LOCKED = "satellite_locked"

    private fun log(context: Context, event: String, source: String) {
        FirebaseAnalytics.getInstance(context.applicationContext)
            .logEvent(event, Bundle().apply { putString("source", source) })
    }

    /** El usuario llegó a la pantalla de venta. Denominador del embudo. */
    fun upgradeDialogShown(context: Context, source: String) =
        log(context, "upgrade_dialog_shown", source)

    /** El usuario apretó comprar. ESTA es la métrica de demanda real. */
    fun upgradeButtonTapped(context: Context, source: String) =
        log(context, "upgrade_button_tapped", source)
}
