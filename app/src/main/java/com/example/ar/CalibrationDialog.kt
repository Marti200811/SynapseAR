package com.example.ar

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Diálogo que guía al usuario para calibrar el magnetómetro.
 * Aparece automáticamente cuando la precisión del sensor es baja.
 *
 * La calibración consiste en mover el teléfono describiendo un "8"
 * en el aire (figura de ocho) durante 5-10 segundos.
 */
class CalibrationDialog : DialogFragment() {

    private var animatorSet: AnimatorSet? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_calibration, null)

        val icon = view.findViewById<ImageView>(R.id.calibIcon)
        startFigureEightAnimation(icon)

        val dialog = MaterialAlertDialogBuilder(requireContext(), R.style.ThemeOverlay_SynapseAR_Dialog)
            .setView(view)
            .setPositiveButton(getString(R.string.calibration_btn_ok)) { _, _ -> dismiss() }
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        return dialog
    }

    override fun onDestroyView() {
        super.onDestroyView()
        animatorSet?.cancel()
    }

    /**
     * Anima el ícono en un patrón de figura-8 para mostrar el movimiento requerido.
     */
    private fun startFigureEightAnimation(view: View) {
        val tx1 = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, 0f, 60f, 0f, -60f, 0f).apply {
            duration = 2000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
        val ty1 = ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, 0f, 40f, 0f, -40f, 0f).apply {
            duration = 2000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
        val rot = ObjectAnimator.ofFloat(view, View.ROTATION, 0f, 360f).apply {
            duration = 4000L
            repeatCount = ObjectAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
        animatorSet = AnimatorSet().apply {
            playTogether(tx1, ty1, rot)
            start()
        }
    }
}
