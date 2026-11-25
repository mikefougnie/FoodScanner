package com.mikefougnie.foodscanner

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class HistoryEntry(val product: String, val text: String, val verdict: String, val time: Long, val kosher: Boolean)

/** Settings and recent scans, kept on the phone. */
class Store(context: Context) {
    private val p = context.getSharedPreferences("foodscanner", Context.MODE_PRIVATE)

    fun loadSettings() = Settings(
        blood = p.getBoolean("blood", true),
        fat = p.getBoolean("fat", false),
        caution = p.getBoolean("caution", true),
        dyes = p.getBoolean("dyes", false),
        petro = p.getBoolean("petro", false),
        kosher = p.getBoolean("kosher", false),
        allergens = p.getStringSet("allergens", emptySet())?.toSet() ?: emptySet(),
    )

    fun saveSettings(s: Settings) {
        p.edit()
            .putBoolean("blood", s.blood).putBoolean("fat", s.fat).putBoolean("caution", s.caution)
            .putBoolean("dyes", s.dyes).putBoolean("petro", s.petro).putBoolean("kosher", s.kosher)
            .putStringSet("allergens", s.allergens.toSet())
            .apply()
    }

    fun loadHistory(): List<HistoryEntry> = try {
        val a = JSONArray(p.getString("history", "[]"))
        List(a.length()) { i ->
            val o = a.getJSONObject(i)
            HistoryEntry(o.optString("product"), o.optString("text"), o.optString("verdict"), o.optLong("time"), o.optBoolean("kosher"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun addHistory(e: HistoryEntry): List<HistoryEntry> {
        val list = (listOf(e) + loadHistory().filter { it.text != e.text }).take(30)
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().put("product", it.product).put("text", it.text).put("verdict", it.verdict)
                .put("time", it.time).put("kosher", it.kosher))
        }
        p.edit().putString("history", a.toString()).apply()
        return list
    }

    fun clearHistory() = p.edit().remove("history").apply()
}
