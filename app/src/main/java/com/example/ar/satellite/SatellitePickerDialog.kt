package com.example.ar.satellite

import android.app.Dialog
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ar.MainActivity
import com.example.ar.ProManager
import com.example.ar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * @param location  Ubicación actual del usuario. Si se provee, los satélites
 *                  se ordenan por visibilidad (elevación > 0 primero) y se
 *                  muestra la elevación en cada ítem.
 */
class SatellitePickerDialog(
    private val location: Location? = null,
    private val onSelected: (Satellite) -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_satellite_picker, null)

        val etSearch = view.findViewById<EditText>(R.id.etSatSearch)
        val rv       = view.findViewById<RecyclerView>(R.id.rvSatellites)

        val isPro = ProManager.isPro(requireContext())

        // Calcular elevaciones si hay ubicación disponible
        val elevationMap: Map<String, Double> = if (location != null) {
            SatelliteDatabase.satellites.associate { sat ->
                sat.name to SatelliteCalculator.calculate(
                    location.latitude, location.longitude, sat.orbitalLon, location.altitude
                ).elevationDeg
            }
        } else emptyMap()

        // Ordenar: visibles primero (elevación > 0°), dentro de cada grupo por elevación desc
        val sorted = SatelliteDatabase.satellites.sortedWith(
            compareByDescending<Satellite> { (elevationMap[it.name] ?: -90.0) > 0.0 }
                .thenByDescending { elevationMap[it.name] ?: -90.0 }
        )

        val adapter = SatelliteAdapter(sorted, isPro, elevationMap) { satellite ->
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
    private val elevationMap: Map<String, Double>,
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
        private val tvEl   = view.findViewById<TextView>(R.id.tvSatElevation)

        fun bind(sat: Satellite) {
            val locked = !isPro && !isFree(sat)
            tvPos.text  = if (locked) "🔒" else sat.positionLabel
            tvName.text = sat.name
            tvUse.text  = if (locked) "Pro" else sat.use
            itemView.alpha = if (locked) 0.45f else 1f

            val elev = elevationMap[sat.name]
            if (elev != null) {
                tvEl.visibility = View.VISIBLE
                tvEl.text = "%.1f°".format(elev)
                tvEl.setTextColor(when {
                    elev >= 30.0 -> Color.parseColor("#00E5FF")   // cyan = buen ángulo
                    elev >= 10.0 -> Color.parseColor("#FFB300")   // ámbar = aceptable
                    elev >= 0.0  -> Color.parseColor("#FF6D00")   // naranja = bajo
                    else         -> Color.parseColor("#546E7A")   // gris = bajo horizonte
                })
                // Atenuar satélites bajo el horizonte (inútiles desde esta ubicación)
                if (elev < 0.0 && !locked) itemView.alpha = 0.35f
            } else {
                tvEl.visibility = View.GONE
            }

            itemView.setOnClickListener { onClick(sat) }
        }
    }
}
