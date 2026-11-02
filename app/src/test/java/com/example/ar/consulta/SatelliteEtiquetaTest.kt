package com.example.ar.consulta

import com.example.ar.satellite.SatelliteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/**
 * La etiqueta del satélite que viaja en el mensaje de WhatsApp.
 *
 * Es el texto que lee alguien que NO tiene la app instalada, así que acá no hay nada
 * de Android: [etiquetaSatelite] recibe dos strings y se prueba con JUnit común, igual
 * que [ConsultaFicha].
 *
 * Los casos de la tabla pasan los dos strings literales, así que no dependen del
 * locale. El test de propiedad sí lo fija: recorre el DB real y
 * `Satellite.positionLabel` formatea con "%.1f", que toma el separador decimal del
 * sistema (en una PC con coma decimal saldría "61,0°O").
 */
class SatelliteEtiquetaTest {

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

    // ── Los casos exactos ─────────────────────────────────────────────────────
    // Un test por caso: cuando se rompe la detección a propósito, el nombre del
    // test que cae dice cuál de los formatos dejó de reconocerse.

    @Test
    fun `un nombre sin posicion adentro queda intacto`() {
        assertEquals("Amazonas 3 (61.0°O)", etiquetaSatelite("Amazonas 3", "61.0°O"))
    }

    @Test
    fun `saca la posicion sin decimales y pone la canonica`() {
        assertEquals("Apstar 9 (142.0°E)", etiquetaSatelite("Apstar 9 (142°E)", "142.0°E"))
    }

    @Test
    fun `saca la posicion con decimales`() {
        assertEquals("Astra 1 (19.2°E)", etiquetaSatelite("Astra 1 (19.2°E)", "19.2°E"))
    }

    @Test
    fun `saca una posicion de tres digitos`() {
        assertEquals("Intelsat 18 (180.0°E)", etiquetaSatelite("Intelsat 18 (180°E)", "180.0°E"))
    }

    @Test
    fun `la W inglesa del nombre se reemplaza por la O del español`() {
        assertEquals("Amos 3 (4.0°O)", etiquetaSatelite("Amos 3 (4°W)", "4.0°O"))
    }

    @Test
    fun `reconoce la posicion sin signo de grado`() {
        assertEquals("Nilesat 201 (7.0°O)", etiquetaSatelite("Nilesat 201 (7W)", "7.0°O"))
    }

    @Test
    fun `un parentesis que no es una posicion no se toca`() {
        // (SES-8) es otra designación comercial, no una posición: borrarla dejaría
        // "NSS-806" mutilado. Este satélite queda con sus dos paréntesis a propósito.
        assertEquals(
            "NSS-806 (SES-8) (47.5°O)",
            etiquetaSatelite("NSS-806 (SES-8)", "47.5°O")
        )
    }

    @Test
    fun `un parentesis con la letra pero sin digitos no es una posicion`() {
        // El único test que fija el `\d+` del patrón. El de NSS-806 no sirve para eso:
        // ahí lo que salva al nombre es la clase [EWO], que "SES-8" tampoco cumple.
        // Sin el dígito obligatorio, un paréntesis final con sola letra se comería
        // parte del nombre comercial.
        assertEquals(
            "Eutelsat Quantum (W) (5.0°E)",
            etiquetaSatelite("Eutelsat Quantum (W)", "5.0°E")
        )
    }

    @Test
    fun `solo saca el parentesis final, no uno del medio`() {
        // Lo que se saca es el paréntesis FINAL. Sin el ancla de fin de string se
        // borraría cualquiera que tenga forma de longitud, también en el medio del
        // nombre. Hoy ningún satélite del DB tiene esa forma, así que sin este caso
        // romper el ancla dejaba toda la suite en verde (comprobado por mutación).
        assertEquals(
            "Astra (19.2°E) Plus (19.2°E)",
            etiquetaSatelite("Astra (19.2°E) Plus", "19.2°E")
        )
    }

    // ── La propiedad sobre el DB real ─────────────────────────────────────────

    /**
     * Una longitud entre paréntesis, en cualquier parte del texto.
     *
     * A propósito NO reutiliza la expresión de `SatelliteEtiqueta.kt`: si las dos
     * salieran de la misma constante, una expresión mal escrita pasaría los dos lados
     * a la vez y el test no probaría nada.
     */
    private val longitudEntreParentesis = Regex(
        """\(\s*\d+(?:[.,]\d+)?\s*°?\s*[EWO]\s*\)""",
        RegexOption.IGNORE_CASE
    )

    @Test
    fun `ningun satelite del DB queda con la posicion dos veces`() {
        val todos = SatelliteDatabase.satellites
        assertTrue("el DB no puede estar vacío", todos.isNotEmpty())

        todos.forEach { sat ->
            val etiqueta = etiquetaSatelite(sat.name, sat.positionLabel)
            assertEquals(
                "la etiqueta de '${sat.name}' quedó '$etiqueta'",
                1,
                longitudEntreParentesis.findAll(etiqueta).count()
            )
            assertTrue(
                "la etiqueta de '${sat.name}' no termina en la posición canónica: '$etiqueta'",
                etiqueta.endsWith(" (${sat.positionLabel})")
            )
        }
    }

    @Test
    fun `el DB tiene satelites con la posicion embebida, o este test no probaria nada`() {
        // Sin esto, borrar todos los nombres con posición adentro dejaría el test de
        // propiedad en verde sin haber ejercitado nunca el borrado.
        val conPosicion = SatelliteDatabase.satellites.count {
            longitudEntreParentesis.containsMatchIn(it.name)
        }
        val total = SatelliteDatabase.satellites.size
        println("satélites con la posición embebida en el nombre: $conPosicion de $total")
        assertTrue("ningún nombre del DB trae la posición adentro", conPosicion > 0)
    }
}
