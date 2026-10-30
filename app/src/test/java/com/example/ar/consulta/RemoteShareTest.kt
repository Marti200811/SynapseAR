package com.example.ar.consulta

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
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

    // Los números se formatean con el locale de FORMAT: lo fijamos igual que
    // RemoteQueryTest para que el resultado no dependa de la PC.
    private lateinit var formatLocaleAntes: Locale

    @Before
    fun fijarLocaleDeFormato() {
        formatLocaleAntes = Locale.getDefault(Locale.Category.FORMAT)
        Locale.setDefault(Locale.Category.FORMAT, Locale.US)
    }

    @After
    fun restaurarLocaleDeFormato() {
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
        assertFalse("no puede mostrar el azimut magnético", msg.contains("6°"))
        assertFalse("no puede mostrar el rótulo del magnético", msg.contains("Con brújula"))
        assertFalse("no puede mostrar una elevación negativa", msg.contains("-51"))
    }
}
