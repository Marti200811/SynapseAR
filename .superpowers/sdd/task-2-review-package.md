# Paquete de revision - Task 2 (cd53324..8bb3f5f)

## Commits
```
8bb3f5f feat: agregar Feature y AccessRules, la logica pura de acceso
```

## Resumen de cambios
```
 .../main/java/com/example/ar/access/AccessRules.kt | 24 ++++++++++
 app/src/main/java/com/example/ar/access/Feature.kt | 16 +++++++
 app/src/test/java/com/example/ar/SanityTest.kt     | 15 -------
 .../java/com/example/ar/access/AccessRulesTest.kt  | 51 ++++++++++++++++++++++
 4 files changed, 91 insertions(+), 15 deletions(-)
```

## Diff completo
```diff
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
diff --git a/app/src/test/java/com/example/ar/SanityTest.kt b/app/src/test/java/com/example/ar/SanityTest.kt
deleted file mode 100644
index e62bbaa..0000000
--- a/app/src/test/java/com/example/ar/SanityTest.kt
+++ /dev/null
@@ -1,15 +0,0 @@
-package com.example.ar
-
-import org.junit.Assert.assertTrue
-import org.junit.Test
-
-/**
- * Verifica que la infraestructura de tests unitarios compila y corre.
- * Se borra en la Task 2, cuando existan los tests reales de AccessRules.
- */
-class SanityTest {
-    @Test
-    fun `la infraestructura de tests funciona`() {
-        assertTrue(true)
-    }
-}
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
```
