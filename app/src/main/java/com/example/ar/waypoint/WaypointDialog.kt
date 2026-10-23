package com.example.ar.waypoint

import android.app.Dialog
import android.location.Location
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.example.ar.R
import com.example.ar.util.GeoUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Lista de puntos guardados. Tocar uno lo fija como objetivo ("volver al punto");
 * mantener apretado lo borra.
 */
class WaypointDialog(
    private val location: Location?,
    private val hasTarget: Boolean,
    private val onGoTo: (Waypoint) -> Unit,
    private val onClearTarget: () -> Unit
) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val ctx = requireContext()
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
            builder.setNeutralButton(getString(R.string.waypoint_clear_target)) { _, _ -> onClearTarget() }
        }

        if (points.isEmpty()) {
            return builder.setMessage(getString(R.string.waypoints_empty)).create()
        }

        val adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_list_item_1, labels().toMutableList())
        val dialog = builder
            .setAdapter(adapter) { _, which -> onGoTo(points[which]) }
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
