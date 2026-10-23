package com.example.ar.waypoint

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Waypoint(
    val id: Long,
    val name: String,
    val lat: Double,
    val lon: Double,
    val altitudeM: Double?
)

/** Puntos guardados por el usuario (auto, carpa, refugio…). Solo en el dispositivo. */
object WaypointManager {

    private const val PREFS_NAME = "synapse_waypoints"
    private const val KEY_LIST   = "list"
    private const val MAX_POINTS = 100

    fun getAll(context: Context): List<Waypoint> {
        val raw = prefs(context).getString(KEY_LIST, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Waypoint(
                    id        = o.getLong("id"),
                    name      = o.getString("name"),
                    lat       = o.getDouble("lat"),
                    lon       = o.getDouble("lon"),
                    altitudeM = if (o.has("alt")) o.getDouble("alt") else null
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(context: Context, name: String, lat: Double, lon: Double, altitudeM: Double?): Waypoint {
        val point = Waypoint(System.currentTimeMillis(), name, lat, lon, altitudeM)
        // Más nuevo primero; al llegar al tope se descarta el más viejo.
        save(context, (listOf(point) + getAll(context)).take(MAX_POINTS))
        return point
    }

    fun remove(context: Context, id: Long) {
        save(context, getAll(context).filterNot { it.id == id })
    }

    private fun save(context: Context, list: List<Waypoint>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(JSONObject().apply {
                put("id", w.id)
                put("name", w.name)
                put("lat", w.lat)
                put("lon", w.lon)
                w.altitudeM?.let { put("alt", it) }
            })
        }
        prefs(context).edit().putString(KEY_LIST, arr.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
