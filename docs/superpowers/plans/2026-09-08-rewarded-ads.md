# Anuncios recompensados en SynapseAR — plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permitir que un usuario gratis desbloquee cada función Pro por 30 minutos mirando un anuncio recompensado, sin tocar la compra Pro ni el banner existentes.

**Architecture:** Una fachada `AccessManager` responde la única pregunta `canUse(context, feature)` — es Pro **o** tiene un desbloqueo vigente. La decisión real vive en `AccessRules`, un objeto puro sin dependencias de Android que se testea con JUnit común. `UnlockStore` guarda vencimientos en `SharedPreferences` y `RewardedAdManager` envuelve el SDK de AdMob. `ProManager` no se modifica.

**Tech Stack:** Kotlin, Android SDK 36, ViewBinding, Google Mobile Ads SDK 23.6.0, JUnit 4.13.2, Firebase Analytics.

## Global Constraints

- Paquete base: `com.example.ar` (NO `com.example.synapsear`, que es un resto de scaffolding).
- `applicationId` es `com.quantixlabs.synapsear` y `namespace` es `com.example.ar` — esa diferencia es intencional, no "arreglarla".
- `ProManager.kt` NO se modifica en ninguna tarea.
- Duración del desbloqueo: **30 minutos** (`30 * 60 * 1000L` ms).
- Sin tope diario de anuncios.
- El banner existente se mantiene sin cambios.
- IDs de AdMob: la app real es `ca-app-pub-5417760954645863~5110678257`. En debug se usan SIEMPRE los IDs de prueba de Google.
- Los strings de UI van en los 3 idiomas: `values/`, `values-en/`, `values-pt/`.
- Compilar con `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"` antes de cualquier comando de Gradle.

---

### Task 1: Habilitar los tests unitarios

Hoy `gradlew testDebugUnitTest` **falla a compilar**: no hay ninguna dependencia de test declarada y el test de ejemplo vive en un paquete equivocado. Sin esto, ninguna tarea posterior puede hacer TDD.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Delete: `app/src/test/java/com/example/synapsear/ExampleUnitTest.kt`
- Create: `app/src/test/java/com/example/ar/SanityTest.kt`

**Interfaces:**
- Consumes: nada
- Produces: `gradlew testDebugUnitTest` funcionando; alias `libs.junit` disponible en el catálogo

- [ ] **Step 1: Agregar la versión de JUnit al catálogo**

En `gradle/libs.versions.toml`, dentro de `[versions]`, después de la línea `firebaseCrashlytics = "3.0.2"`:

```toml
junit = "4.13.2"
```

Y dentro de `[libraries]`, después de la línea de `billing`:

```toml
junit                            = { module = "junit:junit", version.ref = "junit" }
```

- [ ] **Step 2: Declarar la dependencia de test**

En `app/build.gradle.kts`, dentro del bloque `dependencies { ... }`, al final (después de las líneas de `app-update-ktx`):

```kotlin
    // Tests unitarios locales (corren en la JVM, sin dispositivo)
    testImplementation(libs.junit)
```

- [ ] **Step 3: Borrar el test de ejemplo del paquete equivocado**

```bash
rm app/src/test/java/com/example/synapsear/ExampleUnitTest.kt
rmdir app/src/test/java/com/example/synapsear
```

- [ ] **Step 4: Crear un test de sanidad en el paquete correcto**

Crear `app/src/test/java/com/example/ar/SanityTest.kt`:

```kotlin
package com.example.ar

import org.junit.Assert.assertTrue
import org.junit.Test

/** Verifica que la infraestructura de tests unitarios compila y corre. */
class SanityTest {
    @Test
    fun `la infraestructura de tests funciona`() {
        assertTrue(true)
    }
}
```

- [ ] **Step 5: Correr los tests y verificar que pasan**

```bash
./gradlew testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`, sin errores `Unresolved reference 'junit'`.

