package com.example.ar

import android.app.Dialog
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.view.LayoutInflater
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
import com.example.ar.access.RewardedAdManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UpgradeDialog : DialogFragment() {

    /** La activity pasa su BillingManager para poder lanzar la compra */
    var billingManager: BillingManager? = null

    /** Función bloqueada que trajo al usuario acá — se registra en Analytics. */
    var source: String = "unknown"

    /** Manager de anuncios recompensados. Si es null, no se ofrece la opción. */
    var rewardedAdManager: RewardedAdManager? = null

    /** Función que se desbloquea si el usuario gana la recompensa. */
    var feature: Feature? = null

    /** Se invoca tras ganar la recompensa, para ejecutar la acción que estaba bloqueada. */
    var onRewardEarned: (() -> Unit)? = null

    private var btnBuy: MaterialButton? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_upgrade, null)

        btnBuy = view.findViewById<MaterialButton>(R.id.btnBuyPro).apply {
            setOnClickListener {
                // Se registra ANTES de lanzar la compra: mide intención aunque
                // el flujo de Google Play falle (ej. perfil de pagos incompleto).
                Analytics.upgradeButtonTapped(requireContext(), source)
                billingManager?.launchPurchase()
                dismiss()
            }
        }

        val ads = rewardedAdManager
        val feat = feature
        val btnAd = view.findViewById<MaterialButton>(R.id.btnWatchAd)

        // Si no hay anuncio listo, se intenta cargar uno para la próxima vez que
        // se abra el diálogo. Sin esto, si la precarga inicial falló (sin red al
        // arrancar, por ejemplo), la opción no volvería a aparecer nunca.
        if (ads != null && !ads.isAdReady()) ads.preload()

        // Solo se ofrece si HAY un anuncio cargado ahora. Nunca se muestra un botón
        // deshabilitado con "Cargando…": ese fue un bug real en el proyecto hermano
        // Oráculo, donde el usuario veía un botón muerto sin saber por qué.
        if (ads != null && feat != null && ads.isAdReady()) {
            btnAd.visibility = android.view.View.VISIBLE
            Analytics.rewardedOffered(requireContext(), source)
            btnAd.setOnClickListener {
                Analytics.rewardedStarted(requireContext(), source)
                ads.show(
                    onEarned = {
                        AccessManager.grantTemporary(requireContext(), feat)
                        Analytics.rewardedEarned(requireContext(), source)
                        onRewardEarned?.invoke()
                        dismiss()
                    },
                    onFailed = {
                        android.widget.Toast.makeText(
                            requireContext(),
                            getString(R.string.rewarded_failed),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }

        // Mostrar el precio real de Google Play (localizado, refleja promos)
        applyPrice(billingManager?.formattedProPrice)
        billingManager?.setPriceListener { price ->
            if (isAdded) applyPrice(price)
        }

        view.findViewById<TextView>(R.id.btnRestore).setOnClickListener {
            billingManager?.restorePurchases()
            dismiss()
        }

        view.findViewById<TextView>(R.id.btnDismiss).setOnClickListener {
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    /**
     * Muestra el precio en el botón. Si hay una promo de lanzamiento activa
     * (Remote Config), antepone el precio "normal" tachado (precio ancla),
     * un clásico de la estrategia de ventas.
     */
    private fun applyPrice(price: String?) {
        val btn = btnBuy ?: return

        if (price.isNullOrBlank()) {
            btn.text = getString(R.string.upgrade_btn_buy)   // fallback sin precio
            return
        }

        val bm = billingManager
        val anchor = bm?.let {
            PromoManager.anchorPrice(it.proPriceMicros, it.proPriceCurrencyCode)
        }

        if (anchor != null) {
            // "✨ Desbloquear Pro — $9.98  $4.99"  (con $9.98 tachado y más chico)
            val sb = SpannableStringBuilder(getString(R.string.upgrade_btn_buy)).append(" — ")
            val start = sb.length
            sb.append(anchor)
            sb.setSpan(StrikethroughSpan(), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.85f), start, sb.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.append("  ").append(price)
            btn.text = sb
        } else {
            btn.text = getString(R.string.upgrade_btn_buy_price, price)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Evitar fugas: el listener captura la vista del botón
        billingManager?.setPriceListener(null)
        btnBuy = null
    }
}
