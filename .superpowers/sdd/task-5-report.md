# Task 5: `RewardedAdManager` y configuración de AdMob — Reporte

## Estado: DONE

## Archivos creados
- `app/src/main/java/com/example/ar/access/RewardedAdManager.kt`
  Copiado textual del brief (Step 3): `class RewardedAdManager(activity: Activity)`,
  `preload()`, `isAdReady()`, `show(onEarned, onFailed)`. Ningún import del SDK dio
  error (`AdError`, `AdRequest`, `FullScreenContentCallback`, `LoadAdError`,
  `RewardedAd`, `RewardedAdLoadCallback` — todos resuelven en
  `com.google.android.gms.ads` / `com.google.android.gms.ads.rewarded` tal como
  están en el brief, sin necesidad de ajustar ningún paquete para la versión
  23.6.0 del SDK ya usada para el banner).

## Archivos modificados
- `app/src/debug/res/values/admob.xml` — agregado
  `rewarded_ad_unit_id` = `ca-app-pub-3940256099942544/5224354917` (ID de prueba
  oficial de Google).
- `app/src/release/res/values/admob.xml` — agregado
  `rewarded_ad_unit_id` = `ca-app-pub-5417760954645863/1848125012` (ID real,
  usado textual como indicó el brief).
- `app/src/main/java/com/example/ar/MainActivity.kt`:
  - import `com.example.ar.access.RewardedAdManager`
  - propiedad `lateinit var rewardedAdManager: RewardedAdManager` (sin `private`,
    junto a `billingManager`, para que Task 6 pueda usarla)
  - `rewardedAdManager = RewardedAdManager(this)` en `onCreate`, justo antes de
    `initAdsWithConsent()`
  - dentro de `MobileAds.initialize(this) { runOnUiThread { ... } }`, agregada
    `rewardedAdManager.preload()` junto a la carga del banner (solo si
    `!isPro`)

`ProManager.kt` no se tocó.

## Salida `compileDebugKotlin testDebugUnitTest`
```
> Task :app:compileDebugKotlin
> Task :app:compileDebugJavaWithJavac UP-TO-DATE
> Task :app:bundleDebugClassesToRuntimeJar
> Task :app:bundleDebugClassesToCompileJar
> Task :app:compileDebugUnitTestKotlin
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes UP-TO-DATE
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 13s
31 actionable tasks: 13 executed, 18 up-to-date
```
Resultados de test (`TEST-com.example.ar.access.AccessRulesTest.xml`):
`tests="8" skipped="0" failures="0" errors="0"` — los 8 tests siguen pasando.

## Salida `compileReleaseKotlin`
```
> Task :app:compileReleaseKotlin
w: .../wifi/WifiScanner.kt:38:30 'field SSID: String!' is deprecated. Deprecated in Java.
w: .../wifi/WifiScanner.kt:39:39 'field SSID: String!' is deprecated. Deprecated in Java.

BUILD SUCCESSFUL in 11s
22 actionable tasks: 22 executed
```
Los dos warnings son preexistentes (deprecación de la API `WifiInfo.SSID`, no
relacionados con este cambio). El string `rewarded_ad_unit_id` existe en ambos
flavors, así que release compiló sin problema.

## Commit
```
git add app/src/main/java/com/example/ar app/src/debug/res/values/admob.xml app/src/release/res/values/admob.xml
git commit -m "feat: agregar RewardedAdManager y las unidades de anuncio recompensado"
```
Hash: `e96f0078aeabdbe5b96daf4279a60bd19cbabef8` (corto: `e96f007`)
4 files changed, 93 insertions(+):
- `app/src/debug/res/values/admob.xml`
- `app/src/main/java/com/example/ar/MainActivity.kt`
- `app/src/main/java/com/example/ar/access/RewardedAdManager.kt` (nuevo)
- `app/src/release/res/values/admob.xml`

Nota: había otros archivos modificados/sin trackear en el repo (`.idea/*`,
`docs/superpowers/plans/2026-09-08-rewarded-ads.md`, `.superpowers/`) que
quedaron deliberadamente FUERA de este commit — no están en el alcance de
Task 5 y el comando de commit del brief los excluye explícitamente al listar
paths concretos.

## Dudas
Ninguna. No hubo errores de import del SDK de AdMob que resolver; el código del
brief compiló tal cual en ambos build types.
