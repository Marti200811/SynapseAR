# Paquete de revision - Task 5 (975393a..e96f007)

## Commits
```
e96f007 feat: agregar RewardedAdManager y las unidades de anuncio recompensado
```

## Resumen de cambios
```
 app/src/debug/res/values/admob.xml                 |  5 ++
 app/src/main/java/com/example/ar/MainActivity.kt   |  5 ++
 .../com/example/ar/access/RewardedAdManager.kt     | 79 ++++++++++++++++++++++
 app/src/release/res/values/admob.xml               |  4 ++
 4 files changed, 93 insertions(+)
```

## Diff completo
```diff
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
diff --git a/app/src/main/java/com/example/ar/MainActivity.kt b/app/src/main/java/com/example/ar/MainActivity.kt
index 124af6e..67e7819 100644
--- a/app/src/main/java/com/example/ar/MainActivity.kt
+++ b/app/src/main/java/com/example/ar/MainActivity.kt
@@ -1,43 +1,45 @@
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
 import com.example.ar.access.AccessManager
 import com.example.ar.access.Feature
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
 
         // Mantener pantalla activa durante toda la sesión (app de trabajo profesional)
         window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
 
@@ -95,30 +97,32 @@ class MainActivity : AppCompatActivity() {
 
         // Inicializar isPro desde caché local (respeta DEBUG_FORCE_FREE en debug)
         sharedVm.isPro.value = ProManager.isPro(this)
 
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
 
         consentInformation.requestConsentInfoUpdate(
             this, params,
@@ -154,30 +158,31 @@ class MainActivity : AppCompatActivity() {
             MobileAds.setRequestConfiguration(
                 RequestConfiguration.Builder()
                     .setTestDeviceIds(listOf(
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
      *        Se registra en Analytics para saber qué función vende (ver [Analytics]).
      */
     fun showUpgradeDialog(source: String) {
         Analytics.upgradeDialogShown(this, source)
         val dialog = UpgradeDialog()
         dialog.billingManager = billingManager
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
```
