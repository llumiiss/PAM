package pl.pam.startproject.data

import org.json.JSONArray

/** Kompaktowy JSON: [[tMs,vKmh], ...] */
object SpeedProfileCodec {

    fun encode(samples: List<Pair<Long, Float>>): String? {
        if (samples.isEmpty()) return null
        val arr = JSONArray()
        for ((t, v) in samples) {
            val pair = JSONArray()
            pair.put(t)
            pair.put(v.toDouble())
            arr.put(pair)
        }
        return arr.toString()
    }

    fun decode(json: String?): List<Pair<Long, Float>> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val pair = arr.getJSONArray(i)
                    val t = pair.getLong(0)
                    val v = pair.getDouble(1).toFloat()
                    add(t to v)
                }
            }
        }.getOrElse { emptyList() }
    }
}