- [ ] **Step 6: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/test
git commit -m "chore: habilitar tests unitarios (faltaba la dependencia de JUnit)"
```

---

### Task 2: `Feature` y `AccessRules` (lógica pura)

El corazón de la decisión, sin nada de Android para que se pueda testear de verdad.

**Files:**
- Create: `app/src/main/java/com/example/ar/access/Feature.kt`
- Create: `app/src/main/java/com/example/ar/access/AccessRules.kt`
- Test: `app/src/test/java/com/example/ar/access/AccessRulesTest.kt`

**Interfaces:**
- Consumes: nada
- Produces:
  - `enum class Feature { AR, COMPASS_THEMES, WIFI_SCANNER, TDT_PICKER, SATELLITE }`
  - `object AccessRules` con `canUse(isPro: Boolean, expiryMillis: Long, nowMillis: Long): Boolean`, `remaining(expiryMillis: Long, nowMillis: Long): Long` y `const val UNLOCK_DURATION_MS: Long`

- [ ] **Step 1: Escribir los tests que fallan**

Crear `app/src/test/java/com/example/ar/access/AccessRulesTest.kt`:

```kotlin
package com.example.ar.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccessRulesTest {

    private val ahora = 1_000_000L

    @Test
    fun `un usuario Pro puede usar aunque no tenga desbloqueo`() {
        assertTrue(AccessRules.canUse(isPro = true, expiryMillis = 0L, nowMillis = ahora))
    }

    @Test
    fun `un desbloqueo vigente permite el acceso`() {
        assertTrue(AccessRules.canUse(isPro = false, expiryMillis = ahora + 1, nowMillis = ahora))
    }

    @Test
    fun `un desbloqueo vencido deniega el acceso`() {
        assertFalse(AccessRules.canUse(isPro = false, expiryMillis = ahora - 1, nowMillis = ahora))
    }

    @Test
    fun `un desbloqueo que vence justo ahora deniega el acceso`() {
        assertFalse(AccessRules.canUse(isPro = false, expiryMillis = ahora, nowMillis = ahora))
    }

    @Test
    fun `sin desbloqueo y sin Pro deniega el acceso`() {
        assertFalse(AccessRules.canUse(isPro = false, expiryMillis = 0L, nowMillis = ahora))
    }

    @Test
    fun `remaining devuelve el tiempo que falta`() {
        assertEquals(5_000L, AccessRules.remaining(expiryMillis = ahora + 5_000L, nowMillis = ahora))
    }

    @Test
    fun `remaining nunca es negativo`() {
        assertEquals(0L, AccessRules.remaining(expiryMillis = ahora - 5_000L, nowMillis = ahora))
    }

    @Test
    fun `la duracion del desbloqueo es de 30 minutos`() {
        assertEquals(30L * 60L * 1000L, AccessRules.UNLOCK_DURATION_MS)
    }
}
```

- [ ] **Step 2: Correr los tests y verificar que fallan**

```bash
./gradlew testDebugUnitTest --tests "com.example.ar.access.AccessRulesTest"
```

Esperado: FALLA a compilar con `Unresolved reference 'AccessRules'`.

- [ ] **Step 3: Crear el enum `Feature`**

Crear `app/src/main/java/com/example/ar/access/Feature.kt`:

```kotlin
package com.example.ar.access

/**
 * Funciones que en la versión gratuita están bloqueadas y se pueden desbloquear
 * temporalmente mirando un anuncio recompensado.
 *
 * OJO: SATELLITE es UNA función, no una por satélite. Un anuncio desbloquea
 * todos los satélites de pago a la vez — ver el spec del 2026-09-08.
 */
enum class Feature {
    AR,
    COMPASS_THEMES,
    WIFI_SCANNER,
    TDT_PICKER,
    SATELLITE
}
```

- [ ] **Step 4: Implementar `AccessRules`**

Crear `app/src/main/java/com/example/ar/access/AccessRules.kt`:

```kotlin
package com.example.ar.access

/**
 * Reglas de acceso, sin dependencias de Android para poder testearlas.
 *
 * Toda la decisión de "¿puede usar esto?" vive acá. La fachada [AccessManager]
 * solo se encarga de conseguir los datos (estado Pro, vencimiento guardado, reloj).
 */
