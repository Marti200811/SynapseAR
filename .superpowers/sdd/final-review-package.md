# Paquete de revision FINAL de la rama feature/rewarded-ads (385d8b2..7d74d6b)

## Commits
```
7d74d6b fix: guardar los callbacks del anuncio contra fragments desasociados
a1938f3 feat: entrar directo a la funcion despues de ganar la recompensa
f93446e feat: ofrecer un anuncio recompensado en el dialogo de upgrade
e96f007 feat: agregar RewardedAdManager y las unidades de anuncio recompensado
975393a docs: actualizar el comentario del gate AR tras migrar a AccessManager
e8b9822 refactor: los gates preguntan por AccessManager en vez de ProManager
ac71c2a feat: agregar UnlockStore y la fachada AccessManager
8bb3f5f feat: agregar Feature y AccessRules, la logica pura de acceso
cd53324 chore: habilitar tests unitarios (faltaba la dependencia de JUnit)
```

## Resumen de cambios
```
 app/build.gradle.kts                               |  3 +
 app/src/debug/res/values/admob.xml                 |  5 ++
 app/src/main/java/com/example/ar/Analytics.kt      | 12 ++++
 app/src/main/java/com/example/ar/MainActivity.kt   | 33 +++++++--
 app/src/main/java/com/example/ar/UpgradeDialog.kt  | 56 +++++++++++++++
 .../java/com/example/ar/access/AccessManager.kt    | 38 +++++++++++
 .../main/java/com/example/ar/access/AccessRules.kt | 24 +++++++
 app/src/main/java/com/example/ar/access/Feature.kt | 16 +++++
 .../com/example/ar/access/RewardedAdManager.kt     | 79 ++++++++++++++++++++++
 .../main/java/com/example/ar/access/UnlockStore.kt | 27 ++++++++
 .../example/ar/satellite/SatellitePickerDialog.kt  | 24 +++++--
 .../com/example/ar/ui/compass/CompassFragment.kt   | 44 +++++++++---
 app/src/main/res/layout/dialog_upgrade.xml         | 11 +++
 app/src/main/res/values-en/strings.xml             |  2 +
 app/src/main/res/values-pt/strings.xml             |  2 +
 app/src/main/res/values/strings.xml                |  2 +
 app/src/release/res/values/admob.xml               |  4 ++
 .../java/com/example/ar/access/AccessRulesTest.kt  | 51 ++++++++++++++
 .../java/com/example/synapsear/ExampleUnitTest.kt  | 17 -----
 gradle/libs.versions.toml                          |  2 +
 20 files changed, 411 insertions(+), 41 deletions(-)
```

