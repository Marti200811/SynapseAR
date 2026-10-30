package com.example.ar.consulta

/**
 * Resultado de una consulta remota, listo para mostrar o compartir.
 *
 * Trae los DOS azimuts a propósito: la persona que va a apuntar puede estar
 * usando una brújula de mano (magnético) o una app de celular (verdadero), y
 * mandarle solo uno garantiza que la mitad de las veces apunte mal.
 */
data class RemoteResult(
    val observerLabel: String,
    val targetLabel: String,
    val azimuthTrueDeg: Double,
    val azimuthMagneticDeg: Double,
    val elevationDeg: Double,
    val declinationDeg: Double,
    val visible: Boolean
)
