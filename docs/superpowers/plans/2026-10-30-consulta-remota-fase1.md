# Consulta remota — Fase 1 — Plan de implementación

> **Para agentes:** SUB-SKILL REQUERIDA: usar superpowers:subagent-driven-development (recomendado)
> o superpowers:executing-plans para implementar este plan tarea por tarea. Los pasos usan
> casillas (`- [ ]`) para el seguimiento.

**Objetivo:** Que el usuario pueda marcar la casa de un cliente en el mapa, elegir un satélite, y
mandarle por WhatsApp un mensaje con el azimut verdadero, el magnético, la elevación y tres
indicaciones para que esa persona apunte la antena sola.

**Arquitectura:** Paquete nuevo `com.example.ar.consulta`. El cálculo (`RemoteQuery`) y el armado
del mensaje (`RemoteShare`) son **Kotlin puro, sin imports de Android**, y por eso se testean con
JUnit común. La UI es una hoja (`BottomSheetDialogFragment`) que recibe el punto por argumentos:
**no hay estado en el ViewModel**, así que el modo no puede quedar colgado entre pantallas.

**Stack:** Kotlin, ViewBinding, Material3 BottomSheet, `android.hardware.GeomagneticField`,
JUnit 4. Sin dependencias nuevas.

## Restricciones globales

- Compilar con `$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"` y `.\gradlew.bat`. Shell: PowerShell.
- **No modificar** `SatelliteCalculator.kt`, `OrientationManager.kt` ni `ProManager.kt`.
- **No tocar el modo normal.** Todo lo que funciona hoy tiene que seguir igual.
- El acceso a satélites de pago sigue la misma regla de siempre:
  `AccessManager.canUse(context, Feature.SATELLITE)`. La consulta remota **no** es una puerta
  trasera para los satélites Pro.
- Fase 1 es **solo satelital**. Nada de TDT, Yagi, WiFi, punto a punto ni enlace profundo.
- Strings nuevos en los **tres** idiomas: `values`, `values-en`, `values-pt`.

## Estructura de archivos

| Archivo | Responsabilidad |
|---|---|
| `consulta/ObserverPoint.kt` | Dónde está la antena del cliente |
| `consulta/RemoteResult.kt` | El resultado del cálculo |
| `consulta/RemoteQuery.kt` | **El cálculo. Puro** |
| `consulta/RemoteShare.kt` | **Arma el mensaje. Puro** |
| `consulta/RemoteSheet.kt` | La hoja: declinación, selector de satélite, compartir |
| `res/layout/sheet_consulta.xml` | La vista de la hoja |
| `ui/map/MapFragment.kt` | Suma el botón "Calcular para acá" |

**Por qué el cálculo no llama a `GeomagneticField`:** es una clase del framework de Android y no
existe en un test de JUnit. `RemoteQuery` **recibe la declinación como parámetro** y quien la
llama la calcula. Mismo patrón que `AccessRules` y `SunMoon`, que ya tienen 21 tests corriendo
sin dispositivo.

## Valores de referencia verificados

Estos números **no son inventados**: se calcularon con un método ECEF independiente (vectores en
marco ENU) y se contrastaron contra la fórmula cerrada de `SatelliteCalculator`, con coincidencia
exacta hasta el cuarto decimal en los cuatro casos. La elevación de Buenos Aires (49,69°) coincide
además con el 49,7° que muestra hoy el selector de satélites de la app.

| Lugar | lat, lon | Satélite | Azimut | Elevación | Visible |
|---|---|---|---|---|---|
| Buenos Aires | −34.6037, −58.3816 | −61.0 | 355.3960 | 49.6943 | sí |
| San Salvador de Jujuy | −24.1858, −65.2995 | −61.0 | 10.3984 | 61.2906 | sí |
| Ushuaia | −54.8019, −68.3030 | −61.0 | 8.9131 | 27.1342 | sí |
| Buenos Aires | −34.6037, −58.3816 | 150.0 | 223.5726 | −51.7705 | **no** |

---

### Task 1: El cálculo puro

**Archivos:**
- Crear: `app/src/main/java/com/example/ar/consulta/ObserverPoint.kt`
- Crear: `app/src/main/java/com/example/ar/consulta/RemoteResult.kt`
- Crear: `app/src/main/java/com/example/ar/consulta/RemoteQuery.kt`
- Test: `app/src/test/java/com/example/ar/consulta/RemoteQueryTest.kt`

**Interfaces:**
- Consume: `SatelliteCalculator.calculate(obsLat, obsLon, satLon, altitudeM): LookAngles`
  (campos `azimuthDeg`, `elevationDeg`, `visible`) y `Satellite` (`name`, `orbitalLon`,
  `positionLabel`). Las dos son clases puras, sin Android.
