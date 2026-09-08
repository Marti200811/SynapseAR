# Paquete de revision - Task 4 con fix (ac71c2a..975393a)

## Commits
```
975393a docs: actualizar el comentario del gate AR tras migrar a AccessManager
e8b9822 refactor: los gates preguntan por AccessManager en vez de ProManager
```

## Diff completo
```diff
diff --git a/app/src/main/java/com/example/ar/MainActivity.kt b/app/src/main/java/com/example/ar/MainActivity.kt
index e6fbb4a..124af6e 100644
--- a/app/src/main/java/com/example/ar/MainActivity.kt
+++ b/app/src/main/java/com/example/ar/MainActivity.kt
@@ -1,27 +1,29 @@
 package com.example.ar
 
 import android.content.Intent
 import android.os.Bundle
 import android.view.View
 import androidx.activity.viewModels
 import androidx.appcompat.app.AppCompatActivity
 import androidx.core.view.ViewCompat
 import androidx.core.view.WindowCompat
 import androidx.core.view.WindowInsetsCompat
 import androidx.navigation.fragment.NavHostFragment
 import androidx.navigation.ui.setupWithNavController
+import com.example.ar.access.AccessManager
+import com.example.ar.access.Feature
 import com.example.ar.databinding.ActivityMainBinding
 import com.google.android.gms.ads.AdRequest
 import com.google.android.gms.ads.MobileAds
 import com.google.android.gms.ads.RequestConfiguration
 import com.google.android.material.snackbar.Snackbar
 import com.google.android.ump.ConsentInformation
 import com.google.android.ump.ConsentRequestParameters
 import com.google.android.ump.UserMessagingPlatform
 
 class MainActivity : AppCompatActivity() {
 
     private lateinit var binding: ActivityMainBinding
     private val sharedVm: SharedViewModel by viewModels()
     lateinit var billingManager: BillingManager
 
@@ -57,36 +59,36 @@ class MainActivity : AppCompatActivity() {
         val navHost = supportFragmentManager
             .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
         val navController = navHost.navController
 
         binding.bottomNav.setupWithNavController(navController)
         // Material3 ignora itemIconTint="@null" del XML — hay que forzarlo en código
         // y recargar cada ícono para que use su propio color
         binding.bottomNav.itemIconTintList = null
         binding.bottomNav.menu.findItem(R.id.compassFragment)?.icon =
             androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_compass)
         binding.bottomNav.menu.findItem(R.id.mapFragment)?.icon =
             androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_map)
         binding.bottomNav.menu.findItem(R.id.arFragment)?.icon =
             androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_ar)
 
-        // Bloquear tab AR si no es Pro.
-        // Usa sharedVm.isPro como única fuente de verdad: así respeta
-        // también el toggle DEBUG_FORCE_FREE en builds de desarrollo.
+        // Bloquear tab AR si no tiene acceso.
+        // AccessManager resuelve "es Pro O tiene un desbloqueo temporal vigente",
+        // y ProManager sigue respetando el toggle DEBUG_FORCE_FREE en builds de desarrollo.
         navController.addOnDestinationChangedListener { _, destination, _ ->
             if (destination.id == R.id.arFragment
-                && sharedVm.isPro.value != true) {
+                && !AccessManager.canUse(this, Feature.AR)) {
                 navController.popBackStack()
                 showUpgradeDialog(Analytics.SRC_AR_TAB)
             }
         }
 
         // Billing: en release actualiza el estado Pro desde Google Play.
         // En debug, ProManager.isPro() ya maneja DEBUG_FORCE_FREE → no pisamos.
         billingManager = BillingManager(this) { isPro ->
             if (!BuildConfig.DEBUG) {
                 // Si billing devuelve false, la verificación local (TESTING_MODE) tiene prioridad
                 if (isPro) sharedVm.isPro.postValue(true)
                 else sharedVm.isPro.postValue(ProManager.isPro(this))
             }
         }
         billingManager.connect()
diff --git a/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt b/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
index 604470f..ae214ba 100644
--- a/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
+++ b/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
@@ -4,71 +4,75 @@ import android.app.Dialog
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
 import androidx.fragment.app.DialogFragment
 import androidx.recyclerview.widget.LinearLayoutManager
 import androidx.recyclerview.widget.RecyclerView
 import com.example.ar.Analytics
 import com.example.ar.MainActivity
-import com.example.ar.ProManager
 import com.example.ar.R
+import com.example.ar.access.AccessManager
+import com.example.ar.access.Feature
 import com.google.android.material.dialog.MaterialAlertDialogBuilder
 
 /**
  * @param location  Ubicación actual del usuario. Si se provee, los satélites
  *                  se ordenan por visibilidad (elevación > 0 primero) y se
  *                  muestra la elevación en cada ítem.
  */
 class SatellitePickerDialog(
     private val location: Location? = null,
     private val onSelected: (Satellite) -> Unit
 ) : DialogFragment() {
 
     override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
         val inflater = LayoutInflater.from(requireContext())
         val view = inflater.inflate(R.layout.dialog_satellite_picker, null)
 
         val etSearch = view.findViewById<EditText>(R.id.etSatSearch)
         val rv       = view.findViewById<RecyclerView>(R.id.rvSatellites)
 
-        val isPro = ProManager.isPro(requireContext())
+        // Vale tanto para el candado visual como para el gate de selección.
+        // Se llama una vez acá a propósito: si se consultara por fila, el estado
+        // podría cambiar a mitad del scroll.
+        val hasAccess = AccessManager.canUse(requireContext(), Feature.SATELLITE)
 
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
 
-        val adapter = SatelliteAdapter(sorted, isPro, elevationMap) { satellite ->
+        val adapter = SatelliteAdapter(sorted, hasAccess, elevationMap) { satellite ->
             val isFree = SatelliteDatabase.freeSatelliteNames.contains(satellite.name)
-            if (isPro || isFree) {
+            if (hasAccess || isFree) {
                 onSelected(satellite)
                 dismiss()
             } else {
                 (requireActivity() as MainActivity)
                     .showUpgradeDialog(Analytics.SRC_SATELLITE_LOCKED)
             }
         }
 
         rv.layoutManager = LinearLayoutManager(requireContext())
         rv.adapter = adapter
 
         etSearch.addTextChangedListener(object : TextWatcher {
             override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
             override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                 adapter.filter(s?.toString() ?: "")
diff --git a/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt b/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
index 5d7e57b..6a920ec 100644
--- a/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
+++ b/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
@@ -19,32 +19,33 @@ import android.view.ViewGroup
 import kotlin.math.abs
 import androidx.activity.result.contract.ActivityResultContracts
 import androidx.core.content.ContextCompat
 import androidx.fragment.app.Fragment
 import androidx.fragment.app.activityViewModels
 import androidx.lifecycle.lifecycleScope
 import com.google.android.gms.location.*
 import kotlinx.coroutines.Dispatchers
 import kotlinx.coroutines.launch
 import kotlinx.coroutines.withContext
 import java.util.Locale
 import com.example.ar.Analytics
 import com.example.ar.CalibrationDialog
 import com.example.ar.CompassTheme
 import com.example.ar.MainActivity
-import com.example.ar.ProManager
 import com.example.ar.ProximityBeeper
+import com.example.ar.access.AccessManager
+import com.example.ar.access.Feature
 import com.example.ar.R
 import com.example.ar.SettingsDialog
 import com.example.ar.SharedViewModel
 import com.example.ar.TargetPoint
 import com.example.ar.ThemeManager
 import com.example.ar.antenna.AntennaPickerDialog
 import com.example.ar.antenna.AntennaType
 import com.example.ar.tdt.TdtPickerDialog
 import com.example.ar.wifi.WifiNetwork
 import com.example.ar.wifi.WifiScannerDialog
 import com.example.ar.databinding.FragmentCompassBinding
 import com.example.ar.satellite.SatelliteCalculator
 import com.example.ar.satellite.SatellitePickerDialog
 import com.example.ar.sensors.OrientationManager
 import com.example.ar.util.GeoUtils
@@ -105,72 +106,72 @@ class CompassFragment : Fragment(), OrientationManager.Listener {
         orientation = OrientationManager(requireContext()).also { it.listener = this }
         fusedLocation = LocationServices.getFusedLocationProviderClient(requireContext())
 
         // Botón configuración ⚙
         binding.btnSettings.setOnClickListener {
             val dialog = SettingsDialog()
             dialog.onUnitsChanged = { updateReadouts() }
             dialog.show(parentFragmentManager, "settings")
         }
 
         // Toque en la brújula → calibración (si calibración baja) o selector de tema (Pro)
         binding.compass.setOnClickListener {
             val acc = orientation.calibrationAccuracy
             if (acc <= android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                 CalibrationDialog().show(parentFragmentManager, "calib")
-            } else if (ProManager.isPro(requireContext())) {
+            } else if (AccessManager.canUse(requireContext(), Feature.COMPASS_THEMES)) {
                 showThemePicker()
             }
         }
 
         // Long press en brújula → selector de tema (Pro)
         binding.compass.setOnLongClickListener {
-            if (ProManager.isPro(requireContext())) {
+            if (AccessManager.canUse(requireContext(), Feature.COMPASS_THEMES)) {
                 showThemePicker()
             } else {
                 (requireActivity() as MainActivity)
                     .showUpgradeDialog(Analytics.SRC_COMPASS_THEME)
             }
             true
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
-                    if (ProManager.isPro(requireContext())) {
+                    if (AccessManager.canUse(requireContext(), Feature.WIFI_SCANNER)) {
                         WifiScannerDialog { network ->
                             sharedVm.trackedWifi.value = network
                         }.show(parentFragmentManager, "wifi_scanner")
                     } else {
                         (requireActivity() as MainActivity)
                             .showUpgradeDialog(Analytics.SRC_WIFI_SCANNER)
                     }
                 }
                 AntennaType.TDT -> {
-                    if (ProManager.isPro(requireContext())) {
+                    if (AccessManager.canUse(requireContext(), Feature.TDT_PICKER)) {
                         // Pasar código de país para filtrar por país del usuario
                         TdtPickerDialog(currentLocation, userCountryCode) { transmitter ->
                             sharedVm.selectedTdt.value = transmitter
                         }.show(parentFragmentManager, "tdt_picker")
                     } else {
                         (requireActivity() as MainActivity)
                             .showUpgradeDialog(Analytics.SRC_TDT_PICKER)
                     }
                 }
                 else -> {
                     // Pasar ubicación actual para filtrar por visibilidad desde el hemisferio del usuario
                     SatellitePickerDialog(currentLocation) { satellite ->
                         sharedVm.selectedSatellite.value = satellite
                         sharedVm.antennaType.value = AntennaType.SATELLITE
                     }.show(parentFragmentManager, "sat_picker")
```
