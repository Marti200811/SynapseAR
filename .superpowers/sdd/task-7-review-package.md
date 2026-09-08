# Paquete de revision - Task 7 (f93446e..a1938f3)

## Commits
```
a1938f3 feat: entrar directo a la funcion despues de ganar la recompensa
```

## Resumen de cambios
```
 app/src/main/java/com/example/ar/MainActivity.kt   |  6 ++++-
 .../example/ar/satellite/SatellitePickerDialog.kt  | 10 ++++++--
 .../com/example/ar/ui/compass/CompassFragment.kt   | 29 +++++++++++++++++-----
 3 files changed, 36 insertions(+), 9 deletions(-)
```

## Diff completo
```diff
diff --git a/app/src/main/java/com/example/ar/MainActivity.kt b/app/src/main/java/com/example/ar/MainActivity.kt
index b373615..359657d 100644
--- a/app/src/main/java/com/example/ar/MainActivity.kt
+++ b/app/src/main/java/com/example/ar/MainActivity.kt
@@ -68,31 +68,35 @@ class MainActivity : AppCompatActivity() {
         binding.bottomNav.itemIconTintList = null
         binding.bottomNav.menu.findItem(R.id.compassFragment)?.icon =
             androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_compass)
         binding.bottomNav.menu.findItem(R.id.mapFragment)?.icon =
             androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_map)
         binding.bottomNav.menu.findItem(R.id.arFragment)?.icon =
             androidx.core.content.ContextCompat.getDrawable(this, R.drawable.ic_nav_ar)
 
         // Bloquear tab AR si no tiene acceso.
         // AccessManager resuelve "es Pro O tiene un desbloqueo temporal vigente",
         // y ProManager sigue respetando el toggle DEBUG_FORCE_FREE en builds de desarrollo.
         navController.addOnDestinationChangedListener { _, destination, _ ->
             if (destination.id == R.id.arFragment
                 && !AccessManager.canUse(this, Feature.AR)) {
                 navController.popBackStack()
-                showUpgradeDialog(Analytics.SRC_AR_TAB)
+                showUpgradeDialog(
+                    source = Analytics.SRC_AR_TAB,
+                    feature = Feature.AR,
+                    onUnlocked = { navController.navigate(R.id.arFragment) }
+                )
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
 
         // Inicializar isPro desde caché local (respeta DEBUG_FORCE_FREE en debug)
diff --git a/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt b/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
index ae214ba..62b5fc4 100644
--- a/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
+++ b/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
@@ -52,32 +52,38 @@ class SatellitePickerDialog(
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
                 onSelected(satellite)
                 dismiss()
             } else {
-                (requireActivity() as MainActivity)
-                    .showUpgradeDialog(Analytics.SRC_SATELLITE_LOCKED)
+                (requireActivity() as MainActivity).showUpgradeDialog(
+                    source = Analytics.SRC_SATELLITE_LOCKED,
+                    feature = Feature.SATELLITE,
+                    onUnlocked = {
+                        onSelected(satellite)
+                        dismiss()
+                    }
+                )
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
diff --git a/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt b/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
index 6a920ec..50a36b7 100644
--- a/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
+++ b/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
@@ -116,69 +116,86 @@ class CompassFragment : Fragment(), OrientationManager.Listener {
         // Toque en la brújula → calibración (si calibración baja) o selector de tema (Pro)
         binding.compass.setOnClickListener {
             val acc = orientation.calibrationAccuracy
             if (acc <= android.hardware.SensorManager.SENSOR_STATUS_ACCURACY_LOW) {
                 CalibrationDialog().show(parentFragmentManager, "calib")
             } else if (AccessManager.canUse(requireContext(), Feature.COMPASS_THEMES)) {
                 showThemePicker()
             }
         }
 
         // Long press en brújula → selector de tema (Pro)
         binding.compass.setOnLongClickListener {
             if (AccessManager.canUse(requireContext(), Feature.COMPASS_THEMES)) {
                 showThemePicker()
             } else {
-                (requireActivity() as MainActivity)
-                    .showUpgradeDialog(Analytics.SRC_COMPASS_THEME)
+                (requireActivity() as MainActivity).showUpgradeDialog(
+                    source = Analytics.SRC_COMPASS_THEME,
+                    feature = Feature.COMPASS_THEMES,
+                    onUnlocked = { showThemePicker() }
+                )
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
                     if (AccessManager.canUse(requireContext(), Feature.WIFI_SCANNER)) {
                         WifiScannerDialog { network ->
                             sharedVm.trackedWifi.value = network
                         }.show(parentFragmentManager, "wifi_scanner")
                     } else {
-                        (requireActivity() as MainActivity)
-                            .showUpgradeDialog(Analytics.SRC_WIFI_SCANNER)
+                        (requireActivity() as MainActivity).showUpgradeDialog(
+                            source = Analytics.SRC_WIFI_SCANNER,
+                            feature = Feature.WIFI_SCANNER,
+                            onUnlocked = {
+                                WifiScannerDialog { network ->
+                                    sharedVm.trackedWifi.value = network
+                                }.show(parentFragmentManager, "wifi_scanner")
+                            }
+                        )
                     }
                 }
                 AntennaType.TDT -> {
                     if (AccessManager.canUse(requireContext(), Feature.TDT_PICKER)) {
                         // Pasar código de país para filtrar por país del usuario
                         TdtPickerDialog(currentLocation, userCountryCode) { transmitter ->
                             sharedVm.selectedTdt.value = transmitter
                         }.show(parentFragmentManager, "tdt_picker")
                     } else {
-                        (requireActivity() as MainActivity)
-                            .showUpgradeDialog(Analytics.SRC_TDT_PICKER)
+                        (requireActivity() as MainActivity).showUpgradeDialog(
+                            source = Analytics.SRC_TDT_PICKER,
+                            feature = Feature.TDT_PICKER,
+                            onUnlocked = {
+                                TdtPickerDialog(currentLocation, userCountryCode) { transmitter ->
+                                    sharedVm.selectedTdt.value = transmitter
+                                }.show(parentFragmentManager, "tdt_picker")
+                            }
+                        )
                     }
                 }
                 else -> {
                     // Pasar ubicación actual para filtrar por visibilidad desde el hemisferio del usuario
                     SatellitePickerDialog(currentLocation) { satellite ->
                         sharedVm.selectedSatellite.value = satellite
                         sharedVm.antennaType.value = AntennaType.SATELLITE
                     }.show(parentFragmentManager, "sat_picker")
                 }
             }
         }
 
         // Observar tipo de antena
         sharedVm.antennaType.observe(viewLifecycleOwner) { type ->
             binding.btnSelectAntenna.text = "${type.emoji} ${type.label.take(12)}"
```
