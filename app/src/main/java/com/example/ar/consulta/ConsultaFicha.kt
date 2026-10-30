package com.example.ar.consulta

import com.example.ar.R
import java.util.Locale
import kotlin.math.abs

/**
 * Las decisiones de la hoja de consulta que no necesitan Android, separadas de
 * [RemoteSheet] para poder probarlas con JUnit común.
 *
 * Lo único que toca del framework es la clase `R` de la propia app, que son enteros.
 */
object ConsultaFicha {

    /**
     * Si se ofrece "Compartir". Solo con un satélite elegido que se ve desde ese lugar.
     *
     * Con uno bajo el horizonte el mensaje dice "hay que elegir otro satélite", frase
     * pensada para quien lo está eligiendo, no para el cliente que va a apuntar: mandársela
     * no le sirve de nada. Por eso ahí el botón no se muestra.
     */
    fun canShare(result: RemoteResult?): Boolean = result != null && result.visible

    /**
     * La declinación con un decimal y el separador del teléfono (categoría FORMAT), igual
     * que [RemoteShare.build]. Así la ficha no mezcla `-10.3°` con una elevación `49,7°`.
     *
     * Un valor que redondea a cero se muestra `0,0` y no `-0,0`.
     */
    fun declinationText(
        declinationDeg: Double,
        locale: Locale = Locale.getDefault(Locale.Category.FORMAT)
    ): String {
        val valor = if (abs(declinationDeg) < 0.05) 0.0 else declinationDeg
        return "%.1f".format(locale, valor)
    }

    /**
     * Arma los textos del mensaje a partir de un resolvedor de strings (en la hoja,
     * `{ getString(it) }`).
     *
     * El resolvedor recibe solo el id, a propósito: los `%1$s` tienen que llegar CRUDOS a
     * [RemoteShare.build], que es quien los formatea. Si se resolvieran acá con argumentos
     * se formatearía dos veces.
     */
    fun shareTexts(string: (Int) -> String) = RemoteShare.Texts(
        headerFmt      = string(R.string.consulta_share_header),
        targetFmt      = string(R.string.consulta_share_target),
        azimuthFmt     = string(R.string.consulta_share_azimuth),
        magneticFmt    = string(R.string.consulta_share_magnetic),
        elevationFmt   = string(R.string.consulta_share_elevation),
        notVisibleFmt  = string(R.string.consulta_share_not_visible),
        tipsHeader     = string(R.string.consulta_share_tips_header),
        tipCalibration = string(R.string.consulta_share_tip_calibration),
        tipMetal       = string(R.string.consulta_share_tip_metal),
        tipElevation   = string(R.string.consulta_share_tip_elevation)
    )
}