## Diff completo de la rama
```diff
diff --git a/app/build.gradle.kts b/app/build.gradle.kts
index adbece8..275c852 100644
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -105,13 +105,16 @@ dependencies {
     // Google Play Billing
     implementation(libs.billing)
 
     // Firebase Crashlytics + Remote Config
     implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
     implementation("com.google.firebase:firebase-crashlytics-ktx")
     implementation("com.google.firebase:firebase-analytics-ktx")
     implementation("com.google.firebase:firebase-config-ktx")
 
     // Google Play In-App Updates
     implementation("com.google.android.play:app-update:2.1.0")
     implementation("com.google.android.play:app-update-ktx:2.1.0")
+
+    // Tests unitarios locales (corren en la JVM, sin dispositivo)
+    testImplementation(libs.junit)
 }
\ No newline at end of file
diff --git a/app/src/debug/res/values/admob.xml b/app/src/debug/res/values/admob.xml
index d3f471b..b7bfeca 100644
--- a/app/src/debug/res/values/admob.xml
+++ b/app/src/debug/res/values/admob.xml
@@ -1,9 +1,14 @@
 <?xml version="1.0" encoding="utf-8"?>
 <!--
     IDs de AdMob para builds DEBUG.
     Usa los IDs de test oficiales de Google: garantizan anuncios de prueba
     en cualquier dispositivo/emulador sin registrar device hashes.
 -->
 <resources>
     <string name="banner_ad_unit_id" translatable="false">ca-app-pub-3940256099942544/6300978111</string>
+
+    <!-- ID de prueba oficial de Google para anuncios recompensados. NO reemplazar
+         por el real: Google suspende cuentas si el desarrollador clickea sus
+         propios anuncios de produccion. -->
+    <string name="rewarded_ad_unit_id" translatable="false">ca-app-pub-3940256099942544/5224354917</string>
 </resources>
diff --git a/app/src/main/java/com/example/ar/Analytics.kt b/app/src/main/java/com/example/ar/Analytics.kt
index 5c211b0..54799a2 100644
--- a/app/src/main/java/com/example/ar/Analytics.kt
+++ b/app/src/main/java/com/example/ar/Analytics.kt
@@ -32,13 +32,25 @@ object Analytics {
     private fun log(context: Context, event: String, source: String) {
         FirebaseAnalytics.getInstance(context.applicationContext)
             .logEvent(event, Bundle().apply { putString("source", source) })
     }
 
     /** El usuario llegó a la pantalla de venta. Denominador del embudo. */
     fun upgradeDialogShown(context: Context, source: String) =
         log(context, "upgrade_dialog_shown", source)
 
     /** El usuario apretó comprar. ESTA es la métrica de demanda real. */
     fun upgradeButtonTapped(context: Context, source: String) =
         log(context, "upgrade_button_tapped", source)
+
+    /** Al usuario se le ofreció la opción de mirar un anuncio (había uno cargado). */
+    fun rewardedOffered(context: Context, source: String) =
+        log(context, "rewarded_offered", source)
+
+    /** Tocó "Ver un anuncio". */
+    fun rewardedStarted(context: Context, source: String) =
+        log(context, "rewarded_started", source)
+
+    /** Completó el anuncio y ganó el desbloqueo. */
+    fun rewardedEarned(context: Context, source: String) =
+        log(context, "rewarded_earned", source)
 }
diff --git a/app/src/main/java/com/example/ar/MainActivity.kt b/app/src/main/java/com/example/ar/MainActivity.kt
index e6fbb4a..359657d 100644
--- a/app/src/main/java/com/example/ar/MainActivity.kt
+++ b/app/src/main/java/com/example/ar/MainActivity.kt
@@ -1,38 +1,42 @@
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
+import com.example.ar.access.RewardedAdManager
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
+    lateinit var rewardedAdManager: RewardedAdManager
 
     private lateinit var consentInformation: ConsentInformation
     private var isMobileAdsInitialized = false
 
     // ── In-App Updates ────────────────────────────────────────────────────
     private val updateManager by lazy { UpdateManager(this) }
 
     override fun onCreate(savedInstanceState: Bundle?) {
         super.onCreate(savedInstanceState)
         binding = ActivityMainBinding.inflate(layoutInflater)
         setContentView(binding.root)
 
@@ -60,32 +64,36 @@ class MainActivity : AppCompatActivity() {
 
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
@@ -96,24 +104,26 @@ class MainActivity : AppCompatActivity() {
 
         // Ocultar banner si es Pro
         sharedVm.isPro.observe(this) { isPro ->
             binding.adBanner.visibility = if (isPro) View.GONE else View.VISIBLE
         }
 
         // En debug con DEBUG_FORCE_FREE activo: mostrar snackbar indicador
         // para confirmar que el modo "usuario gratis" está funcionando.
         if (BuildConfig.DEBUG && !ProManager.isPro(this)) {
             Snackbar.make(binding.root, "🆓 FREE MODE activo (DEBUG_FORCE_FREE=true)", Snackbar.LENGTH_LONG).show()
         }
 
+        rewardedAdManager = RewardedAdManager(this)
+
         // ── Consentimiento UMP → inicializar AdMob ────────────────────────
         initAdsWithConsent()
     }
 
     // ── UMP: flujo de consentimiento GDPR (Europa) ────────────────────────
 
     private fun initAdsWithConsent() {
         consentInformation = UserMessagingPlatform.getConsentInformation(this)
 
         val params = ConsentRequestParameters.Builder()
             .setTagForUnderAgeOfConsent(false)
             .build()
@@ -155,40 +165,49 @@ class MainActivity : AppCompatActivity() {
                         AdRequest.DEVICE_ID_EMULATOR,
                         "2DBF6E008A7DA07D85064E66C5BBF4D5"  // Moto G15
                     ))
                     .build()
             )
         }
 
         MobileAds.initialize(this) {
             // M01: el callback puede llegar desde un background thread — postear a UI
             runOnUiThread {
                 if (sharedVm.isPro.value != true) {
                     binding.adBanner.loadAd(AdRequest.Builder().build())
+                    rewardedAdManager.preload()
                 }
             }
         }
     }
 
     // ── Upgrade dialog ────────────────────────────────────────────────────
 
     /**
      * @param source cuál de las funciones bloqueadas trajo al usuario acá.
-     *        Se registra en Analytics para saber qué función vende (ver [Analytics]).
+     * @param feature qué se desbloquea si mira un anuncio. Null = no se ofrece anuncio.
+     * @param onUnlocked qué ejecutar si gana la recompensa.
      */
-    fun showUpgradeDialog(source: String) {
+    fun showUpgradeDialog(
+        source: String,
+        feature: Feature? = null,
+        onUnlocked: (() -> Unit)? = null
+    ) {
         Analytics.upgradeDialogShown(this, source)
         val dialog = UpgradeDialog()
         dialog.billingManager = billingManager
         dialog.source = source
+        dialog.rewardedAdManager = rewardedAdManager
+        dialog.feature = feature
+        dialog.onRewardEarned = onUnlocked
         dialog.show(supportFragmentManager, "upgrade")
     }
 
     // ── Ciclo de vida AdMob + Updates ────────────────────────────────────
 
     override fun onPause()  { super.onPause();  binding.adBanner.pause() }
     override fun onResume() {
         super.onResume()
         binding.adBanner.resume()
         // Chequea updates cada vez que la app vuelve al frente
         updateManager.checkForUpdates()
     }
diff --git a/app/src/main/java/com/example/ar/UpgradeDialog.kt b/app/src/main/java/com/example/ar/UpgradeDialog.kt
index 28752ae..e2f3680 100644
--- a/app/src/main/java/com/example/ar/UpgradeDialog.kt
+++ b/app/src/main/java/com/example/ar/UpgradeDialog.kt
@@ -1,50 +1,106 @@
 package com.example.ar
 
 import android.app.Dialog
 import android.os.Bundle
 import android.text.Spannable
 import android.text.SpannableStringBuilder
 import android.text.style.RelativeSizeSpan
 import android.text.style.StrikethroughSpan
 import android.view.LayoutInflater
 import android.widget.TextView
 import androidx.fragment.app.DialogFragment
+import com.example.ar.access.AccessManager
+import com.example.ar.access.Feature
+import com.example.ar.access.RewardedAdManager
 import com.google.android.material.button.MaterialButton
 import com.google.android.material.dialog.MaterialAlertDialogBuilder
 
 class UpgradeDialog : DialogFragment() {
 
     /** La activity pasa su BillingManager para poder lanzar la compra */
     var billingManager: BillingManager? = null
 
     /** Función bloqueada que trajo al usuario acá — se registra en Analytics. */
     var source: String = "unknown"
 
+    /** Manager de anuncios recompensados. Si es null, no se ofrece la opción. */
+    var rewardedAdManager: RewardedAdManager? = null
+
+    /** Función que se desbloquea si el usuario gana la recompensa. */
+    var feature: Feature? = null
+
+    /** Se invoca tras ganar la recompensa, para ejecutar la acción que estaba bloqueada. */
+    var onRewardEarned: (() -> Unit)? = null
+
     private var btnBuy: MaterialButton? = null
 
     override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
         val view = LayoutInflater.from(requireContext())
             .inflate(R.layout.dialog_upgrade, null)
 
         btnBuy = view.findViewById<MaterialButton>(R.id.btnBuyPro).apply {
             setOnClickListener {
                 // Se registra ANTES de lanzar la compra: mide intención aunque
                 // el flujo de Google Play falle (ej. perfil de pagos incompleto).
                 Analytics.upgradeButtonTapped(requireContext(), source)
                 billingManager?.launchPurchase()
                 dismiss()
             }
         }
 
+        val ads = rewardedAdManager
+        val feat = feature
+        val btnAd = view.findViewById<MaterialButton>(R.id.btnWatchAd)
+
+        // Si no hay anuncio listo, se intenta cargar uno para la próxima vez que
+        // se abra el diálogo. Sin esto, si la precarga inicial falló (sin red al
+        // arrancar, por ejemplo), la opción no volvería a aparecer nunca.
+        if (ads != null && !ads.isAdReady()) ads.preload()
+
+        // Solo se ofrece si HAY un anuncio cargado ahora. Nunca se muestra un botón
+        // deshabilitado con "Cargando…": ese fue un bug real en el proyecto hermano
+        // Oráculo, donde el usuario veía un botón muerto sin saber por qué.
+        if (ads != null && feat != null && ads.isAdReady()) {
+            btnAd.visibility = android.view.View.VISIBLE
+            Analytics.rewardedOffered(requireContext(), source)
+            btnAd.setOnClickListener {
+                // El applicationContext sobrevive al fragment: si este muere mientras
+                // corre el anuncio, la recompensa igual se otorga. Sería injusto que
+                // el usuario mire el anuncio completo y no reciba el desbloqueo.
+                val appCtx = requireContext().applicationContext
+                Analytics.rewardedStarted(appCtx, source)
+                ads.show(
+                    onEarned = {
+                        AccessManager.grantTemporary(appCtx, feat)
+                        Analytics.rewardedEarned(appCtx, source)
+                        // La UI sí depende de que el fragment siga vivo.
+                        if (isAdded) {
+                            onRewardEarned?.invoke()
+                            dismiss()
+                        }
+                    },
+                    onFailed = {
+                        if (isAdded) {
+                            android.widget.Toast.makeText(
+                                requireContext(),
+                                getString(R.string.rewarded_failed),
+                                android.widget.Toast.LENGTH_SHORT
+                            ).show()
+                        }
+                    }
+                )
+            }
+        }
+
         // Mostrar el precio real de Google Play (localizado, refleja promos)
         applyPrice(billingManager?.formattedProPrice)
         billingManager?.setPriceListener { price ->
             if (isAdded) applyPrice(price)
         }
 
         view.findViewById<TextView>(R.id.btnRestore).setOnClickListener {
             billingManager?.restorePurchases()
             dismiss()
         }
 
         view.findViewById<TextView>(R.id.btnDismiss).setOnClickListener {
diff --git a/app/src/main/java/com/example/ar/access/AccessManager.kt b/app/src/main/java/com/example/ar/access/AccessManager.kt
new file mode 100644
index 0000000..8d6d00a
--- /dev/null
+++ b/app/src/main/java/com/example/ar/access/AccessManager.kt
@@ -0,0 +1,38 @@
+package com.example.ar.access
+
+import android.content.Context
+import com.example.ar.ProManager
+
+/**
+ * Única pregunta que deben hacer los call sites: ¿puede usar esta función?
+ *
+ * Existe para que la condición "es Pro O tiene desbloqueo vigente" viva en un
+ * solo lugar. Repetirla en cada pantalla es donde aparecen los agujeros: en el
+ * proyecto hermano Oráculo, una condición armada a mano en un call site permitía
+ * consultas ilimitadas gratis.
+ */
+object AccessManager {
+
+    fun canUse(context: Context, feature: Feature): Boolean =
+        AccessRules.canUse(
+            isPro = ProManager.isPro(context),
+            expiryMillis = UnlockStore.expiryMillis(context, feature),
+            nowMillis = System.currentTimeMillis()
+        )
+
+    /** Otorga acceso temporal a [feature]. Se llama al ganar la recompensa de un anuncio. */
+    fun grantTemporary(context: Context, feature: Feature) {
+        UnlockStore.setExpiryMillis(
+            context,
+            feature,
+            System.currentTimeMillis() + AccessRules.UNLOCK_DURATION_MS
+        )
+    }
+
+    /** Milisegundos que le quedan al desbloqueo temporal. 0 si no hay o ya venció. */
+    fun remainingMillis(context: Context, feature: Feature): Long =
+        AccessRules.remaining(
+            expiryMillis = UnlockStore.expiryMillis(context, feature),
+            nowMillis = System.currentTimeMillis()
+        )
+}
diff --git a/app/src/main/java/com/example/ar/access/AccessRules.kt b/app/src/main/java/com/example/ar/access/AccessRules.kt
new file mode 100644
index 0000000..9d66879
--- /dev/null
+++ b/app/src/main/java/com/example/ar/access/AccessRules.kt
@@ -0,0 +1,24 @@
+package com.example.ar.access
+
+/**
+ * Reglas de acceso, sin dependencias de Android para poder testearlas.
+ *
+ * Toda la decisión de "¿puede usar esto?" vive acá. La fachada AccessManager
+ * solo se encarga de conseguir los datos (estado Pro, vencimiento guardado, reloj).
+ */
+object AccessRules {
+
+    /** Cuánto dura el desbloqueo que otorga un anuncio recompensado. */
+    const val UNLOCK_DURATION_MS: Long = 30L * 60L * 1000L
+
+    /**
+     * Un usuario Pro siempre puede. Si no lo es, necesita un desbloqueo que
+     * todavía no haya vencido. Un vencimiento exactamente igual a "ahora" ya venció.
+     */
+    fun canUse(isPro: Boolean, expiryMillis: Long, nowMillis: Long): Boolean =
+        isPro || nowMillis < expiryMillis
+
+    /** Milisegundos que le quedan al desbloqueo. Nunca negativo. */
+    fun remaining(expiryMillis: Long, nowMillis: Long): Long =
+        (expiryMillis - nowMillis).coerceAtLeast(0L)
+}
diff --git a/app/src/main/java/com/example/ar/access/Feature.kt b/app/src/main/java/com/example/ar/access/Feature.kt
new file mode 100644
index 0000000..107e033
--- /dev/null
+++ b/app/src/main/java/com/example/ar/access/Feature.kt
@@ -0,0 +1,16 @@
+package com.example.ar.access
+
+/**
+ * Funciones que en la versión gratuita están bloqueadas y se pueden desbloquear
+ * temporalmente mirando un anuncio recompensado.
+ *
+ * OJO: SATELLITE es UNA función, no una por satélite. Un anuncio desbloquea
+ * todos los satélites de pago a la vez — ver el spec del 2026-09-08.
+ */
+enum class Feature {
+    AR,
+    COMPASS_THEMES,
+    WIFI_SCANNER,
+    TDT_PICKER,
+    SATELLITE
+}
diff --git a/app/src/main/java/com/example/ar/access/RewardedAdManager.kt b/app/src/main/java/com/example/ar/access/RewardedAdManager.kt
new file mode 100644
index 0000000..bd13f87
--- /dev/null
+++ b/app/src/main/java/com/example/ar/access/RewardedAdManager.kt
@@ -0,0 +1,79 @@
+package com.example.ar.access
+
+import android.app.Activity
+import com.example.ar.R
+import com.google.android.gms.ads.AdError
+import com.google.android.gms.ads.AdRequest
+import com.google.android.gms.ads.FullScreenContentCallback
+import com.google.android.gms.ads.LoadAdError
+import com.google.android.gms.ads.rewarded.RewardedAd
+import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
+
+/**
+ * Envuelve la carga y exhibición de un anuncio recompensado.
+ *
+ * Precarga uno solo por vez. No se puede llamar a [preload] antes de que
+ * MobileAds.initialize() haya corrido — en esta app eso pasa recién después del
+ * flujo de consentimiento UMP, así que el enganche está en
+ * MainActivity.initializeMobileAds().
+ */
+class RewardedAdManager(private val activity: Activity) {
+
+    private var rewardedAd: RewardedAd? = null
+    private var loading = false
+
+    /** true si hay un anuncio listo para mostrar AHORA. */
+    fun isAdReady(): Boolean = rewardedAd != null
+
+    fun preload() {
+        if (loading || rewardedAd != null) return
+        loading = true
+        RewardedAd.load(
+            activity,
+            activity.getString(R.string.rewarded_ad_unit_id),
+            AdRequest.Builder().build(),
+            object : RewardedAdLoadCallback() {
+                override fun onAdLoaded(ad: RewardedAd) {
+                    rewardedAd = ad
+                    loading = false
+                }
+                override fun onAdFailedToLoad(error: LoadAdError) {
+                    rewardedAd = null
+                    loading = false
+                }
+            }
+        )
+    }
+
+    /**
+     * Muestra el anuncio. [onEarned] se invoca SOLO si el usuario lo completó y
+     * ganó la recompensa; si lo cierra antes, se invoca [onFailed].
+     */
+    fun show(onEarned: () -> Unit, onFailed: () -> Unit) {
+        val ad = rewardedAd
+        if (ad == null) {
+            onFailed()
+            return
+        }
+
+        var earned = false
+
+        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
+            override fun onAdDismissedFullScreenContent() {
+                rewardedAd = null
+                preload()                       // dejar listo el siguiente
+                if (!earned) onFailed()
+            }
+            override fun onAdFailedToShowFullScreenContent(error: AdError) {
+                rewardedAd = null
+                preload()
+                onFailed()
+            }
+        }
+
+        ad.show(activity) {
+            earned = true
+            onEarned()
+        }
+    }
+}
diff --git a/app/src/main/java/com/example/ar/access/UnlockStore.kt b/app/src/main/java/com/example/ar/access/UnlockStore.kt
new file mode 100644
index 0000000..41da09b
--- /dev/null
+++ b/app/src/main/java/com/example/ar/access/UnlockStore.kt
@@ -0,0 +1,27 @@
+package com.example.ar.access
+
+import android.content.Context
+
+/**
+ * Guarda hasta cuándo está desbloqueada cada función.
+ *
+ * Usa el mismo archivo de preferencias que ProManager y SettingsManager.
+ * No hace falta limpiar los vencidos: un valor viejo simplemente falla la
+ * comparación en AccessRules.
+ */
+internal object UnlockStore {
+
+    private const val PREFS = "synapse_prefs"
+
+    private fun key(feature: Feature) = "unlock_until_${feature.name}"
+
+    /** Timestamp (epoch millis) hasta el que vale el desbloqueo. 0 = nunca se desbloqueó. */
+    fun expiryMillis(context: Context, feature: Feature): Long =
+        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
+            .getLong(key(feature), 0L)
+
+    fun setExpiryMillis(context: Context, feature: Feature, millis: Long) {
+        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
+            .edit().putLong(key(feature), millis).apply()
+    }
+}
diff --git a/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt b/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
index 604470f..e21fc00 100644
--- a/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
+++ b/app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt
@@ -7,70 +7,82 @@ import android.os.Bundle
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
diff --git a/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt b/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
index 5d7e57b..da497e7 100644
--- a/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
+++ b/app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt
@@ -22,26 +22,27 @@ import androidx.core.content.ContextCompat
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
@@ -108,73 +109,94 @@ class CompassFragment : Fragment(), OrientationManager.Listener {
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
-                    if (ProManager.isPro(requireContext())) {
+                    if (AccessManager.canUse(requireContext(), Feature.WIFI_SCANNER)) {
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
-                    if (ProManager.isPro(requireContext())) {
+                    if (AccessManager.canUse(requireContext(), Feature.TDT_PICKER)) {
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
 
diff --git a/app/src/main/res/layout/dialog_upgrade.xml b/app/src/main/res/layout/dialog_upgrade.xml
index 8ec44ee..e6799ba 100644
--- a/app/src/main/res/layout/dialog_upgrade.xml
+++ b/app/src/main/res/layout/dialog_upgrade.xml
@@ -113,24 +113,35 @@
             android:backgroundTint="@color/accent_cyan"/>
 
         <TextView
             android:layout_width="match_parent"
             android:layout_height="wrap_content"
             android:text="@string/upgrade_trial_note"
             android:textColor="@color/text_secondary"
             android:textSize="11sp"
             android:gravity="center"
             android:layout_marginTop="6dp"
             android:layout_marginBottom="2dp"/>
 
+        <Button
+            android:id="@+id/btnWatchAd"
+            android:layout_width="match_parent"
+            android:layout_height="48dp"
+            android:text="@string/upgrade_btn_watch_ad"
+            android:textSize="14sp"
+            android:textColor="@color/accent_cyan"
+            android:backgroundTint="@android:color/transparent"
+            android:layout_marginTop="8dp"
+            android:visibility="gone"/>
+
         <TextView
             android:id="@+id/btnRestore"
             android:layout_width="match_parent"
             android:layout_height="wrap_content"
             android:text="@string/upgrade_btn_restore"
             android:textColor="@color/text_secondary"
             android:textSize="13sp"
             android:gravity="center"
             android:padding="10dp"
             android:layout_marginTop="4dp"/>
 
         <TextView
diff --git a/app/src/main/res/values-en/strings.xml b/app/src/main/res/values-en/strings.xml
index 9db867e..3c4dbe3 100644
--- a/app/src/main/res/values-en/strings.xml
+++ b/app/src/main/res/values-en/strings.xml
@@ -63,24 +63,26 @@
     <string name="upgrade_title">Synapse AR Pro</string>
     <string name="upgrade_subtitle">Unlock all professional features</string>
     <string name="upgrade_feature_satellites">📡  Full satellite database (~35)</string>
     <string name="upgrade_feature_wifi">📶  Real-time WiFi scanner</string>
     <string name="upgrade_feature_tdt">📺  Worldwide TDT database</string>
     <string name="upgrade_feature_ar">🎯  AR screen with visual guide</string>
     <string name="upgrade_feature_noads">🚫  No ads</string>
     <string name="upgrade_btn_buy">✨ Unlock Pro</string>
     <string name="upgrade_btn_buy_price">✨ Unlock Pro — %1$s</string>
     <string name="upgrade_btn_restore">Restore previous purchase</string>
     <string name="upgrade_btn_later">Maybe later</string>
     <string name="upgrade_trial_note">One-time payment. No subscription or recurring charges.</string>
+    <string name="upgrade_btn_watch_ad">▶ Watch an ad and use it free for 30 min</string>
+    <string name="rewarded_failed">Couldn\'t load the ad. Try again in a moment.</string>
     <string name="pro_purchase_unavailable">Purchase is not available right now. Check your connection and try again later.</string>
 
     <!-- Settings -->
     <string name="settings_units_label">Unit system</string>
     <string name="units_auto">Automatic (based on phone language)</string>
     <string name="units_metric">Metric  —  km / m</string>
     <string name="units_imperial">Imperial  —  mi / ft</string>
 
     <!-- Compass themes (Pro) -->
     <string name="settings_theme_label">Compass theme</string>
     <string name="theme_pro_badge">PRO</string>
     <string name="theme_cyber">⚡ Cyber  —  Futuristic cyan</string>
diff --git a/app/src/main/res/values-pt/strings.xml b/app/src/main/res/values-pt/strings.xml
index f617f34..70d1df8 100644
--- a/app/src/main/res/values-pt/strings.xml
+++ b/app/src/main/res/values-pt/strings.xml
@@ -63,24 +63,26 @@
     <string name="upgrade_title">Synapse AR Pro</string>
     <string name="upgrade_subtitle">Desbloqueie todos os recursos profissionais</string>
     <string name="upgrade_feature_satellites">📡  Base completa de satélites (~35)</string>
     <string name="upgrade_feature_wifi">📶  Scanner WiFi em tempo real</string>
     <string name="upgrade_feature_tdt">📺  Base de dados TDT mundial</string>
     <string name="upgrade_feature_ar">🎯  Tela AR com guia visual</string>
     <string name="upgrade_feature_noads">🚫  Sem publicidade</string>
     <string name="upgrade_btn_buy">✨ Desbloquear Pro</string>
     <string name="upgrade_btn_buy_price">✨ Desbloquear Pro — %1$s</string>
     <string name="upgrade_btn_restore">Restaurar compra anterior</string>
     <string name="upgrade_btn_later">Talvez depois</string>
     <string name="upgrade_trial_note">Pagamento único. Sem assinatura nem cobranças recorrentes.</string>
+    <string name="upgrade_btn_watch_ad">▶ Ver um anúncio e usar 30 min grátis</string>
+    <string name="rewarded_failed">Não foi possível carregar o anúncio. Tente novamente em instantes.</string>
     <string name="pro_purchase_unavailable">A compra não está disponível no momento. Verifique sua conexão e tente novamente mais tarde.</string>
 
     <!-- Settings -->
     <string name="settings_units_label">Sistema de unidades</string>
     <string name="units_auto">Automático (baseado no idioma do telefone)</string>
     <string name="units_metric">Métrico  —  km / m</string>
     <string name="units_imperial">Imperial  —  mi / ft</string>
 
     <!-- Temas da bússola (Pro) -->
     <string name="settings_theme_label">Tema da bússola</string>
     <string name="theme_pro_badge">PRO</string>
     <string name="theme_cyber">⚡ Cyber  —  Ciano futurista</string>
diff --git a/app/src/main/res/values/strings.xml b/app/src/main/res/values/strings.xml
index 8b80935..8e5ac16 100644
--- a/app/src/main/res/values/strings.xml
+++ b/app/src/main/res/values/strings.xml
@@ -63,24 +63,26 @@
     <string name="upgrade_title">Synapse AR Pro</string>
     <string name="upgrade_subtitle">Desbloqueá todas las funciones profesionales</string>
     <string name="upgrade_feature_satellites">📡  Base completa de satélites (~35)</string>
     <string name="upgrade_feature_wifi">📶  Escáner WiFi en tiempo real</string>
     <string name="upgrade_feature_tdt">📺  Base de datos TDT mundial</string>
     <string name="upgrade_feature_ar">🎯  Pantalla AR con guía visual</string>
     <string name="upgrade_feature_noads">🚫  Sin publicidad</string>
     <string name="upgrade_btn_buy">✨ Desbloquear Pro</string>
     <string name="upgrade_btn_buy_price">✨ Desbloquear Pro — %1$s</string>
     <string name="upgrade_btn_restore">Restaurar compra anterior</string>
     <string name="upgrade_btn_later">Tal vez después</string>
     <string name="upgrade_trial_note">Pago único. Sin suscripción ni cargos recurrentes.</string>
+    <string name="upgrade_btn_watch_ad">▶ Ver un anuncio y usar 30 min gratis</string>
+    <string name="rewarded_failed">No se pudo cargar el anuncio. Probá de nuevo en un momento.</string>
     <string name="pro_purchase_unavailable">La compra no está disponible en este momento. Verificá tu conexión e intentá de nuevo más tarde.</string>
 
     <!-- Settings -->
     <string name="settings_units_label">Sistema de unidades</string>
     <string name="units_auto">Automático (según idioma del teléfono)</string>
     <string name="units_metric">Métrico  —  km / m</string>
     <string name="units_imperial">Imperial  —  mi / ft</string>
 
     <!-- Temas brújula (Pro) -->
     <string name="settings_theme_label">Tema de brújula</string>
     <string name="theme_pro_badge">PRO</string>
     <string name="theme_cyber">⚡ Cyber  —  Cyan futurista</string>
diff --git a/app/src/release/res/values/admob.xml b/app/src/release/res/values/admob.xml
index 997578c..136d240 100644
--- a/app/src/release/res/values/admob.xml
+++ b/app/src/release/res/values/admob.xml
@@ -1,9 +1,13 @@
 <?xml version="1.0" encoding="utf-8"?>
 <!--
     IDs de AdMob para builds RELEASE (producción).
 -->
 <resources>
     <!-- "Banner principal", creado el 2026-09-01 en la app ~5110678257.
          El anterior (/7302944941) pertenecía a la app duplicada ~1056694271. -->
     <string name="banner_ad_unit_id" translatable="false">ca-app-pub-5417760954645863/2320588681</string>
+
+    <!-- Unidad "Recompensado desbloqueo" de la app ~5110678257 (la verificada),
+         creada el 2026-09-08. -->
+    <string name="rewarded_ad_unit_id" translatable="false">ca-app-pub-5417760954645863/1848125012</string>
 </resources>
diff --git a/app/src/test/java/com/example/ar/access/AccessRulesTest.kt b/app/src/test/java/com/example/ar/access/AccessRulesTest.kt
new file mode 100644
index 0000000..d90ea91
--- /dev/null
+++ b/app/src/test/java/com/example/ar/access/AccessRulesTest.kt
@@ -0,0 +1,51 @@
+package com.example.ar.access
+
+import org.junit.Assert.assertEquals
+import org.junit.Assert.assertFalse
+import org.junit.Assert.assertTrue
+import org.junit.Test
+
+class AccessRulesTest {
+
+    private val ahora = 1_000_000L
+
+    @Test
+    fun `un usuario Pro puede usar aunque no tenga desbloqueo`() {
+        assertTrue(AccessRules.canUse(isPro = true, expiryMillis = 0L, nowMillis = ahora))
+    }
+
+    @Test
+    fun `un desbloqueo vigente permite el acceso`() {
+        assertTrue(AccessRules.canUse(isPro = false, expiryMillis = ahora + 1, nowMillis = ahora))
+    }
+
+    @Test
+    fun `un desbloqueo vencido deniega el acceso`() {
+        assertFalse(AccessRules.canUse(isPro = false, expiryMillis = ahora - 1, nowMillis = ahora))
+    }
+
+    @Test
+    fun `un desbloqueo que vence justo ahora deniega el acceso`() {
+        assertFalse(AccessRules.canUse(isPro = false, expiryMillis = ahora, nowMillis = ahora))
+    }
+
+    @Test
+    fun `sin desbloqueo y sin Pro deniega el acceso`() {
+        assertFalse(AccessRules.canUse(isPro = false, expiryMillis = 0L, nowMillis = ahora))
+    }
+
+    @Test
+    fun `remaining devuelve el tiempo que falta`() {
+        assertEquals(5_000L, AccessRules.remaining(expiryMillis = ahora + 5_000L, nowMillis = ahora))
+    }
+
+    @Test
+    fun `remaining nunca es negativo`() {
+        assertEquals(0L, AccessRules.remaining(expiryMillis = ahora - 5_000L, nowMillis = ahora))
+    }
+
+    @Test
+    fun `la duracion del desbloqueo es de 30 minutos`() {
+        assertEquals(30L * 60L * 1000L, AccessRules.UNLOCK_DURATION_MS)
+    }
+}
diff --git a/app/src/test/java/com/example/synapsear/ExampleUnitTest.kt b/app/src/test/java/com/example/synapsear/ExampleUnitTest.kt
deleted file mode 100644
index d7a7a4d..0000000
--- a/app/src/test/java/com/example/synapsear/ExampleUnitTest.kt
+++ /dev/null
@@ -1,17 +0,0 @@
-package com.example.synapsear
-
-import org.junit.Test
-
-import org.junit.Assert.*
-
-/**
- * Example local unit test, which will execute on the development machine (host).
- *
- * See [testing documentation](http://d.android.com/tools/testing).
- */
-class ExampleUnitTest {
-    @Test
-    fun addition_isCorrect() {
-        assertEquals(4, 2 + 2)
-    }
-}
\ No newline at end of file
diff --git a/gradle/libs.versions.toml b/gradle/libs.versions.toml
index 0b8cfbd..28a4cd9 100644
--- a/gradle/libs.versions.toml
+++ b/gradle/libs.versions.toml
@@ -8,39 +8,41 @@ constraintlayout = "2.2.0"
 fragmentKtx = "1.8.5"
 navigation = "2.9.6"
 playServicesMaps = "19.0.0"
 playServicesLocation = "21.3.0"
 camerax = "1.4.1"
 lifecycle = "2.8.7"
 coroutines = "1.9.0"
 secrets = "2.0.1"
 admob = "23.6.0"
 billing = "8.3.0"
 googleServices = "4.4.4"
 firebaseCrashlytics = "3.0.2"
+junit = "4.13.2"
 
 [libraries]
 androidx-core-ktx                = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
 androidx-appcompat               = { module = "androidx.appcompat:appcompat", version.ref = "appcompat" }
 material                         = { module = "com.google.android.material:material", version.ref = "material" }
 androidx-constraintlayout        = { module = "androidx.constraintlayout:constraintlayout", version.ref = "constraintlayout" }
 androidx-fragment-ktx            = { module = "androidx.fragment:fragment-ktx", version.ref = "fragmentKtx" }
 androidx-navigation-fragment-ktx = { module = "androidx.navigation:navigation-fragment-ktx", version.ref = "navigation" }
 androidx-navigation-ui-ktx       = { module = "androidx.navigation:navigation-ui-ktx", version.ref = "navigation" }
 play-services-maps               = { module = "com.google.android.gms:play-services-maps", version.ref = "playServicesMaps" }
 play-services-location           = { module = "com.google.android.gms:play-services-location", version.ref = "playServicesLocation" }
 camerax-core                     = { module = "androidx.camera:camera-core", version.ref = "camerax" }
 camerax-camera2                  = { module = "androidx.camera:camera-camera2", version.ref = "camerax" }
 camerax-lifecycle                = { module = "androidx.camera:camera-lifecycle", version.ref = "camerax" }
 camerax-view                     = { module = "androidx.camera:camera-view", version.ref = "camerax" }
 androidx-lifecycle-runtime-ktx   = { module = "androidx.lifecycle:lifecycle-runtime-ktx", version.ref = "lifecycle" }
 kotlinx-coroutines-android       = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "coroutines" }
 admob                            = { module = "com.google.android.gms:play-services-ads", version.ref = "admob" }
 billing                          = { module = "com.android.billingclient:billing-ktx",    version.ref = "billing" }
+junit                            = { module = "junit:junit", version.ref = "junit" }
 
 [plugins]
 android-application       = { id = "com.android.application", version.ref = "agp" }
 kotlin-android            = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
 navigation-safeargs       = { id = "androidx.navigation.safeargs.kotlin", version.ref = "navigation" }
 secrets-gradle            = { id = "com.google.android.libraries.mapsplatform.secrets-gradle-plugin", version.ref = "secrets" }
 google-services           = { id = "com.google.gms.google-services",    version.ref = "googleServices" }
 firebase-crashlytics-plugin = { id = "com.google.firebase.crashlytics", version.ref = "firebaseCrashlytics" }
\ No newline at end of file
```
