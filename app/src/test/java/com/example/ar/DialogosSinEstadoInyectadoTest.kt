package com.example.ar

import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Ningún diálogo puede exponer estado mutable.
 *
 * Una propiedad pública `var` en un `DialogFragment` solo existe para que alguien de afuera le
 * empuje algo después de construirlo (`dialog.billingManager = …`). Eso funciona la primera
 * vez y se rompe en silencio en la segunda: cuando Android recrea la Activity, el diálogo se
 * reinstancia por su constructor vacío y **nadie vuelve a empujar nada**, así que la propiedad
 * queda en su valor inicial. No hay crash, que es lo que lo hace peligroso.
 *
 * Es el bug que tenían `UpgradeDialog` (cinco propiedades, entre ellas `billingManager`) y
 * `SettingsDialog` (`onUnitsChanged`): el diálogo de compra reaparecía entero pero con el
 * botón muerto, y Analytics igual registraba la intención de compra. **Este test los habría
 * detectado a los dos**, y a la vez deja pasar a `CalibrationDialog`, que tiene un `var` pero
 * privado y de uso interno — que es exactamente la distinción que importa.
 *
 * Lo que tiene que entrar por otro lado:
 * - datos serializables → `newInstance(...)` + `arguments`
 * - avisos hacia afuera → `setFragmentResult` / `setFragmentResultListener`
 * - objetos vivos (managers) → leerlos de la Activity en cada acceso
 *
 * Device-free y sin dependencias nuevas: la reflexión de la JDK alcanza, y nunca se instancia
 * nada de Android.
 */
class DialogosSinEstadoInyectadoTest {

    @Test
    fun `ningun dialogo expone propiedades mutables`() {
        val rotos = DIALOGOS_DE_LA_APP.flatMap { nombre ->
            val clase = Class.forName(nombre, false, javaClass.classLoader)

            // Un `var` público de Kotlin se compila a un setter público. Se miran solo los
            // métodos DECLARADOS por la clase: los heredados de DialogFragment no son
            // asunto nuestro. El filtro de un parámetro descarta cosas como `setupDialog`,
            // que empieza con "set" y no es un setter.
            val setters = clase.declaredMethods
                .filter {
                    Modifier.isPublic(it.modifiers) &&
                        it.name.startsWith("set") &&
                        it.parameterCount == 1
                }
                .map { "${clase.simpleName}.${it.name}()" }

            // Y un `var` anotado con @JvmField queda como campo público no-final, sin setter,
            // así que el chequeo de arriba por su cuenta no lo vería.
            val campos = clase.declaredFields
                .filter { Modifier.isPublic(it.modifiers) && !Modifier.isFinal(it.modifiers) }
                .map { "${clase.simpleName}.${it.name}" }

            setters + campos
        }.sorted()

        assertEquals(
            "Estos diálogos exponen estado mutable, así que alguien se lo empuja desde " +
                "afuera y se pierde cuando Android los recrea (cambio de tamaño de letra, " +
                "tema, idioma, o muerte del proceso). No crashea: se degrada en silencio. " +
                "Los datos van por newInstance/arguments, los avisos por setFragmentResult, " +
                "y los managers se leen de la Activity en cada acceso.",
            emptyList<String>(), rotos
        )
    }
}
