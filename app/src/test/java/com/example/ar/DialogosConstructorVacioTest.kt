package com.example.ar

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Todos los diálogos de la app tienen que poder construirse SIN argumentos.
 *
 * Cuando cambia el tamaño de letra, el tema o el idioma, Android recrea la Activity y
 * `FragmentFactory` vuelve a instanciar los diálogos que estaban abiertos: busca la clase
 * por su nombre y llama a `getConstructor()`, sin argumentos. Si el diálogo declara
 * parámetros obligatorios en el constructor, ese camino tira `NoSuchMethodException`, que
 * sale como `Fragment$InstantiationException`, y la app se cierra.
 *
 * Seis de los diálogos tenían ese problema y estaba vivo en producción (v21 / 1.4.1,
 * reproducido en el Moto el 2026-11-02 con el selector de satélites). **Este test los
 * habría detectado a los seis**, y está acá para que mañana nadie agregue un diálogo con
 * parámetros en el constructor y vuelva a romper lo mismo.
 *
 * Tener constructor vacío es necesario pero no suficiente: un diálogo puede instanciarse bien
 * y seguir perdiendo sus dependencias. Eso lo cubre [DialogosSinEstadoInyectadoTest], que
 * recorre la misma lista.
 *
 * No hace falta ningún dispositivo ni ninguna dependencia nueva: `Class.forName` y
 * `getConstructor` son de la JDK, y nunca se llega a instanciar nada de Android.
 */
class DialogosConstructorVacioTest {

    @Test
    fun `los dialogos se pueden instanciar sin argumentos`() {
        // Todos de una, y no un test por clase: si se rompen varios, el rojo los nombra
        // a todos juntos en vez de contarlos de a uno.
        val rotos = DIALOGOS_DE_LA_APP.mapNotNull { nombre ->
            // initialize = false, igual que FragmentFactory.loadFragmentClass: alcanza con
            // cargar la clase, no hay que inicializarla para saber qué constructores tiene.
            val clase = Class.forName(nombre, false, javaClass.classLoader)
            try {
                clase.getConstructor()
                null
            } catch (e: NoSuchMethodException) {
                nombre
            }
        }

        assertEquals(
            "Estos diálogos no tienen constructor sin argumentos: la app se cierra al " +
                "cambiar el tamaño de letra, el tema o el idioma con uno de ellos abierto. " +
                "Los datos tienen que entrar por newInstance/arguments y el resultado salir " +
                "por setFragmentResult.",
            emptyList<String>(), rotos
        )
    }
}
