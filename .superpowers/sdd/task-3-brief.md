# Task 3: `UnlockStore` y `AccessManager`

La persistencia de los desbloqueos y la fachada que van a usar todos los call sites.

## Global Constraints (aplican a esta tarea)

- Paquete base: `com.example.ar`; el código nuevo va en `com.example.ar.access`.
- `ProManager.kt` NO se modifica (solo se lo consulta).
- El archivo de SharedPreferences es `"synapse_prefs"` — el mismo que ya usan `ProManager` y
  `SettingsManager`. No crear uno nuevo.
- Compilar con `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"`.
- Shell: PowerShell en Windows. Usar `.\gradlew.bat`.

## Files

- Create: `app/src/main/java/com/example/ar/access/UnlockStore.kt`
- Create: `app/src/main/java/com/example/ar/access/AccessManager.kt`

## Interfaces

- Consumes (ya existen, creados en la Task 2):
  - `enum class Feature { AR, COMPASS_THEMES, WIFI_SCANNER, TDT_PICKER, SATELLITE }`
  - `AccessRules.canUse(isPro: Boolean, expiryMillis: Long, nowMillis: Long): Boolean`
  - `AccessRules.remaining(expiryMillis: Long, nowMillis: Long): Long`
  - `AccessRules.UNLOCK_DURATION_MS: Long`
- Consumes (ya existe): `ProManager.isPro(context: Context): Boolean` en `com.example.ar`
- Produces (las tareas 4, 6 y 7 dependen de estas firmas exactas):
  - `AccessManager.canUse(context: Context, feature: Feature): Boolean`
  - `AccessManager.grantTemporary(context: Context, feature: Feature)`
  - `AccessManager.remainingMillis(context: Context, feature: Feature): Long`

## Steps

### Step 1: Crear `UnlockStore`

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

Que la clave se derive de `feature.name` es lo que garantiza que cada función tenga su propio
desbloqueo independiente. No la reemplaces por un índice ni por un string fijo.

### Step 2: Crear `AccessManager`

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

### Step 3: Compilar y correr los tests existentes

```
.\gradlew.bat compileDebugKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`. Los 8 tests de `AccessRulesTest` siguen pasando.

No se agregan tests nuevos en esta tarea: `UnlockStore` y `AccessManager` son cableado sobre
`SharedPreferences` y el reloj del sistema, y testearlos exigiría Robolectric o un emulador.
Toda la lógica que vale la pena testear ya vive en `AccessRules`, cubierta en la Task 2. Esto
es deliberado — no agregues Robolectric ni mocks.

### Step 4: Commit

```
git add app/src/main/java/com/example/ar/access
git commit -m "feat: agregar UnlockStore y la fachada AccessManager"
```
