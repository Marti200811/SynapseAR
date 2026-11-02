package com.example.ar.consulta

/**
 * La etiqueta del satélite tal como sale en el mensaje que se le manda al cliente.
 *
 * 27 de los 70 nombres de `SatelliteDatabase` ya traen la posición adentro, así que
 * concatenar el nombre y la posición dejaba la línea "Satélite: Apstar 9 (142°E)
 * (142.0°E)". Acá se saca ese paréntesis y se vuelve a poner la posición CANÓNICA,
 * la que deriva de `orbitalLon` vía `Satellite.positionLabel`: así la salida es
 * uniforme, está en español y no puede contradecir a `orbitalLon`.
 *
 * Es un arreglo de PRESENTACIÓN a propósito: no toca `satellite.name` ni el DB,
 * porque el set de satélites gratis se indexa por el string exacto del nombre
 * (`SatelliteDatabase.freeSatelliteNames.contains(satellite.name)`) y renombrarlos
 * movería el paywall en silencio.
 *
 * Sin imports de Android y sin importar `Satellite`: recibe dos strings y se prueba sola.
 */

/**
 * Un paréntesis FINAL que es una longitud. Reconoce las cuatro formas que hay en el
 * DB: `(19.2°E)`, `(142°E)`, `(4°W)` y `(7W)` — el grado es opcional y la letra puede
 * venir en inglés (`W`) o en español (`O`).
 *
 * Está anclado al final (`$`) y exige dígitos después del paréntesis que abre, para no
 * mutilar `NSS-806 (SES-8)`, donde el paréntesis es otra designación comercial y no
 * una posición.
 */
private val PARENTESIS_DE_POSICION = Regex(
    """\s*\(\s*\d+(?:[.,]\d+)?\s*°?\s*[EWO]\s*\)$""",
    RegexOption.IGNORE_CASE
)

/**
 * @param nombre   el nombre comercial del satélite, tal cual está en el DB.
 * @param posicion la posición canónica ya formateada, o sea `Satellite.positionLabel`.
 * @return el nombre sin su paréntesis de posición, más la posición canónica entre
 *         paréntesis. Si el nombre no trae una posición adentro, queda intacto.
 */
fun etiquetaSatelite(nombre: String, posicion: String): String =
    nombre.replace(PARENTESIS_DE_POSICION, "") + " ($posicion)"
