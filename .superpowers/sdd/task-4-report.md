# Task 4 — Reporte: migrar los 5 call sites a AccessManager

## Resumen

Refactor puro completado. Los 5 gates de acceso ahora preguntan por
`AccessManager.canUse(context, feature)` en vez de `ProManager.isPro(...)` /
`sharedVm.isPro.value`. Como todavía nadie llama a `AccessManager.grantTemporary`,
el comportamiento observable de la app es idéntico al de antes del refactor.

`ProManager.kt` no fue modificado.

## Gates migrados (5 de 5)

| # | Archivo | Línea (post-cambio) | Antes | Feature |
|---|---------|----------------------|-------|---------|
| 1 | `app/src/main/java/com/example/ar/MainActivity.kt` | 78 | `sharedVm.isPro.value != true` | `Feature.AR` |
| 2 | `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` | 121 | `ProManager.isPro(requireContext())` (toque corto en la brújula) | `Feature.COMPASS_THEMES` |
| 3 | `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` | 128 | `ProManager.isPro(requireContext())` (toque largo en la brújula) | `Feature.COMPASS_THEMES` |
| 4 | `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` | 152 | `ProManager.isPro(requireContext())` (escáner WiFi) | `Feature.WIFI_SCANNER` |
| 5 | `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt` | 159 | `ProManager.isPro(requireContext())` (selector TDT) | `Feature.TDT_PICKER` |
| 6* | `app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt` | 44 (+ usos en línea 61 y 63) | `ProManager.isPro(requireContext())` | `Feature.SATELLITE` |

\* El brief cuenta esto como "5 gates" agrupando los 2 usos de `CompassFragment`
(toque corto + toque largo) como parte del mismo par de gates de tema, más
WiFi, TDT y Satélite = 5 condiciones de negocio, aunque en código son 4 call
sites en `CompassFragment.kt` (tal como advirtió el contexto: "no tres, son
cuatro") + 1 en `MainActivity.kt` + 1 en `SatellitePickerDialog.kt` = 6 líneas
de código migradas en total, cubriendo las 5 features del enum.

Se confirmó explícitamente que los **cuatro** usos de `ProManager.isPro(requireContext())`
en `CompassFragment.kt` fueron migrados (no solo los tres de los `else` de los
diálogos) — incluyendo el del `setOnClickListener` de toque corto en la
brújula (línea 121), que es el que el contexto advirtió que era fácil pasar
por alto.

En `MainActivity.kt`, la línea `sharedVm.isPro.observe(...)` que controla la
visibilidad del banner **no se tocó** — sigue dependiendo de Pro, no de
`AccessManager`, tal como pedía el brief.

En `SatellitePickerDialog.kt`, la variable `isPro` se renombró a `hasAccess`
y se actualizaron sus dos usos (constructor de `SatelliteAdapter` y condición
del callback `onClick`), resolviendo de una sola consulta tanto el candado
visual como el gate de selección.

Imports de `ProManager` eliminados en `CompassFragment.kt` y
`SatellitePickerDialog.kt` por quedar sin uso. `MainActivity.kt` conserva el
import de `ProManager` porque sigue usándolo para billing/banner (no forma
parte de este refactor).

## Step 5 — Verificación de gates sin migrar

```
$ git grep -n "ProManager.isPro" -- app/src/main
app/src/main/java/com/example/ar/MainActivity.kt:86:        // En debug, ProManager.isPro() ya maneja DEBUG_FORCE_FREE → no pisamos.
app/src/main/java/com/example/ar/MainActivity.kt:91:                else sharedVm.isPro.postValue(ProManager.isPro(this))
app/src/main/java/com/example/ar/MainActivity.kt:97:        sharedVm.isPro.value = ProManager.isPro(this)
app/src/main/java/com/example/ar/MainActivity.kt:106:        if (BuildConfig.DEBUG && !ProManager.isPro(this)) {
app/src/main/java/com/example/ar/access/AccessManager.kt:18:            isPro = ProManager.isPro(context),
```

Resultado esperado, confirmado: solo aparece en `AccessManager.kt` (el único
que debe consultarlo) y en `MainActivity.kt` en las líneas que manejan
billing (línea 91), inicialización de `sharedVm.isPro` para el banner (línea
97) y el snackbar de modo FREE en debug (línea 106) — ninguna es una de las 5
condiciones de acceso migradas.

## Step 4 — Compilación y tests

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

```
BUILD SUCCESSFUL in 4s
31 actionable tasks: 4 executed, 27 up-to-date
```

`app/build/test-results/testDebugUnitTest/TEST-com.example.ar.access.AccessRulesTest.xml`:

```
<testsuite name="com.example.ar.access.AccessRulesTest" tests="8" skipped="0" failures="0" errors="0" .../>
```

8/8 tests pasando, 0 fallas, 0 errores.

## Commit

```
$ git add app/src/main/java/com/example/ar
$ git commit -m "refactor: los gates preguntan por AccessManager en vez de ProManager"
[feature/rewarded-ads e8b9822] refactor: los gates preguntan por AccessManager en vez de ProManager
 3 files changed, 17 insertions(+), 10 deletions(-)
```

Hash: `e8b9822`

Solo se agregaron al stage los 3 archivos del brief
(`app/src/main/java/com/example/ar/MainActivity.kt`,
`app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt`,
`app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt`); los
cambios preexistentes fuera de alcance (`.idea/*`,
`docs/superpowers/plans/2026-09-08-rewarded-ads.md`, `.superpowers/`) quedaron
sin commitear, tal como estaban antes de esta tarea.

## Dudas

Ninguna. No se encontró ningún uso de `ProManager.isPro` fuera de los 5 gates
del brief y de los usos de billing/banner en `MainActivity.kt` que el
contexto ya identificaba como fuera de alcance.

---

## Corrección posterior — Actualizar comentario desactualizado

Después de completar el refactor a `AccessManager`, el comentario de `MainActivity.kt`
(líneas 74-76) quedó desactualizado: seguía describiendo el code anterior que usaba
`sharedVm.isPro`, cuando ya estaba usando `AccessManager.canUse(this, Feature.AR)`.

### Cambio realizado

**Archivo:** `app/src/main/java/com/example/ar/MainActivity.kt` (líneas 74-76)

**Antes:**
```kotlin
        // Bloquear tab AR si no es Pro.
        // Usa sharedVm.isPro como única fuente de verdad: así respeta
        // también el toggle DEBUG_FORCE_FREE en builds de desarrollo.
```

**Después:**
```kotlin
        // Bloquear tab AR si no tiene acceso.
        // AccessManager resuelve "es Pro O tiene un desbloqueo temporal vigente",
        // y ProManager sigue respetando el toggle DEBUG_FORCE_FREE en builds de desarrollo.
```

### Compilación y tests

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Output:
```
BUILD SUCCESSFUL in 2s
31 actionable tasks: 1 executed, 30 up-to-date
```

Verificación: Kotlin compilation exitosa (solo se recompilaron los archivos afectados),
tests unitarios sin cambios pero todos pasando.

### Commit

```
$ git add app/src/main/java/com/example/ar/MainActivity.kt
$ git commit -m "docs: actualizar el comentario del gate AR tras migrar a AccessManager"
[feature/rewarded-ads 975393a] docs: actualizar el comentario del gate AR tras migrar a AccessManager
 1 file changed, 3 insertions(+), 3 deletions(-)
```

Hash: `975393a`
