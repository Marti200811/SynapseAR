package com.example.ar.ui.map

import android.Manifest
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.os.Handler
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ar.R
import com.example.ar.SettingsDialog
import com.example.ar.SharedViewModel
import com.example.ar.TargetPoint
import com.example.ar.util.GeoUtils
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import java.util.Locale

class MapFragment : Fragment(), OnMapReadyCallback {

    private val sharedViewModel: SharedViewModel by activityViewModels()
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var googleMap: GoogleMap? = null

    private lateinit var tvBearing: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvTarget: TextView
    private lateinit var etSearch: EditText
    private lateinit var btnSearch: ImageButton

    private val locationHandler = Handler(Looper.getMainLooper())
    private val locationRunnable = object : Runnable {
        override fun run() {
            refreshDistance()
            locationHandler.postDelayed(this, 2000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

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
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else false
        }

        sharedViewModel.target.observe(viewLifecycleOwner) { target ->
            updateInfoPanel(target)
        }
    }

    override fun onResume() {
        super.onResume()
        locationHandler.post(locationRunnable)
    }

    override fun onPause() {
        super.onPause()
        locationHandler.removeCallbacks(locationRunnable)
    }

    private fun refreshDistance() {
        val target = sharedViewModel.target.value ?: return
        if (ContextCompat.checkSelfPermission(
                requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                val distance = GeoUtils.distanceMeters(it.latitude, it.longitude, target.lat, target.lon)
                val bearing  = GeoUtils.bearingDegrees(it.latitude, it.longitude, target.lat, target.lon)
                tvBearing.text  = "${bearing.toInt()}°"
                tvDistance.text = GeoUtils.formatDistance(distance, requireContext())

            }
        }
    }

    private fun performSearch() {
        val query = etSearch.text.toString().trim()
        if (query.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.search_empty), Toast.LENGTH_SHORT).show()
            return
        }

        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)

        // Geocoder en IO thread → evita ANR en el hilo principal
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    Geocoder(requireContext(), Locale.getDefault())
                        .getFromLocationName(query, 1)
                }
            }

            result.fold(
                onSuccess = { addresses ->
                    if (!addresses.isNullOrEmpty()) {
                        val addr   = addresses[0]
                        val latLng = LatLng(addr.latitude, addr.longitude)
                        val name   = addr.getAddressLine(0) ?: query

                        sharedViewModel.target.value =
                            TargetPoint(addr.latitude, addr.longitude, name)

                        googleMap?.let { map ->
                            map.clear()
                            map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                            map.addMarker(
                                MarkerOptions()
                                    .position(latLng)
                                    .title(name)
                                    .icon(BitmapDescriptorFactory.defaultMarker(
                                        BitmapDescriptorFactory.HUE_CYAN))
                            )
                        }
                        etSearch.setText("")
                    } else {
                        Toast.makeText(requireContext(),
                            getString(R.string.search_not_found, query),
                            Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = {
                    Toast.makeText(requireContext(),
                        getString(R.string.search_error),
                        Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

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
                    val myPos = LatLng(it.latitude, it.longitude)
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(myPos, 15f))
                }
            }
        }

        map.setOnMapClickListener { latLng ->
            val target = TargetPoint(latLng.latitude, latLng.longitude)
            sharedViewModel.target.value = target
            map.clear()
            map.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title("Objetivo")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )
        }

        sharedViewModel.target.value?.let { target ->
            val pos = LatLng(target.lat, target.lon)
            map.addMarker(
                MarkerOptions()
                    .position(pos)
                    .title(target.name)
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_CYAN))
            )
        }
    }

    private fun updateInfoPanel(target: TargetPoint?) {
        if (target == null) {
            tvTarget.text   = getString(R.string.map_tap_hint)
            tvBearing.text  = getString(R.string.no_angle)
            tvDistance.text = getString(R.string.no_value)
            return
        }
        tvTarget.text = target.name.take(20)
    }
}
