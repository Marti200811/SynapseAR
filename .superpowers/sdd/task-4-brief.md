# Task 4: Migrar los 5 call sites a `AccessManager`

Refactor puro, **sin cambio de comportamiento observable**: como todavía nadie llama a
`grantTemporary`, `canUse` devuelve exactamente lo mismo que `ProManager.isPro`. Se hace antes
de meter anuncios para que, si algo se rompe, se sepa que fue el refactor y no la feature nueva.

## Global Constraints (aplican a esta tarea)

- `ProManager.kt` NO se modifica.
- No se agrega ninguna funcionalidad nueva en esta tarea. Si al terminar la app se comporta
  distinto que antes, es un bug.
- Compilar con `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"`.
- Shell: PowerShell en Windows. Usar `.\gradlew.bat`.

## Files

- Modify: `app/src/main/java/com/example/ar/MainActivity.kt`
- Modify: `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt`
- Modify: `app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt`

## Interfaces

- Consumes (creado en la Task 3): `AccessManager.canUse(context: Context, feature: Feature): Boolean`
- Consumes (creado en la Task 2): `Feature.AR`, `Feature.COMPASS_THEMES`, `Feature.WIFI_SCANNER`,
  `Feature.TDT_PICKER`, `Feature.SATELLITE`
- Produces: nada nuevo — los 5 gates ahora preguntan por `AccessManager`

## Steps

### Step 1: Migrar el gate del tab AR

En `MainActivity.kt`, agregar los imports:

```kotlin
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
```

Buscar este bloque (cerca de la línea 75):

```kotlin
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.arFragment
                && sharedVm.isPro.value != true) {
                navController.popBackStack()
                showUpgradeDialog(Analytics.SRC_AR_TAB)
            }
        }
```

Reemplazarlo por:

```kotlin
        navController.addOnDestinationChangedListener { _, destination, _ ->
            if (destination.id == R.id.arFragment
                && !AccessManager.canUse(this, Feature.AR)) {
                navController.popBackStack()
                showUpgradeDialog(Analytics.SRC_AR_TAB)
            }
        }
```

**Ojo:** este gate hoy usa `sharedVm.isPro.value`, no `ProManager.isPro`. Es el único de los
cinco que consulta el LiveData. Al pasar a `AccessManager` la fuente pasa a ser la misma que la
de los otros cuatro, que es justamente lo que se busca. **No toques** la línea
`sharedVm.isPro.observe(...)` que controla la visibilidad del banner: esa sigue dependiendo de
Pro y no de los desbloqueos temporales (un desbloqueo por anuncio no debe sacar el banner).

### Step 2: Migrar los gates de `CompassFragment`

En `CompassFragment.kt`, agregar los imports:

```kotlin
import com.example.ar.access.AccessManager
import com.example.ar.access.Feature
```

Hay **cuatro** usos de `ProManager.isPro(requireContext())` en este archivo. Migrar los cuatro:

**(a)** Toque corto en la brújula (cerca de la línea 119) — abre el selector de temas:

```kotlin
            } else if (AccessManager.canUse(requireContext(), Feature.COMPASS_THEMES)) {
```

**(b)** Toque largo en la brújula (cerca de la línea 126):

```kotlin
            if (AccessManager.canUse(requireContext(), Feature.COMPASS_THEMES)) {
```

**(c)** Escáner WiFi (cerca de la línea 149):

```kotlin
                    if (AccessManager.canUse(requireContext(), Feature.WIFI_SCANNER)) {
```

**(d)** Selector TDT (cerca de la línea 158):

```kotlin
                    if (AccessManager.canUse(requireContext(), Feature.TDT_PICKER)) {
```

Si tras los cambios el import de `ProManager` queda sin uso en este archivo, borralo.

### Step 3: Migrar el gate de satélites

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

Y actualizar los **dos** usos de esa variable — el del adapter y el del callback:

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

Si tras el cambio el import de `ProManager` queda sin uso en este archivo, borralo.

### Step 4: Compilar y correr los tests

```
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`, los 8 tests siguen pasando.

### Step 5: Verificar que NO quedó ningún gate sin migrar

```
git grep -n "ProManager.isPro" -- app/src/main
```

Esperado: solo aparece dentro de `AccessManager.kt` (que es quien debe consultarlo) y en
`MainActivity.kt` en las líneas que manejan el estado Pro para billing y banner — **no** en
ninguna de las 5 condiciones de acceso que migraste.

Si aparece alguna condición de acceso sin migrar, migrala antes de commitear.

### Step 6: Commit

```
git add app/src/main/java/com/example/ar
git commit -m "refactor: los gates preguntan por AccessManager en vez de ProManager"
```
