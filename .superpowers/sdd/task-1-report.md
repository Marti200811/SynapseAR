# Task 1 Report: Habilitar los tests unitarios

## Archivos modificados

1. **gradle/libs.versions.toml**
   - Agregada versión de JUnit 4.13.2 en la sección `[versions]`
   - Agregada librería junit en la sección `[libraries]`

2. **app/build.gradle.kts**
   - Agregada dependencia `testImplementation(libs.junit)` en el bloque `dependencies`

3. **app/src/test/java/com/example/synapsear/ExampleUnitTest.kt**
   - Borrado (test de ejemplo en paquete equivocado)

4. **app/src/test/java/com/example/ar/SanityTest.kt**
   - Creado (test de sanidad en paquete correcto `com.example.ar`)

## Comando ejecutado

```powershell
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"
.\gradlew.bat testDebugUnitTest
```

## Salida del test (líneas relevantes)

```
> Task :app:compileDebugUnitTestKotlin
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 54s
31 actionable tasks: 31 executed
```

## Commit

Hash: `cd53324`

Mensaje:
```
chore: habilitar tests unitarios (faltaba la dependencia de JUnit)
```

## Notas

- La tarea completó exitosamente sin errores ni advertencias de compilación
- El test `SanityTest::la infraestructura de tests funciona` pasó correctamente
- El archivo de test antiguo en `com.example.synapsear` fue eliminado junto con su directorio vacío
- Las advertencias de CRLF/LF en Windows son normales y no afectan la funcionalidad
