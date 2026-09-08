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
 * Precarga uno solo por vez. No se puede llamar a [preload] antes de que
 * MobileAds.initialize() haya corrido — en esta app eso pasa recién después del
 * flujo de consentimiento UMP, así que el enganche está en
 * MainActivity.initializeMobileAds().
 */
class RewardedAdManager(private val activity: Activity) {

    private var rewardedAd: RewardedAd? = null
    private var loading = false

    /** true si hay un anuncio listo para mostrar AHORA. */
    fun isAdReady(): Boolean = rewardedAd != null

    fun preload() {
        if (loading || rewardedAd != null) return
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

    /**
     * Muestra el anuncio.
     *
     * Los tres callbacks corren SIEMPRE con el anuncio ya cerrado, nunca encima de él:
     * Google pide que la navegación y las transacciones de fragmento se hagan en
     * onAdDismissedFullScreenContent, no en onUserEarnedReward. Con la AdActivity
     * translúcida de AdMob hoy funcionaría igual, pero con mediación de terceros
     * (activities opacas) un commit() desde ahí tira IllegalStateException.
     *
     * @param onEarned    completó el anuncio y ganó la recompensa
     * @param onCancelled lo cerró antes de completarlo — no es un error, no avisar como tal
     * @param onFailed    no se pudo mostrar (sin anuncio cargado o error del SDK)
     */
    fun show(onEarned: () -> Unit, onCancelled: () -> Unit, onFailed: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            onFailed()
            return
        }

        var earned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preload()                       // dejar listo el siguiente
                if (earned) onEarned() else onCancelled()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                preload()
                onFailed()
            }
        }

        ad.show(activity) {
            // Solo marcar. El trabajo real se hace al cerrarse el anuncio.
            earned = true
        }
    }
}
