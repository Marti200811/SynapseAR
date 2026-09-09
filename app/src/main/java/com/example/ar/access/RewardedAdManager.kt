package com.example.ar.access

import android.app.Activity
import com.example.ar.R
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Envuelve la carga y exhibición de un anuncio recompensado.
 *
 * Precarga uno solo por vez. No se puede pedir un anuncio antes de que
 * MobileAds.initialize() haya corrido — en esta app eso pasa recién después del
 * flujo de consentimiento UMP, así que el enganche está en
 * MainActivity.initializeMobileAds().
 */
class RewardedAdManager(private val activity: Activity) {

    private var rewardedAd: RewardedAd? = null
    private var loading = false

    /**
     * Lo prende MainActivity cuando MobileAds.initialize() ya corrió, o sea
     * después del consentimiento UMP.
     *
     * Hace falta porque [preload] tiene un segundo llamador: UpgradeDialog, que
     * reintenta la carga si no hay anuncio listo. Ese diálogo puede abrirse antes
     * de que el consentimiento se resuelva (usuario que va derecho a la tab AR al
     * arrancar), y en Europa pedir un anuncio antes del consentimiento es
     * exactamente lo que UMP existe para impedir.
     */
    var adsInitialized = false

    /** true si hay un anuncio listo para mostrar AHORA. */
    fun isAdReady(): Boolean = rewardedAd != null

    fun preload() {
        if (!adsInitialized || loading || rewardedAd != null) return
        loading = true
        RewardedAd.load(
            activity,
            activity.getString(R.string.rewarded_ad_unit_id),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    loading = false
                }
            }
        )
    }

    /** Hay un anuncio presentándose ahora mismo. Evita que un segundo toque lo pise. */
    private var showing = false

    /**
     * Muestra el anuncio.
     *
     * Hay DOS momentos distintos y no se pueden mezclar, cada uno tiene su callback:
     *
     * 1. **Cuando gana** ([onRewardEarned]) — el anuncio sigue en pantalla. Acá va lo que
     *    tiene que quedar guardado sí o sí. Si el usuario mira el anuncio entero y después
     *    el sistema mata el proceso mientras está el end card, el dismissal nunca llega:
     *    lo que no se persistió en este momento se pierde, y el usuario se queda sin la
     *    recompensa que AdMob ya le contabilizó. **Nada de UI acá.**
     * 2. **Cuando se cierra** ([onClosedAfterReward] / [onClosedWithoutReward]) — recién
     *    acá se puede navegar o tocar fragments. Google pide que las transacciones vayan
     *    en onAdDismissedFullScreenContent; hoy funcionaría igual porque la AdActivity de
     *    AdMob es translúcida, pero con mediación de terceros (activities opacas) un
     *    commit() desde el momento 1 tira IllegalStateException.
     *
     * @param onRewardEarned        ganó — persistir acá, sin UI
     * @param onClosedAfterReward   cerró el anuncio habiéndolo completado — la UI va acá
     * @param onClosedWithoutReward lo cerró antes de terminar — no es un error, no avisar
     * @param onFailedToShow        no se pudo mostrar (sin anuncio cargado o error del SDK)
     */
    fun show(
        onRewardEarned: () -> Unit,
        onClosedAfterReward: () -> Unit,
        onClosedWithoutReward: () -> Unit,
        onFailedToShow: () -> Unit
    ) {
        // Segundo toque mientras ya hay uno abriéndose: ignorar en silencio. Sin esto,
        // reasignaría fullScreenContentCallback sobre el mismo anuncio y el usuario vería
        // un Toast de error encima del video que está mirando.
        if (showing) return

        val ad = rewardedAd
        if (ad == null) {
            onFailedToShow()
            return
        }

        // Este anuncio ya se consumió: soltarlo antes de mostrarlo, no después.
        rewardedAd = null
        showing = true

        var earned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                showing = false
                preload()                       // dejar listo el siguiente
                if (earned) onClosedAfterReward() else onClosedWithoutReward()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                showing = false
                preload()
                onFailedToShow()
            }
        }

        ad.show(activity) {
            earned = true
            onRewardEarned()                    // persistir YA, no esperar al cierre
        }
    }
}
