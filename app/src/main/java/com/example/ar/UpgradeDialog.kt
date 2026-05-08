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

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_upgrade, null)

        view.findViewById<MaterialButton>(R.id.btnBuyPro).setOnClickListener {
            billingManager?.launchPurchase()
            dismiss()
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
}
