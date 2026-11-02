package com.example.ar.consulta

import com.example.ar.TargetPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * Las dos reglas del botón "Calcular para acá" del mapa: cuándo se ve y qué etiqueta
 * lleva el punto. Cablear el botón al fragment y abrir la hoja NO se prueban acá, y no
 * con mocks: eso se mira en el teléfono.
 */
class ConsultaEntradaTest {

    private val esAR = Locale.forLanguageTag("es-AR")
    private val ptBR = Locale.forLanguageTag("pt-BR")

    // Un punto de Buenos Aires elegido en el mapa.
    private val lat = -34.6037
    private val lon = -58.3816
    private val delMapa = ObserverPoint(lat, lon, "Av. San Martín 1842")

    // ── Regla 1: cuándo se ve el botón ────────────────────────────────────────

    @Test
    fun `con el objetivo igual al punto de consulta el boton se ve`() {
        assertTrue(ConsultaEntrada.isButtonVisible(delMapa, TargetPoint(lat, lon, "Av. San Martín 1842")))
    }

    @Test
    fun `si el objetivo es otro lugar el boton se oculta`() {
        // Es el caso que esta regla existe para cerrar: se eligió una torre TDT en la
        // Brújula y se abre el Mapa. Calcular "para acá" daría la orientación de la
        // torre, con un número creíble y equivocado.
        val torre = TargetPoint(-31.4135, -64.1811, "📺 Torre Córdoba")
        assertFalse(ConsultaEntrada.isButtonVisible(delMapa, torre))
    }

    @Test
    fun `un waypoint elegido en la Brujula tampoco habilita el boton`() {
        val waypoint = TargetPoint(-34.9011, -56.1645, "📍 Casa de Pedro")
        assertFalse(ConsultaEntrada.isButtonVisible(delMapa, waypoint))
    }

    @Test
    fun `sin punto de consulta el boton se oculta aunque haya objetivo`() {
        assertFalse(ConsultaEntrada.isButtonVisible(null, TargetPoint(lat, lon)))
    }

    @Test
    fun `sin objetivo el boton se oculta aunque haya punto de consulta`() {
        // Pasa cuando la Brújula suelta el objetivo (onClearTarget).
        assertFalse(ConsultaEntrada.isButtonVisible(delMapa, null))
    }

    @Test
    fun `sin nada el boton se oculta`() {
        assertFalse(ConsultaEntrada.isButtonVisible(null, null))
    }

    @Test
    fun `tienen que coincidir las dos coordenadas`() {
        assertFalse(ConsultaEntrada.isButtonVisible(delMapa, TargetPoint(lat, lon + 0.0001)))
        assertFalse(ConsultaEntrada.isButtonVisible(delMapa, TargetPoint(lat + 0.0001, lon)))
    }

    @Test
    fun `lo que cuenta son las coordenadas y no el nombre`() {
        // Si la Brújula eligiera una torre justo en el mismo punto, el cálculo sale
        // igual de correcto: lo que se calcula es la orientación de ese lugar.
        assertTrue(ConsultaEntrada.isButtonVisible(delMapa, TargetPoint(lat, lon, "📺 Torre")))
    }

    // ── Regla 2: la etiqueta ──────────────────────────────────────────────────

    @Test
    fun `un nombre normal se usa tal cual`() {
        assertEquals(
            "Av. San Martín 1842, Mendoza",
            ConsultaEntrada.label("Av. San Martín 1842, Mendoza", lat, lon, esAR)
        )
    }

    @Test
    fun `un nombre vacio se arma con las coordenadas`() {
        assertEquals("-34,6037° / -58,3816°", ConsultaEntrada.label("", lat, lon, esAR))
    }

    @Test
    fun `un nombre en blanco se arma con las coordenadas`() {
        assertEquals("-34,6037° / -58,3816°", ConsultaEntrada.label("   ", lat, lon, esAR))
        assertEquals("-34,6037° / -58,3816°", ConsultaEntrada.label("\n\t", lat, lon, esAR))
    }

    @Test
    fun `el nombre por defecto Objetivo se arma con las coordenadas`() {
        // "Objetivo" es lo que lleva un TargetPoint sin nombre. No le dice nada al cliente.
        assertEquals("-34,6037° / -58,3816°", ConsultaEntrada.label("Objetivo", lat, lon, esAR))
        assertEquals("-34,6037° / -58,3816°", ConsultaEntrada.label(" objetivo ", lat, lon, esAR))
    }

