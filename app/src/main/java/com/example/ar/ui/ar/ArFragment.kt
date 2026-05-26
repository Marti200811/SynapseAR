package com.example.ar.ui.ar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.ar.ProximityBeeper
import com.example.ar.R
import com.example.ar.SharedViewModel
import com.example.ar.databinding.FragmentArBinding
import com.example.ar.satellite.SatelliteCalculator
import com.example.ar.satellite.SatelliteDatabase
import com.example.ar.sensors.OrientationManager
import com.example.ar.ui.ar.ArOverlayView.SatelliteDot
import com.example.ar.util.GeoUtils
import com.google.android.gms.location.*
import kotlin.math.abs

class ArFragment : Fragment(), OrientationManager.Listener {

    private var _binding: FragmentArBinding? = null
    private val binding get() = _binding!!
    private val sharedVm: SharedViewModel by activityViewModels()

    private lateinit var orientationManager: OrientationManager
    private lateinit var fusedLocation: FusedLocationProviderClient
    private var currentLocation: Location? = null

    private val beeper = ProximityBeeper()
    private var lastAzimuth = 0f
    private var lastTargetAzimuth = Double.NaN
    private var lastTargetElevation = Double.NaN
    private var wasAligned = false   // para detectar el cambio false→true y vibrar

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            currentLocation = result.lastLocation
            updateReadouts()
            updateSatelliteBelt()
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms[Manifest.permission.CAMERA] == true) startCamera()
        if (perms[Manifest.permission.ACCESS_FINE_LOCATION] == true) startLocation()
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentArBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        orientationManager = OrientationManager(requireContext()).also { it.listener = this }
        fusedLocation = LocationServices.getFusedLocationProviderClient(requireContext())

        sharedVm.target.observe(viewLifecycleOwner) { updateReadouts() }
        sharedVm.selectedSatellite.observe(viewLifecycleOwner) {
            updateReadouts()
            updateSatelliteBelt()   // refrescar el dot seleccionado en el cinturón
        }

        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        orientationManager.start()
        beeper.start()
    }

    override fun onPause() {
        super.onPause()
        orientationManager.stop()
        fusedLocation.removeLocationUpdates(locationCallback)
        beeper.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // ── Permisos ──────────────────────────────────────────────────────────────

    private fun checkAndRequestPermissions() {
        val camOk  = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val locOk  = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val toRequest = mutableListOf<String>()
        if (!camOk) toRequest.add(Manifest.permission.CAMERA)
        if (!locOk) toRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)

        if (toRequest.isNotEmpty()) {
            permLauncher.launch(toRequest.toTypedArray())
        } else {
            startCamera()
            startLocation()
        }
    }

    // ── Cámara ────────────────────────────────────────────────────────────────

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.cameraPreview.surfaceProvider)
            }
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    // ── Localización ──────────────────────────────────────────────────────────

    private fun startLocation() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) return
        val req = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).build()
        fusedLocation.requestLocationUpdates(req, locationCallback, Looper.getMainLooper())
    }

    // ── Sensores → Overlay ────────────────────────────────────────────────────

    override fun onOrientation(azimuth: Float, pitch: Float, roll: Float, mode: OrientationManager.Mode) {
        lastAzimuth = azimuth
        binding.arOverlay.azimuth        = azimuth
        binding.arOverlay.pitch          = pitch
        binding.arOverlay.roll           = roll
        binding.arOverlay.isVerticalMode = (mode == OrientationManager.Mode.VERTICAL)
        updateAligned()
        updateBeeper()
    }

    override fun onCalibrationChanged(accuracy: Int) {
        binding.arOverlay.calibrationLevel = accuracy
        // Mostrar diálogo de calibración si la precisión es baja o nula
        if (accuracy <= android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
            showCalibrationHint()
        }
    }

    private fun showCalibrationHint() {
        if (!isAdded || parentFragmentManager.findFragmentByTag("calib") != null) return
        com.example.ar.CalibrationDialog().show(parentFragmentManager, "calib")
    }

    // ── Actualizar lecturas ───────────────────────────────────────────────────

    private fun updateReadouts() {
        val loc       = currentLocation
        val satellite = sharedVm.selectedSatellite.value
        val target    = sharedVm.target.value

        when {
            satellite != null && loc != null -> {
                val angles = SatelliteCalculator.calculate(loc.latitude, loc.longitude, satellite.orbitalLon)
                val distStr = if (angles.visible) getString(R.string.sat_visible) else getString(R.string.sat_below_horizon)
                binding.tvArTarget.text    = "📡 ${satellite.name}  ${satellite.positionLabel}"
                binding.tvArBearing.text   = "%03.0f°".format(angles.azimuthDeg)
                binding.tvArElevation.text = "%.1f°".format(angles.elevationDeg)
                binding.tvArDistance.text  = distStr
                binding.arOverlay.targetAzimuth    = angles.azimuthDeg.toFloat()
                binding.arOverlay.targetElevation  = angles.elevationDeg.toFloat()
                binding.arOverlay.targetLabel      = satellite.name
                binding.arOverlay.targetDistanceStr = distStr
                lastTargetAzimuth   = angles.azimuthDeg
                lastTargetElevation = angles.elevationDeg
            }

            target != null && loc != null -> {
                val bearing  = GeoUtils.bearingDegrees(loc.latitude, loc.longitude, target.lat, target.lon)
                val distance = GeoUtils.distanceMeters(loc.latitude, loc.longitude, target.lat, target.lon)
                val distStr  = GeoUtils.formatDistance(distance, requireContext())
                binding.tvArTarget.text    = "🎯 ${target.name.take(25)}"
                binding.tvArBearing.text   = "%03.0f°".format(bearing)
                binding.tvArElevation.text = getString(R.string.no_angle)
                binding.tvArDistance.text  = distStr
                binding.arOverlay.targetAzimuth    = bearing.toFloat()
                binding.arOverlay.targetElevation  = null
                binding.arOverlay.targetLabel      = target.name.take(20)
                binding.arOverlay.targetDistanceStr = distStr
                lastTargetAzimuth   = bearing
                lastTargetElevation = Double.NaN
            }

            else -> {
                binding.tvArTarget.text    = getString(R.string.ar_no_target)
                binding.tvArBearing.text   = getString(R.string.no_angle)
                binding.tvArElevation.text = getString(R.string.no_angle)
                binding.tvArDistance.text  = getString(R.string.no_value)
                binding.arOverlay.targetAzimuth   = null
                binding.arOverlay.targetElevation = null
                lastTargetAzimuth   = Double.NaN
                lastTargetElevation = Double.NaN
                beeper.updateAngularError(Double.MAX_VALUE)
            }
        }
        updateAligned()
    }

    /** Detecta si azimut Y elevación están alineados (< 3° de error) */
    private fun updateAligned() {
        if (lastTargetAzimuth.isNaN()) {
            binding.arOverlay.isAligned = false
            sharedVm.isAligned.postValue(false)
            wasAligned = false
            return
        }
        var azDiff = abs(lastAzimuth.toDouble() - lastTargetAzimuth)
        if (azDiff > 180.0) azDiff = 360.0 - azDiff

        val elDiff = if (!lastTargetElevation.isNaN())
            abs(binding.arOverlay.pitch.toDouble() - lastTargetElevation)
        else 0.0

        val aligned = azDiff < 3.0 && elDiff < 3.0
        binding.arOverlay.isAligned = aligned
        sharedVm.isAligned.postValue(aligned)

        // Vibrar solo en el momento que pasa de no-alineado → alineado
        if (aligned && !wasAligned) vibrateOnLock()
        wasAligned = aligned
    }

    /**
     * Calcula la posición de todos los satélites desde la ubicación actual
     * y los pasa al overlay para dibujarlos como puntos en el cielo.
     * Solo se llama cuando cambia la ubicación GPS (no en cada frame de sensor).
     */
    private fun updateSatelliteBelt() {
        val loc = currentLocation ?: return
        val selectedSat = sharedVm.selectedSatellite.value

        val dots = SatelliteDatabase.satellites.mapNotNull { sat ->
            val angles = SatelliteCalculator.calculate(loc.latitude, loc.longitude, sat.orbitalLon)
            if (angles.elevationDeg < 0.0) return@mapNotNull null   // bajo el horizonte
            SatelliteDot(
                azimuth    = angles.azimuthDeg.toFloat(),
                elevation  = angles.elevationDeg.toFloat(),
                name       = sat.name,
                isSelected = sat.name == selectedSat?.name
            )
        }
        binding.arOverlay.satelliteBelt = dots
    }

    /** Patrón de vibración haptic al lograr el lock */
    private fun vibrateOnLock() {
        val ctx = context ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 180, 60, 80), -1)
            )
        } else {
            @Suppress("DEPRECATION")
            val v = ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 180, 60, 80), -1))
            } else {
                @Suppress("DEPRECATION")
                v?.vibrate(longArrayOf(0, 80, 60, 180, 60, 80), -1)
            }
        }
    }

    private fun updateBeeper() {
        if (lastTargetAzimuth.isNaN()) {
            beeper.updateAngularError(Double.MAX_VALUE)
            return
        }
        var diff = abs(lastAzimuth - lastTargetAzimuth)
        if (diff > 180.0) diff = 360.0 - diff
        beeper.updateAngularError(diff)
    }
}
