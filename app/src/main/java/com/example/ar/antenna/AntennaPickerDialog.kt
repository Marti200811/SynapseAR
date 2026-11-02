package com.example.ar.antenna

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Selector del tipo de antena.
 *
 * Constructor sin parámetros a propósito: al recrear la Activity (tamaño de letra, tema,
 * idioma) `FragmentFactory` reinstancia los diálogos abiertos con el constructor vacío, y
 * si no existe tira `Fragment$InstantiationException`. Los datos entran por [newInstance]
 * y el tipo elegido sale por `setFragmentResult` con [RESULT_KEY]; quien escucha registra
 * el listener en `onViewCreated`, nunca dentro del `setOnClickListener`.
 */
class AntennaPickerDialog : DialogFragment() {

    companion object {
        /** Clave del resultado. Quien escucha lee el Bundle con [antennaTypeFromResult]. */
        const val RESULT_KEY = "antenna_picker_result"

        private const val ARG_CURRENT = "current"
        private const val RESULT_TYPE = "type"

        /** @param current El tipo que ya está elegido, el que lleva el tilde en la lista. */
        fun newInstance(current: AntennaType) = AntennaPickerDialog().apply {
            // El enum viaja por name() y no por ordinal: reordenar AntennaType no puede
            // convertir un tipo en otro.
            arguments = Bundle().apply { putString(ARG_CURRENT, current.name) }
        }

        /** Lee el tipo elegido del Bundle que entrega [RESULT_KEY]. */
        fun antennaTypeFromResult(bundle: Bundle): AntennaType? =
            bundle.getString(RESULT_TYPE)?.let { AntennaType.valueOf(it) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())

        // Layout reutilizamos dialog_satellite_picker pero con otro título
        val view = inflater.inflate(R.layout.dialog_antenna_picker, null)
        val rv   = view.findViewById<RecyclerView>(R.id.rvAntennaTypes)

        // Mismo valor por defecto que usa quien lo abre cuando el ViewModel todavía no
        // tiene tipo: solo decide dónde va el tilde.
        val current = arguments?.getString(ARG_CURRENT)
            ?.let { AntennaType.valueOf(it) }
            ?: AntennaType.SATELLITE

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = AntennaAdapter(AntennaType.entries, current) { type ->
            setFragmentResult(RESULT_KEY, Bundle().apply { putString(RESULT_TYPE, type.name) })
            dismiss()
        }

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class AntennaAdapter(
    private val items: List<AntennaType>,
    private val selected: AntennaType,
    private val onClick: (AntennaType) -> Unit
) : RecyclerView.Adapter<AntennaAdapter.VH>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_antenna_type, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvEmoji = view.findViewById<TextView>(R.id.tvAntennaEmoji)
        private val tvName  = view.findViewById<TextView>(R.id.tvAntennaName)
        private val tvDesc  = view.findViewById<TextView>(R.id.tvAntennaDesc)
        private val tvCheck = view.findViewById<TextView>(R.id.tvAntennaCheck)

        fun bind(type: AntennaType) {
            tvEmoji.text = type.emoji
            tvName.text  = type.label
            tvDesc.text  = type.description
            tvCheck.visibility = if (type == selected) View.VISIBLE else View.GONE
            itemView.setOnClickListener { onClick(type) }
        }
    }
}