object AccessRules {

    /** Cuánto dura el desbloqueo que otorga un anuncio recompensado. */
    const val UNLOCK_DURATION_MS: Long = 30L * 60L * 1000L

    /**
     * Un usuario Pro siempre puede. Si no lo es, necesita un desbloqueo que
     * todavía no haya vencido. Un vencimiento exactamente igual a "ahora" ya venció.
     */
    fun canUse(isPro: Boolean, expiryMillis: Long, nowMillis: Long): Boolean =
        isPro || nowMillis < expiryMillis

    /** Milisegundos que le quedan al desbloqueo. Nunca negativo. */
    fun remaining(expiryMillis: Long, nowMillis: Long): Long =
        (expiryMillis - nowMillis).coerceAtLeast(0L)
}
```

- [ ] **Step 5: Correr los tests y verificar que pasan**

```bash
./gradlew testDebugUnitTest --tests "com.example.ar.access.AccessRulesTest"
```

Esperado: `BUILD SUCCESSFUL`, 8 tests pasan.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ar/access app/src/test/java/com/example/ar/access
git commit -m "feat: agregar Feature y AccessRules, la logica pura de acceso"
```

---

### Task 3: `UnlockStore` y `AccessManager`

La persistencia y la fachada que van a usar los call sites.

**Files:**
- Create: `app/src/main/java/com/example/ar/access/UnlockStore.kt`
- Create: `app/src/main/java/com/example/ar/access/AccessManager.kt`

**Interfaces:**
- Consumes: `Feature`, `AccessRules` (Task 2); `ProManager.isPro(context)` (existente)
- Produces:
  - `AccessManager.canUse(context: Context, feature: Feature): Boolean`
  - `AccessManager.grantTemporary(context: Context, feature: Feature)`
  - `AccessManager.remainingMillis(context: Context, feature: Feature): Long`

- [ ] **Step 1: Crear `UnlockStore`**

Crear `app/src/main/java/com/example/ar/access/UnlockStore.kt`:

```kotlin
package com.example.ar.access

import android.content.Context

/**
 * Guarda hasta cuándo está desbloqueada cada función.
 *
 * Usa el mismo archivo de preferencias que ProManager y SettingsManager.
 * No hace falta limpiar los vencidos: un valor viejo simplemente falla la
 * comparación en AccessRules.
 */
internal object UnlockStore {

    private const val PREFS = "synapse_prefs"

    private fun key(feature: Feature) = "unlock_until_${feature.name}"

    /** Timestamp (epoch millis) hasta el que vale el desbloqueo. 0 = nunca se desbloqueó. */
    fun expiryMillis(context: Context, feature: Feature): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(key(feature), 0L)

    fun setExpiryMillis(context: Context, feature: Feature, millis: Long) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putLong(key(feature), millis).apply()
    }
}
```

- [ ] **Step 2: Crear `AccessManager`**

Crear `app/src/main/java/com/example/ar/access/AccessManager.kt`:

```kotlin
package com.example.ar.access

import android.content.Context
import com.example.ar.ProManager

/**
 * Única pregunta que deben hacer los call sites: ¿puede usar esta función?
 *
 * Existe para que la condición "es Pro O tiene desbloqueo vigente" viva en un
 * solo lugar. Repetirla en cada pantalla es donde aparecen los agujeros: en el
 * proyecto hermano Oráculo, una condición armada a mano en un call site permitía
 * consultas ilimitadas gratis.
 */
object AccessManager {

    fun canUse(context: Context, feature: Feature): Boolean =
        AccessRules.canUse(
            isPro = ProManager.isPro(context),
            expiryMillis = UnlockStore.expiryMillis(context, feature),
            nowMillis = System.currentTimeMillis()
        )

    /** Otorga acceso temporal a [feature]. Se llama al ganar la recompensa de un anuncio. */
    fun grantTemporary(context: Context, feature: Feature) {
        UnlockStore.setExpiryMillis(
            context,
            feature,
            System.currentTimeMillis() + AccessRules.UNLOCK_DURATION_MS
        )
    }

    /** Milisegundos que le quedan al desbloqueo temporal. 0 si no hay o ya venció. */
    fun remainingMillis(context: Context, feature: Feature): Long =
        AccessRules.remaining(
            expiryMillis = UnlockStore.expiryMillis(context, feature),
            nowMillis = System.currentTimeMillis()
        )
}
```

