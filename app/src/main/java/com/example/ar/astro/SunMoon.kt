package com.example.ar.astro

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * Posición y horarios del sol y la luna, sin internet. Fórmulas astronómicas de baja
 * precisión (del orden de 1 minuto para el sol, pocos minutos para la luna), de sobra
 * para decidir cuánta luz queda o dónde mirar. Azimut: grados desde el norte verdadero,
 * en sentido horario, igual que el resto de la app.
 */
object SunMoon {

    data class SkyPos(val azimuthDeg: Double, val elevationDeg: Double)

    /** [fraction]: parte iluminada (0..1). [phase]: 0 nueva, 0.25 cuarto creciente, 0.5 llena, 0.75 cuarto menguante. */
    data class MoonPhase(val fraction: Double, val phase: Double)

    /** Cada hora es el próximo cruce del horizonte; null si no ocurre en las próximas ~26 h (día o noche polar). */
    data class Times(val rise: Long?, val set: Long?)

    private const val RAD = PI / 180.0
    private const val DAY_MS = 86_400_000.0
    private const val OBLIQUITY = RAD * 23.4397
    private const val EARTH_RADIUS_KM = 6378.14

    /** Umbral de salida/puesta: refracción atmosférica + radio aparente del disco. */
    const val HORIZON_DEG = -0.833
    const val CIVIL_TWILIGHT_DEG = -6.0

    private fun toDays(ms: Long) = ms / DAY_MS - 10957.5   // días desde J2000

    private fun rightAscension(l: Double, b: Double) =
        atan2(sin(l) * cos(OBLIQUITY) - tan(b) * sin(OBLIQUITY), cos(l))

    private fun declination(l: Double, b: Double) =
        asin(sin(b) * cos(OBLIQUITY) + cos(b) * sin(OBLIQUITY) * sin(l))

    private class Coords(val ra: Double, val dec: Double, val distKm: Double)

    private fun sunCoords(d: Double): Coords {
        val m = RAD * (357.5291 + 0.98560028 * d)
        val c = RAD * (1.9148 * sin(m) + 0.02 * sin(2 * m) + 0.0003 * sin(3 * m))
        val l = m + c + RAD * 102.9372 + PI
        return Coords(rightAscension(l, 0.0), declination(l, 0.0), 149_598_000.0)
    }

    private fun moonCoords(d: Double): Coords {
        val l0 = RAD * (218.316 + 13.176396 * d)
        val m  = RAD * (134.963 + 13.064993 * d)
        val f  = RAD * (93.272 + 13.229350 * d)
        val l  = l0 + RAD * 6.289 * sin(m)
        val b  = RAD * 5.128 * sin(f)
        return Coords(rightAscension(l, b), declination(l, b), 385_001.0 - 20_905.0 * cos(m))
    }

    private fun position(d: Double, lat: Double, lon: Double, c: Coords): SkyPos {
        val phi = RAD * lat
        val h = RAD * (280.16 + 360.9856235 * d) - RAD * -lon - c.ra   // ángulo horario
        val alt = asin(sin(phi) * sin(c.dec) + cos(phi) * cos(c.dec) * cos(h))
        val azFromSouth = atan2(sin(h), cos(h) * sin(phi) - tan(c.dec) * cos(phi))
        val az = (azFromSouth / RAD + 180.0 + 360.0) % 360.0
        return SkyPos(az, alt / RAD)
    }

    fun sun(timeMs: Long, lat: Double, lon: Double): SkyPos {
        val d = toDays(timeMs)
        return position(d, lat, lon, sunCoords(d))
    }

    /** Elevación topocéntrica: descuenta el paralaje, que para la luna llega a casi 1°. */
    fun moon(timeMs: Long, lat: Double, lon: Double): SkyPos {
        val d = toDays(timeMs)
        val c = moonCoords(d)
        val geo = position(d, lat, lon, c)
        val parallax = asin(EARTH_RADIUS_KM / c.distKm)
        val el = geo.elevationDeg - (parallax * cos(geo.elevationDeg * RAD)) / RAD
        return SkyPos(geo.azimuthDeg, el)
    }

    fun moonPhase(timeMs: Long): MoonPhase {
        val d = toDays(timeMs)
        val s = sunCoords(d)
        val m = moonCoords(d)
        val phi = acos(sin(s.dec) * sin(m.dec) + cos(s.dec) * cos(m.dec) * cos(s.ra - m.ra))
        val inc = atan2(s.distKm * sin(phi), m.distKm - s.distKm * cos(phi))
        val angle = atan2(
            cos(s.dec) * sin(s.ra - m.ra),
            sin(s.dec) * cos(m.dec) - cos(s.dec) * sin(m.dec) * cos(s.ra - m.ra)
        )
        val fraction = (1 + cos(inc)) / 2
        val phase = 0.5 + 0.5 * inc * (if (angle < 0) -1 else 1) / PI
        return MoonPhase(fraction, phase)
    }

    fun sunTimes(nowMs: Long, lat: Double, lon: Double, thresholdDeg: Double = HORIZON_DEG) =
        crossings(nowMs, thresholdDeg) { sun(it, lat, lon).elevationDeg }

    fun moonTimes(nowMs: Long, lat: Double, lon: Double) =
        crossings(nowMs, HORIZON_DEG) { moon(it, lat, lon).elevationDeg }

    /** Primer cruce ascendente y primer cruce descendente de [thresholdDeg] después de [fromMs]. */
    private fun crossings(fromMs: Long, thresholdDeg: Double, elevationAt: (Long) -> Double): Times {
        val stepMs = 5 * 60_000L
        val spanMs = 26 * 3_600_000L
        var rise: Long? = null
        var set: Long? = null
        var prevT = fromMs
        var prev = elevationAt(prevT) - thresholdDeg
        var t = fromMs + stepMs
        while (t <= fromMs + spanMs && (rise == null || set == null)) {
            val cur = elevationAt(t) - thresholdDeg
            if (prev < 0 && cur >= 0 && rise == null) rise = refine(prevT, t, thresholdDeg, elevationAt)
            if (prev >= 0 && cur < 0 && set == null) set = refine(prevT, t, thresholdDeg, elevationAt)
            prevT = t
            prev = cur
            t += stepMs
        }
        return Times(rise, set)
    }

    private fun refine(a0: Long, b0: Long, thresholdDeg: Double, elevationAt: (Long) -> Double): Long {
        var a = a0
        var b = b0
        val aBelow = elevationAt(a) < thresholdDeg
        while (b - a > 1000) {
            val mid = (a + b) / 2
            val below = elevationAt(mid) < thresholdDeg
            if (below == aBelow) a = mid else b = mid
        }
        return (a + b) / 2
    }

    /** Muestras de la trayectoria del sol cada [stepMin] minutos, desde [startMs] durante 24 h. */
    fun sunPath(startMs: Long, lat: Double, lon: Double, stepMin: Int = 15): List<Pair<Long, SkyPos>> {
        val n = 24 * 60 / stepMin
        return (0..n).map { i ->
            val t = startMs + i * stepMin * 60_000L
            t to sun(t, lat, lon)
        }
    }

    /** Diferencia angular absoluta entre dos azimuts (0..180). */
    fun azimuthDelta(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }
}
