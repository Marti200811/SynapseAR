package com.example.ar.wifi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper

/**
 * Gestiona el escaneo de redes WiFi y el monitoreo de señal en tiempo real.
 */
class WifiScanner(private val context: Context) {

    interface Listener {
        fun onNetworksFound(networks: List<WifiNetwork>)
        fun onRssiUpdate(bssid: String, rssi: Int)
    }

    var listener: Listener? = null

    private val wifiManager =
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    private val handler = Handler(Looper.getMainLooper())
    private var trackedBssid: String? = null
    private var isMonitoring = false

    // ── BroadcastReceiver para resultados de escaneo ──────────────────────────

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent?.action != WifiManager.SCAN_RESULTS_AVAILABLE_ACTION) return
            val results = wifiManager.scanResults
            val networks = results
                .filter { it.SSID.isNotBlank() }
                .map { WifiNetwork(it.SSID, it.BSSID, it.level, it.frequency) }
                .sortedByDescending { it.rssi }
                .distinctBy { it.bssid }
            listener?.onNetworksFound(networks)

            // Actualizar RSSI de la red rastreada
            trackedBssid?.let { bssid ->
                val tracked = results.firstOrNull { it.BSSID == bssid }
                if (tracked != null) listener?.onRssiUpdate(bssid, tracked.level)
            }
        }
    }

    // ── Runnable para escaneo periódico ───────────────────────────────────────

    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!isMonitoring) return
            // W11: verificar que el WiFi esté habilitado antes de escanear
            if (!wifiManager.isWifiEnabled) {
                listener?.onNetworksFound(emptyList())
                handler.postDelayed(this, 2500)
                return
            }
            @Suppress("DEPRECATION")
            wifiManager.startScan()
            handler.postDelayed(this, 2500)
        }
    }

    // ── API pública ───────────────────────────────────────────────────────────

    /** Inicia el escaneo continuo */
    fun startScanning() {
        isMonitoring = true
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(scanReceiver, filter)
        }
        handler.post(scanRunnable)
    }

    /** Detiene el escaneo */
    fun stopScanning() {
        isMonitoring = false
        handler.removeCallbacks(scanRunnable)
        try { context.unregisterReceiver(scanReceiver) } catch (_: Exception) {}
    }

    /** Define qué red rastrear para el RSSI en tiempo real */
    fun trackNetwork(bssid: String?) {
        trackedBssid = bssid
    }

    /** Devuelve el RSSI actual de la red rastreada desde la caché */
    fun getCurrentRssi(bssid: String): Int? {
        return wifiManager.scanResults.firstOrNull { it.BSSID == bssid }?.level
    }
}
