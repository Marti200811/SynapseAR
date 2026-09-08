# Task 7 — Reporte

## Resumen
Se conectaron las 5 llamadas a `showUpgradeDialog` con `feature =` y `onUnlocked =`, tal como
especifica el brief. En los pasos 3, 4 y 5, el cuerpo de `onUnlocked` es una copia literal del
código que ya corre en la rama `if` de la misma condición (no se reescribió nada).

El toque corto en la brújula (`setOnClickListener`, `CompassFragment.kt` línea ~117-124) se dejó
intacto — sigue sin llamar a `showUpgradeDialog` cuando no hay acceso. Solo se tocó el toque largo
(`setOnLongClickListener`).

`ProManager.kt` no se modificó.

## Los 5 call sites actualizados

### 1. Tab AR — `app/src/main/java/com/example/ar/MainActivity.kt` (línea 83, dentro de `addOnDestinationChangedListener`)
```kotlin
                showUpgradeDialog(
                    source = Analytics.SRC_AR_TAB,
                    feature = Feature.AR,
                    onUnlocked = { navController.navigate(R.id.arFragment) }
                )
```

### 2. Temas de brújula (toque largo) — `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` (línea 131, dentro de `setOnLongClickListener`)
```kotlin
                (requireActivity() as MainActivity).showUpgradeDialog(
                    source = Analytics.SRC_COMPASS_THEME,
                    feature = Feature.COMPASS_THEMES,
                    onUnlocked = { showThemePicker() }
                )
```

### 3. Escáner WiFi — `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` (línea 160, rama `else` de `AntennaType.WIFI_DIRECTIONAL`)
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
Cuerpo copiado literal de la rama `if` de ese mismo bloque (líneas 152-155 antes del cambio).

### 4. Selector TDT — `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` (línea 178, rama `else` de `AntennaType.TDT`)
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
Cuerpo copiado literal de la rama `if` de ese mismo bloque (líneas 162-166 antes del cambio).

### 5. Selector de satélites — `app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt` (línea 67, rama `else` del callback del adapter)
```kotlin
                (requireActivity() as MainActivity).showUpgradeDialog(
                    source = Analytics.SRC_SATELLITE_LOCKED,
                    feature = Feature.SATELLITE,
                    onUnlocked = {
                        onSelected(satellite)
                        dismiss()
                    }
                )
```

`Feature` ya estaba importado en los 3 archivos desde la Task 4 — se verificó antes de tocar nada,
no se agregó de nuevo.

## Step 8 — git grep

```
$ git grep -n "showUpgradeDialog" -- app/src/main
app/src/main/java/com/example/ar/MainActivity.kt:83:                showUpgradeDialog(
app/src/main/java/com/example/ar/MainActivity.kt:190:    fun showUpgradeDialog(
app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt:67:                (requireActivity() as MainActivity).showUpgradeDialog(
app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt:131:                (requireActivity() as MainActivity).showUpgradeDialog(
app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt:160:                        (requireActivity() as MainActivity).showUpgradeDialog(
app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt:178:                        (requireActivity() as MainActivity).showUpgradeDialog(
```

1 declaración (`MainActivity.kt:190`) + 5 llamadas. Verificación adicional de que las 5 pasan
`feature =`:

```
$ git grep -c "feature = Feature\." -- app/src/main
app/src/main/java/com/example/ar/MainActivity.kt:1
app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt:1
app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt:3

$ git grep -n "feature = Feature\." -- app/src/main
app/src/main/java/com/example/ar/MainActivity.kt:85:                    feature = Feature.AR,
app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt:69:                    feature = Feature.SATELLITE,
app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt:133:                    feature = Feature.COMPASS_THEMES,
app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt:162:                            feature = Feature.WIFI_SCANNER,
app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt:180:                            feature = Feature.TDT_PICKER,
```

Total: 5/5 llamadas con `feature =`. Ninguna quedó pasando solo `source`.

## Compilación

### Debug + tests (`.\gradlew.bat compileDebugKotlin testDebugUnitTest`)
```
BUILD SUCCESSFUL in 4s
31 actionable tasks: 4 executed, 27 up-to-date
```
Resultados de test (`app/build/test-results/testDebugUnitTest/TEST-com.example.ar.access.AccessRulesTest.xml`):
```
tests="8" skipped="0" failures="0" errors="0"
```
Los 8 tests siguen pasando.

### Release (`.\gradlew.bat compileReleaseKotlin`)
```
BUILD SUCCESSFUL in 6s
22 actionable tasks: 6 executed, 16 up-to-date
```