- Produce: `ObserverPoint(lat, lon, label)`, `RemoteResult(...)`,
  `RemoteQuery.satellite(observer, satellite, declinationDeg, altitudeM): RemoteResult`,
  `RemoteQuery.toMagnetic(trueAzimuthDeg, declinationDeg): Double`

- [ ] **Paso 1: Escribir el test que falla**

Crear `app/src/test/java/com/example/ar/consulta/RemoteQueryTest.kt`:

```kotlin
package com.example.ar.consulta

import com.example.ar.satellite.Satellite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteQueryTest {

    private val amazonas3 = Satellite("Amazonas 3", -61.0, "Sudamérica", "TV / Internet")
    private val satLejano = Satellite("Lejano", 150.0, "Pacífico", "TV")

    // ── La conversión verdadero → magnético ───────────────────────────────
    // Es la cuenta que más silenciosamente puede arruinar todo: si alguien
    // invierte el signo, el número sigue pareciendo razonable y la antena no
    // engancha nada. Convención tomada de OrientationManager, que hace
    // sensor + declinación = verdadero, o sea magnético = verdadero − declinación.

    @Test
    fun `magnetico resta la declinacion`() {
        assertEquals(90.0, RemoteQuery.toMagnetic(100.0, 10.0), 0.0001)
    }

    @Test
    fun `magnetico con declinacion negativa suma`() {
        // Declinación de Buenos Aires: -10.3°
        assertEquals(20.3, RemoteQuery.toMagnetic(10.0, -10.3), 0.0001)
    }

    @Test
    fun `magnetico da la vuelta al pasar de 360`() {
        // Caso real: Buenos Aires hacia Amazonas 3 da 355.396° verdadero.
        // Con declinación -10.3 el magnético se pasa de 360 y tiene que volver a 5.696,
        // no quedar en 365.696.
        assertEquals(5.696, RemoteQuery.toMagnetic(355.396, -10.3), 0.001)
    }

    @Test
    fun `magnetico nunca es negativo`() {
        assertEquals(355.0, RemoteQuery.toMagnetic(5.0, 10.0), 0.0001)
    }

    // ── Ángulos contra valores de referencia ──────────────────────────────
    // Calculados con un método ECEF independiente, no con esta misma fórmula.
    // Tres latitudes bien separadas: un error que crezca con la latitud no se
    // vería con un solo punto.

    @Test
    fun `Buenos Aires hacia Amazonas 3`() {
        val r = RemoteQuery.satellite(
            ObserverPoint(-34.6037, -58.3816, "Buenos Aires"), amazonas3, declinationDeg = 0.0
        )
        assertEquals(355.3960, r.azimuthTrueDeg, 0.001)
        assertEquals(49.6943, r.elevationDeg, 0.001)
        assertTrue(r.visible)
    }

    @Test
    fun `Jujuy hacia Amazonas 3`() {
        val r = RemoteQuery.satellite(
            ObserverPoint(-24.1858, -65.2995, "Jujuy"), amazonas3, declinationDeg = 0.0
        )
        assertEquals(10.3984, r.azimuthTrueDeg, 0.001)
        assertEquals(61.2906, r.elevationDeg, 0.001)
    }

    @Test
    fun `Ushuaia hacia Amazonas 3`() {
        val r = RemoteQuery.satellite(
            ObserverPoint(-54.8019, -68.3030, "Ushuaia"), amazonas3, declinationDeg = 0.0
        )
        assertEquals(8.9131, r.azimuthTrueDeg, 0.001)
        assertEquals(27.1342, r.elevationDeg, 0.001)
    }

    @Test
    fun `satelite del otro lado del mundo no es visible`() {
        val r = RemoteQuery.satellite(
            ObserverPoint(-34.6037, -58.3816, "Buenos Aires"), satLejano, declinationDeg = 0.0
        )
        assertFalse(r.visible)
        assertTrue("la elevación tiene que ser negativa", r.elevationDeg < 0.0)
    }

    // ── El resultado arrastra los datos de contexto ───────────────────────

    @Test
    fun `el resultado conserva etiquetas y declinacion`() {
        val r = RemoteQuery.satellite(
            ObserverPoint(-34.6037, -58.3816, "Av. San Martín 1842"), amazonas3,
            declinationDeg = -10.3
        )
        assertEquals("Av. San Martín 1842", r.observerLabel)
        assertEquals("Amazonas 3 (61.0°O)", r.targetLabel)
        assertEquals(-10.3, r.declinationDeg, 0.0001)
    }
}
```

- [ ] **Paso 2: Correr el test y verificar que falla**

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"; .\gradlew.bat testDebugUnitTest --tests "*RemoteQueryTest*"
```

Esperado: FALLA al compilar, con "Unresolved reference: RemoteQuery" / "ObserverPoint".

- [ ] **Paso 3: Escribir la implementación mínima**

Crear `app/src/main/java/com/example/ar/consulta/ObserverPoint.kt`:

```kotlin
package com.example.ar.consulta

