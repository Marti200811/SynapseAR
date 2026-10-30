package com.example.ar.consulta

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Prueba RemoteShare.build() con los strings.xml REALES de los tres idiomas.
 *
 * RemoteShareTest usa plantillas simplificadas a propósito; este test es el que
 * atrapa lo que ellas no pueden: un marcador sobrante en un idioma (en el teléfono
 * eso es un crash), una indicación que cambió o se perdió al traducir, o un idioma
 * donde el orden de las líneas quedó distinto.
 *
 * Sin dependencias nuevas: el XML se lee con javax.xml (viene en la JVM). Los
 * marcadores `%1$s` se dejan literales; recién los resuelve build().
 */
class RemoteShareRecursosTest {

    private val carpetas = listOf("values", "values-en", "values-pt")

    private val claves = listOf(
        "consulta_share_header",
        "consulta_share_target",
        "consulta_share_azimuth",
        "consulta_share_magnetic",
        "consulta_share_elevation",
        "consulta_share_not_visible",
        "consulta_share_tips_header",
        "consulta_share_tip_calibration",
        "consulta_share_tip_metal",
        "consulta_share_tip_elevation"
    )

    // Los números van con Locale.US explícito: nada depende de la PC.
    private val locale = Locale.US

    // ── Lectura de los recursos ────────────────────────────────────────────

    /** Gradle corre los tests con `app/` como directorio de trabajo. */
    private fun archivoStrings(carpeta: String): File {
        val candidatos = listOf(
            File("src/main/res/$carpeta/strings.xml"),
            File("app/src/main/res/$carpeta/strings.xml")
        )
        return candidatos.firstOrNull { it.isFile }
            ?: throw AssertionError(
                "No encuentro $carpeta/strings.xml. Directorio de trabajo: " +
                    File(".").absoluteFile.normalize()
            )
    }

    private fun leerStrings(carpeta: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(archivoStrings(carpeta))
        val nodos = doc.getElementsByTagName("string")
        val mapa = mutableMapOf<String, String>()
        for (i in 0 until nodos.length) {
            val nodo = nodos.item(i) as Element
            mapa[nodo.getAttribute("name")] = desescapar(nodo.textContent)
        }
        return mapa
    }

