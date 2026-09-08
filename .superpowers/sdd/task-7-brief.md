# Task 7: Ejecutar la acción bloqueada tras la recompensa

Sin esto el usuario mira el anuncio completo y queda en la misma pantalla, teniendo que volver a
tocar lo que quería usar. Esta tarea conecta el desbloqueo con la acción.

## Global Constraints (aplican a esta tarea)

- `ProManager.kt` NO se modifica.
- **No cambiar el comportamiento de ningún gate**, solo agregar qué pasa después de la
  recompensa. Si una función estaba bloqueada, sigue estándolo hasta que se gane el anuncio.
- Compilar con `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"`.
- Shell: PowerShell en Windows. Usar `.\gradlew.bat`.

## Files

- Modify: `app/src/main/java/com/example/ar/MainActivity.kt`
- Modify: `app/src/main/java/com/example/ar/ui/compass/CompassFragment.kt`
- Modify: `app/src/main/java/com/example/ar/satellite/SatellitePickerDialog.kt`

## Interfaces

- Consumes (Task 6): `showUpgradeDialog(source: String, feature: Feature? = null, onUnlocked: (() -> Unit)? = null)`
- Consumes (Task 2): `Feature`
- Produces: nada nuevo

## Contexto: cuáles son los call sites

Hay **5** llamadas a `showUpgradeDialog` en el proyecto. Todas hoy pasan solo `source`, así que
el botón de anuncio nunca aparece. Esta tarea les agrega `feature` y `onUnlocked`.

**Importante:** el toque *corto* en la brújula (`setOnClickListener`) NO llama a
`showUpgradeDialog` — si no hay acceso, simplemente no hace nada. Ese es el comportamiento actual
y **no hay que cambiarlo**: agregarle un diálogo sería una funcionalidad nueva que nadie pidió.
Solo el toque *largo* (`setOnLongClickListener`) muestra el diálogo.

## Steps

### Step 1: Tab AR

En `MainActivity.kt`, en el listener de navegación, reemplazar:

```kotlin
                showUpgradeDialog(Analytics.SRC_AR_TAB)
```

por:

```kotlin
                showUpgradeDialog(
                    source = Analytics.SRC_AR_TAB,
                    feature = Feature.AR,
                    onUnlocked = { navController.navigate(R.id.arFragment) }
                )
```

`navController` es la variable local del `onCreate` donde está ese listener — el lambda la
captura sin problema.

### Step 2: Temas de brújula (toque largo)

En `CompassFragment.kt`, dentro del `setOnLongClickListener`, reemplazar:

```kotlin
                (requireActivity() as MainActivity)
                    .showUpgradeDialog(Analytics.SRC_COMPASS_THEME)
```

por:

```kotlin
                (requireActivity() as MainActivity).showUpgradeDialog(
                    source = Analytics.SRC_COMPASS_THEME,
                    feature = Feature.COMPASS_THEMES,
                    onUnlocked = { showThemePicker() }
                )
```

### Step 3: Escáner WiFi

En `CompassFragment.kt`, en la rama `else` del bloque `AntennaType.WIFI_DIRECTIONAL`, reemplazar:

```kotlin
                        (requireActivity() as MainActivity)
                            .showUpgradeDialog(Analytics.SRC_WIFI_SCANNER)
```

por:

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

El cuerpo del `onUnlocked` es exactamente el mismo código que corre en la rama `if` cuando el
usuario sí tiene acceso — copialo de ahí para no introducir diferencias.

### Step 4: Selector TDT

En `CompassFragment.kt`, en la rama `else` del bloque `AntennaType.TDT`, reemplazar:

```kotlin
                        (requireActivity() as MainActivity)
                            .showUpgradeDialog(Analytics.SRC_TDT_PICKER)
```

por:

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

Igual que el anterior: el cuerpo replica el de la rama `if`.

### Step 5: Selector de satélites

En `SatellitePickerDialog.kt`, en la rama `else` del callback del adapter, reemplazar:

```kotlin
                (requireActivity() as MainActivity)
                    .showUpgradeDialog(Analytics.SRC_SATELLITE_LOCKED)
```

por:

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

`satellite` es el parámetro del lambda del adapter y `onSelected` es la propiedad del
`DialogFragment` — ambos están en alcance.

### Step 6: Compilar y correr los tests

```
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`, los 8 tests siguen pasando.

### Step 7: Verificar que el release también compila

```
.\gradlew.bat compileReleaseKotlin
```

Esperado: `BUILD SUCCESSFUL`.

### Step 8: Verificar que no quedó ninguna llamada sin actualizar

```
git grep -n "showUpgradeDialog" -- app/src/main
```

Esperado: la declaración en `MainActivity` más **5** llamadas, y las 5 deben pasar `feature =`.
Si alguna sigue pasando solo `source`, el botón de anuncio nunca va a aparecer ahí.

### Step 9: Commit

```
git add app/src/main/java/com/example/ar
git commit -m "feat: entrar directo a la funcion despues de ganar la recompensa"
```