/**
 * Dónde está la antena que hay que orientar. No es necesariamente donde está
 * el usuario: ese es justamente el punto de la consulta remota.
 */
data class ObserverPoint(
    val lat: Double,
    val lon: Double,
    val label: String
)
```

Crear `app/src/main/java/com/example/ar/consulta/RemoteResult.kt`:

```kotlin
package com.example.ar.consulta

/**
 * Resultado de una consulta remota, listo para mostrar o compartir.
 *
 * Trae los DOS azimuts a propósito: la persona que va a apuntar puede estar
 * usando una brújula de mano (magnético) o una app de celular (verdadero), y
 * mandarle solo uno garantiza que la mitad de las veces apunte mal.
 */
data class RemoteResult(
    val observerLabel: String,
    val targetLabel: String,
    val azimuthTrueDeg: Double,
    val azimuthMagneticDeg: Double,
    val elevationDeg: Double,
    val declinationDeg: Double,
    val visible: Boolean
)
```

Crear `app/src/main/java/com/example/ar/consulta/RemoteQuery.kt`:

```kotlin
package com.example.ar.consulta

import com.example.ar.satellite.Satellite
import com.example.ar.satellite.SatelliteCalculator

/**
 * Calcula la orientación de una antena que está en OTRO lugar.
 *
 * Sin imports de Android a propósito: así se testea con JUnit común, sin
 * emulador. Por eso la declinación magnética entra como parámetro en vez de
 * calcularse acá — GeomagneticField es del framework y no existe en un test.
 */
object RemoteQuery {

    /**
     * Pasa un azimut de norte verdadero a norte magnético.
     *
     * `OrientationManager` hace `sensor + declinación = verdadero`, así que la
     * vuelta es `verdadero − declinación`. El resultado se normaliza a 0–360:
     * sin eso, Buenos Aires hacia Amazonas 3 daría 365,7° en vez de 5,7°.
     */
    fun toMagnetic(trueAzimuthDeg: Double, declinationDeg: Double): Double =
        ((trueAzimuthDeg - declinationDeg) % 360.0 + 360.0) % 360.0

    fun satellite(
        observer: ObserverPoint,
        satellite: Satellite,
        declinationDeg: Double,
        altitudeM: Double = 0.0
    ): RemoteResult {
        val angles = SatelliteCalculator.calculate(
            obsLat = observer.lat,
            obsLon = observer.lon,
            satLon = satellite.orbitalLon,
            altitudeM = altitudeM
        )
        return RemoteResult(
            observerLabel      = observer.label,
            targetLabel        = "${satellite.name} (${satellite.positionLabel})",
            azimuthTrueDeg     = angles.azimuthDeg,
            azimuthMagneticDeg = toMagnetic(angles.azimuthDeg, declinationDeg),
            elevationDeg       = angles.elevationDeg,
            declinationDeg     = declinationDeg,
            visible            = angles.visible
        )
    }
}
```

- [ ] **Paso 4: Correr el test y verificar que pasa**

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"; .\gradlew.bat testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`. Los 9 tests nuevos pasan y los 21 que ya existían siguen pasando.

- [ ] **Paso 5: Commit**

```
git add app/src/main/java/com/example/ar/consulta app/src/test/java/com/example/ar/consulta
git commit -m "feat: calculo de orientacion para una antena en otro lugar"
```

---

### Task 2: El mensaje que se comparte

**Archivos:**
- Crear: `app/src/main/java/com/example/ar/consulta/RemoteShare.kt`
- Test: `app/src/test/java/com/example/ar/consulta/RemoteShareTest.kt`
- Modificar: `app/src/main/res/values/strings.xml`
- Modificar: `app/src/main/res/values-en/strings.xml`
- Modificar: `app/src/main/res/values-pt/strings.xml`

**Interfaces:**
- Consume (Task 1): `RemoteResult`
- Produce: `RemoteShare.Texts(...)` y `RemoteShare.build(result, texts, locale): String`

- [ ] **Paso 1: Escribir el test que falla**

Crear `app/src/test/java/com/example/ar/consulta/RemoteShareTest.kt`:

