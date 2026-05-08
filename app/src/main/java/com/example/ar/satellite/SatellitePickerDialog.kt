package com.example.ar.satellite

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.widget.TextView
import com.example.ar.MainActivity
import com.example.ar.ProManager
import com.example.ar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SatellitePickerDialog(
    private val onSelected: (Satellite) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_satellite_picker, null)

        val etSearch = view.findViewById<EditText>(R.id.etSatSearch)
        val rv       = view.findViewById<RecyclerView>(R.id.rvSatellites)

        val isPro = ProManager.isPro(requireContext())
        val adapter = SatelliteAdapter(SatelliteDatabase.satellites, isPro) { satellite ->
            val isFree = SatelliteDatabase.freeSatelliteNames.contains(satellite.name)
            if (isPro || isFree) {
                onSelected(satellite)
                dismiss()
            } else {
                (requireActivity() as MainActivity).showUpgradeDialog()
            }
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class SatelliteAdapter(
    private val allItems: List<Satellite>,
    private val isPro: Boolean,
    private val onClick: (Satellite) -> Unit
) : RecyclerView.Adapter<SatelliteAdapter.VH>() {

    private var displayed = allItems.toMutableList()

    fun filter(query: String) {
        displayed = if (query.isBlank()) allItems.toMutableList()
        else allItems.filter { it.name.contains(query, ignoreCase = true) }.toMutableList()
        notifyDataSetChanged()
    }

    private fun isFree(sat: Satellite) =
        SatelliteDatabase.freeSatelliteNames.contains(sat.name)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_satellite, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(displayed[position])

    override fun getItemCount() = displayed.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvPos  = view.findViewById<TextView>(R.id.tvSatPosition)
        private val tvName = view.findViewById<TextView>(R.id.tvSatName)
        private val tvUse  = view.findViewById<TextView>(R.id.tvSatUse)

        fun bind(sat: Satellite) {
            val locked = !isPro && !isFree(sat)
            tvPos.text  = if (locked) "🔒" else sat.positionLabel
            tvName.text = sat.name
            tvUse.text  = if (locked) "Pro" else sat.use
            // Atenuar visualmente los satélites bloqueados
            itemView.alpha = if (locked) 0.45f else 1f
            itemView.setOnClickListener { onClick(sat) }
        }
    }
}
