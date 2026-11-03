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
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ar.Analytics
import com.example.ar.MainActivity
import com.example.ar.R
import com.example.ar.UpgradeDialog
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Selector de satélite geoestacionario.
 *
 * El constructor va SIN parámetros a propósito. Cuando cambia el tamaño de letra, el tema
 * o el idioma, Android recrea la Activity y `FragmentFactory` vuelve a instanciar los
 * diálogos que estaban abiertos llamando al constructor vacío; si no existe, tira
 * `Fragment$InstantiationException` y la app se cierra (es el crash de la v21, reproducido
 * en el Moto el 2026-11-02). Por eso los datos entran por [newInstance] / `arguments` y el
 * satélite elegido sale por `setFragmentResult` con [RESULT_KEY].
 *
 * Quien lo abre registra su listener en `onViewCreated`, NUNCA dentro del
 * `setOnClickListener`: después de recrearse, el diálogo restaurado entrega el resultado
 * enseguida, y si todavía no hay nadie escuchando se pierde en silencio.
 *
 * El control de acceso Pro vive acá adentro ([AccessManager] más `freeSatelliteNames`):
 * quien llama no decide nada de eso.
 */
class SatellitePickerDialog : DialogFragment() {

    companion object {
        /** Clave del resultado. Quien escucha lee el Bundle con [satelliteFromResult]. */
        const val RESULT_KEY = "satellite_picker_result"

        private const val ARG_LOCATION = "location"

        private const val RESULT_NAME = "name"
        private const val RESULT_ORBITAL_LON = "orbital_lon"
        private const val RESULT_REGION = "region"
        private const val RESULT_USE = "use"

        /** Clave del estado guardado: ver [satelitePendiente]. */
        private const val STATE_PENDIENTE = "satelite_pendiente"

        /**
         * @param location Ubicación desde donde se mira. Si se provee, los satélites se
         *                 ordenan por visibilidad (elevación > 0 primero) y se muestra la
         *                 elevación de cada ítem. `Location` es `Parcelable`, así que
         *                 viaja entero en el Bundle.
         */
        fun newInstance(location: Location? = null) = SatellitePickerDialog().apply {
            arguments = Bundle().apply { putParcelable(ARG_LOCATION, location) }
        }

        /**
         * Rearma el [Satellite] que viajó en el Bundle de [RESULT_KEY].
         *
         * Se lee junto con [resultBundle], que es su par: `Satellite` no es `Parcelable`,
         * así que sus campos viajan sueltos. Con una clave con nombre por campo no hay
         * forma de cruzar dos valores del mismo tipo sin que se vea en estas líneas.
         */
        fun satelliteFromResult(bundle: Bundle) = Satellite(
            name       = bundle.getString(RESULT_NAME) ?: "",
            orbitalLon = bundle.getDouble(RESULT_ORBITAL_LON),
            region     = bundle.getString(RESULT_REGION) ?: "",
            use        = bundle.getString(RESULT_USE) ?: ""
        )

        private fun resultBundle(satellite: Satellite) = Bundle().apply {
            putString(RESULT_NAME, satellite.name)
            putDouble(RESULT_ORBITAL_LON, satellite.orbitalLon)
            putString(RESULT_REGION, satellite.region)
            putString(RESULT_USE, satellite.use)
        }
    }

    /**
     * Satélite que el usuario tocó estando bloqueado, mientras espera el desbloqueo por
     * anuncio. Se guarda el **nombre** y no el objeto porque es lo único que hace falta para
     * volver a encontrarlo en [SatelliteDatabase], y un String entra en el estado guardado.
     *
     * Hace falta recordarlo porque este es el único desbloqueo que depende de en qué tocó el
     * usuario. Los otros rehacen una acción fija; este tiene que entregar *ese* satélite, y
     * la variable capturada por la lambda no sobrevivía a que Android recreara el diálogo.
     */
    private var satelitePendiente: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        satelitePendiente = savedInstanceState?.getString(STATE_PENDIENTE)

        // Entregar el satélite que el usuario había tocado, si se desbloqueó con un anuncio.
        //
        // El listener va en `onCreate` y NO adentro del click: después de una recreación el
        // diálogo de upgrade entrega su resultado enseguida, así que si nadie está
        // escuchando todavía, el desbloqueo se pierde en silencio. Y va en el
        // `FragmentManager` de la Activity porque es ahí donde se muestra ese diálogo.
        // El owner es el fragment y no `viewLifecycleOwner`: al venir de `onCreateDialog`,
        // este DialogFragment no tiene vista propia y `viewLifecycleOwner` tiraría.
        requireActivity().supportFragmentManager.setFragmentResultListener(
            UpgradeDialog.resultKeyFor(Feature.SATELLITE), this
        ) { _, _ ->
            val nombre = satelitePendiente ?: return@setFragmentResultListener
            SatelliteDatabase.satellites.firstOrNull { it.name == nombre }
                ?.let { entregarResultado(it) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PENDIENTE, satelitePendiente)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.dialog_satellite_picker, null)

        val location = arguments?.let {
            BundleCompat.getParcelable(it, ARG_LOCATION, Location::class.java)
        }

        val etSearch = view.findViewById<EditText>(R.id.etSatSearch)
        val rv       = view.findViewById<RecyclerView>(R.id.rvSatellites)

        // Vale tanto para el candado visual como para el gate de selección.
        // Se llama una vez acá a propósito: si se consultara por fila, el estado
        // podría cambiar a mitad del scroll.
        val hasAccess = AccessManager.canUse(requireContext(), Feature.SATELLITE)

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

        val adapter = SatelliteAdapter(sorted, hasAccess, elevationMap) { satellite ->
            val isFree = SatelliteDatabase.freeSatelliteNames.contains(satellite.name)
            if (hasAccess || isFree) {
                entregarResultado(satellite)
            } else {
                satelitePendiente = satellite.name
                (requireActivity() as MainActivity).showUpgradeDialog(
                    source = Analytics.SRC_SATELLITE_LOCKED,
                    feature = Feature.SATELLITE
                )
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

    private fun entregarResultado(satellite: Satellite) {
        setFragmentResult(RESULT_KEY, resultBundle(satellite))
        dismiss()
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
        else allItems.filter {
                it.name.contains(query, ignoreCase = true) ||
                it.positionLabel.contains(query, ignoreCase = true) ||
                it.use.contains(query, ignoreCase = true)
            }.toMutableList()
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
