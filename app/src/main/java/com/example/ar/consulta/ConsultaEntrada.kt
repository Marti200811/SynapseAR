package com.example.ar.consulta

import com.example.ar.TargetPoint
import java.util.Locale
import kotlin.math.abs

/**
 * Las decisiones del botón "Calcular para acá" del mapa que no necesitan Android,
 * separadas de `MapFragment` para poder probarlas con JUnit común.
 *
 * Existen porque `SharedViewModel.target` no es solo del mapa: la Brújula también lo
 * escribe, con waypoints (`📍`) y torres TDT (`📺`). El mapa lleva aparte el punto que
 * eligió él ([ObserverPoint] en `consultaObserver`) y estas reglas impiden que una torre
 * o un waypoint se cuelen como "el lugar del cliente".
 */
object ConsultaEntrada {

    /** El nombre que lleva un [TargetPoint] cuando nadie le puso uno. */
    private const val NOMBRE_POR_DEFECTO = "Objetivo"

    /**
     * El nombre que el mapa le pone al punto tocado: `"%.4f°, %.4f°"` con el locale del
     * teléfono. Se guarda así en el historial. Los grados son lo que lo distingue de
     * una dirección con números ("Calle 12, 3° B" no calza: el primer número no lleva °).
     */
    private val PAR_DE_COORDENADAS = Regex("""-?\d+[.,]\d+°\s*,\s*-?\d+[.,]\d+°""")

    /**
     * Si se ofrece el botón. Solo cuando hay un punto de consulta elegido en el mapa Y el
     * objetivo actual es ese mismo punto (misma latitud y longitud, exactas: el mapa
     * escribe las dos cosas con los mismos números).
     *
     * Falla cerrado: si la Brújula cambia el objetivo a una torre o a un waypoint, deja de
     * coincidir y el botón desaparece, en vez de calcular una orientación para otro lugar.
     */
    fun isButtonVisible(observer: ObserverPoint?, target: TargetPoint?): Boolean =
        observer != null && target != null &&
            observer.lat == target.lat && observer.lon == target.lon

    /**
     * El punto de consulta para un lugar elegido en el mapa, con una etiqueta que sirve
     * para el cliente (ver [label]).
     */
    fun observerFor(
        lat: Double,
        lon: Double,
        name: String,
        locale: Locale = Locale.getDefault(Locale.Category.FORMAT)
    ) = ObserverPoint(lat, lon, label(name, lat, lon, locale))

    /**
     * Lo que lee el cliente en el encabezado del mensaje, así que nunca queda vacía.
     *
     * Un nombre real se usa tal cual. Si está en blanco, es el "Objetivo" por defecto o es
     * un par de coordenadas con coma (el nombre que arma el toque del mapa), se arma con
     * las coordenadas separadas por `" / "`: en un teléfono en español el separador
     * decimal ya es una coma y `-34,6037, -58,3816` no se lee. Los decimales siguen al
     * teléfono (categoría FORMAT), igual que el resto del mensaje; no fijar `Locale.US`.
     */
    fun label(
        name: String,
        lat: Double,
        lon: Double,
        locale: Locale = Locale.getDefault(Locale.Category.FORMAT)
    ): String {
        val limpio = name.trim()
        val sinNombre = limpio.isEmpty() ||
            limpio.equals(NOMBRE_POR_DEFECTO, ignoreCase = true) ||
            PAR_DE_COORDENADAS.matches(limpio)
        if (!sinNombre) return name
        return "${grados(lat, locale)} / ${grados(lon, locale)}"
    }

    /** Cuatro decimales (~11 m) y un valor que redondea a cero sale `0,0000` y no `-0,0000`. */
    private fun grados(valor: Double, locale: Locale): String {
        val v = if (abs(valor) < 0.00005) 0.0 else valor
        return "%.4f°".format(locale, v)
    }
}
