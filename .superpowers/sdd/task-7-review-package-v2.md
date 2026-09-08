# Paquete de revision - Task 7 con fix (f93446e..7d74d6b)

## Commits
```
7d74d6b fix: guardar los callbacks del anuncio contra fragments desasociados
a1938f3 feat: entrar directo a la funcion despues de ganar la recompensa
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
diff --git a/app/src/main/java/com/example/ar/UpgradeDialog.kt b/app/src/main/java/com/example/ar/UpgradeDialog.kt
index 34bbd91..e2f3680 100644
--- a/app/src/main/java/com/example/ar/UpgradeDialog.kt
+++ b/app/src/main/java/com/example/ar/UpgradeDialog.kt
@@ -52,44 +52,53 @@ class UpgradeDialog : DialogFragment() {
         val feat = feature
         val btnAd = view.findViewById<MaterialButton>(R.id.btnWatchAd)
 
         // Si no hay anuncio listo, se intenta cargar uno para la próxima vez que
         // se abra el diálogo. Sin esto, si la precarga inicial falló (sin red al
         // arrancar, por ejemplo), la opción no volvería a aparecer nunca.
         if (ads != null && !ads.isAdReady()) ads.preload()
 
         // Solo se ofrece si HAY un anuncio cargado ahora. Nunca se muestra un botón
         // deshabilitado con "Cargando…": ese fue un bug real en el proyecto hermano
         // Oráculo, donde el usuario veía un botón muerto sin saber por qué.
         if (ads != null && feat != null && ads.isAdReady()) {
             btnAd.visibility = android.view.View.VISIBLE
             Analytics.rewardedOffered(requireContext(), source)
             btnAd.setOnClickListener {
-                Analytics.rewardedStarted(requireContext(), source)
+                // El applicationContext sobrevive al fragment: si este muere mientras
+                // corre el anuncio, la recompensa igual se otorga. Sería injusto que
+                // el usuario mire el anuncio completo y no reciba el desbloqueo.
+                val appCtx = requireContext().applicationContext
+                Analytics.rewardedStarted(appCtx, source)
                 ads.show(
                     onEarned = {
-                        AccessManager.grantTemporary(requireContext(), feat)
-                        Analytics.rewardedEarned(requireContext(), source)
-                        onRewardEarned?.invoke()
-                        dismiss()
+                        AccessManager.grantTemporary(appCtx, feat)
+                        Analytics.rewardedEarned(appCtx, source)
+                        // La UI sí depende de que el fragment siga vivo.
+                        if (isAdded) {
+                            onRewardEarned?.invoke()
+                            dismiss()
+                        }
                     },
                     onFailed = {
-                        android.widget.Toast.makeText(
-                            requireContext(),
-                            getString(R.string.rewarded_failed),
-                            android.widget.Toast.LENGTH_SHORT
-                        ).show()
+                        if (isAdded) {
+                            android.widget.Toast.makeText(
+                                requireContext(),
+                                getString(R.string.rewarded_failed),
+                                android.widget.Toast.LENGTH_SHORT
+                            ).show()
+                        }
                     }
                 )
             }
         }
 
         // Mostrar el precio real de Google Play (localizado, refleja promos)
         applyPrice(billingManager?.formattedProPrice)
         billingManager?.setPriceListener { price ->
             if (isAdded) applyPrice(price)
         }
 
         view.findViewById<TextView>(R.id.btnRestore).setOnClickListener {
             billingManager?.restorePurchases()
             dismiss()
         }
diff --git a/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt b/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
index ae214ba..e21fc00 100644
--- a/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
+++ b/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
@@ -52,32 +52,40 @@ class SatellitePickerDialog(
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
+                        if (isAdded) {
+                            onSelected(satellite)
+                            dismiss()
+                        }
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
index 6a920ec..da497e7 100644
--- a/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
+++ b/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
@@ -116,69 +116,90 @@ class CompassFragment : Fragment(), OrientationManager.Listener {
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
+                    onUnlocked = { if (isAdded) showThemePicker() }
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
+                                if (isAdded) {
+                                    WifiScannerDialog { network ->
+                                        sharedVm.trackedWifi.value = network
+                                    }.show(parentFragmentManager, "wifi_scanner")
+                                }
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
+                                if (isAdded) {
+                                    TdtPickerDialog(currentLocation, userCountryCode) { transmitter ->
+                                        sharedVm.selectedTdt.value = transmitter
+                                    }.show(parentFragmentManager, "tdt_picker")
+                                }
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
