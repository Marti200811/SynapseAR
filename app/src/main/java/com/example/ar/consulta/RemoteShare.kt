package com.example.ar.consulta

import java.util.Locale

/**
 * Arma el mensaje que se le manda a la persona que va a apuntar la antena.
 *
 * Sin imports de Android: las plantillas entran como [Texts] y quien llama las
 * saca de los recursos. Así el formato se testea sin emulador y en un idioma fijo.
 *
 * El mensaje tiene que servirle a alguien que NO tiene la app instalada: es todo
 * lo que va a tener para trabajar.
 */
object RemoteShare {

    data class Texts(
        val headerFmt: String,
        val targetFmt: String,
        val azimuthFmt: String,
        val magneticFmt: String,
        val elevationFmt: String,
        val notVisibleFmt: String,
        val tipsHeader: String,
        val tipCalibration: String,
        val tipMetal: String,
        val tipElevation: String
    )

    fun build(
        result: RemoteResult,
        texts: Texts,
        locale: Locale = Locale.getDefault()
    ): String {
        val sb = StringBuilder()
        sb.appendLine("📡 " + texts.headerFmt.format(locale, result.observerLabel))
        sb.appendLine()
        sb.appendLine(texts.targetFmt.format(locale, result.targetLabel))
        sb.appendLine()

        if (!result.visible) {
            // Un satélite bajo el horizonte no se agarra con ninguna antena. Mandar
            // igual un azimut sería mandar a alguien a perder la tarde en un techo.
            sb.appendLine(texts.notVisibleFmt.format(locale, result.targetLabel))
            return sb.toString().trimEnd()
        }

        // Azimut a grados enteros: una brújula de mano no resuelve decimales.
        sb.appendLine(texts.azimuthFmt.format(locale, "%.0f".format(locale, result.azimuthTrueDeg)))
        sb.appendLine(texts.magneticFmt.format(locale, "%.0f".format(locale, result.azimuthMagneticDeg)))
        sb.appendLine(texts.elevationFmt.format(locale, "%.1f".format(locale, result.elevationDeg)))
        sb.appendLine()
        sb.appendLine(texts.tipsHeader)
        sb.appendLine("• " + texts.tipCalibration)
        sb.appendLine("• " + texts.tipMetal)
        sb.appendLine("• " + texts.tipElevation)
        return sb.toString().trimEnd()
    }
}