```kotlin
package com.example.ar.consulta

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RemoteShareTest {

    // Plantillas equivalentes a los strings de recursos. Se pasan literales para
    // que el test no dependa de Android ni del idioma del equipo.
    private val textos = RemoteShare.Texts(
        headerFmt      = "Orientación de antena — %s",
        targetFmt      = "Satélite: %s",
        azimuthFmt     = "Azimut: %s° (norte verdadero)",
        magneticFmt    = "Con brújula común: %s°",
        elevationFmt   = "Elevación: %s°",
        notVisibleFmt  = "Desde ese lugar no se ve %s.",
        tipsHeader     = "Antes de medir:",
        tipCalibration = "Calibrá la brújula.",
        tipMetal       = "Medí a 2-3 metros de la antena.",
        tipElevation   = "La elevación es la inclinación del plato."
    )

    private fun visible() = RemoteResult(
        observerLabel      = "Av. San Martín 1842",
        targetLabel        = "Amazonas 3 (61.0°O)",
        azimuthTrueDeg     = 355.3960,
        azimuthMagneticDeg = 5.6960,
        elevationDeg       = 49.6943,
        declinationDeg     = -10.3,
        visible            = true
    )

    private fun noVisible() = visible().copy(elevationDeg = -51.77, visible = false)

    @Test
    fun `incluye el lugar y el satelite`() {
        val msg = RemoteShare.build(visible(), textos, Locale.US)
        assertTrue(msg.contains("Av. San Martín 1842"))
        assertTrue(msg.contains("Amazonas 3 (61.0°O)"))
    }

    @Test
    fun `redondea el azimut a grados enteros`() {
        // Una brújula de mano no resuelve decimales: 355, no 355.396.
        val msg = RemoteShare.build(visible(), textos, Locale.US)
        assertTrue("falta el azimut verdadero", msg.contains("355°"))
        assertFalse("no debería traer decimales en el azimut", msg.contains("355.4"))
    }

    @Test
    fun `incluye el azimut magnetico`() {
        val msg = RemoteShare.build(visible(), textos, Locale.US)
        assertTrue("falta el magnético", msg.contains("6°"))
    }

    @Test
    fun `la elevacion lleva un decimal`() {
        val msg = RemoteShare.build(visible(), textos, Locale.US)
        assertTrue(msg.contains("49.7"))
    }

    @Test
    fun `incluye las tres indicaciones`() {
        val msg = RemoteShare.build(visible(), textos, Locale.US)
        assertTrue(msg.contains("Calibrá la brújula."))
        assertTrue(msg.contains("Medí a 2-3 metros de la antena."))
        assertTrue(msg.contains("La elevación es la inclinación del plato."))
    }

    @Test
    fun `si no es visible lo dice y no manda numeros`() {
        val msg = RemoteShare.build(noVisible(), textos, Locale.US)
        assertTrue(msg.contains("Desde ese lugar no se ve Amazonas 3 (61.0°O)."))
        assertFalse("no puede mostrar un azimut de un satélite que no se ve", msg.contains("355°"))
        assertFalse("no puede mostrar una elevación negativa", msg.contains("-51"))
    }
}
```

- [ ] **Paso 2: Correr el test y verificar que falla**

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"; .\gradlew.bat testDebugUnitTest --tests "*RemoteShareTest*"
```

Esperado: FALLA con "Unresolved reference: RemoteShare".

- [ ] **Paso 3: Escribir la implementación**

Crear `app/src/main/java/com/example/ar/consulta/RemoteShare.kt`:

```kotlin
package com.example.ar.consulta

import java.util.Locale

/**
 * Arma el mensaje que se le manda a la persona que va a apuntar la antena.
 *
 * Sin imports de Android: las plantillas entran como [Texts] y quien llama las
 * saca de los recursos. Así el formato se testea sin emulador y en un idioma fijo.
 *
 * El mensaje tiene que servirle a alguien que NO tiene la app instalada: es todo
 * lo que va a tener para trabajar.
 */
object RemoteShare {

    data class Texts(
        val headerFmt: String,
        val targetFmt: String,
        val azimuthFmt: String,
        val magneticFmt: String,
        val elevationFmt: String,
        val notVisibleFmt: String,
        val tipsHeader: String,
        val tipCalibration: String,
        val tipMetal: String,
        val tipElevation: String
    )

    fun build(
        result: RemoteResult,
        texts: Texts,
        locale: Locale = Locale.getDefault()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("📡 " + texts.headerFmt.format(locale, result.observerLabel))
        sb.appendLine()
        sb.appendLine(texts.targetFmt.format(locale, result.targetLabel))
        sb.appendLine()

        if (!result.visible) {
            // Un satélite bajo el horizonte no se agarra con ninguna antena. Mandar
            // igual un azimut sería mandar a alguien a perder la tarde en un techo.
            sb.appendLine(texts.notVisibleFmt.format(locale, result.targetLabel))
            return sb.toString().trimEnd()
        }

        // Azimut a grados enteros: una brújula de mano no resuelve decimales.
        sb.appendLine(texts.azimuthFmt.format(locale, "%.0f".format(locale, result.azimuthTrueDeg)))
        sb.appendLine(texts.magneticFmt.format(locale, "%.0f".format(locale, result.azimuthMagneticDeg)))
        sb.appendLine(texts.elevationFmt.format(locale, "%.1f".format(locale, result.elevationDeg)))
        sb.appendLine()
        sb.appendLine(texts.tipsHeader)
        sb.appendLine("• " + texts.tipCalibration)
        sb.appendLine("• " + texts.tipMetal)
        sb.appendLine("• " + texts.tipElevation)
        return sb.toString().trimEnd()
    }
}
```

- [ ] **Paso 4: Correr el test y verificar que pasa**

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"; .\gradlew.bat testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL`, 6 tests nuevos verdes, los 30 anteriores siguen pasando.

