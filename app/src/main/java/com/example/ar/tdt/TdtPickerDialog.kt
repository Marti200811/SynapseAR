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
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Selector de transmisor de TDT.
 *
 * Constructor sin parámetros a propósito: al recrear la Activity (tamaño de letra, tema,
 * idioma) `FragmentFactory` reinstancia los diálogos abiertos con el constructor vacío, y
 * si no existe tira `Fragment$InstantiationException`. Los datos entran por [newInstance]
 * y el transmisor elegido sale por `setFragmentResult` con [RESULT_KEY]; quien escucha
 * registra el listener en `onViewCreated`, nunca dentro del `setOnClickListener`.
 */
class TdtPickerDialog : DialogFragment() {

    private lateinit var adapter: TdtAdapter

    companion object {
        /** Clave del resultado. Quien escucha lee el Bundle con [transmitterFromResult]. */
        const val RESULT_KEY = "tdt_picker_result"

        private const val ARG_LOCATION = "location"
        private const val ARG_COUNTRY_CODE = "country_code"

        private const val RESULT_NAME = "name"
        private const val RESULT_CITY = "city"
        private const val RESULT_COUNTRY = "country"
        private const val RESULT_STANDARD = "standard"
        private const val RESULT_LAT = "lat"
        private const val RESULT_LON = "lon"
        private const val RESULT_POWER = "power"

        /**
         * @param location    Desde dónde se mira: ordena por cercanía y muestra la distancia.
         *                    `Location` es `Parcelable`, así que viaja entero en el Bundle.
         * @param countryCode ISO 3166-1 del país del usuario, para filtrar el listado.
         */
        fun newInstance(location: Location?, countryCode: String? = null) =
            TdtPickerDialog().apply {
                arguments = Bundle().apply {
                    putParcelable(ARG_LOCATION, location)
                    putString(ARG_COUNTRY_CODE, countryCode)
                }
            }

        /**
         * Rearma el [TdtTransmitter] que viajó en el Bundle de [RESULT_KEY].
         *
         * Se lee junto con [resultBundle], que es su par: `TdtTransmitter` no es
         * `Parcelable`, así que sus campos viajan sueltos. Con una clave con nombre por
         * campo no hay forma de cruzar lat con lon sin que se vea en estas líneas.
         */
        fun transmitterFromResult(bundle: Bundle) = TdtTransmitter(
            name     = bundle.getString(RESULT_NAME) ?: "",
            city     = bundle.getString(RESULT_CITY) ?: "",
            country  = bundle.getString(RESULT_COUNTRY) ?: "",
            standard = bundle.getString(RESULT_STANDARD) ?: "",
            lat      = bundle.getDouble(RESULT_LAT),
            lon      = bundle.getDouble(RESULT_LON),
            power    = bundle.getDouble(RESULT_POWER)
        )

        private fun resultBundle(transmitter: TdtTransmitter) = Bundle().apply {
            putString(RESULT_NAME, transmitter.name)
            putString(RESULT_CITY, transmitter.city)
            putString(RESULT_COUNTRY, transmitter.country)
            putString(RESULT_STANDARD, transmitter.standard)
            putDouble(RESULT_LAT, transmitter.lat)
            putDouble(RESULT_LON, transmitter.lon)
            putDouble(RESULT_POWER, transmitter.power)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view     = inflater.inflate(R.layout.dialog_tdt_picker, null)

        val etSearch = view.findViewById<EditText>(R.id.etTdtSearch)
        val rv       = view.findViewById<RecyclerView>(R.id.rvTdtTransmitters)

        // Se leen una vez acá y se pasan como parámetros: siguen siendo val, así que el
        // smart cast de `location != null` adentro de buildInitialList sigue funcionando.
        val location = arguments?.let {
            BundleCompat.getParcelable(it, ARG_LOCATION, Location::class.java)
        }
        val countryCode = arguments?.getString(ARG_COUNTRY_CODE)

        val initial = buildInitialList(location, countryCode)

        adapter = TdtAdapter(initial) { transmitter ->
            setFragmentResult(RESULT_KEY, resultBundle(transmitter))
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
                    buildInitialList(location, countryCode)
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

    private fun buildInitialList(
        location: Location?, countryCode: String?
    ): List<Pair<TdtTransmitter, Double?>> {
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
