package com.example.ar.ui.compass

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.location.*
import com.example.ar.MainActivity
import com.example.ar.ProManager
import com.example.ar.ProximityBeeper
import com.example.ar.R
import com.example.ar.SettingsDialog
import com.example.ar.SharedViewModel
import com.example.ar.antenna.AntennaPickerDialog
import com.example.ar.antenna.AntennaType
import com.example.ar.tdt.TdtPickerDialog
import com.example.ar.wifi.WifiScannerDialog
import com.example.ar.databinding.FragmentCompassBinding
import com.example.ar.satellite.SatelliteCalculator
import com.example.ar.satellite.SatellitePickerDialog
import com.example.ar.sensors.OrientationManager
import com.example.ar.util.GeoUtils

class CompassFragment : Fragment(), OrientationManager.Listener {

    private var _binding: FragmentCompassBinding? = null
    private val binding get() = _binding!!
    private val sharedVm: SharedViewModel by activityViewModels()

    private lateinit var orientation: OrientationManager
    private lateinit var fusedLocation: FusedLocationProviderClient
    private var currentLocation: Location? = null

    private val beeper = ProximityBeeper()
    private var lastAzimuth = 0f
    private var lastTargetBearing = Double.NaN

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            currentLocation = result.lastLocation
            updateReadouts()
        }
    }

    private val locPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { startLocationUpdates() }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, s: Bundle?
    ): View {
        _binding = FragmentCompassBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        orientation = OrientationManager(requireContext()).also { it.listener = this }
        fusedLocation = LocationServices.getFusedLocationProviderClient(requireContext())

        // Botón configuración ⚙
        binding.btnSettings.setOnClickListener {
            val dialog = SettingsDialog()
            dialog.onUnitsChanged = { updateReadouts() }
            dialog.show(parentFragmentManager, "settings")
        }

        // Botón tipo de antena
        binding.btnSelectAntenna.setOnClickListener {
            val current = sharedVm.antennaType.value ?: AntennaType.SATELLITE
            AntennaPickerDialog(current) { type ->
                sharedVm.antennaType.value = type
                // Si no es satelital, limpiar satélite seleccionado
                if (!type.needsElevation) sharedVm.selectedSatellite.value = null
            }.show(parentFragmentManager, "antenna_picker")
        }

        // Botón selector de satélite
        binding.btnSelectSatellite.setOnClickListener {
            val type = sharedVm.antennaType.value ?: AntennaType.SATELLITE
            when (sharedVm.antennaType.value) {
                AntennaType.WIFI_DIRECTIONAL -> {
                    if (ProManager.isPro(requireContext())) {
                        WifiScannerDialog { network ->
                            sharedVm.trackedWifi.value = network
                        }.show(parentFragmentManager, "wifi_scanner")
                    } else {
                        (requireActivity() as MainActivity).showUpgradeDialog()
                    }
                }
                AntennaType.TDT -> {
                    if (ProManager.isPro(requireContext())) {
                        TdtPickerDialog(currentLocation) { transmitter ->
                            sharedVm.selectedTdt.value = transmitter
                        }.show(parentFragmentManager, "tdt_picker")
                    } else {
                        (requireActivity() as MainActivity).showUpgradeDialog()
                    }
                }
                else -> {
                    SatellitePickerDialog { satellite ->
                        sharedVm.selectedSatellite.value = satellite
                        sharedVm.antennaType.value = AntennaType.SATELLITE
                    }.show(parentFragmentManager, "sat_picker")
                }
            }
        }

        // Observar tipo de antena
        sharedVm.antennaType.observe(viewLifecycleOwner) { type ->
            binding.btnSelectAntenna.text = "${type.emoji} ${type.label.take(12)}"
            when (type) {
                AntennaType.SATELLITE -> {
                    binding.btnSelectSatellite.visibility = android.view.View.VISIBLE
                    binding.btnSelectSatellite.text = getString(R.string.select_satellite)
                }
                AntennaType.WIFI_DIRECTIONAL -> {
                    binding.btnSelectSatellite.visibility = android.view.View.VISIBLE
                    binding.btnSelectSatellite.text = getString(R.string.btn_scan_wifi)
                }
                AntennaType.TDT -> {
                    binding.btnSelectSatellite.visibility = android.view.View.VISIBLE
                    binding.btnSelectSatellite.text = getString(R.string.btn_select_tower)
                }
                else -> {
                    binding.btnSelectSatellite.visibility = android.view.View.GONE
                }
            }
            updateReadouts()
        }

        // Observar red WiFi rastreada
        sharedVm.trackedWifi.observe(viewLifecycleOwner) { wifi ->
            if (wifi != null) {
                binding.tvSelectedSat.text = "📶 ${wifi.ssid}  ${wifi.rssi} dBm"
            }
            updateReadouts()
        }

        // Actualizar RSSI en tiempo real en el panel
        sharedVm.liveWifiRssi.observe(viewLifecycleOwner) { rssi ->
            val wifi = sharedVm.trackedWifi.value ?: return@observe
            if (rssi != null) {
                binding.tvSelectedSat.text = "📶 ${wifi.ssid}  $rssi dBm"
                binding.txtDistance.text = "$rssi dBm"
            }
        }

        // Observar satélite seleccionado
        sharedVm.selectedSatellite.observe(viewLifecycleOwner) { sat ->
            binding.tvSelectedSat.text = if (sat != null) "📡 ${sat.name}  ${sat.positionLabel}" else ""
            updateReadouts()
        }

        // Observar transmisor TDT
        sharedVm.selectedTdt.observe(viewLifecycleOwner) { updateReadouts() }

        // Observar objetivo de mapa
        sharedVm.target.observe(viewLifecycleOwner) { updateReadouts() }
    }

    override fun onResume() {
        super.onResume()
        orientation.start()
        ensureLocation()
        beeper.start()
    }

    override fun onPause() {
        super.onPause()
        orientation.stop()
        fusedLocation.removeLocationUpdates(locationCallback)
        beeper.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun ensureLocation() {
        val fine = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (fine) startLocationUpdates()
        else locPermissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ))
    }

    private fun startLocationUpdates() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1500L).build()
        fusedLocation.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    private fun updateReadouts() {
        val loc       = currentLocation ?: return
        val satellite = sharedVm.selectedSatellite.value
        val tdt       = sharedVm.selectedTdt.value
        val target    = sharedVm.target.value

        when {
            // ── Modo satélite ─────────────────────────────────────────
            satellite != null -> {
                val angles = SatelliteCalculator.calculate(
                    loc.latitude, loc.longitude, satellite.orbitalLon
                )
                binding.txtBearing.text   = "%03.0f°".format(angles.azimuthDeg)
                binding.txtElevation.text = "%.1f°".format(angles.elevationDeg)
                binding.txtDistance.text  = if (angles.visible) getString(R.string.sat_visible) else getString(R.string.sat_below_horizon)
                binding.compass.targetBearing   = angles.azimuthDeg.toFloat()
                binding.compass.targetElevation = angles.elevationDeg.toFloat()
                binding.compass.distanceMeters  = null
                lastTargetBearing = angles.azimuthDeg
            }

            // ── Modo TDT ──────────────────────────────────────────────
            tdt != null -> {
                val bearing  = GeoUtils.bearingDegrees(loc.latitude, loc.longitude, tdt.lat, tdt.lon)
                val distance = GeoUtils.distanceMeters(loc.latitude, loc.longitude, tdt.lat, tdt.lon)
                binding.txtBearing.text   = "%03.0f°".format(bearing)
                binding.txtElevation.text = "--°"
                binding.txtDistance.text  = GeoUtils.formatDistance(distance, requireContext())
                binding.tvSelectedSat.text = "📺 ${tdt.name}  ${tdt.standard}"
                binding.compass.targetBearing   = bearing.toFloat()
                binding.compass.targetElevation = null
                binding.compass.distanceMeters  = distance
                lastTargetBearing = bearing
            }

            // ── Modo objetivo de mapa ─────────────────────────────────
            target != null -> {
                val bearing  = GeoUtils.bearingDegrees(loc.latitude, loc.longitude, target.lat, target.lon)
                val distance = GeoUtils.distanceMeters(loc.latitude, loc.longitude, target.lat, target.lon)
                binding.txtBearing.text   = "%03.0f°".format(bearing)
                binding.txtElevation.text = "--°"
                binding.txtDistance.text  = GeoUtils.formatDistance(distance, requireContext())
                binding.compass.targetBearing  = bearing.toFloat()
                binding.compass.distanceMeters = distance
                lastTargetBearing = bearing
            }

            // ── Sin objetivo ──────────────────────────────────────────
            else -> {
                binding.txtBearing.text   = getString(R.string.no_angle)
                binding.txtElevation.text = getString(R.string.no_angle)
                binding.txtDistance.text  = getString(R.string.no_value)
                binding.compass.targetBearing   = null
                binding.compass.targetElevation = null
                binding.compass.distanceMeters  = null
                lastTargetBearing = Double.NaN
                beeper.updateAngularError(Double.MAX_VALUE)
            }
        }
        updateBeeper()
    }

    override fun onOrientation(
        azimuth: Float, pitch: Float, roll: Float, mode: OrientationManager.Mode
    ) {
        binding.compass.azimuth = azimuth
        binding.compass.pitch   = pitch
        binding.compass.mode    =
            if (mode == OrientationManager.Mode.VERTICAL) CompassView.Mode.VERTICAL
            else CompassView.Mode.HORIZONTAL
        binding.modeBadge.text =
            if (mode == OrientationManager.Mode.VERTICAL) "VERTICAL" else "HORIZONTAL"
        lastAzimuth = azimuth
        updateBeeper()
    }

    private fun updateBeeper() {
        if (lastTargetBearing.isNaN()) {
            beeper.updateAngularError(Double.MAX_VALUE)
            return
        }
        var diff = Math.abs(lastAzimuth - lastTargetBearing)
        if (diff > 180.0) diff = 360.0 - diff
        // Escalar según precisión del tipo de antena
        val precision = sharedVm.antennaType.value?.precisionDeg ?: 3f
        val scaled = diff * (3f / precision)   // normalizar a escala del beeper
        beeper.updateAngularError(scaled)
    }
}
