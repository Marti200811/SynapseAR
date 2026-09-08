# Paquete de revision - Task 1 (385d8b2..cd53324)

## Commits

```
cd53324 chore: habilitar tests unitarios (faltaba la dependencia de JUnit)
```

## Resumen de cambios

```
 app/build.gradle.kts                                    |  3 +++
 app/src/test/java/com/example/ar/SanityTest.kt          | 15 +++++++++++++++
 .../test/java/com/example/synapsear/ExampleUnitTest.kt  | 17 -----------------
 gradle/libs.versions.toml                               |  2 ++
 4 files changed, 20 insertions(+), 17 deletions(-)
```

## Diff completo

```diff
diff --git a/app/build.gradle.kts b/app/build.gradle.kts
index adbece8..275c852 100644
--- a/app/build.gradle.kts
+++ b/app/build.gradle.kts
@@ -107,11 +107,14 @@ dependencies {
 
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
diff --git a/app/src/test/java/com/example/ar/SanityTest.kt b/app/src/test/java/com/example/ar/SanityTest.kt
new file mode 100644
index 0000000..e62bbaa
--- /dev/null
+++ b/app/src/test/java/com/example/ar/SanityTest.kt
@@ -0,0 +1,15 @@
+package com.example.ar
+
+import org.junit.Assert.assertTrue
+import org.junit.Test
+
+/**
+ * Verifica que la infraestructura de tests unitarios compila y corre.
+ * Se borra en la Task 2, cuando existan los tests reales de AccessRules.
+ */
+class SanityTest {
+    @Test
+    fun `la infraestructura de tests funciona`() {
+        assertTrue(true)
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
@@ -10,37 +10,39 @@ navigation = "2.9.6"
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
