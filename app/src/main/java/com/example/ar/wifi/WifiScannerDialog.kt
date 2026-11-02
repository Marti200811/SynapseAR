package com.example.ar.wifi

import android.Manifest
import android.app.Dialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Escáner de redes WiFi: lista lo que se ve alrededor y devuelve la que se elige.
 *
 * Constructor sin parámetros a propósito: al recrear la Activity (tamaño de letra, tema,
 * idioma) `FragmentFactory` reinstancia los diálogos abiertos con el constructor vacío, y
 * si no existe tira `Fragment$InstantiationException`. La red elegida sale por
 * `setFragmentResult` con [RESULT_KEY]; quien escucha registra el listener en
 * `onViewCreated`, nunca dentro del `setOnClickListener`.
 */
class WifiScannerDialog : DialogFragment(), WifiScanner.Listener {

    companion object {
        /** Clave del resultado. Quien escucha lee el Bundle con [wifiNetworkFromResult]. */
        const val RESULT_KEY = "wifi_scanner_result"

        private const val RESULT_SSID = "ssid"
        private const val RESULT_BSSID = "bssid"
        private const val RESULT_RSSI = "rssi"
        private const val RESULT_FREQUENCY = "frequency"

        /**
         * No recibe nada todavía, pero se crea igual por acá: así los nueve call sites de
         * diálogos se leen iguales y, si algún día necesita un argumento, hay un solo lugar
         * donde ponerlo.
         */
        fun newInstance() = WifiScannerDialog()

        /**
         * Rearma la [WifiNetwork] que viajó en el Bundle de [RESULT_KEY].
         *
         * Se lee junto con [resultBundle], que es su par: `WifiNetwork` no es `Parcelable`,
         * así que sus campos viajan sueltos. El resto (banda, canal, distancia estimada) son
         * propiedades calculadas y salen solas.
         */
        fun wifiNetworkFromResult(bundle: Bundle) = WifiNetwork(
            ssid      = bundle.getString(RESULT_SSID) ?: "",
            bssid     = bundle.getString(RESULT_BSSID) ?: "",
            rssi      = bundle.getInt(RESULT_RSSI),
            frequency = bundle.getInt(RESULT_FREQUENCY)
        )

        private fun resultBundle(network: WifiNetwork) = Bundle().apply {
            putString(RESULT_SSID, network.ssid)
            putString(RESULT_BSSID, network.bssid)
            putInt(RESULT_RSSI, network.rssi)
            putInt(RESULT_FREQUENCY, network.frequency)
        }
    }

    private lateinit var scanner: WifiScanner
    private lateinit var adapter: WifiNetworkAdapter
    private var progressBar: ProgressBar? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = LayoutInflater.from(requireContext())
        val view     = inflater.inflate(R.layout.dialog_wifi_scanner, null)

        progressBar = view.findViewById(R.id.wifiProgress)
        val rv      = view.findViewById<RecyclerView>(R.id.rvWifiNetworks)

        adapter = WifiNetworkAdapter { network ->
            scanner.stopScanning()
            setFragmentResult(RESULT_KEY, resultBundle(network))
            dismiss()
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // W01: verificar permiso ANTES de iniciar el scanner — sin permiso, startScan()
        // lanza SecurityException en Android 6+. Si no tiene permiso, mostrar mensaje y salir.
        val hasLocation = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) {
            Toast.makeText(requireContext(),
                getString(R.string.wifi_no_permission),
                Toast.LENGTH_LONG).show()
            // No iniciar el scanner sin permiso — devolver diálogo vacío
            return MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create()
        }

        scanner = WifiScanner(requireContext()).also { it.listener = this }
        scanner.startScanning()

        return MaterialAlertDialogBuilder(requireContext())
            .setView(view)
            .setOnDismissListener { scanner.stopScanning() }
            .create()
    }

    override fun onNetworksFound(networks: List<WifiNetwork>) {
        progressBar?.visibility = View.GONE
        adapter.update(networks)
    }

    override fun onRssiUpdate(bssid: String, rssi: Int) {
        adapter.updateRssi(bssid, rssi)
    }
}

// ── Adapter ──────────────────────────────────────────────────────────────────

class WifiNetworkAdapter(
    private val onClick: (WifiNetwork) -> Unit
) : RecyclerView.Adapter<WifiNetworkAdapter.VH>() {

    private val items = mutableListOf<WifiNetwork>()

    fun update(networks: List<WifiNetwork>) {
        items.clear()
        items.addAll(networks)
        notifyDataSetChanged()
    }

    fun updateRssi(bssid: String, rssi: Int) {
        val idx = items.indexOfFirst { it.bssid == bssid }
        if (idx >= 0) {
            items[idx] = items[idx].copy(rssi = rssi)
            notifyItemChanged(idx)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_wifi_network, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) =
        holder.bind(items[position])

    override fun getItemCount() = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val tvSsid    = view.findViewById<TextView>(R.id.tvWifiSsid)
        private val tvDetails = view.findViewById<TextView>(R.id.tvWifiDetails)
        private val tvRssi    = view.findViewById<TextView>(R.id.tvWifiRssi)
        private val pbSignal  = view.findViewById<ProgressBar>(R.id.pbWifiSignal)

        fun bind(net: WifiNetwork) {
            tvSsid.text    = net.ssid
            tvDetails.text = "${net.band}  Ch ${net.channel}  ${net.signalLabel}"
            tvRssi.text    = "${net.rssi} dBm"
            pbSignal.progress = net.signalPercent

            // Color de la barra según señal
            val color = when {
                net.rssi >= -60 -> 0xFF00FF88.toInt()
                net.rssi >= -70 -> 0xFFFFB300.toInt()
                else            -> 0xFFFF3D6E.toInt()
            }
            pbSignal.progressTintList =
                android.content.res.ColorStateList.valueOf(color)

            itemView.setOnClickListener { onClick(net) }
        }
    }
}
