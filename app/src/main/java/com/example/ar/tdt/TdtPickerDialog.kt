package com.example.ar.tdt

import android.app.Dialog
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
import com.example.ar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class TdtPickerDialog(
    private val location: Location?,
    private val countryCode: String? = null,
    private val onSelected: (TdtTransmitter) -> Unit
) : DialogFragment() {

    private lateinit var adapter: TdtAdapter

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view     = inflater.inflate(R.layout.dialog_tdt_picker, null)

        val etSearch = view.findViewById<EditText>(R.id.etTdtSearch)
        val rv       = view.findViewById<RecyclerView>(R.id.rvTdtTransmitters)

        val initial = buildInitialList()

        adapter = TdtAdapter(initial) { transmitter ->
            onSelected(transmitter)
            dismiss()
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                val results = if (query.isBlank()) {
                    buildInitialList()
                } else {
                    if (countryCode != null) {
                        TdtDatabase.search(query, location?.latitude, location?.longitude)
                            .filter { it.first.country.equals(countryCode, ignoreCase = true) }
                            .ifEmpty { TdtDatabase.search(query, location?.latitude, location?.longitude) }
                    } else {
                        TdtDatabase.search(query, location?.latitude, location?.longitude)
                    }
                }
                adapter.update(results)
            }
        })

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .create()
    }

    private fun buildInitialList(): List<Pair<TdtTransmitter, Double?>> {
        return when {
            countryCode != null -> {
                val local = TdtDatabase.byCountry(countryCode, location?.latitude, location?.longitude)
                local.ifEmpty {
                    if (location != null)
                        TdtDatabase.nearest(location.latitude, location.longitude, 30)
                    else
                        TdtDatabase.transmitters.map { it to null }
                }
            }
            location != null ->
                TdtDatabase.nearest(location.latitude, location.longitude, 30)
            else ->
                TdtDatabase.transmitters.map { it to null }
        }
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class TdtAdapter(
    items: List<Pair<TdtTransmitter, Double?>>,
    private val onClick: (TdtTransmitter) -> Unit
) : RecyclerView.Adapter<TdtAdapter.VH>() {

    private val list = items.toMutableList()

    fun update(items: List<Pair<TdtTransmitter, Double?>>) {
        list.clear(); list.addAll(items); notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tdt_transmitter, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(list[position].first, list[position].second)

    override fun getItemCount() = list.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvFlag     = view.findViewById<TextView>(R.id.tvTdtFlag)
        private val tvName     = view.findViewById<TextView>(R.id.tvTdtName)
        private val tvCity     = view.findViewById<TextView>(R.id.tvTdtCity)
        private val tvStandard = view.findViewById<TextView>(R.id.tvTdtStandard)
        private val tvDistance = view.findViewById<TextView>(R.id.tvTdtDistance)

        fun bind(t: TdtTransmitter, distKm: Double?) {
            tvFlag.text     = t.countryFlag
            tvName.text     = t.name
            tvCity.text     = t.city
            tvStandard.text = t.standard
            tvDistance.text = when {
                distKm == null  -> ""
                distKm < 1.0   -> "< 1 km"
                else           -> "%.0f km".format(distKm)
            }
            tvDistance.setTextColor(when {
                distKm == null  -> 0xFF00E5FF.toInt()
                distKm < 100   -> 0xFF00FF88.toInt()
                distKm < 500   -> 0xFFFFB300.toInt()
                else           -> 0xFF7A8CA8.toInt()
            })
            itemView.setOnClickListener { onClick(t) }
        }
    }
}
