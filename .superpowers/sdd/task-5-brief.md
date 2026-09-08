# Task 5: `RewardedAdManager` y configuración de AdMob

Envuelve el SDK de anuncios recompensados y deja los IDs configurados por build type.

## Global Constraints (aplican a esta tarea)

- Paquete: `com.example.ar.access` para la clase nueva.
- `ProManager.kt` NO se modifica.
- En **debug** se usan SIEMPRE los IDs de prueba públicos de Google. Nunca los reales:
  Google suspende cuentas por tráfico inválido si el desarrollador clickea sus propios anuncios.
- Compilar con `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"`.
- Shell: PowerShell en Windows. Usar `.\gradlew.bat`.

## Files

- Create: `app/src/main/java/com/example/ar/access/RewardedAdManager.kt`
- Modify: `app/src/debug/res/values/admob.xml`
- Modify: `app/src/release/res/values/admob.xml`
- Modify: `app/src/main/java/com/example/ar/MainActivity.kt`

## Interfaces

- Consumes: nada de tareas anteriores (usa solo el SDK de AdMob y `R.string`)
- Produces (la Task 6 depende de estas firmas exactas):
  - `class RewardedAdManager(activity: Activity)`
  - `fun preload()`
  - `fun isAdReady(): Boolean`
  - `fun show(onEarned: () -> Unit, onFailed: () -> Unit)`
- Produces también: la propiedad `rewardedAdManager` en `MainActivity`, ya precargada

## Steps

### Step 1: Agregar el ID de prueba en debug

En `app/src/debug/res/values/admob.xml`, dentro de `<resources>`:

```xml
    <!-- ID de prueba oficial de Google para anuncios recompensados. NO reemplazar
         por el real: Google suspende cuentas si el desarrollador clickea sus
         propios anuncios de produccion. -->
    <string name="rewarded_ad_unit_id" translatable="false">ca-app-pub-3940256099942544/5224354917</string>
```

### Step 2: Agregar el ID real en release

En `app/src/release/res/values/admob.xml`, dentro de `<resources>`:

```xml
    <!-- Unidad "Recompensado desbloqueo" de la app ~5110678257 (la verificada),
         creada el 2026-09-08. -->
    <string name="rewarded_ad_unit_id" translatable="false">ca-app-pub-5417760954645863/1848125012</string>
```

Ese ID es real y ya existe en AdMob — usalo textualmente, no lo cambies.

### Step 3: Crear `RewardedAdManager`

Crear `app/src/main/java/com/example/ar/access/RewardedAdManager.kt`:

```kotlin
package com.example.ar.access

import android.app.Activity
import com.example.ar.R
import com.google.android.gms.ads.AdError
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
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
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

El flag local `earned` es lo que distingue "completó y ganó" de "cerró antes de tiempo": el
callback de recompensa dispara primero y el de cierre después, así que al llegar a
`onAdDismissedFullScreenContent` ya se sabe cuál de los dos casos fue.

### Step 4: Precargar después del consentimiento

En `MainActivity.kt`:

**(a)** Agregar el import:

```kotlin
import com.example.ar.access.RewardedAdManager
```

**(b)** Agregar la propiedad junto a `billingManager` (que está declarada como
`lateinit var billingManager: BillingManager`):

```kotlin
    lateinit var rewardedAdManager: RewardedAdManager
```

Tiene que ser accesible desde afuera (sin `private`): la Task 6 se la pasa al diálogo.

**(c)** Inicializarla en `onCreate`, justo antes de la llamada a `initAdsWithConsent()`:

```kotlin
        rewardedAdManager = RewardedAdManager(this)
```

**(d)** Dentro de `initializeMobileAds()`, en el bloque `MobileAds.initialize(this) { ... }`,
agregar la precarga junto a la carga del banner. El bloque queda así:

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

### Step 5: Compilar y correr los tests

```
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`, los 8 tests siguen pasando.

### Step 6: Verificar que el build de release también compila

El string `rewarded_ad_unit_id` tiene que existir en **ambos** flavors o el release no compila.

```
.\gradlew.bat compileReleaseKotlin
```

Esperado: `BUILD SUCCESSFUL`.

### Step 7: Commit

```
git add app/src/main/java/com/example/ar app/src/debug/res/values/admob.xml app/src/release/res/values/admob.xml
git commit -m "feat: agregar RewardedAdManager y las unidades de anuncio recompensado"
```