- [ ] **Step 3: Compilar**

```bash
./gradlew compileDebugKotlin
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/ar/access
git commit -m "feat: agregar UnlockStore y la fachada AccessManager"
```

---

### Task 4: Migrar los 5 call sites a `AccessManager`

Refactor puro, **sin cambio de comportamiento**: como todavía nadie llama a `grantTemporary`, `canUse` devuelve exactamente lo mismo que `ProManager.isPro`. Se hace antes de meter anuncios para que, si algo se rompe, se sepa que fue el refactor y no la feature.

**Files:**
- Modify: `app/src/main/java/com/example/ar/MainActivity.kt`
- Modify: `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt`
- Modify: `app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt`

**Interfaces:**
- Consumes: `AccessManager.canUse` (Task 3), `Feature` (Task 2)
- Produces: los 5 gates preguntando por `AccessManager`

- [ ] **Step 1: Migrar el gate del tab AR**

En `MainActivity.kt`, agregar los imports:

```kotlin
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
```

Reemplazar el listener de navegación (hoy usa `sharedVm.isPro.value != true`):

```kotlin
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.arFragment
                && !AccessManager.canUse(this, Feature.AR)) {
                navController.popBackStack()
                showUpgradeDialog(Analytics.SRC_AR_TAB)
            }
        }
```

- [ ] **Step 2: Migrar los 3 gates de `CompassFragment`**

En `CompassFragment.kt`, agregar los imports:

```kotlin
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
```

Reemplazar las tres condiciones `ProManager.isPro(requireContext())` por, respectivamente:

```kotlin
            if (AccessManager.canUse(requireContext(), Feature.COMPASS_THEMES)) {
```

```kotlin
                    if (AccessManager.canUse(requireContext(), Feature.WIFI_SCANNER)) {
```

```kotlin
                    if (AccessManager.canUse(requireContext(), Feature.TDT_PICKER)) {
```

**Ojo:** hay un cuarto uso de `ProManager.isPro` en el `setOnClickListener` de la brújula (el que abre el selector de temas con un toque corto cuando la calibración es buena). Ese también pasa a `AccessManager.canUse(requireContext(), Feature.COMPASS_THEMES)`.

- [ ] **Step 3: Migrar el gate de satélites**

En `SatellitePickerDialog.kt`, agregar los imports:

```kotlin
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
```

La línea 40 alimenta **dos cosas a la vez**: el candado visual de cada fila (vía el
`SatelliteAdapter`) y la condición de selección del callback. Cambiarla resuelve las dos, y
además evita que tras desbloquear con un anuncio queden candados dibujados en satélites que
ya se pueden usar.

Reemplazar:

```kotlin
        val isPro = ProManager.isPro(requireContext())
```

por:

```kotlin
        // Vale tanto para el candado visual como para el gate de selección.
        // Se llama una vez acá a propósito: si se consultara por fila, el estado
        // podría cambiar a mitad del scroll.
        val hasAccess = AccessManager.canUse(requireContext(), Feature.SATELLITE)
```

Y actualizar los dos usos de esa variable — el del adapter y el del callback:

```kotlin
        val adapter = SatelliteAdapter(sorted, hasAccess, elevationMap) { satellite ->
            val isFree = SatelliteDatabase.freeSatelliteNames.contains(satellite.name)
            if (hasAccess || isFree) {
                onSelected(satellite)
                dismiss()
            } else {
                (requireActivity() as MainActivity)
                    .showUpgradeDialog(Analytics.SRC_SATELLITE_LOCKED)
            }
        }
```

Si tras el cambio queda algún uso de `ProManager` sin referencias en este archivo, borrar su import.

