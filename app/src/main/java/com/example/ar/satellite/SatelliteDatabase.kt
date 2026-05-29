package com.example.ar.satellite

/**
 * Base de datos de satélites geoestacionarios.
 * Cubre principalmente Latinoamérica, España y Europa.
 */
object SatelliteDatabase {

    /** Satélites disponibles en la versión gratuita */
    val freeSatelliteNames = setOf(
        "Hispasat 30W-5",      // España / Latinoamérica
        "Astra 1 (19.2°E)",    // Europa
        "Hot Bird 13E",        // Europa
        "Intelsat 21",         // Latinoamérica
        "Star One C2"          // Brasil / Sudamérica
    )

    val satellites: List<Satellite> = listOf(

        // ── LATINOAMÉRICA ────────────────────────────────────────────
        Satellite("Hispasat 30W-6",     -30.0, "América Latina / España", "TV / Banda ancha"),
        Satellite("Hispasat 30W-5",     -30.0, "América Latina / España", "TV / Datos"),
        Satellite("Amazonas 3",         -61.0, "América Latina",          "TV / Internet"),
        Satellite("Amazonas 5",         -61.0, "América Latina",          "Internet"),
        Satellite("Intelsat 11",        -43.0, "América Latina",          "TV / Datos"),
        Satellite("Intelsat 21",        -58.0, "América Latina",          "TV / Internet"),
        Satellite("Intelsat 34",        -55.5, "América Latina",          "TV HD / Internet"),
        Satellite("SES-6",             -40.5, "América Latina / Atlántico","TV / DTH"),
        Satellite("Star One C2",        -70.0, "Brasil / Sudamérica",     "TV / Internet"),
        Satellite("Star One C4",        -70.0, "Brasil / Sudamérica",     "Banda ancha"),
        Satellite("Star One D1",        -84.0, "Brasil / Sudamérica",     "TV / Internet"),
        Satellite("Satmex 5",           -116.8,"México / Centroamérica",  "TV / Datos"),
        Satellite("Morelos 3",          -116.8,"México / Centroamérica",  "TV / Internet"),
        Satellite("Eutelsat 65W",       -65.0, "Brasil",                  "DTH Brasil"),
        Satellite("Intelsat 14",        -45.0, "América Latina",          "Internet"),
        Satellite("Intelsat 16",        -58.0, "América Latina",          "DTH"),
        Satellite("Intelsat 23",        -53.0, "América Latina",          "TV / Datos"),
        Satellite("NSS-806 (SES-8)",    -47.5, "América Latina",          "TV / Datos"),
        Satellite("Horizons 2",         -74.0, "Norteamérica / Atlántico","Datos"),
        Satellite("Galaxy 3C",          -95.0, "Norteamérica",            "TV / Datos"),

        // ── ESPAÑA / EUROPA ──────────────────────────────────────────
        Satellite("Astra 1 (19.2°E)",   19.2, "Europa",                  "TV HD / DTH"),
        Satellite("Astra 2 (28.2°E)",   28.2, "Reino Unido / Europa",    "TV HD"),
        Satellite("Astra 3 (23.5°E)",   23.5, "Europa Central",          "TV / Radio"),
        Satellite("Hot Bird 13E",        13.0, "Europa / Oriente Medio",  "TV HD / DTH"),
        Satellite("Eutelsat 7W",         -7.0, "Europa / África del Norte","TV"),
        Satellite("Eutelsat 9B",          9.0, "Europa",                  "TV / Internet"),
        Satellite("Eutelsat 13W",        -13.0,"África / Europa",         "TV / Datos"),
        Satellite("Turksat 4A (42°E)",   42.0, "Turquía / Asia Central",  "TV / Internet"),
        Satellite("SES-5 (5°E)",          5.0, "Europa / África",         "TV / Banda ancha"),
        Satellite("Amos 3 (4°W)",        -4.0, "Europa / Oriente Medio",  "TV / Internet"),

        // ── AFRICA / ORIENTE MEDIO ───────────────────────────────────
        Satellite("Arabsat 5A (30.5°E)", 30.5, "Oriente Medio / África",  "TV / DTH"),
        Satellite("Nilesat 7W",          -7.0, "Norte de África",         "TV árabe"),
        Satellite("Intelsat 20 (68.5°E)",68.5, "África / Asia del Sur",   "TV / Internet"),

        // ── ASIA / PACÍFICO ──────────────────────────────────────────
        Satellite("AsiaSat 7 (105.5°E)", 105.5,"Asia",                    "TV / DTH"),
        Satellite("Intelsat 18 (180°E)", 180.0,"Pacífico",                "TV / Datos")
    )

    /** Devuelve la lista agrupada por región */
    val byRegion: Map<String, List<Satellite>> =
        satellites.groupBy { it.region }

    /** Busca satélites por nombre (sin importar mayúsculas) */
    fun search(query: String): List<Satellite> =
        satellites.filter { it.name.contains(query, ignoreCase = true) }
}
