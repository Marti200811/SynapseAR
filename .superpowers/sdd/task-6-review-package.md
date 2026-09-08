# Paquete de revision - Task 6 (e96f007..f93446e)

## Commits
```
f93446e feat: ofrecer un anuncio recompensado en el dialogo de upgrade
```

## Resumen de cambios
```
 app/src/main/java/com/example/ar/Analytics.kt     | 12 ++++++
 app/src/main/java/com/example/ar/MainActivity.kt  | 12 +++++-
 app/src/main/java/com/example/ar/UpgradeDialog.kt | 47 +++++++++++++++++++++++
 app/src/main/res/layout/dialog_upgrade.xml        | 11 ++++++
 app/src/main/res/values-en/strings.xml            |  2 +
 app/src/main/res/values-pt/strings.xml            |  2 +
 app/src/main/res/values/strings.xml               |  2 +
 7 files changed, 86 insertions(+), 2 deletions(-)
```

## Diff completo
```diff
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
index 67e7819..b373615 100644
--- a/app/src/main/java/com/example/ar/MainActivity.kt
+++ b/app/src/main/java/com/example/ar/MainActivity.kt
@@ -171,31 +171,39 @@ class MainActivity : AppCompatActivity() {
                 if (sharedVm.isPro.value != true) {
                     binding.adBanner.loadAd(AdRequest.Builder().build())
                     rewardedAdManager.preload()
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
index 28752ae..34bbd91 100644
--- a/app/src/main/java/com/example/ar/UpgradeDialog.kt
+++ b/app/src/main/java/com/example/ar/UpgradeDialog.kt
@@ -1,50 +1,97 @@
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
+                Analytics.rewardedStarted(requireContext(), source)
+                ads.show(
+                    onEarned = {
+                        AccessManager.grantTemporary(requireContext(), feat)
+                        Analytics.rewardedEarned(requireContext(), source)
+                        onRewardEarned?.invoke()
+                        dismiss()
+                    },
+                    onFailed = {
+                        android.widget.Toast.makeText(
+                            requireContext(),
+                            getString(R.string.rewarded_failed),
+                            android.widget.Toast.LENGTH_SHORT
+                        ).show()
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
```
