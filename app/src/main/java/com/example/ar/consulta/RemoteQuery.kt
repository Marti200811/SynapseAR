package com.example.ar.consulta

import com.example.ar.satellite.Satellite
import com.example.ar.satellite.SatelliteCalculator

/**
 * Calcula la orientación de una antena que está en OTRO lugar.
 *
 * Sin imports de Android a propósito: así se testea con JUnit común, sin
 * emulador. Por eso la declinación magnética entra como parámetro en vez de
 * calcularse acá — GeomagneticField es del framework y no existe en un test.
 */
object RemoteQuery {

    /**
     * Pasa un azimut de norte verdadero a norte magnético.
     *
     * `OrientationManager` hace `sensor + declinación = verdadero`, así que la
     * vuelta es `verdadero − declinación`. El resultado se normaliza a 0–360:
     * sin eso, Buenos Aires hacia Amazonas 3 daría 365,7° en vez de 5,7°.
     */
    fun toMagnetic(trueAzimuthDeg: Double, declinationDeg: Double): Double =
        ((trueAzimuthDeg - declinationDeg) % 360.0 + 360.0) % 360.0

    fun satellite(
        observer: ObserverPoint,
        satellite: Satellite,
        declinationDeg: Double,
        altitudeM: Double = 0.0
    ): RemoteResult {
        val angles = SatelliteCalculator.calculate(
            obsLat = observer.lat,
            obsLon = observer.lon,
            satLon = satellite.orbitalLon,
            altitudeM = altitudeM
        )
        return RemoteResult(
            observerLabel      = observer.label,
            targetLabel        = etiquetaSatelite(satellite.name, satellite.positionLabel),
            azimuthTrueDeg     = angles.azimuthDeg,
            azimuthMagneticDeg = toMagnetic(angles.azimuthDeg, declinationDeg),
            elevationDeg       = angles.elevationDeg,
            declinationDeg     = declinationDeg,
            visible            = angles.visible
        )
    }
}
