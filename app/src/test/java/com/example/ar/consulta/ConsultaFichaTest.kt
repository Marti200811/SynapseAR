package com.example.ar.consulta

import com.example.ar.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Lo que de la hoja de consulta se puede probar sin Android: cuándo se ofrece
 * compartir, cómo se escribe la declinación y que cada campo de [RemoteShare.Texts]
 * salga del string que corresponde.
 *
 * Inflar el layout y lanzar el intent de compartir NO se prueban acá, y no con mocks:
 * un Context falso solo demostraría que se llamó al método. Eso se mira en el teléfono.
 */
class ConsultaFichaTest {

    private val esAR = Locale.forLanguageTag("es-AR")
    private val ptBR = Locale.forLanguageTag("pt-BR")

    private fun resultado(visible: Boolean, elevacion: Double) = RemoteResult(
        observerLabel      = "Av. San Martín 1842",
        targetLabel        = "Amazonas 3 (61.0°O)",
        azimuthTrueDeg     = 355.4,
        azimuthMagneticDeg = 5.7,
        elevationDeg       = elevacion,
        declinationDeg     = -10.3,
        visible            = visible
    )

    // ── Cuándo se ofrece compartir ────────────────────────────────────────────

    @Test
    fun `sin satelite elegido no hay nada que compartir`() {
        assertFalse(ConsultaFicha.canShare(null))
    }

    @Test
    fun `un satelite que se ve desde esa casa se puede compartir`() {
        assertTrue(ConsultaFicha.canShare(resultado(visible = true, elevacion = 49.7)))
    }

    @Test
    fun `un satelite bajo el horizonte no se comparte`() {
        // El aviso de ese caso dice "hay que elegir otro satélite": va dirigido al
        // instalador. Mandárselo al cliente no le sirve de nada.
        assertFalse(ConsultaFicha.canShare(resultado(visible = false, elevacion = -51.8)))
    }

    // ── Declinación ───────────────────────────────────────────────────────────

    @Test
    fun `la declinacion usa el separador decimal del idioma`() {
        assertEquals("-10,3", ConsultaFicha.declinationText(-10.3, esAR))
        assertEquals("-10,3", ConsultaFicha.declinationText(-10.3, ptBR))
        assertEquals("-10.3", ConsultaFicha.declinationText(-10.3, Locale.US))
    }

    @Test
    fun `la declinacion lleva un decimal`() {
        assertEquals("-10,3", ConsultaFicha.declinationText(-10.34, esAR))
        assertEquals("-10,4", ConsultaFicha.declinationText(-10.36, esAR))
        assertEquals("3,0", ConsultaFicha.declinationText(3.0, esAR))
    }

    @Test
    fun `una declinacion que redondea a cero no se muestra como menos cero`() {
        // "%.1f" de -0.04 da "-0.0", que parece un error de la pantalla.
        assertEquals("0,0", ConsultaFicha.declinationText(-0.04, esAR))
        assertEquals("0,0", ConsultaFicha.declinationText(0.04, esAR))
    }

    @Test
    fun `sin locale explicito la declinacion sigue al formato del telefono`() {
        // Es la misma regla que RemoteShare.build y la que no cumple el precedente de
        // CompassFragment (Locale.US): sin esto la ficha mostraría "-10.3°" al lado de
        // una elevación "49,7°".
        val anterior = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.Category.FORMAT, esAR)
            assertEquals("-10,3", ConsultaFicha.declinationText(-10.3))
            Locale.setDefault(Locale.Category.FORMAT, Locale.US)
            assertEquals("-10.3", ConsultaFicha.declinationText(-10.3))
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, anterior)
        }
    }

    // ── Los textos del mensaje ────────────────────────────────────────────────

    /**
     * El resolvedor devuelve el NOMBRE del string en vez de su texto, así se ve a
     * cuál fue cada campo. Importa porque azimut verdadero y magnético tienen la
     * misma forma (`%1$s`): si se cruzan compila, no falla ningún otro test y el
     * mensaje le dice al cliente cuál brújula usar al revés.
     */
    private fun nombrePorId(): Map<Int, String> =
        R.string::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }

    @Test
    fun `cada campo de Texts sale del string que le corresponde`() {
        val nombres = nombrePorId()
        val t = ConsultaFicha.shareTexts { id -> nombres.getValue(id) }

        assertEquals("consulta_share_header", t.headerFmt)
        assertEquals("consulta_share_target", t.targetFmt)
        assertEquals("consulta_share_azimuth", t.azimuthFmt)
        assertEquals("consulta_share_magnetic", t.magneticFmt)
        assertEquals("consulta_share_elevation", t.elevationFmt)
        assertEquals("consulta_share_not_visible", t.notVisibleFmt)
        assertEquals("consulta_share_tips_header", t.tipsHeader)
        assertEquals("consulta_share_tip_calibration", t.tipCalibration)
        assertEquals("consulta_share_tip_metal", t.tipMetal)
        assertEquals("consulta_share_tip_elevation", t.tipElevation)
    }

    @Test
    fun `los textos llegan crudos, con los marcadores sin resolver`() {
        // Si se resolvieran acá, build() los formatearía por segunda vez. El resolvedor
        // recibe solo el id, así que no hay forma de pasarle argumentos.
        val t = ConsultaFicha.shareTexts { id -> "crudo-$id %1\$s" }
        val ids = listOf(
            t.headerFmt, t.targetFmt, t.azimuthFmt, t.magneticFmt, t.elevationFmt,
            t.notVisibleFmt, t.tipsHeader, t.tipCalibration, t.tipMetal, t.tipElevation
        )
        assertEquals("tienen que ser diez textos distintos", 10, ids.toSet().size)
        assertTrue(ids.all { it.endsWith("%1\$s") })
    }
}