- [ ] **Step 4: Compilar y correr los tests**

```bash
./gradlew compileDebugKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Verificar a mano que no cambió nada**

Instalar en el Motorola y confirmar que las 5 funciones siguen bloqueadas igual que antes:

```bash
./gradlew installDebug
```

Como en debug `ProManager.isPro` devuelve `true`, para ver los candados hay que poner `DEBUG_FORCE_FREE = true` en `ProManager.kt` temporalmente. **Revertirlo antes de commitear.**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ar
git commit -m "refactor: los gates preguntan por AccessManager en vez de ProManager"
```

---

### Task 5: `RewardedAdManager` y configuración de AdMob

**Files:**
- Create: `app/src/main/java/com/example/ar/access/RewardedAdManager.kt`
- Modify: `app/src/debug/res/values/admob.xml`
- Modify: `app/src/release/res/values/admob.xml`

**Interfaces:**
- Consumes: `Feature` (Task 2)
- Produces:
  - `RewardedAdManager(activity: Activity)` con `preload()`, `isAdReady(): Boolean`, `show(onEarned: () -> Unit, onFailed: () -> Unit)`

> 🛑 **BLOQUEANTE — acción humana antes de empezar esta tarea.**
> Hay que crear una unidad **Recompensada** en la consola de AdMob, dentro de la app
> `ca-app-pub-5417760954645863~5110678257` (la verificada — **no** `~1056694271`, que tiene
> servicio de anuncios limitado), y anotar el ID que devuelve.
>
> Sin ese ID, el Step 2 no se puede completar. **No inventar un ID ni dejar un placeholder en
> el código**: un ID inválido en release hace que los anuncios nunca carguen, en silencio.
>
> Al crear la unidad, dejar **"Puja de partner" SIN marcar** — marcarla impide usar la
> mediación de AdMob y no se puede cambiar después.
>
> El Step 1 (debug) sí se puede hacer sin esperar: usa el ID de prueba público de Google.

- [ ] **Step 1: Agregar el ID de prueba en debug**

En `app/src/debug/res/values/admob.xml`, dentro de `<resources>`:

```xml
    <!-- ID de prueba oficial de Google para anuncios recompensados. NO reemplazar
         por el real: Google suspende cuentas si el desarrollador clickea sus
         propios anuncios de produccion. -->
    <string name="rewarded_ad_unit_id" translatable="false">ca-app-pub-3940256099942544/5224354917</string>
```

- [ ] **Step 2: Agregar el ID real en release**

En `app/src/release/res/values/admob.xml`, dentro de `<resources>`, reemplazando `XXXXXXXXXX` por el ID de la unidad creada en AdMob:

```xml
    <!-- Unidad recompensada de la app ~5110678257 (la verificada). -->
    <string name="rewarded_ad_unit_id" translatable="false">ca-app-pub-5417760954645863/XXXXXXXXXX</string>
```

- [ ] **Step 3: Crear `RewardedAdManager`**

Crear `app/src/main/java/com/example/ar/access/RewardedAdManager.kt`:

```kotlin
package com.example.ar.access

import android.app.Activity
import com.example.ar.R
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

/**
 * Envuelve la carga y exhibición de un anuncio recompensado.
 *
 * Precarga uno solo por vez. No se puede llamar a [preload] antes de que
 * MobileAds.initialize() haya corrido — en esta app eso pasa recién después del
 * flujo de consentimiento UMP, así que el enganche está en
 * MainActivity.initializeMobileAds().
 */
class RewardedAdManager(private val activity: Activity) {

    private var rewardedAd: RewardedAd? = null
    private var loading = false

    /** true si hay un anuncio listo para mostrar AHORA. */
    fun isAdReady(): Boolean = rewardedAd != null

    fun preload() {
        if (loading || rewardedAd != null) return
        loading = true
        RewardedAd.load(
            activity,
            activity.getString(R.string.rewarded_ad_unit_id),
            AdRequest.Builder().build(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    loading = false
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewardedAd = null
                    loading = false
                }
            }
        )
    }

    /**
     * Muestra el anuncio. [onEarned] se invoca SOLO si el usuario lo completó y
     * ganó la recompensa; si lo cierra antes, se invoca [onFailed].
     */
    fun show(onEarned: () -> Unit, onFailed: () -> Unit) {
        val ad = rewardedAd
        if (ad == null) {
            onFailed()
            return
        }

        var earned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preload()                       // dejar listo el siguiente
                if (!earned) onFailed()
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                rewardedAd = null
                preload()
                onFailed()
            }
        }

        ad.show(activity) {
            earned = true
            onEarned()
        }
    }
}
```

