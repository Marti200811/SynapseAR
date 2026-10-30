package com.example.ar.consulta

import com.example.ar.satellite.Satellite
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class RemoteQueryTest {

    private val amazonas3 = Satellite("Amazonas 3", -61.0, "Sudamérica", "TV / Internet")
    private val satLejano = Satellite("Lejano", 150.0, "Pacífico", "TV")

    // positionLabel usa "%.1f", que toma el separador decimal del sistema: en una
    // PC con coma decimal sale "61,0°O" y el test fallaba solo ahí. Fijamos el
    // locale de formato para que el resultado no dependa de la máquina.
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
