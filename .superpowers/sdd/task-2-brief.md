# Task 2: `Feature` y `AccessRules` (lógica pura)

El corazón de la decisión de acceso, sin nada de Android para que se pueda testear de verdad
con JUnit común (sin Robolectric, sin mocks, sin emulador).

## Global Constraints (aplican a esta tarea)

- Paquete base: `com.example.ar`.
- `ProManager.kt` NO se modifica.
- Duración del desbloqueo: **30 minutos** = `30L * 60L * 1000L` ms.
- Compilar con `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"` antes de cualquier comando de Gradle.
- Shell: PowerShell en Windows. Usar `.\gradlew.bat`, no `./gradlew`.

## Files

- Create: `app/src/main/java/com/example/ar/access/Feature.kt`
- Create: `app/src/main/java/com/example/ar/access/AccessRules.kt`
- Test: `app/src/test/java/com/example/ar/access/AccessRulesTest.kt`
- Delete: `app/src/test/java/com/example/ar/SanityTest.kt`

> `SanityTest` existe solo para probar, en la Task 1, que la infraestructura de tests quedó
> funcionando. No prueba nada del producto, así que se borra en esta tarea, una vez que los 8
> tests de `AccessRulesTest` cumplen ese rol. Decisión ya tomada con el usuario — no hace falta
> proponer alternativas ni conservarlo.

## Interfaces

- Consumes: nada
- Produces:
  - `enum class Feature { AR, COMPASS_THEMES, WIFI_SCANNER, TDT_PICKER, SATELLITE }`
  - `object AccessRules` con:
    - `const val UNLOCK_DURATION_MS: Long`
    - `fun canUse(isPro: Boolean, expiryMillis: Long, nowMillis: Long): Boolean`
    - `fun remaining(expiryMillis: Long, nowMillis: Long): Long`

## Steps

### Step 1: Escribir los tests que fallan

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

### Step 2: Correr los tests y verificar que fallan

```
.\gradlew.bat testDebugUnitTest --tests "com.example.ar.access.AccessRulesTest"
```

Esperado: FALLA a compilar con `Unresolved reference 'AccessRules'`.

Este paso importa: confirma que los tests prueban algo real. Si pasaran sin la implementación,
estarían mal escritos.

### Step 3: Crear el enum `Feature`

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

### Step 4: Implementar `AccessRules`

Crear `app/src/main/java/com/example/ar/access/AccessRules.kt`:

```kotlin
package com.example.ar.access

/**
 * Reglas de acceso, sin dependencias de Android para poder testearlas.
 *
 * Toda la decisión de "¿puede usar esto?" vive acá. La fachada AccessManager
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

### Step 5: Correr los tests y verificar que pasan

```
.\gradlew.bat testDebugUnitTest --tests "com.example.ar.access.AccessRulesTest"
```

Esperado: `BUILD SUCCESSFUL`, 8 tests pasan.

### Step 6: Borrar el test de sanidad

Ya cumplió su función: la infraestructura quedó probada y ahora hay 8 tests reales.

Borrar `app/src/test/java/com/example/ar/SanityTest.kt` y correr toda la suite:

```
.\gradlew.bat testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`, los 8 tests de `AccessRulesTest` siguen pasando.

### Step 7: Commit

```
git add app/src/main/java/com/example/ar/access app/src/test/java/com/example/ar
git commit -m "feat: agregar Feature y AccessRules, la logica pura de acceso"
```
