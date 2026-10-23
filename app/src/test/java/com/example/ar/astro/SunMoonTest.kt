package com.example.ar.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.time.Instant
import org.junit.Test

class SunMoonTest {

    private fun ms(iso: String) = Instant.parse(iso).toEpochMilli()

    // Buenos Aires
    private val lat = -34.6037
    private val lon = -58.3816

    @Test
    fun sunIsNearZenithAtEquatorOnEquinoxNoon() {
        val pos = SunMoon.sun(ms("2026-03-20T12:07:00Z"), 0.0, 0.0)
        assertTrue("elevación ${pos.elevationDeg}", pos.elevationDeg > 88.0)
    }

    @Test
    fun sunRisesInTheEastAndSetsInTheWest() {
        val start = ms("2026-09-23T03:00:00Z")   // medianoche local de Buenos Aires, cerca del equinoccio
        val t = SunMoon.sunTimes(start, lat, lon)
        val rise = SunMoon.sun(t.rise!!, lat, lon)
        val set = SunMoon.sun(t.set!!, lat, lon)
        assertTrue("sale en ${rise.azimuthDeg}", SunMoon.azimuthDelta(rise.azimuthDeg, 90.0) < 6.0)
        assertTrue("se pone en ${set.azimuthDeg}", SunMoon.azimuthDelta(set.azimuthDeg, 270.0) < 6.0)
    }

    @Test
    fun summerSolsticeDayLengthInBuenosAires() {
        // Referencia publicada: ~14 h 22 min el 21 de diciembre.
        val t = SunMoon.sunTimes(ms("2026-12-21T03:00:00Z"), lat, lon)
        val hours = (t.set!! - t.rise!!) / 3_600_000.0
        assertTrue("duración $hours h", hours in 14.2..14.6)
    }

    @Test
    fun winterSolsticeDayIsShorter() {
        val t = SunMoon.sunTimes(ms("2026-06-21T03:00:00Z"), lat, lon)
        val hours = (t.set!! - t.rise!!) / 3_600_000.0
        // cos H = (sin h0 − sin φ sin δ) / (cos φ cos δ) → 9,83 h para φ = −34,6° y δ = +23,44°
        assertTrue("duración $hours h", hours in 9.75..9.9)
    }

    @Test
    fun polarNightHasNoSunrise() {
        val t = SunMoon.sunTimes(ms("2026-12-21T00:00:00Z"), 80.0, 0.0)
        assertEquals(null, t.rise)
        assertEquals(null, t.set)
    }

    @Test
    fun civilTwilightEndsAfterSunset() {
        val now = ms("2026-12-21T03:00:00Z")
        val sunset = SunMoon.sunTimes(now, lat, lon).set!!
        val dusk = SunMoon.sunTimes(now, lat, lon, SunMoon.CIVIL_TWILIGHT_DEG).set!!
        val minutes = (dusk - sunset) / 60_000.0
        assertTrue("diferencia $minutes min", minutes in 15.0..40.0)
    }

    @Test
    fun newMoonIsDarkAndFullMoonIsBright() {
        // Luna nueva 17/feb/2026 y llena 3/ene/2026.
        assertTrue(SunMoon.moonPhase(ms("2026-02-17T12:00:00Z")).fraction < 0.03)
        assertTrue(SunMoon.moonPhase(ms("2026-01-03T10:00:00Z")).fraction > 0.97)
    }

    @Test
    fun phaseGrowsFromNewToFull() {
        val newMoon = SunMoon.moonPhase(ms("2026-02-17T12:00:00Z")).phase
        val firstQuarter = SunMoon.moonPhase(ms("2026-02-24T12:00:00Z")).phase
        assertTrue("nueva $newMoon", newMoon < 0.05 || newMoon > 0.95)
        assertTrue("cuarto creciente $firstQuarter", firstQuarter in 0.2..0.35)
    }

    @Test
    fun moonRisesAndSetsWithinAboutADay() {
        val t = SunMoon.moonTimes(ms("2026-10-23T03:00:00Z"), lat, lon)
        assertNotNull(t.rise)
        assertNotNull(t.set)
    }
}