- [ ] **Step 4: Precargar después del consentimiento**

En `MainActivity.kt`, agregar la propiedad junto a `billingManager`:

```kotlin
    private lateinit var rewardedAdManager: RewardedAdManager
```

Inicializarla en `onCreate`, antes de `initAdsWithConsent()`:

```kotlin
        rewardedAdManager = RewardedAdManager(this)
```

Y dentro de `initializeMobileAds()`, en el bloque `MobileAds.initialize(this) { ... }`, agregar la precarga junto a la carga del banner:

```kotlin
        MobileAds.initialize(this) {
            // M01: el callback puede llegar desde un background thread — postear a UI
            runOnUiThread {
                if (sharedVm.isPro.value != true) {
                    binding.adBanner.loadAd(AdRequest.Builder().build())
                    rewardedAdManager.preload()
                }
            }
        }
```

Agregar el import:

```kotlin
import com.example.ar.access.RewardedAdManager
```

- [ ] **Step 5: Compilar**

```bash
./gradlew compileDebugKotlin
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ar app/src/debug/res/values/admob.xml app/src/release/res/values/admob.xml
git commit -m "feat: agregar RewardedAdManager y las unidades de anuncio recompensado"
```

---

### Task 6: La opción de anuncio en `UpgradeDialog`

**Files:**
- Modify: `app/src/main/res/layout/dialog_upgrade.xml`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Modify: `app/src/main/res/values-pt/strings.xml`
- Modify: `app/src/main/java/com/example/ar/Analytics.kt`
- Modify: `app/src/main/java/com/example/ar/UpgradeDialog.kt`
- Modify: `app/src/main/java/com/example/ar/MainActivity.kt`

**Interfaces:**
- Consumes: `RewardedAdManager` (Task 5), `AccessManager.grantTemporary` (Task 3), `Feature` (Task 2)
- Produces: `UpgradeDialog` con las vars públicas `rewardedAdManager: RewardedAdManager?`, `feature: Feature?` y `onRewardEarned: (() -> Unit)?`

- [ ] **Step 1: Agregar el botón al layout**

En `dialog_upgrade.xml`, dentro del último `LinearLayout` (el de los botones), justo después del `TextView` de `upgrade_trial_note` y antes de `btnRestore`:

```xml
        <Button
            android:id="@+id/btnWatchAd"
            android:layout_width="match_parent"
            android:layout_height="48dp"
            android:text="@string/upgrade_btn_watch_ad"
            android:textSize="14sp"
            android:textColor="@color/accent_cyan"
            android:backgroundTint="@android:color/transparent"
            android:layout_marginTop="8dp"
            android:visibility="gone"/>
```

Empieza en `gone`: solo se muestra si hay un anuncio cargado.

- [ ] **Step 2: Agregar los strings en los 3 idiomas**

En `app/src/main/res/values/strings.xml` (español), junto a los otros `upgrade_*`:

```xml
    <string name="upgrade_btn_watch_ad">▶ Ver un anuncio y usar 30 min gratis</string>
    <string name="rewarded_failed">No se pudo cargar el anuncio. Probá de nuevo en un momento.</string>
```

En `app/src/main/res/values-en/strings.xml`:

```xml
    <string name="upgrade_btn_watch_ad">▶ Watch an ad and use it free for 30 min</string>
    <string name="rewarded_failed">Couldn\'t load the ad. Try again in a moment.</string>
```

En `app/src/main/res/values-pt/strings.xml`:

