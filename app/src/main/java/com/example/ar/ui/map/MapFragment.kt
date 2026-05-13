package com.example.ar.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Geocoder
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.ar.R
import com.example.ar.SettingsDialog
import com.example.ar.SharedViewModel
import com.example.ar.TargetPoint
import com.example.ar.util.GeoUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.Dash
import com.google.android.gms.maps.model.Gap
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polygon
import com.google.android.gms.maps.model.PolygonOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class MapFragment : Fragment(), OnMapReadyCallback {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

    private lateinit var tvBearing: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvTarget: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: ImageButton

    // Estado de ubicación y apuntamiento
    private var userLatLng: LatLng? = null
    private var aimingLine: Polyline? = null
    private var aimingCone: Polygon? = null
    private var targetMarker: com.google.android.gms.maps.model.Marker? = null

    // Actualización continua de ubicación
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { onNewLocation(it) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_map, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvBearing  = view.findViewById(R.id.mapTxtBearing)
        tvDistance = view.findViewById(R.id.mapTxtDistance)
        tvTarget   = view.findViewById(R.id.mapTxtTarget)
        etSearch   = view.findViewById(R.id.etSearch)
        btnSearch  = view.findViewById(R.id.btnSearch)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        val mapFragment = childFragmentManager
            .findFragmentById(R.id.mapContainer) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also {
                childFragmentManager.beginTransaction()
                    .replace(R.id.mapContainer, it)
                    .commit()
            }
        mapFragment.getMapAsync(this)

        view.findViewById<TextView>(R.id.btnMapSettings).setOnClickListener {
            SettingsDialog().show(parentFragmentManager, "settings")
        }

        btnSearch.setOnClickListener { performSearch() }
        etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { performSearch(); true } else false
        }

        sharedViewModel.target.observe(viewLifecycleOwner) { target ->
            updateInfoPanel(target)
            // Redibujar indicador cuando cambia el objetivo
            userLatLng?.let { user -> target?.let { t -> drawAimingIndicator(user, t) } }
                ?: run { clearAimingOverlays() }
        }
    }

    override fun onResume() {
        super.onResume()
        startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    // ── Ubicación continua ───────────────────────────────────────────────────

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L)
            .setMinUpdateIntervalMillis(1000L)
            .build()

        fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }

    private fun onNewLocation(location: Location) {
        userLatLng = LatLng(location.latitude, location.longitude)
        val target = sharedViewModel.target.value ?: run {
            tvBearing.text  = getString(R.string.no_angle)
            tvDistance.text = getString(R.string.no_value)
            return
        }

        val distance = GeoUtils.distanceMeters(
            location.latitude, location.longitude, target.lat, target.lon
        )
        val bearing = GeoUtils.bearingDegrees(
            location.latitude, location.longitude, target.lat, target.lon
        )
        tvBearing.text  = "${bearing.toInt()}°"
        tvDistance.text = GeoUtils.formatDistance(distance, requireContext())

        drawAimingIndicator(userLatLng!!, target)
    }

    // ── Indicador de apuntamiento ────────────────────────────────────────────

    /**
     * Dibuja en el mapa:
     *  - Línea punteada cyan desde posición del usuario hasta el objetivo
     *  - Cono semitransparente (±15°) que representa el haz de apuntamiento
     */
    private fun drawAimingIndicator(userPos: LatLng, target: TargetPoint) {
        val map = googleMap ?: return
        val targetPos = LatLng(target.lat, target.lon)

        // Calcular azimut y distancia total
        val bearing = GeoUtils.bearingDegrees(
            userPos.latitude, userPos.longitude, target.lat, target.lon
        )
        val totalDist = GeoUtils.distanceMeters(
            userPos.latitude, userPos.longitude, target.lat, target.lon
        )

        // ── 1. Línea punteada de apuntamiento ──────────────────────────────
        aimingLine?.remove()
        aimingLine = map.addPolyline(
            PolylineOptions()
                .add(userPos, targetPos)
                .color(Color.argb(220, 0, 229, 255))   // cyan fuerte
                .width(4f)
                .pattern(listOf(Dash(20f), Gap(12f)))   // punteada
                .geodesic(true)
                .zIndex(2f)
        )

        // ── 2. Cono de haz (semitransparente) ──────────────────────────────
        // Longitud del cono: 40% de la distancia total, mínimo 200 m, máximo 5 km
        val coneLen = min(totalDist * 0.40, 5_000.0).coerceAtLeast(200.0)
        val halfAngle = 15.0   // grados de apertura de cada lado
        val arcSteps  = 16     // puntos del arco para curva suave

        val conePoints = mutableListOf<LatLng>()
        conePoints.add(userPos)  // vértice

        // Arco desde (bearing - halfAngle) hasta (bearing + halfAngle)
        for (i in 0..arcSteps) {
            val angle = (bearing - halfAngle) + (2 * halfAngle * i / arcSteps)
            conePoints.add(destinationPoint(userPos, angle, coneLen))
        }
        conePoints.add(userPos)  // cerrar el polígono

        aimingCone?.remove()
        aimingCone = map.addPolygon(
            PolygonOptions()
                .addAll(conePoints)
                .fillColor(Color.argb(55, 0, 229, 255))    // cyan muy transparente
                .strokeColor(Color.argb(160, 0, 229, 255)) // borde cyan
                .strokeWidth(2f)
                .geodesic(true)
                .zIndex(1f)
        )
    }

    private fun clearAimingOverlays() {
        aimingLine?.remove()
        aimingCone?.remove()
        aimingLine = null
        aimingCone = null
    }

    /**
     * Calcula el punto destino desde [origin] en dirección [bearingDeg] a [distanceM] metros.
     * Usa la fórmula de rumbo geodésico (Haversine inverso).
     */
    private fun destinationPoint(origin: LatLng, bearingDeg: Double, distanceM: Double): LatLng {
        val R   = 6_371_000.0
        val d   = distanceM / R
        val brg = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(origin.latitude)
        val lon1 = Math.toRadians(origin.longitude)

        val lat2 = Math.asin(
            sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brg)
        )
        val lon2 = lon1 + Math.atan2(
            sin(brg) * sin(d) * cos(lat1),
            cos(d) - sin(lat1) * sin(lat2)
        )
        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    // ── Búsqueda ─────────────────────────────────────────────────────────────

    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.search_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)

        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(requireContext(), Locale.getDefault()).getFromLocationName(query, 1)
                }
            }

            result.fold(
                onSuccess = { addresses ->
                    if (!addresses.isNullOrEmpty()) {
                        val addr   = addresses[0]
                        val latLng = LatLng(addr.latitude, addr.longitude)
                        val name   = addr.getAddressLine(0) ?: query

                        sharedViewModel.target.value = TargetPoint(addr.latitude, addr.longitude, name)

                        googleMap?.let { map ->
                            targetMarker?.remove()
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 13f))
                            targetMarker = map.addMarker(
                                MarkerOptions()
                                    .position(latLng)
                                    .title(name)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
                            )
                        }
                        etSearch.setText("")
                    } else {
                        Toast.makeText(requireContext(),
                            getString(R.string.search_not_found, query), Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = {
                    Toast.makeText(requireContext(),
                        getString(R.string.search_error), Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    // ── GoogleMap callback ───────────────────────────────────────────────────

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.mapType = GoogleMap.MAP_TYPE_HYBRID

        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            map.isMyLocationEnabled = true
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    userLatLng = LatLng(it.latitude, it.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng!!, 15f))
                    // Si ya hay un objetivo guardado, dibujar indicador de inmediato
                    sharedViewModel.target.value?.let { t -> drawAimingIndicator(userLatLng!!, t) }
                }
            }
        }

        map.setOnMapClickListener { latLng ->
            val target = TargetPoint(latLng.latitude, latLng.longitude)
            sharedViewModel.target.value = target

            targetMarker?.remove()
            targetMarker = map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(getString(R.string.map_target_label))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )
        }

        // Restaurar marcador si ya había objetivo
        sharedViewModel.target.value?.let { target ->
            val pos = LatLng(target.lat, target.lon)
            targetMarker?.remove()
            targetMarker = map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title(target.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )
        }
    }

    // ── Panel de info ────────────────────────────────────────────────────────

    private fun updateInfoPanel(target: TargetPoint?) {
        if (target == null) {
            tvTarget.text   = getString(R.string.map_tap_hint)
            tvBearing.text  = getString(R.string.no_angle)
            tvDistance.text = getString(R.string.no_value)
            return
        }
        tvTarget.text = target.name.take(22)
    }
}
