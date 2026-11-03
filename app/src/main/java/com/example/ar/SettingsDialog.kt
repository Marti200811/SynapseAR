package com.example.ar

import android.app.Dialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Ajustes de la app: sistema de unidades y el acceso para calificar.
 *
 * El aviso de "cambiaron las unidades" sale por [RESULT_KEY] y no por una propiedad.
 * Antes era `var onUnitsChanged: (() -> Unit)?`, que el fragment padre asignaba al abrir el
 * diálogo. Al cambiar el tamaño de letra, el tema o el idioma, Android recrea el diálogo sin
 * volver a pasar por ese código y la propiedad quedaba en null: la preferencia **sí** se
 * guardaba, pero el padre no se enteraba y seguía mostrando los números en la unidad vieja
 * hasta que algo más lo refrescara.
 */
class SettingsDialog : DialogFragment() {

    companion object {
        /**
         * Clave del resultado. El Bundle viene vacío a propósito: el aviso es el hecho de
         * que llegó. La unidad elegida ya está guardada en [SettingsManager], que es de
         * donde la lee quien escucha.
         */
        const val RESULT_KEY = "settings_units_changed"

        fun newInstance() = SettingsDialog()
    }

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
                setFragmentResult(RESULT_KEY, Bundle())
                dialog.dismiss()
            }
            // Única superficie de ajustes que tiene la app, así que el acceso para
            // calificar va acá. Abre la ficha de Play, NO la In-App Review API:
            // Google desaconseja disparar esa API desde un botón, porque el usuario
            // puede tener la cuota agotada y no pasaría nada al tocarlo.
            .setNeutralButton(getString(R.string.rate_app)) { _, _ ->
                RatingPrompt.openStoreListing(ctx)
            }
            .create()
    }
}