- [ ] **Paso 5: Agregar los strings en los tres idiomas**

En `app/src/main/res/values/strings.xml`, antes de `</resources>`:

```xml
    <!-- Consulta remota: orientar una antena que está en otro lugar -->
    <string name="consulta_cta">📡 Calcular para acá</string>
    <string name="consulta_title">Orientar antena a distancia</string>
    <string name="consulta_pick_satellite">Elegir satélite</string>
    <string name="consulta_share">Compartir</string>
    <string name="consulta_no_satellite">Elegí un satélite para ver la orientación</string>
    <string name="consulta_declination">Declinación en ese lugar: %1$s°</string>
    <string name="consulta_share_header">Orientación de antena — %1$s</string>
    <string name="consulta_share_target">Satélite: %1$s</string>
    <string name="consulta_share_azimuth">Azimut: %1$s° (norte verdadero)</string>
    <string name="consulta_share_magnetic">Con brújula común: %1$s°</string>
    <string name="consulta_share_elevation">Elevación: %1$s°</string>
    <string name="consulta_share_not_visible">Desde ese lugar no se ve %1$s. Hay que elegir otro satélite.</string>
    <string name="consulta_share_tips_header">Antes de medir:</string>
    <string name="consulta_share_tip_calibration">Calibrá la brújula: movela en forma de 8 durante 5-10 segundos.</string>
    <string name="consulta_share_tip_metal">Medí parado a 2-3 metros de la antena, NO apoyado en el caño: el metal desvía la brújula del celular.</string>
    <string name="consulta_share_tip_elevation">La elevación es la inclinación del plato hacia arriba.</string>
```

En `app/src/main/res/values-en/strings.xml`:

```xml
    <!-- Remote lookup: aim an antenna that is somewhere else -->
    <string name="consulta_cta">📡 Calculate for here</string>
    <string name="consulta_title">Aim an antenna remotely</string>
    <string name="consulta_pick_satellite">Choose satellite</string>
    <string name="consulta_share">Share</string>
    <string name="consulta_no_satellite">Choose a satellite to see the aiming angles</string>
    <string name="consulta_declination">Declination there: %1$s°</string>
    <string name="consulta_share_header">Antenna aiming — %1$s</string>
    <string name="consulta_share_target">Satellite: %1$s</string>
    <string name="consulta_share_azimuth">Azimuth: %1$s° (true north)</string>
    <string name="consulta_share_magnetic">With a regular compass: %1$s°</string>
    <string name="consulta_share_elevation">Elevation: %1$s°</string>
    <string name="consulta_share_not_visible">%1$s is below the horizon from there. Pick another satellite.</string>
    <string name="consulta_share_tips_header">Before you measure:</string>
    <string name="consulta_share_tip_calibration">Calibrate the compass: move the phone in a figure 8 for 5-10 seconds.</string>
    <string name="consulta_share_tip_metal">Stand 2-3 metres away from the antenna, NOT against the mast: metal throws the phone compass off.</string>
    <string name="consulta_share_tip_elevation">Elevation is how far the dish tilts upward.</string>
```

En `app/src/main/res/values-pt/strings.xml`:

```xml
    <!-- Consulta remota: orientar uma antena que está em outro lugar -->
    <string name="consulta_cta">📡 Calcular para cá</string>
    <string name="consulta_title">Orientar antena à distância</string>
    <string name="consulta_pick_satellite">Escolher satélite</string>
    <string name="consulta_share">Compartilhar</string>
    <string name="consulta_no_satellite">Escolha um satélite para ver a orientação</string>
    <string name="consulta_declination">Declinação nesse lugar: %1$s°</string>
    <string name="consulta_share_header">Orientação de antena — %1$s</string>
    <string name="consulta_share_target">Satélite: %1$s</string>
    <string name="consulta_share_azimuth">Azimute: %1$s° (norte verdadeiro)</string>
    <string name="consulta_share_magnetic">Com bússola comum: %1$s°</string>
    <string name="consulta_share_elevation">Elevação: %1$s°</string>
    <string name="consulta_share_not_visible">De lá não se vê %1$s. Escolha outro satélite.</string>
    <string name="consulta_share_tips_header">Antes de medir:</string>
    <string name="consulta_share_tip_calibration">Calibre a bússola: mova o celular em forma de 8 por 5-10 segundos.</string>
    <string name="consulta_share_tip_metal">Meça a 2-3 metros da antena, NÃO encostado no mastro: o metal desvia a bússola do celular.</string>
    <string name="consulta_share_tip_elevation">A elevação é a inclinação do prato para cima.</string>
```

