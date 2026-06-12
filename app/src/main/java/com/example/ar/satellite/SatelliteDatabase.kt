package com.example.ar.satellite

/**
 * Base de datos de satélites geoestacionarios.
 * Cubre principalmente Latinoamérica, España y Europa.
 */
object SatelliteDatabase {

    /** Satélites disponibles en la versión gratuita (uno por región principal) */
    val freeSatelliteNames = setOf(
        "Hispasat 30W-5",      // España / Latinoamérica
        "Astra 1 (19.2°E)",    // Europa
        "Intelsat 21"          // Latinoamérica
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
        Satellite("Galaxy 16",          -99.0, "Norteamérica",            "DirecTV / Datos"),
        Satellite("Galaxy 18",         -123.0, "Norteamérica / Pacífico", "TV / Datos"),
        Satellite("Galaxy 19",          -97.0, "Norteamérica",            "TV / FTA"),
        Satellite("DirecTV 9S",        -101.0, "Norteamérica",            "DirecTV HD"),
        Satellite("DirecTV 10",        -102.8, "Norteamérica",            "DirecTV HD"),
        Satellite("DirecTV 12",        -103.0, "Norteamérica",            "DirecTV HD"),
        Satellite("Dish / Echostar 15", -61.5, "Norteamérica",            "Dish Network"),
        Satellite("Dish / Echostar 16", -61.5, "Norteamérica",            "Dish Network HD"),
        Satellite("Dish / AMC-3",       -87.0, "Norteamérica",            "Dish Network"),
        Satellite("ViaSat-2",           -69.9, "Norteamérica",            "Internet banda ancha"),
        Satellite("ViaSat-3 Americas",  -88.0, "Norteamérica",            "Internet banda ancha"),
        Satellite("AMC-4 / SES-4",      -67.0, "Norteamérica",            "TV / Datos"),
        Satellite("AMC-9 / SES-9",      -83.0, "Norteamérica / Caribe",   "TV / Datos"),
        Satellite("Anik F1",           -107.3, "Canadá / Norteamérica",   "Bell TV"),
        Satellite("Anik F2",           -111.1, "Canadá / Norteamérica",   "Bell TV / Internet"),
        Satellite("Nimiq 5",            -72.7, "Canadá",                  "Bell TV HD"),
        Satellite("SES-1",             -101.0, "Norteamérica",            "TV / Datos"),
        Satellite("SES-3",             -103.0, "Norteamérica",            "TV / Internet"),

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
        Satellite("Arabsat 6A (26°E)",   26.0, "Oriente Medio / África",  "TV HD / Internet"),
        Satellite("Nilesat 7W",          -7.0, "Norte de África",         "TV árabe"),
        Satellite("Nilesat 201 (7W)",    -7.0, "Norte de África / Europa","TV árabe HD"),
        Satellite("Badr 4 (26°E)",       26.0, "Oriente Medio",           "TV árabe / DTH"),
        Satellite("Yahsat 1A (52.5°E)",  52.5, "Oriente Medio / África",  "TV / Internet"),
        Satellite("Es'hail 2 (26°E)",    26.0, "Qatar / Oriente Medio",   "TV / Ham radio"),
        Satellite("Intelsat 20 (68.5°E)",68.5, "África / Asia del Sur",   "TV / Internet"),
        Satellite("SES-5 (5°E)",          5.0, "Europa / África",         "TV / Internet"),
        Satellite("Intelsat 33e (60°E)", 60.0, "Oriente Medio / Asia",    "Internet / Datos"),

        // ── ASIA / PACÍFICO ──────────────────────────────────────────
        Satellite("AsiaSat 7 (105.5°E)", 105.5,"Asia",                    "TV / DTH"),
        Satellite("AsiaSat 9 (122°E)",   122.0,"Asia / Pacífico",         "TV HD / Internet"),
        Satellite("Intelsat 18 (180°E)", 180.0,"Pacífico",                "TV / Datos"),
        Satellite("Apstar 7 (76.5°E)",   76.5, "Asia del Sur / SE Asia",  "TV / DTH"),
        Satellite("Apstar 9 (142°E)",    142.0,"Asia / Pacífico",         "TV / Internet"),
        Satellite("SES-12 (95°E)",        95.0, "India / SE Asia",        "TV / Internet"),
        Satellite("Measat 3 (91.5°E)",   91.5, "SE Asia / Malaysia",      "TV / DTH"),
        Satellite("Thaicom 5 (78.5°E)",  78.5, "SE Asia / Tailandia",     "TV / Internet"),
        Satellite("Koreasat 5 (113°E)",  113.0,"Corea / Asia NE",         "TV / Datos"),
        Satellite("ChinaSat 6A (125°E)", 125.0,"China / Asia NE",         "TV CCTV"),
        Satellite("ChinaSat 9 (92.2°E)", 92.2, "China / Asia",            "TV / DTH"),
        Satellite("JCSAT-3A (128°E)",    128.0,"Japón / Asia NE",         "TV / Internet")
    )

    /** Devuelve la lista agrupada por región */
    val byRegion: Map<String, List<Satellite>> =
        satellites.groupBy { it.region }

    /** Busca satélites por nombre (sin importar mayúsculas) */
    fun search(query: String): List<Satellite> =
        satellites.filter { it.name.contains(query, ignoreCase = true) }
}