```xml
    <string name="upgrade_btn_watch_ad">▶ Ver um anúncio e usar 30 min grátis</string>
    <string name="rewarded_failed">Não foi possível carregar o anúncio. Tente novamente em instantes.</string>
```

- [ ] **Step 3: Agregar los 3 eventos de analytics**

En `Analytics.kt`, después de la función `upgradeButtonTapped`:

```kotlin
    /** Al usuario se le ofreció la opción de mirar un anuncio (había uno cargado). */
    fun rewardedOffered(context: Context, source: String) =
        log(context, "rewarded_offered", source)

    /** Tocó "Ver un anuncio". */
    fun rewardedStarted(context: Context, source: String) =
        log(context, "rewarded_started", source)

    /** Completó el anuncio y ganó el desbloqueo. */
    fun rewardedEarned(context: Context, source: String) =
        log(context, "rewarded_earned", source)
```

- [ ] **Step 4: Cablear el botón en `UpgradeDialog`**

En `UpgradeDialog.kt`, agregar los imports:

```kotlin
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
import com.example.ar.access.RewardedAdManager
```

Agregar las propiedades junto a `billingManager` y `source`:

```kotlin
    /** Manager de anuncios recompensados. Si es null, no se ofrece la opción. */
    var rewardedAdManager: RewardedAdManager? = null

    /** Función que se desbloquea si el usuario gana la recompensa. */
    var feature: Feature? = null

    /** Se invoca tras ganar la recompensa, para ejecutar la acción que estaba bloqueada. */
    var onRewardEarned: (() -> Unit)? = null
```

Dentro de `onCreateDialog`, después del bloque que configura `btnBuy` y antes de `applyPrice(...)`:

```kotlin
        val ads = rewardedAdManager
        val feat = feature
        val btnAd = view.findViewById<MaterialButton>(R.id.btnWatchAd)

        // Solo se ofrece si HAY un anuncio cargado ahora. Nunca se muestra un botón
        // deshabilitado con "Cargando…": ese fue un bug real en el proyecto hermano
        // Oráculo, donde el usuario veía un botón muerto sin saber por qué.
        if (ads != null && feat != null && ads.isAdReady()) {
            btnAd.visibility = android.view.View.VISIBLE
            Analytics.rewardedOffered(requireContext(), source)
            btnAd.setOnClickListener {
                Analytics.rewardedStarted(requireContext(), source)
                ads.show(
                    onEarned = {
                        AccessManager.grantTemporary(requireContext(), feat)
                        Analytics.rewardedEarned(requireContext(), source)
                        onRewardEarned?.invoke()
                        dismiss()
                    },
                    onFailed = {
                        android.widget.Toast.makeText(
                            requireContext(),
                            getString(R.string.rewarded_failed),
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
```

- [ ] **Step 5: Pasarle el manager desde `MainActivity`**

En `MainActivity.kt`, cambiar la firma de `showUpgradeDialog` para aceptar la función y la acción:

```kotlin
    /**
     * @param source cuál de las funciones bloqueadas trajo al usuario acá.
     * @param feature qué se desbloquea si mira un anuncio. Null = no se ofrece anuncio.
     * @param onUnlocked qué ejecutar si gana la recompensa.
     */
    fun showUpgradeDialog(
        source: String,
        feature: Feature? = null,
        onUnlocked: (() -> Unit)? = null
    ) {
        Analytics.upgradeDialogShown(this, source)
        val dialog = UpgradeDialog()
        dialog.billingManager = billingManager
        dialog.source = source
        dialog.rewardedAdManager = rewardedAdManager
        dialog.feature = feature
        dialog.onRewardEarned = onUnlocked
        dialog.show(supportFragmentManager, "upgrade")
    }
```

Agregar el import de `Feature` si no está.

- [ ] **Step 6: Compilar y correr los tests**

```bash
./gradlew compileDebugKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main app/src/test
git commit -m "feat: ofrecer un anuncio recompensado en el dialogo de upgrade"
```

---

### Task 7: Ejecutar la acción bloqueada tras la recompensa