- [ ] **Paso 6: Verificar que compila y commitear**

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"; .\gradlew.bat compileDebugKotlin compileReleaseKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL` en las tres.

```
git add app/src/main/java/com/example/ar/consulta app/src/test/java/com/example/ar/consulta app/src/main/res
git commit -m "feat: mensaje compartible con los angulos y las indicaciones de medicion"
```

---

### Task 3: La hoja de consulta

**Archivos:**
- Crear: `app/src/main/res/layout/sheet_consulta.xml`
- Crear: `app/src/main/java/com/example/ar/consulta/RemoteSheet.kt`

**Interfaces:**
- Consume (Tasks 1 y 2): `ObserverPoint`, `RemoteResult`, `RemoteQuery.satellite(...)`,
  `RemoteShare.Texts`, `RemoteShare.build(...)`
- Consume (ya existe): `SatellitePickerDialog(location: Location?, onSelected: (Satellite) -> Unit)`
- Produce: `RemoteSheet.newInstance(lat: Double, lon: Double, label: String): RemoteSheet`

- [ ] **Paso 1: Crear el layout**

Crear `app/src/main/res/layout/sheet_consulta.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:background="@color/bg_deep">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="20dp">

        <TextView
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/consulta_title"
            android:textColor="@color/accent_cyan"
            android:textSize="18sp"
            android:textStyle="bold"/>

        <TextView
            android:id="@+id/tvConsultaPlace"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/text_primary"
            android:textSize="14sp"
            android:layout_marginTop="4dp"/>

        <Button
            android:id="@+id/btnConsultaSatellite"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:minHeight="48dp"
            android:insetTop="0dp"
            android:insetBottom="0dp"
            android:paddingTop="8dp"
            android:paddingBottom="8dp"
            android:text="@string/consulta_pick_satellite"
            android:textSize="14sp"
            android:layout_marginTop="16dp"/>

        <TextView
            android:id="@+id/tvConsultaResult"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="@string/consulta_no_satellite"
            android:textColor="@color/text_primary"
            android:textSize="15sp"
            android:lineSpacingMultiplier="1.2"
            android:layout_marginTop="16dp"/>

        <TextView
            android:id="@+id/tvConsultaDeclination"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:textColor="@color/text_secondary"
            android:textSize="12sp"
            android:layout_marginTop="8dp"/>

        <Button
            android:id="@+id/btnConsultaShare"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:minHeight="52dp"
            android:insetTop="0dp"
            android:insetBottom="0dp"
            android:paddingTop="8dp"
            android:paddingBottom="8dp"
            android:text="@string/consulta_share"
            android:textSize="15sp"
            android:textStyle="bold"
            android:textColor="@color/bg_deep"
            android:backgroundTint="@color/accent_cyan"
            android:layout_marginTop="16dp"
            android:visibility="gone"/>

    </LinearLayout>
</ScrollView>
```

> Los botones van con `wrap_content` + `minHeight` y los insets de `MaterialButton` anulados.
> Es la misma corrección que hubo que hacer en `dialog_upgrade.xml`: el equipo de prueba tiene
> `font_scale 1.3` y con alto fijo el texto se recorta.

- [ ] **Paso 2: Escribir la hoja**

Crear `app/src/main/java/com/example/ar/consulta/RemoteSheet.kt`:

