package com.example.ar.consulta

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class RemoteShareTest {

    // Plantillas simplificadas a propósito; NO son los strings reales (ver
    // RemoteShareRecursosTest, que lee los strings.xml de verdad). Sirven para probar
    // la lógica de armado (orden, redondeo, separador) sin depender del texto final.
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

    // build() sin locale explícito toma el de Locale.Category.FORMAT (el del teléfono).
    // Lo fijamos en US, igual que RemoteQueryTest, para que los tests que no pasan
    // locale no dependan de la PC; el test de es-AR lo cambia a propósito.
    private lateinit var localeAntes: Locale
    private lateinit var formatLocaleAntes: Locale

    @Before
    fun fijarLocaleDeFormato() {
        localeAntes = Locale.getDefault()
        formatLocaleAntes = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
    }

    @After
    fun restaurarLocaleDeFormato() {
        // setDefault(Locale) pisa todas las categorías: primero el general, después FORMAT.
        Locale.setDefault(localeAntes)
        Locale.setDefault(Locale.Category.FORMAT, formatLocaleAntes)
    }

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
        // Se asertan líneas COMPLETAS, no subcadenas: con `contains("355°")` un
        // mensaje que intercambia verdadero y magnético sigue pareciendo correcto
        // (las dos cifras aparecen igual) y el cliente apunta 10° afuera.
        val msg = RemoteShare.build(visible(), textos, Locale.US)
        assertTrue("falta el azimut verdadero (línea completa)",
            msg.lines().contains("Azimut: 355° (norte verdadero)"))
        assertFalse("no debería traer decimales en el azimut", msg.contains("355.4"))
    }

    @Test
    fun `incluye el azimut magnetico`() {
        val msg = RemoteShare.build(visible(), textos, Locale.US)
        assertTrue("falta el magnético (línea completa)",
            msg.lines().contains("Con brújula común: 6°"))
    }

    // ── Separador decimal ─────────────────────────────────────────────────
    // Decisión del proyecto: el separador es el del teléfono, para que el mensaje
    // diga lo mismo que la pantalla ("61,0°O" en un teléfono en español). Si alguien
    // cablea Locale.US adentro de build(), se ponen rojos DOS de estos tres tests: el de
    // es-AR y el de "sin locale explícito". El de Locale.US es el control: seguiría verde,
    // porque US es justamente lo que espera.

    private val esAR: Locale = Locale.forLanguageTag("es-AR")

    @Test
    fun `con locale es-AR la elevacion lleva coma`() {
        val msg = RemoteShare.build(visible(), textos, esAR)
        assertTrue("falta la elevación con coma", msg.lines().contains("Elevación: 49,7°"))
    }

    @Test
    fun `con locale US la elevacion lleva punto`() {
        val msg = RemoteShare.build(visible(), textos, Locale.US)
        assertTrue("falta la elevación con punto", msg.lines().contains("Elevación: 49.7°"))
    }

    @Test
    fun `sin locale explicito usa el de formato del telefono`() {
        // El locale general queda en US y SOLO FORMAT en es-AR: así el test distingue
        // getDefault(FORMAT) de getDefault() sin importar el idioma de la PC.
        // Nota: setDefault(Locale) pisa todas las categorías, por eso va primero.
        Locale.setDefault(Locale.US)
        Locale.setDefault(Locale.Category.FORMAT, esAR)
        val msg = RemoteShare.build(visible(), textos)
        assertTrue("el default tiene que ser el de FORMAT: coma en es-AR",
            msg.lines().contains("Elevación: 49,7°"))
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
        assertFalse("no puede mostrar el azimut magnético", msg.contains("6°"))
        assertFalse("no puede mostrar el rótulo del magnético", msg.contains("Con brújula"))
        assertFalse("no puede mostrar una elevación negativa", msg.contains("-51"))
    }
}
