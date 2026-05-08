package com.example.ar

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsDialog : DialogFragment() {

    /** Notifica al fragmento padre cuando cambia la preferencia */
    var onUnitsChanged: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx     = requireContext()
        val current = SettingsManager.getUnitSystem(ctx)

        val options = arrayOf(
            getString(R.string.units_auto),
            getString(R.string.units_metric),
            getString(R.string.units_imperial)
        )
        val checkedItem = when (current) {
            UnitSystem.AUTO     -> 0
            UnitSystem.METRIC   -> 1
            UnitSystem.IMPERIAL -> 2
        }

        return MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.settings_units_label))
            .setSingleChoiceItems(options, checkedItem) { dialog, which ->
                val system = when (which) {
                    1    -> UnitSystem.METRIC
                    2    -> UnitSystem.IMPERIAL
                    else -> UnitSystem.AUTO
                }
                SettingsManager.setUnitSystem(ctx, system)
                onUnitsChanged?.invoke()
                dialog.dismiss()
            }
            .create()
    }
}
