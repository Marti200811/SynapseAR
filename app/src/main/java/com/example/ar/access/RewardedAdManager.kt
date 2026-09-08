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
     * Muestra el anuncio. [onEarned] se invoca SOLO si el usuario lo completó y
     * ganó la recompensa; si lo cierra antes, se invoca [onFailed].
     */
    fun show(onEarned: () -> Unit, onFailed: () -> Unit) {
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
                if (!earned) onFailed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                rewardedAd = null
                preload()
                onFailed()
            }
        }

        ad.show(activity) {
            earned = true
            onEarned()
        }
    }
}