```kotlin
package com.example.ar.consulta

import android.content.Intent
import android.hardware.GeomagneticField
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.example.ar.R
import com.example.ar.satellite.Satellite
import com.example.ar.satellite.SatellitePickerDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

/**
 * Hoja de consulta remota: calcula la orientación de una antena que está en el
 * lugar recibido por argumentos, y arma el mensaje para mandarle a quien la va
 * a apuntar.
 *
 * No guarda nada en el ViewModel a propósito. El "modo consulta" es exactamente
 * el tiempo que esta hoja está abierta: al cerrarla no queda estado dando vueltas
 * que pueda desincronizarse con la brújula o el AR, que siguen usando el GPS real.
 */
class RemoteSheet : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_LAT = "lat"
        private const val ARG_LON = "lon"
        private const val ARG_LABEL = "label"

        fun newInstance(lat: Double, lon: Double, label: String) = RemoteSheet().apply {
            arguments = Bundle().apply {
                putDouble(ARG_LAT, lat)
                putDouble(ARG_LON, lon)
                putString(ARG_LABEL, label)
            }
        }
    }

    private lateinit var observer: ObserverPoint
    private var declinationDeg: Double = 0.0
    private var result: RemoteResult? = null

    private var tvResult: TextView? = null
    private var btnShare: Button? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.sheet_consulta, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        observer = ObserverPoint(
            lat = args.getDouble(ARG_LAT),
            lon = args.getDouble(ARG_LON),
            label = args.getString(ARG_LABEL) ?: ""
        )

        // La declinación es la DEL LUGAR DEL CLIENTE, no la nuestra. Es justamente
        // lo que hace que el azimut magnético le sirva a él y no a nosotros.
        declinationDeg = GeomagneticField(
            observer.lat.toFloat(),
            observer.lon.toFloat(),
            0f,
            System.currentTimeMillis()
        ).declination.toDouble()

        view.findViewById<TextView>(R.id.tvConsultaPlace).text = observer.label
        view.findViewById<TextView>(R.id.tvConsultaDeclination).text =
            getString(R.string.consulta_declination, "%.1f".format(declinationDeg))

        tvResult = view.findViewById(R.id.tvConsultaResult)
        btnShare = view.findViewById(R.id.btnConsultaShare)

        view.findViewById<Button>(R.id.btnConsultaSatellite).setOnClickListener {
            // Se le pasa la ubicación del CLIENTE para que el listado muestre los
            // ángulos de esa casa. El control de acceso a satélites Pro queda como
            // está: SatellitePickerDialog ya consulta AccessManager por su cuenta.
            val loc = Location("consulta").apply {
                latitude = observer.lat
                longitude = observer.lon
            }
            SatellitePickerDialog(loc) { satellite -> onSatelliteChosen(satellite) }
                .show(parentFragmentManager, "consulta_sat")
        }

        btnShare?.setOnClickListener { shareResult() }
    }

    private fun onSatelliteChosen(satellite: Satellite) {
        if (!isAdded) return
        val r = RemoteQuery.satellite(observer, satellite, declinationDeg)
        result = r
        tvResult?.text = RemoteShare.build(r, shareTexts())
        btnShare?.visibility = View.VISIBLE
    }

    private fun shareResult() {
        val r = result ?: return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, RemoteShare.build(r, shareTexts()))
        }
        startActivity(Intent.createChooser(intent, getString(R.string.consulta_share)))
    }

    private fun shareTexts() = RemoteShare.Texts(
        headerFmt      = getString(R.string.consulta_share_header),
        targetFmt      = getString(R.string.consulta_share_target),
        azimuthFmt     = getString(R.string.consulta_share_azimuth),
        magneticFmt    = getString(R.string.consulta_share_magnetic),
        elevationFmt   = getString(R.string.consulta_share_elevation),
        notVisibleFmt  = getString(R.string.consulta_share_not_visible),
        tipsHeader     = getString(R.string.consulta_share_tips_header),
        tipCalibration = getString(R.string.consulta_share_tip_calibration),
        tipMetal       = getString(R.string.consulta_share_tip_metal),
        tipElevation   = getString(R.string.consulta_share_tip_elevation)
    )

    override fun onDestroyView() {
        super.onDestroyView()
        tvResult = null
        btnShare = null
    }
}
```

> La hoja muestra en pantalla **el mismo texto que se va a compartir**. Así el usuario ve
> exactamente lo que le va a llegar al cliente, en vez de una versión distinta de los datos.

- [ ] **Paso 3: Verificar que compila**

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"; .\gradlew.bat compileDebugKotlin compileReleaseKotlin
```

Esperado: `BUILD SUCCESSFUL` en las dos.

Si `SatellitePickerDialog` resulta tener otra firma, corregir la llamada según la real —
el contrato que importa es que reciba una `Location?` y devuelva el `Satellite` elegido.

- [ ] **Paso 4: Commit**

```
git add app/src/main/java/com/example/ar/consulta app/src/main/res/layout
git commit -m "feat: hoja de consulta remota con los angulos y el boton de compartir"
```

---

### Task 4: La entrada desde el mapa

**Archivos:**
- Modificar: `app/src/main/res/layout/fragment_map.xml`
- Modificar: `app/src/main/java/com/example/ar/ui/map/MapFragment.kt`

**Interfaces:**
- Consume (Task 3): `RemoteSheet.newInstance(lat, lon, label)`
- Consume (ya existe): `sharedViewModel.target` de tipo `MutableLiveData<TargetPoint?>`,
  donde `TargetPoint(lat, lon, name)`

**Contexto:** `performSearch()` (línea ~387) y el `setOnMapClickListener` (línea ~465) **ya
escriben `sharedViewModel.target.value`** con el punto elegido. No hace falta UI nueva para
elegir el lugar: alcanza con colgarse de ese LiveData.

- [ ] **Paso 1: Agregar el botón al layout**

En `app/src/main/res/layout/fragment_map.xml`, agregar dentro del contenedor del panel de
info inferior (junto a los TextView de Rumbo/Distancia/Objetivo), al final:

```xml
    <Button
        android:id="@+id/btnConsultaRemota"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:minHeight="48dp"
        android:insetTop="0dp"
        android:insetBottom="0dp"
        android:paddingTop="8dp"
        android:paddingBottom="8dp"
        android:text="@string/consulta_cta"
        android:textSize="14sp"
        android:textColor="@color/accent_cyan"
        android:backgroundTint="@android:color/transparent"
        android:layout_marginTop="8dp"
        android:visibility="gone"/>
