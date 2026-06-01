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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class UpgradeDialog : DialogFragment() {

    /** La activity pasa su BillingManager para poder lanzar la compra */
    var billingManager: BillingManager? = null

    private var btnBuy: MaterialButton? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_upgrade, null)

        btnBuy = view.findViewById<MaterialButton>(R.id.btnBuyPro).apply {
            setOnClickListener {
                billingManager?.launchPurchase()
                dismiss()
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