Sin esto el usuario mira el anuncio y queda en la misma pantalla, teniendo que tocar de nuevo.

**Files:**
- Modify: `app/src/main/java/com/example/ar/MainActivity.kt`
- Modify: `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt`
- Modify: `app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt`

**Interfaces:**
- Consumes: `showUpgradeDialog(source, feature, onUnlocked)` (Task 6)
- Produces: nada nuevo

- [ ] **Step 1: Tab AR**

En `MainActivity.kt`, en el listener de navegación:

```kotlin
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.arFragment
                && !AccessManager.canUse(this, Feature.AR)) {
                navController.popBackStack()
                showUpgradeDialog(
                    source = Analytics.SRC_AR_TAB,
                    feature = Feature.AR,
                    onUnlocked = { navController.navigate(R.id.arFragment) }
                )
            }
        }
```

- [ ] **Step 2: Los 3 gates de `CompassFragment`**

Temas de brújula (los dos usos, el de toque corto y el de toque largo):

```kotlin
                (requireActivity() as MainActivity).showUpgradeDialog(
                    source = Analytics.SRC_COMPASS_THEME,
                    feature = Feature.COMPASS_THEMES,
                    onUnlocked = { showThemePicker() }
                )
```

Escáner WiFi:

```kotlin
                        (requireActivity() as MainActivity).showUpgradeDialog(
                            source = Analytics.SRC_WIFI_SCANNER,
                            feature = Feature.WIFI_SCANNER,
                            onUnlocked = {
                                WifiScannerDialog { network ->
                                    sharedVm.trackedWifi.value = network
                                }.show(parentFragmentManager, "wifi_scanner")
                            }
                        )
```

Selector TDT:

```kotlin
                        (requireActivity() as MainActivity).showUpgradeDialog(
                            source = Analytics.SRC_TDT_PICKER,
                            feature = Feature.TDT_PICKER,
                            onUnlocked = {
                                TdtPickerDialog(currentLocation, userCountryCode) { transmitter ->
                                    sharedVm.selectedTdt.value = transmitter
                                }.show(parentFragmentManager, "tdt_picker")
                            }
                        )
```

- [ ] **Step 3: Selector de satélites**

En `SatellitePickerDialog.kt`, en la rama `else` del callback:

```kotlin
            } else {
                (requireActivity() as MainActivity).showUpgradeDialog(
                    source = Analytics.SRC_SATELLITE_LOCKED,
                    feature = Feature.SATELLITE,
                    onUnlocked = {
                        onSelected(satellite)
                        dismiss()
                    }
                )
            }
```

- [ ] **Step 4: Compilar y correr los tests**

```bash
./gradlew compileDebugKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Smoke test manual en el Motorola**

Poner `DEBUG_FORCE_FREE = true` en `ProManager.kt`, instalar, y verificar en el dispositivo:

```bash
./gradlew installDebug
```

- Tocar cada una de las 5 funciones bloqueadas → aparece el botón "Ver un anuncio"
- Completar un anuncio de prueba → entra directo a la función
- Cerrar un anuncio a la mitad → NO desbloquea, aparece el Toast
- Volver a tocar la misma función dentro de los 30 min → entra sin pedir anuncio
- Tocar una función distinta → SÍ pide otro anuncio (los desbloqueos son independientes)

**Revertir `DEBUG_FORCE_FREE = false` antes de commitear.**

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/example/ar
git commit -m "feat: entrar directo a la funcion despues de ganar la recompensa"
```

---

## Notas de cierre

Cuando el plan esté completo, falta (fuera del alcance de estas tareas):

1. Crear la unidad recompensada real en AdMob y pegar su ID en `app/src/release/res/values/admob.xml` (Task 5, Step 2).
2. Subir el `versionCode` a **15** (el 14 ya está publicado en producción).
3. Generar el AAB con `gradlew bundleRelease` y verificar la firma con
   `keytool -printcert -jarfile` (debe dar `31:A4:F1:12:0C:E7:67…`).
4. Copiarlo a `Downloads\AABs\` con nombre identificable antes de subirlo.
