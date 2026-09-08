# Paquete de revision - Task 3 (8bb3f5f..ac71c2a)

## Commits
```
ac71c2a feat: agregar UnlockStore y la fachada AccessManager
```

## Resumen de cambios
```
 .../java/com/example/ar/access/AccessManager.kt    | 38 ++++++++++++++++++++++
 .../main/java/com/example/ar/access/UnlockStore.kt | 27 +++++++++++++++
 2 files changed, 65 insertions(+)
```

## Diff completo
```diff
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
```
