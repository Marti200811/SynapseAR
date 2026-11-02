package com.example.ar.astro

import android.app.Dialog
import android.location.Location
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import com.example.ar.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Horarios de sol y luna, y cuánta luz de día queda, para el lugar donde estás.
 *
 * Constructor sin parámetros a propósito: al recrear la Activity (tamaño de letra, tema,
 * idioma) `FragmentFactory` reinstancia los diálogos abiertos con el constructor vacío, y
 * si no existe tira `Fragment$InstantiationException`. La ubicación entra por [newInstance].
 *
 * No devuelve nada: solo muestra horarios, así que no tiene clave de resultado ni listener.
 */
class SunMoonDialog : DialogFragment() {

    companion object {
        private const val ARG_LOCATION = "location"

        /** @param location Lugar del que se calculan los horarios; sin él, el diálogo avisa que no hay GPS. */
        fun newInstance(location: Location?) = SunMoonDialog().apply {
            arguments = Bundle().apply { putParcelable(ARG_LOCATION, location) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.sunmoon_title))
            .setPositiveButton(android.R.string.ok, null)
        val location = arguments?.let {
            BundleCompat.getParcelable(it, ARG_LOCATION, Location::class.java)
        }
        val loc = location ?: return builder.setMessage(getString(R.string.sunmoon_no_gps)).create()

        val now = System.currentTimeMillis()
        val sun = SunMoon.sun(now, loc.latitude, loc.longitude)
        val sunTimes = SunMoon.sunTimes(now, loc.latitude, loc.longitude)
        val dusk = SunMoon.sunTimes(now, loc.latitude, loc.longitude, SunMoon.CIVIL_TWILIGHT_DEG)
        val moonTimes = SunMoon.moonTimes(now, loc.latitude, loc.longitude)
        val phase = SunMoon.moonPhase(now)

        val lines = mutableListOf<String>()
        if (sun.elevationDeg > SunMoon.HORIZON_DEG) {
            val left = sunTimes.set
            lines += if (left != null) getString(R.string.sunmoon_daylight_left, duration(left - now))
                     else getString(R.string.sunmoon_sun_all_day)
        } else {
            lines += getString(R.string.sunmoon_sun_down)
        }
        lines += getString(R.string.sunmoon_sunrise, time(sunTimes.rise, now))
        lines += getString(R.string.sunmoon_sunset, time(sunTimes.set, now))
        lines += getString(R.string.sunmoon_civil_dusk, time(dusk.set, now))
        lines += ""
        lines += getString(R.string.sunmoon_moon_phase, phaseName(phase.phase), (phase.fraction * 100).toInt())
        lines += getString(R.string.sunmoon_moonrise, time(moonTimes.rise, now))
        lines += getString(R.string.sunmoon_moonset, time(moonTimes.set, now))
        lines += ""
        lines += getString(R.string.sunmoon_note)

        return builder.setMessage(lines.joinToString("\n")).create()
    }

    private fun time(ms: Long?, now: Long): String {
        if (ms == null) return "--"
        val text = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(ms))
        val a = Calendar.getInstance().apply { timeInMillis = now }
        val b = Calendar.getInstance().apply { timeInMillis = ms }
        val sameDay = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
        return if (sameDay) text else "$text (+1)"
    }

    private fun duration(ms: Long): String {
        val minutes = (ms / 60_000L).coerceAtLeast(0L)
        return "%d h %02d min".format(minutes / 60, minutes % 60)
    }

    private fun phaseName(phase: Double): String {
        val idx = (((phase * 8) + 0.5).toInt()) % 8   // 0 nueva … 4 llena
        return getString(
            intArrayOf(
                R.string.moon_new, R.string.moon_waxing_crescent, R.string.moon_first_quarter,
                R.string.moon_waxing_gibbous, R.string.moon_full, R.string.moon_waning_gibbous,
                R.string.moon_last_quarter, R.string.moon_waning_crescent
            )[idx]
        )
    }
}
