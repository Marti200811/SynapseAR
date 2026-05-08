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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class WifiScannerDialog(
    private val onSelected: (WifiNetwork) -> Unit
) : DialogFragment(), WifiScanner.Listener {

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
            onSelected(network)
            dismiss()
        }

        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter

        // Verificar permiso de ubicación (requerido para escanear WiFi en Android 9+)
        val hasLocation = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) {
            Toast.makeText(requireContext(),
                getString(R.string.wifi_no_permission),
                Toast.LENGTH_LONG).show()
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
