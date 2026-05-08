package com.example.ar.antenna

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class AntennaPickerDialog(
    private val current: AntennaType,
    private val onSelected: (AntennaType) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())

        // Layout reutilizamos dialog_satellite_picker pero con otro título
        val view = inflater.inflate(R.layout.dialog_antenna_picker, null)
        val rv   = view.findViewById<RecyclerView>(R.id.rvAntennaTypes)

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = AntennaAdapter(AntennaType.entries, current) { type ->
            onSelected(type)
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
