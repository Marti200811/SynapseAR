package com.example.ar

import android.content.Context
import android.graphics.Color

// ── Temas disponibles ────────────────────────────────────────────────────────

enum class CompassTheme(val displayName: String) {
    CYBER("Cyber"),        // cyan futurista (default)
    MILITARY("Military"),  // verde táctico
    CLASSIC("Classic"),    // dorado/blanco analógico
    NEON("Neon")           // rosa/violeta
}

// ── Paleta de colores de cada tema ───────────────────────────────────────────

data class ThemePalette(
    val primary: Int,     // color principal (acento, N de la brújula)
    val secondary: Int,   // color secundario (indicador de objetivo)
    val background: Int,  // fondo del dial
    val glow: Int,        // halo/glow del dial
    val dial: Int,        // ticks y anillos
    val text: Int,        // etiquetas principales
    val textSub: Int,     // etiquetas secundarias
    val needle: Int,      // aguja Norte
    val align: Int        // color de alineación (LOCKED)
)

object ThemeManager {

    private const val PREFS    = "synapse_prefs"
    private const val KEY_THEME = "compass_theme"

    val palettes: Map<CompassTheme, ThemePalette> = mapOf(

        CompassTheme.CYBER to ThemePalette(
            primary    = Color.parseColor("#00E5FF"),
            secondary  = Color.parseColor("#FFB300"),
            background = Color.parseColor("#CC0A0E1A"),
            glow       = Color.parseColor("#1A00E5FF"),
            dial       = Color.parseColor("#1F2A44"),
            text       = Color.WHITE,
            textSub    = Color.parseColor("#7A8CA8"),
            needle     = Color.parseColor("#FF3D6E"),
            align      = Color.parseColor("#00FF88")
        ),

        CompassTheme.MILITARY to ThemePalette(
            primary    = Color.parseColor("#00FF41"),
            secondary  = Color.parseColor("#FFDD00"),
            background = Color.parseColor("#DD0A0D08"),
            glow       = Color.parseColor("#1500FF41"),
            dial       = Color.parseColor("#1A2B1A"),
            text       = Color.parseColor("#C8E6C9"),
            textSub    = Color.parseColor("#558B2F"),
            needle     = Color.parseColor("#FF4444"),
            align      = Color.parseColor("#76FF03")
        ),

        CompassTheme.CLASSIC to ThemePalette(
            primary    = Color.parseColor("#FFD700"),
            secondary  = Color.parseColor("#FF8C00"),
            background = Color.parseColor("#CC0A0F1E"),
            glow       = Color.parseColor("#15FFD700"),
            dial       = Color.parseColor("#2A2540"),
            text       = Color.parseColor("#F5F0E8"),
            textSub    = Color.parseColor("#A89F80"),
            needle     = Color.parseColor("#FF3D6E"),
            align      = Color.parseColor("#FFD700")
        ),

        CompassTheme.NEON to ThemePalette(
            primary    = Color.parseColor("#FF00E5"),
            secondary  = Color.parseColor("#00FFF0"),
            background = Color.parseColor("#CC08001A"),
            glow       = Color.parseColor("#1AFF00E5"),
            dial       = Color.parseColor("#200A1A"),
            text       = Color.parseColor("#FFE8FF"),
            textSub    = Color.parseColor("#9B4DCA"),
            needle     = Color.parseColor("#FF1744"),
            align      = Color.parseColor("#00FFF0")
        )
    )

    fun getTheme(context: Context): CompassTheme {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME, CompassTheme.CYBER.name)
        return CompassTheme.values().firstOrNull { it.name == name } ?: CompassTheme.CYBER
    }

    fun setTheme(context: Context, theme: CompassTheme) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME, theme.name).apply()
    }

    fun getPalette(context: Context): ThemePalette =
        palettes[getTheme(context)] ?: palettes[CompassTheme.CYBER]!!
}