    @Test
    fun `con es-AR el separador decimal es coma y la etiqueta sigue siendo legible`() {
        val etiqueta = ConsultaEntrada.label("", lat, lon, esAR)
        // La coma decimal ya está en los números: separar latitud y longitud con otra
        // coma daría "-34,6037, -58,3816", que no se sabe dónde parte.
        assertEquals("-34,6037° / -58,3816°", etiqueta)
        assertFalse(etiqueta.contains(", "))
        assertFalse(etiqueta.contains("."))
        assertEquals("el separador entre las dos es ' / '", 2, etiqueta.split(" / ").size)
    }

    @Test
    fun `el separador decimal sigue al idioma`() {
        assertEquals("-34,6037° / -58,3816°", ConsultaEntrada.label("", lat, lon, ptBR))
        assertEquals("-34.6037° / -58.3816°", ConsultaEntrada.label("", lat, lon, Locale.US))
    }

    @Test
    fun `sin locale explicito la etiqueta sigue al formato del telefono`() {
        // Es la misma regla que RemoteShare.build y la que no cumple el precedente de
        // CompassFragment (Locale.US): sin esto la etiqueta saldría con punto al lado de
        // un azimut con coma.
        val anterior = Locale.getDefault(Locale.Category.FORMAT)
        try {
            Locale.setDefault(Locale.Category.FORMAT, esAR)
            assertEquals("-34,6037° / -58,3816°", ConsultaEntrada.label("", lat, lon))
            Locale.setDefault(Locale.Category.FORMAT, Locale.US)
            assertEquals("-34.6037° / -58.3816°", ConsultaEntrada.label("", lat, lon))
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, anterior)
        }
    }

    @Test
    fun `una coordenada que redondea a cero no sale como menos cero`() {
        // "%.4f" de -0.00001 da "-0,0000", que parece un error.
        assertEquals("0,0000° / 0,0000°", ConsultaEntrada.label("", -0.00001, 0.00001, esAR))
    }

    @Test
    fun `el nombre de coordenadas que arma el toque del mapa se reescribe con la barra`() {
        // El mapa nombra el punto tocado "%.4f°, %.4f°" (con el locale del teléfono) y lo
        // guarda así en el historial. Elegido de ahí, llega como un nombre "normal" pero
        // es justo la forma con coma que no se lee.
        assertEquals("-34,6037° / -58,3816°", ConsultaEntrada.label("-34,6037°, -58,3816°", lat, lon, esAR))
        assertEquals("-34.6037° / -58.3816°", ConsultaEntrada.label("-34.6037°, -58.3816°", lat, lon, Locale.US))
    }

    @Test
    fun `una direccion que lleva numeros no se confunde con un par de coordenadas`() {
        assertEquals("Calle 12, 3° B", ConsultaEntrada.label("Calle 12, 3° B", lat, lon, esAR))
        assertEquals("1,5° Norte", ConsultaEntrada.label("1,5° Norte", lat, lon, esAR))
    }

    @Test
    fun `la etiqueta nunca queda vacia`() {
        for (nombre in listOf("", " ", "\n", "Objetivo", "-34,6037°, -58,3816°", "Casa")) {
            val etiqueta = ConsultaEntrada.label(nombre, lat, lon, esAR)
            assertTrue("con '$nombre' la etiqueta quedó vacía", etiqueta.isNotBlank())
        }
    }

    // ── observerFor: junta las dos cosas ──────────────────────────────────────

    @Test
    fun `observerFor conserva las coordenadas tal cual y pone la etiqueta`() {
        val p = ConsultaEntrada.observerFor(lat, lon, "", esAR)
        assertEquals(lat, p.lat, 0.0)
        assertEquals(lon, p.lon, 0.0)
        assertEquals("-34,6037° / -58,3816°", p.label)
    }

    @Test
    fun `el punto que arma observerFor coincide con el objetivo de las mismas coordenadas`() {
        // Es lo que hace el mapa: escribe las dos cosas con los mismos números. Si acá
        // hubiera redondeo, el botón no aparecería nunca.
        val p = ConsultaEntrada.observerFor(lat, lon, "Casa", esAR)
        assertTrue(ConsultaEntrada.isButtonVisible(p, TargetPoint(lat, lon, "Casa")))
    }
}
