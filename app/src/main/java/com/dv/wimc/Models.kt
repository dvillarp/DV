package com.dv.wimc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Un aparcamiento guardado. acc == null => posición manual / sin precisión conocida. */
data class ParkPoint(
    val lat: Double,
    val lng: Double,
    val ts: Long,
    val acc: Float?,
    val source: String = "gps",
)

fun fmtCoord(v: Double): String = String.format(Locale.US, "%.5f", v)
fun coordStr(v: Double): String = String.format(Locale.US, "%.6f", v)
fun fmtDate(ts: Long): String =
    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(ts))

fun fmtAgo(ts: Long, now: Long): String {
    val m = ((now - ts) / 60_000L).coerceAtLeast(0L)
    return when {
        m < 1L -> "hace menos de 1 min"
        m < 60L -> "hace $m min"
        m < 1440L -> "hace ${m / 60} h ${m % 60} min"
        else -> "hace ${m / 1440} d"
    }
}

fun accLabel(acc: Float): String = if (acc <= 20f) "Excelente" else "Media"

/** Persistencia simple en SharedPreferences (claves wimc_parked / wimc_history), usable también desde receivers. */
class Store(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("wimc", Context.MODE_PRIVATE)

    private fun toJson(p: ParkPoint): JSONObject = JSONObject()
        .put("lat", p.lat)
        .put("lng", p.lng)
        .put("ts", p.ts)
        .put("acc", p.acc?.toDouble() ?: JSONObject.NULL)
        .put("src", p.source)

    private fun fromJson(o: JSONObject): ParkPoint = ParkPoint(
        lat = o.getDouble("lat"),
        lng = o.getDouble("lng"),
        ts = o.getLong("ts"),
        acc = if (o.isNull("acc")) null else o.getDouble("acc").toFloat(),
        source = o.optString("src", "gps"),
    )

    fun parked(): ParkPoint? =
        sp.getString("wimc_parked", null)?.let { s -> runCatching { fromJson(JSONObject(s)) }.getOrNull() }

    fun setParked(p: ParkPoint?) {
        sp.edit().apply {
            if (p == null) remove("wimc_parked") else putString("wimc_parked", toJson(p).toString())
        }.apply()
    }

    fun history(): List<ParkPoint> {
        val s = sp.getString("wimc_history", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(s)
            (0 until arr.length()).map { fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun writeHistory(list: List<ParkPoint>) {
        val arr = JSONArray()
        list.forEach { arr.put(toJson(it)) }
        sp.edit().putString("wimc_history", arr.toString()).apply()
    }

    /** Guarda como aparcamiento actual y lo añade al principio del historial (máx. 50). */
    fun savePark(p: ParkPoint) {
        setParked(p)
        writeHistory((listOf(p) + history()).take(50))
    }

    fun clearHistory() {
        sp.edit().remove("wimc_history").apply()
    }

    var autoDetect: Boolean
        get() = sp.getBoolean("auto_detect", false)
        set(v) = sp.edit().putBoolean("auto_detect", v).apply()

    var autoPark: Boolean
        get() = sp.getBoolean("auto_park", false)
        set(v) = sp.edit().putBoolean("auto_park", v).apply()

    var driving: Boolean
        get() = sp.getBoolean("driving", false)
        set(v) = sp.edit().putBoolean("driving", v).apply()
}
