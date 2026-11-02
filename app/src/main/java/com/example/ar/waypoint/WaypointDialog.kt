package com.example.ar.waypoint

import android.app.Dialog
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.example.ar.R
import com.example.ar.util.GeoUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Lista de puntos guardados. Tocar uno lo fija como objetivo ("volver al punto");
 * mantener apretado lo borra.
 *
 * Constructor sin parámetros a propósito: al recrear la Activity (tamaño de letra, tema,
 * idioma) `FragmentFactory` reinstancia los diálogos abiertos con el constructor vacío, y
 * si no existe tira `Fragment$InstantiationException`. Los datos entran por [newInstance]
 * y lo que eligió el usuario sale por `setFragmentResult` con [RESULT_KEY]; quien escucha
 * registra el listener en `onViewCreated`, nunca dentro del `setOnClickListener`.
 *
 * Son dos acciones y una sola clave de resultado: el Bundle lleva [RESULT_ACTION] como
 * discriminador ([ACTION_GO_TO] o [ACTION_CLEAR_TARGET]). Dos claves distintas obligarían
 * a registrar dos listeners para un mismo diálogo, y es fácil olvidarse de uno.
 */
class WaypointDialog : DialogFragment() {

    companion object {
        /** Clave del resultado. Quien escucha mira [RESULT_ACTION] y, si corresponde, [waypointFromResult]. */
        const val RESULT_KEY = "waypoint_dialog_result"

        /** Qué eligió el usuario: [ACTION_GO_TO] o [ACTION_CLEAR_TARGET]. */
        const val RESULT_ACTION = "action"

        /** Tocó un punto de la lista: hay que leerlo con [waypointFromResult]. */
        const val ACTION_GO_TO = "go_to"

        /** Tocó "quitar objetivo": no viene ningún punto. */
        const val ACTION_CLEAR_TARGET = "clear_target"

        private const val ARG_LOCATION = "location"
        private const val ARG_HAS_TARGET = "has_target"

        private const val RESULT_ID = "id"
        private const val RESULT_NAME = "name"
        private const val RESULT_LAT = "lat"
        private const val RESULT_LON = "lon"
        private const val RESULT_ALTITUDE = "altitude"

        /**
         * @param location  Desde dónde se mira: con GPS la lista va de más cerca a más
         *                  lejos y cada punto muestra su distancia. `Location` es
         *                  `Parcelable`, así que viaja entero en el Bundle.
         * @param hasTarget Si ya hay un objetivo fijado, para ofrecer "quitar objetivo".
         */
        fun newInstance(location: Location?, hasTarget: Boolean) = WaypointDialog().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_LOCATION, location)
                putBoolean(ARG_HAS_TARGET, hasTarget)
            }
        }

        /**
         * Rearma el [Waypoint] que viajó en el Bundle cuando la acción es [ACTION_GO_TO];
         * devuelve null para cualquier otra.
         *
         * Se lee junto con [resultBundle], que es su par: `Waypoint` no es `Parcelable`,
         * así que sus campos viajan sueltos. La altitud es opcional y se distingue por la
         * presencia de la clave: un punto sin altitud no es lo mismo que uno a 0 m.
         */
        fun waypointFromResult(bundle: Bundle): Waypoint? {
            if (bundle.getString(RESULT_ACTION) != ACTION_GO_TO) return null
            return Waypoint(
                id        = bundle.getLong(RESULT_ID),
                name      = bundle.getString(RESULT_NAME) ?: "",
                lat       = bundle.getDouble(RESULT_LAT),
                lon       = bundle.getDouble(RESULT_LON),
                altitudeM = if (bundle.containsKey(RESULT_ALTITUDE)) bundle.getDouble(RESULT_ALTITUDE) else null
            )
        }

        private fun resultBundle(waypoint: Waypoint) = Bundle().apply {
            putString(RESULT_ACTION, ACTION_GO_TO)
            putLong(RESULT_ID, waypoint.id)
            putString(RESULT_NAME, waypoint.name)
            putDouble(RESULT_LAT, waypoint.lat)
            putDouble(RESULT_LON, waypoint.lon)
            waypoint.altitudeM?.let { putDouble(RESULT_ALTITUDE, it) }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
        val location = arguments?.let {
            BundleCompat.getParcelable(it, ARG_LOCATION, Location::class.java)
        }
        val hasTarget = arguments?.getBoolean(ARG_HAS_TARGET) ?: false
        var points = WaypointManager.getAll(ctx).let { all ->
            // Con GPS, los más cercanos primero; sin GPS, los más nuevos.
            if (location == null) all
            else all.sortedBy { GeoUtils.distanceMeters(location.latitude, location.longitude, it.lat, it.lon) }
        }

        fun labels() = points.map { w ->
            val dist = location?.let {
                GeoUtils.formatDistance(GeoUtils.distanceMeters(it.latitude, it.longitude, w.lat, w.lon), ctx)
            }
            if (dist != null) "📍 ${w.name}  ·  $dist" else "📍 ${w.name}"
        }

        val builder = MaterialAlertDialogBuilder(ctx)
            .setTitle(getString(R.string.waypoints_title))
            .setNegativeButton(android.R.string.cancel, null)
        if (hasTarget) {
            builder.setNeutralButton(getString(R.string.waypoint_clear_target)) { _, _ ->
                setFragmentResult(
                    RESULT_KEY, Bundle().apply { putString(RESULT_ACTION, ACTION_CLEAR_TARGET) }
                )
            }
        }

        if (points.isEmpty()) {
            return builder.setMessage(getString(R.string.waypoints_empty)).create()
        }

        val adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labels().toMutableList())
        val dialog = builder
            .setAdapter(adapter) { _, which -> setFragmentResult(RESULT_KEY, resultBundle(points[which])) }
            .create()

        dialog.setOnShowListener {
            dialog.listView.setOnItemLongClickListener { _, _, pos, _ ->
                val point = points[pos]
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(point.name)
                    .setMessage(getString(R.string.waypoint_delete_confirm))
                    .setPositiveButton(getString(R.string.waypoint_delete)) { _, _ ->
                        WaypointManager.remove(ctx, point.id)
                        points = points.filterNot { it.id == point.id }
                        adapter.clear()
                        adapter.addAll(labels())
                        if (points.isEmpty()) dialog.dismiss()
                        Toast.makeText(ctx, getString(R.string.waypoint_deleted), Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
                true
            }
        }
        return dialog
    }
}
