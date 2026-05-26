package com.example.ar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestiona el historial de búsquedas y sitios favoritos del usuario.
 * Usa SharedPreferences + JSON — sin base de datos, sin latencia.
 *
 * Capacidad: hasta MAX_HISTORY entradas. Los favoritos nunca se borran
 * con clearNonFavorites(). Ordenación: favoritos primero, luego por timestamp desc.
 */
object SearchHistoryManager {

    private const val PREFS = "synapse_prefs"
    private const val KEY   = "search_history_v1"
    private const val MAX_HISTORY = 30

    data class Entry(
        val name: String,
        val lat: Double,
        val lon: Double,
        val isFavorite: Boolean = false,
        val timestamp: Long = System.currentTimeMillis()
    )

    // ── API pública ───────────────────────────────────────────────────────────

    /** Agrega o actualiza una entrada. Si ya existe (misma lat/lon), actualiza timestamp. */
    fun add(context: Context, entry: Entry) {
        val list = getAll(context).toMutableList()
        val idx  = list.indexOfFirst { samePlace(it, entry) }
        if (idx >= 0) {
            // actualizar timestamp pero respetar estado de favorito existente
            list[idx] = list[idx].copy(timestamp = entry.timestamp)
        } else {
            list.add(0, entry)
        }
        // Mantener solo MAX_HISTORY, pero nunca borrar favoritos
        val trimmed = trim(list)
        save(context, trimmed)
    }

    /** Devuelve todo el historial: favoritos primero, luego por fecha desc. */
    fun getAll(context: Context): List<Entry> {
        return load(context).sortedWith(
            compareByDescending<Entry> { it.isFavorite }.thenByDescending { it.timestamp }
        )
    }

    /** Solo los favoritos marcados con estrella. */
    fun getFavorites(context: Context): List<Entry> =
        getAll(context).filter { it.isFavorite }

    /** Alterna el estado favorito de la entrada que coincide con lat/lon. */
    fun toggleFavorite(context: Context, lat: Double, lon: Double) {
        val list = load(context).toMutableList()
        val idx  = list.indexOfFirst { approxEqual(it.lat, lat) && approxEqual(it.lon, lon) }
        if (idx >= 0) {
            list[idx] = list[idx].copy(isFavorite = !list[idx].isFavorite)
            save(context, list)
        }
    }

    /** Elimina una entrada del historial (no aplica si es favorito — usar toggleFavorite). */
    fun remove(context: Context, lat: Double, lon: Double) {
        val list = load(context).filter {
            !(approxEqual(it.lat, lat) && approxEqual(it.lon, lon))
        }
        save(context, list)
    }

    /** Borra todo el historial excepto los favoritos. */
    fun clearNonFavorites(context: Context) {
        save(context, load(context).filter { it.isFavorite })
    }

    /** Borra absolutamente todo (historial + favoritos). */
    fun clearAll(context: Context) {
        save(context, emptyList())
    }

    fun count(context: Context) = load(context).size
    fun favoriteCount(context: Context) = load(context).count { it.isFavorite }

    // ── Serialización ─────────────────────────────────────────────────────────

    private fun load(context: Context): List<Entry> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                Entry(
                    name       = obj.getString("name"),
                    lat        = obj.getDouble("lat"),
                    lon        = obj.getDouble("lon"),
                    isFavorite = obj.optBoolean("fav", false),
                    timestamp  = obj.optLong("ts", 0L)
                )
            }
        } catch (e: Exception) { emptyList() }
    }

    private fun save(context: Context, list: List<Entry>) {
        val arr = JSONArray()
        list.forEach { e ->
            arr.put(JSONObject().apply {
                put("name", e.name)
                put("lat",  e.lat)
                put("lon",  e.lon)
                put("fav",  e.isFavorite)
                put("ts",   e.timestamp)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun samePlace(a: Entry, b: Entry) =
        approxEqual(a.lat, b.lat) && approxEqual(a.lon, b.lon)

    /** Considera igual si la diferencia es < 0.001° (~100 m) */
    private fun approxEqual(a: Double, b: Double) = Math.abs(a - b) < 0.001

    /** Mantiene MAX_HISTORY entradas, eliminando primero las más viejas no favoritas. */
    private fun trim(list: List<Entry>): List<Entry> {
        if (list.size <= MAX_HISTORY) return list
        val favorites    = list.filter { it.isFavorite }
        val nonFavorites = list.filter { !it.isFavorite }
            .sortedByDescending { it.timestamp }
            .take(MAX_HISTORY - favorites.size)
        return (favorites + nonFavorites).sortedByDescending { it.timestamp }
    }
}