## Commit

```
git add app/src/main/java/com/example/ar
git commit -m "feat: entrar directo a la funcion despues de ganar la recompensa"
```

Hash: `a1938f3b6c899d2bf73a34a3258faee894b4e55f` (corto: `a1938f3`)
Rama: `feature/rewarded-ads`
3 files changed, 36 insertions(+), 9 deletions(-)

Nota: el commit incluyó solo `app/src/main/java/com/example/ar` tal como indica el brief. Quedaron
sin commitear (fuera de esta tarea, ya existían modificados/untracked antes de empezar):
`.idea/appInsightsSettings.xml`, `.idea/deploymentTargetSelector.xml`,
`docs/superpowers/plans/2026-09-08-rewarded-ads.md`, `.idea/deviceManager.xml` (untracked),
`.superpowers/` (untracked). No se tocaron ni se agregaron al commit.

## Dudas
Ninguna. Los 5 call sites, el grep de verificación y ambas compilaciones (debug + release)
coinciden exactamente con lo esperado por el brief.

---

# Fix post-revisión — guardar callbacks del anuncio contra fragments desasociados

## Resumen
Dos revisiones distintas señalaron el mismo defecto: los callbacks que corren **después** de
que el usuario mira el anuncio recompensado (15-30s, con otra Activity encima) usaban APIs de
fragment sin verificar que el fragment siguiera adjunto, arriesgando
`IllegalStateException: Fragment not attached` si el fragment se destruye en esa ventana.

Se aplicó el patrón ya existente en el código (`isAdded` / `!isAdded || ... return`) en los 5
lugares señalados, con la salvedad de que en `UpgradeDialog.kt` la recompensa se otorga siempre
(vía `applicationContext`, capturado antes de lanzar el anuncio) aunque el fragment ya no esté
adjunto — solo la UI (`onRewardEarned?.invoke()`, `dismiss()`, el `Toast` de fallo) queda detrás
del guard `isAdded`.

## Los 5 lugares corregidos

### 1. `app/src/main/java/com/example/ar/UpgradeDialog.kt` — `btnAd.setOnClickListener`
- Se capturó `val appCtx = requireContext().applicationContext` antes de `ads.show(...)`.
- `Analytics.rewardedStarted`, `AccessManager.grantTemporary` y `Analytics.rewardedEarned` ahora
  usan `appCtx` (corren siempre, incluso si el fragment ya se destruyó).
- `onRewardEarned?.invoke()` + `dismiss()` quedaron dentro de `if (isAdded) { ... }`.
- `onFailed` (el `Toast`) quedó dentro de `if (isAdded) { ... }`.
- No se tocó `Analytics.rewardedOffered(requireContext(), source)` (corre síncrono al abrir el
  diálogo, fragment garantizado adjunto).

### 2. `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` — `onUnlocked` de temas de brújula (línea ~134)
`onUnlocked = { showThemePicker() }` → `onUnlocked = { if (isAdded) showThemePicker() }`.

### 3. `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` — `onUnlocked` de escáner WiFi (línea ~163)
Cuerpo envuelto en `if (isAdded) { ... }` (el `WifiScannerDialog.show(...)`).

### 4. `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` — `onUnlocked` de selector TDT (línea ~181)
Cuerpo envuelto en `if (isAdded) { ... }` (el `TdtPickerDialog.show(...)`).

### 5. `app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt` — `onUnlocked` del selector de satélites (línea ~70)
`onSelected(satellite)` + `dismiss()` envueltos en `if (isAdded) { ... }`.

## Comando corrido y salida

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"
.\gradlew.bat compileDebugKotlin compileReleaseKotlin testDebugUnitTest
```

```
BUILD SUCCESSFUL in 10s
53 actionable tasks: 10 executed, 43 up-to-date
```

Resultados de test (`app/build/test-results/testDebugUnitTest/TEST-com.example.ar.access.AccessRulesTest.xml`):
```
tests="8" skipped="0" failures="0" errors="0"
```
Los 8 tests de `AccessRulesTest` siguen pasando.

## Commit

```
git add app/src/main/java/com/example/ar
git commit -m "fix: guardar los callbacks del anuncio contra fragments desasociados"
```

Hash: `7d74d6bbc1aff93cfcbe3cfe303da7a9b93058d8` (corto: `7d74d6b`)
Rama: `feature/rewarded-ads`
3 files changed, 34 insertions(+), 19 deletions(-)
