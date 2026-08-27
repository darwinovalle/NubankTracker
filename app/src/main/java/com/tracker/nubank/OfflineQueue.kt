package com.tracker.nubank

import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONException

/**
 * A small persistent queue of rows that failed to reach Google Sheets (offline,
 * auth issue, transient API error). Rows are a JSON array in SharedPreferences so
 * they survive service restarts.
 *
 * A row is `[fecha, monto, comercio, notificacionOriginal]`.
 */
class OfflineQueue(private val prefs: SharedPreferences) {

    @Synchronized
    fun add(row: List<String>) {
        val arr = read()
        if (arr.length() >= MAX_SIZE) {
            val dropped = arr.remove(0)
            Log.w(TAG, "Cola llena, descartando fila más antigua: $dropped")
        }
        arr.put(JSONArray(row))
        write(arr)
    }

    /** First row without removing it, or `null` when empty. */
    @Synchronized
    fun peek(): List<String>? {
        val arr = read()
        if (arr.length() == 0) return null
        val sub = arr.getJSONArray(0)
        return (0 until sub.length()).map { j -> sub.getString(j) }
    }

    @Synchronized
    fun all(): List<List<String>> {
        val arr = read()
        return (0 until arr.length()).map { i ->
            val sub = arr.getJSONArray(i)
            (0 until sub.length()).map { j -> sub.getString(j) }
        }
    }

    @Synchronized
    fun removeFirst(n: Int) {
        val arr = read()
        val toRemove = minOf(n, arr.length())
        for (i in 0 until toRemove) arr.remove(0)
        write(arr)
    }

    @Synchronized
    fun size(): Int = read().length()

    @Synchronized
    fun clear() = write(JSONArray())

    private fun read(): JSONArray {
        return try {
            JSONArray(prefs.getString(KEY, "[]") ?: "[]")
        } catch (e: JSONException) {
            JSONArray()
        }
    }

    private fun write(arr: JSONArray) {
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    companion object {
        const val KEY = "pending_rows"
        const val MAX_SIZE = 100
        const val MAX_ROWS_PER_DRAIN = 20
        private const val TAG = "NubankTracker"
    }
}