    /**
     * Lo mínimo de lo que hace aapt al compilar un string: las comillas sin escapar
     * desaparecen y las secuencias `\"`, `\'`, `\n`, `\\`, `\@`, `\?` y `\uXXXX`
     * pasan a ser el carácter que representan.
     */
    private fun desescapar(crudo: String): String {
        val texto = crudo.trim()
        val sb = StringBuilder()
        var i = 0
        while (i < texto.length) {
            val c = texto[i]
            when {
                c == '\\' && i + 1 < texto.length -> {
                    when (val siguiente = texto[i + 1]) {
                        'n' -> { sb.append('\n'); i += 2 }
                        't' -> { sb.append('\t'); i += 2 }
                        'u' -> {
                            sb.append(texto.substring(i + 2, i + 6).toInt(16).toChar())
                            i += 6
                        }
                        else -> { sb.append(siguiente); i += 2 }
                    }
                }
                c == '"' -> i++
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString()
    }

    private fun textos(carpeta: String): RemoteShare.Texts {
        val s = leerStrings(carpeta)
        fun clave(nombre: String) = s[nombre]
            ?: throw AssertionError("Falta '$nombre' en $carpeta/strings.xml")
        return RemoteShare.Texts(
            headerFmt      = clave("consulta_share_header"),
            targetFmt      = clave("consulta_share_target"),
            azimuthFmt     = clave("consulta_share_azimuth"),
            magneticFmt    = clave("consulta_share_magnetic"),
            elevationFmt   = clave("consulta_share_elevation"),
            notVisibleFmt  = clave("consulta_share_not_visible"),
            tipsHeader     = clave("consulta_share_tips_header"),
            tipCalibration = clave("consulta_share_tip_calibration"),
            tipMetal       = clave("consulta_share_tip_metal"),
            tipElevation   = clave("consulta_share_tip_elevation")
        )
    }

    // ── Datos ──────────────────────────────────────────────────────────────

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

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    fun `los diez strings existen y no estan vacios en los tres idiomas`() {
        for (carpeta in carpetas) {
            val s = leerStrings(carpeta)
            for (nombre in claves) {
                val valor = s[nombre]
                assertTrue("Falta '$nombre' en $carpeta", valor != null)
                assertTrue("'$nombre' está vacío en $carpeta", valor!!.isNotBlank())
            }
        }
    }

    @Test
    fun `build no lanza excepcion con los strings reales de ningun idioma`() {
        // Un %2$s de más en una traducción es MissingFormatArgumentException: en el
        // teléfono, un crash al tocar "Compartir". Acá se ve antes de publicar.
        for (carpeta in carpetas) {
            val t = textos(carpeta)
            try {
                RemoteShare.build(visible(), t, locale)
                RemoteShare.build(noVisible(), t, locale)
            } catch (e: Exception) {
                throw AssertionError("build() lanzó ${e::class.simpleName} con $carpeta: ${e.message}", e)
            }
        }
    }

    @Test
    fun `el azimut verdadero va en una linea anterior a la del magnetico`() {
        for (carpeta in carpetas) {
            val t = textos(carpeta)
            val lineas = RemoteShare.build(visible(), t, locale).lines()

            val verdadero = t.azimuthFmt.format(locale, "355")
            val magnetico = t.magneticFmt.format(locale, "6")
            val iVerdadero = lineas.indexOf(verdadero)
            val iMagnetico = lineas.indexOf(magnetico)

            assertTrue("$carpeta: falta la línea '$verdadero'", iVerdadero >= 0)
            assertTrue("$carpeta: falta la línea '$magnetico'", iMagnetico >= 0)
            assertTrue(
                "$carpeta: el verdadero ($iVerdadero) tiene que ir antes que el magnético ($iMagnetico)",
                iVerdadero < iMagnetico
            )
        }
    }

    @Test
    fun `la elevacion lleva un decimal y va despues de los azimuts`() {
        for (carpeta in carpetas) {
            val t = textos(carpeta)
            val lineas = RemoteShare.build(visible(), t, locale).lines()
            val elevacion = t.elevationFmt.format(locale, "49.7")
            assertTrue("$carpeta: falta la línea '$elevacion'", lineas.contains(elevacion))
            assertTrue(
                "$carpeta: la elevación tiene que ir después del magnético",
                lineas.indexOf(elevacion) > lineas.indexOf(t.magneticFmt.format(locale, "6"))
            )
        }
    }

    @Test
    fun `trae tres vinetas y son las indicaciones reales`() {
        for (carpeta in carpetas) {
            val t = textos(carpeta)
            val lineas = RemoteShare.build(visible(), t, locale).lines()

            val vinetas = lineas.filter { it.startsWith("• ") }
            assertEquals("$carpeta: tiene que haber 3 viñetas", 3, vinetas.size)

            // Que sean los textos reales, no un resumen: la del metal es la que más
            // desalineaciones evita y es la que más fácil se pierde en una traducción.
            assertEquals(
                "$carpeta: viñetas distintas de las indicaciones de los strings",
                listOf("• " + t.tipCalibration, "• " + t.tipMetal, "• " + t.tipElevation),
                vinetas
            )
            assertTrue("$carpeta: falta el encabezado de las indicaciones", lineas.contains(t.tipsHeader))
        }
    }

    @Test
    fun `si no es visible no trae azimut ni elevacion negativa`() {
        for (carpeta in carpetas) {
            val t = textos(carpeta)
            val msg = RemoteShare.build(noVisible(), t, locale)
            val lineas = msg.lines()

            assertTrue(
                "$carpeta: falta el aviso completo de que no se ve",
                lineas.contains(t.notVisibleFmt.format(locale, "Amazonas 3 (61.0°O)"))
            )
            assertFalse("$carpeta: trae el azimut verdadero",
                lineas.contains(t.azimuthFmt.format(locale, "355")))
            assertFalse("$carpeta: trae el azimut magnético",
                lineas.contains(t.magneticFmt.format(locale, "6")))
            assertFalse("$carpeta: trae la elevación",
                lineas.any { it == t.elevationFmt.format(locale, "-51.8") })
            assertFalse("$carpeta: aparece 355 en el mensaje", msg.contains("355"))
            assertFalse("$carpeta: aparece una elevación negativa", msg.contains("-51"))
            assertTrue("$carpeta: no debería traer viñetas de medición",
                lineas.none { it.startsWith("• ") })
        }
    }

    @Test
    fun `el tip del metal conserva la distancia y la negacion en mayusculas en cada idioma`() {
        // El spec llama a esta advertencia "la indicación que más desalineaciones evita en
        // la práctica", y es la que casi nadie sabe: apoyarse en el caño le desvía la
        // brújula al celular. Se fija solo lo que no cambia con la redacción (la distancia
        // y la negación en mayúsculas), no el texto completo, para no volver frágil el
        // test ante un retoque de estilo. La negación es la que está en cada idioma.
        val negacionPorCarpeta = mapOf(
            "values" to "NO",
            "values-en" to "NOT",
            "values-pt" to "NÃO"
        )

        for (carpeta in carpetas) {
            val tip = textos(carpeta).tipMetal
            val negacion = negacionPorCarpeta.getValue(carpeta)

            assertTrue(
                "$carpeta: el tip del metal perdió la distancia '2-3'. Sin ella el cliente no " +
                    "sabe a cuánto de la antena medir. Texto actual: $tip",
                tip.contains("2-3")
            )

            // Palabra suelta en mayúsculas: "NO" no puede darse por buena porque aparezca
            // dentro de "NOT", ni "NOT" dentro de otra palabra.
            val negacionEnMayusculas = Regex("(?<!\\p{L})${Regex.escape(negacion)}(?!\\p{L})")
            assertTrue(
                "$carpeta: el tip del metal perdió la negación '$negacion' en mayúsculas. Sin " +
                    "esa advertencia el cliente se apoya en el caño, el metal le desvía la " +
                    "brújula y la antena queda desalineada. Texto actual: $tip",
                negacionEnMayusculas.containsMatchIn(tip)
            )
        }
    }

    @Test
    fun `los marcadores de cada clave coinciden en los tres idiomas`() {
        val marcador = Regex("%([0-9]+[\$])?[a-zA-Z]")
        val porIdioma = carpetas.associateWith { leerStrings(it) }
        val base = carpetas.first()

        for (nombre in claves) {
            val esperado = marcador.findAll(porIdioma.getValue(base).getValue(nombre))
                .map { it.value }.toList()
            for (carpeta in carpetas.drop(1)) {
                val encontrado = marcador.findAll(porIdioma.getValue(carpeta).getValue(nombre))
                    .map { it.value }.toList()
                assertEquals(
                    "'$nombre': marcadores de $carpeta distintos de los de $base",
                    esperado,
                    encontrado
                )
            }
        }
    }
}
