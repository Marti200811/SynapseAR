# Task 1: Habilitar los tests unitarios

Hoy `gradlew testDebugUnitTest` **falla a compilar**: no hay ninguna dependencia de test
declarada y el test de ejemplo vive en un paquete equivocado. Sin esto, ninguna tarea
posterior puede hacer TDD.

## Global Constraints (aplican a esta tarea)

- Paquete base: `com.example.ar` (NO `com.example.synapsear`, que es un resto de scaffolding).
- `applicationId` es `com.quantixlabs.synapsear` y `namespace` es `com.example.ar` — esa
  diferencia es intencional, no "arreglarla".
- `ProManager.kt` NO se modifica.
- Compilar con `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"` antes de cualquier comando de Gradle.
- Shell: PowerShell en Windows. Usar `.\gradlew.bat`, no `./gradlew`.

## Files

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Delete: `app/src/test/java/com/example/synapsear/ExampleUnitTest.kt`
- Create: `app/src/test/java/com/example/ar/SanityTest.kt`

## Interfaces

- Consumes: nada
- Produces: `gradlew testDebugUnitTest` funcionando; alias `libs.junit` disponible en el catálogo

## Steps

### Step 1: Agregar la versión de JUnit al catálogo

En `gradle/libs.versions.toml`, dentro de `[versions]`, después de la línea
`firebaseCrashlytics = "3.0.2"`:

```toml
junit = "4.13.2"
```

Y dentro de `[libraries]`, después de la línea de `billing`:

```toml
junit                            = { module = "junit:junit", version.ref = "junit" }
```

### Step 2: Declarar la dependencia de test

En `app/build.gradle.kts`, dentro del bloque `dependencies { ... }`, al final (después de las
líneas de `app-update-ktx`):

```kotlin
    // Tests unitarios locales (corren en la JVM, sin dispositivo)
    testImplementation(libs.junit)
```

### Step 3: Borrar el test de ejemplo del paquete equivocado

Borrar el archivo `app/src/test/java/com/example/synapsear/ExampleUnitTest.kt` y, si queda
vacía, la carpeta `app/src/test/java/com/example/synapsear`.

### Step 4: Crear un test de sanidad en el paquete correcto

Crear `app/src/test/java/com/example/ar/SanityTest.kt`:

```kotlin
package com.example.ar

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica que la infraestructura de tests unitarios compila y corre.
 * Se borra en la Task 2, cuando existan los tests reales de AccessRules.
 */
class SanityTest {
    @Test
    fun `la infraestructura de tests funciona`() {
        assertTrue(true)
    }
}
```

**Nota:** sí, este test no prueba nada del producto. Es deliberado y temporal — su única
función es demostrar que JUnit quedó bien cableado. La Task 2 lo elimina. Decisión ya tomada
con el usuario; no hace falta proponer alternativas.

### Step 5: Correr los tests y verificar que pasan

```
.\gradlew.bat testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`, sin errores `Unresolved reference 'junit'`.

### Step 6: Commit

```
git add gradle/libs.versions.toml app/build.gradle.kts app/src/test
git commit -m "chore: habilitar tests unitarios (faltaba la dependencia de JUnit)"
```
