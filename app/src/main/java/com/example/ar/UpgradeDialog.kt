package com.example.ar

import android.app.Dialog
import android.os.Bundle
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

    /** Si hay precio, mostrarlo en el botón; sino texto genérico (fallback). */
    private fun applyPrice(price: String?) {
        btnBuy?.text = if (!price.isNullOrBlank()) {
            getString(R.string.upgrade_btn_buy_price, price)
        } else {
            getString(R.string.upgrade_btn_buy)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        // Evitar fugas: el listener captura la vista del botón
        billingManager?.setPriceListener(null)
        btnBuy = null
    }
}