```

Si el panel usa `ConstraintLayout`, agregar además las restricciones necesarias para anclarlo
debajo de la fila de datos existente.

- [ ] **Paso 2: Cablear el botón**

En `MapFragment.kt`, dentro de `onViewCreated`, después de donde ya se observan los LiveData,
agregar:

```kotlin
        val btnConsulta = view.findViewById<Button>(R.id.btnConsultaRemota)

        // El botón aparece solo cuando hay un lugar elegido. performSearch() y el
        // click en el mapa ya escriben sharedViewModel.target, así que no hace
        // falta ninguna forma nueva de elegir el punto.
        sharedViewModel.target.observe(viewLifecycleOwner) { target ->
            btnConsulta.visibility = if (target == null) View.GONE else View.VISIBLE
        }

        btnConsulta.setOnClickListener {
            val target = sharedViewModel.target.value ?: return@setOnClickListener
            RemoteSheet.newInstance(target.lat, target.lon, target.name)
                .show(parentFragmentManager, "consulta")
        }
```

Agregar los imports que falten:

```kotlin
import android.widget.Button
import com.example.ar.consulta.RemoteSheet
```

- [ ] **Paso 3: Verificar que compila y que los tests siguen pasando**

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"; .\gradlew.bat compileDebugKotlin compileReleaseKotlin testDebugUnitTest
```

Esperado: `BUILD SUCCESSFUL` en las tres, 36 tests verdes.

- [ ] **Paso 4: Commit**

```
git add app/src/main/java/com/example/ar/ui/map app/src/main/res/layout
git commit -m "feat: boton Calcular para aca en el mapa"
```

---

### Task 5: Verificación en el teléfono

**Archivos:** ninguno. Es la prueba manual, y **es la verificación más importante del plan**:
los tests confirman que la cuenta es la que decidimos, no que la antena enganche.

- [ ] **Paso 1: Instalar la versión de desarrollo**

```
$env:JAVA_HOME="C:\jdk17\jdk-17.0.14+7"; .\gradlew.bat installDebug
```

Se instala como `com.quantixlabs.synapsear.debug` ("Antena AR (dev)") y **no pisa la app de
Play**. Con dos dispositivos conectados, usar `adb -s ZY32LK2337`.

- [ ] **Paso 2: La comparación contra la propia app — paso obligatorio**

1. Abrir "Antena AR (dev)" → pestaña Mapa.
2. Buscar **la dirección donde estás parado ahora**.
3. Tocar "📡 Calcular para acá" y elegir un satélite cualquiera.
4. Anotar el **azimut verdadero** y la **elevación**.
5. Cerrar la hoja, ir a Brújula y elegir **el mismo satélite** en modo normal.
6. Comparar.

**Los dos azimuts verdaderos tienen que dar iguales**, con una diferencia menor a 0,1°.

Si coinciden, queda probado que el camino nuevo pasa bien los parámetros, porque el azimut del
modo normal ya está validado contra la realidad: es el que se usa para apuntar antenas que
efectivamente enganchan. Si NO coinciden, hay un error de cableado y no se publica.

- [ ] **Paso 3: Probar el caso del satélite que no se ve**

Buscar una dirección en Europa (por ejemplo "Madrid") y elegir un satélite sudamericano.

Esperado: la hoja dice que desde ese lugar no se ve ese satélite, y **no muestra ningún azimut
ni una elevación negativa**. El botón de compartir, si se usa, manda ese mismo aviso.

- [ ] **Paso 4: Probar el compartir de punta a punta**

Tocar "Compartir" y mandarse el mensaje a uno mismo por WhatsApp. Verificar que llega completo:
lugar, satélite, los dos azimuts, la elevación y las tres indicaciones.

- [ ] **Paso 5: Verificar que no se rompió nada de lo que ya andaba**

- La brújula sigue apuntando con **tu** ubicación, no con la del cliente.
- El AR sigue funcionando igual.
- Cerrar la hoja y navegar entre pestañas no deja nada raro.
- El banner de anuncios sigue apareciendo para un usuario gratis.

- [ ] **Paso 6: Anotar el resultado**

Escribir en `.superpowers/sdd/progress.md` qué se probó y qué dio, incluidos los números
concretos de la comparación del Paso 2.

---

## Lo que queda para la Fase 2

No se implementa ahora, y está en el spec: TDT con los repetidores del lugar del cliente;
Yagi, WiFi Direccional y Punto a Punto con el segundo punto en el mapa; y el enlace profundo
con su página web de respaldo.
