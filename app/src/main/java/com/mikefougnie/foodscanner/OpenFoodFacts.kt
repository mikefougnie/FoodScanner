package com.mikefougnie.foodscanner

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Product lookup by barcode in the free Open Food Facts database. */
object OpenFoodFacts {
    sealed interface Result {
        data class Found(
            val name: String,
            val ingredients: String,
            val allergens: List<String>,
            val traces: List<String>,
            val kosher: Boolean,
        ) : Result
        data class NoIngredients(val name: String) : Result
        data object NotFound : Result
        data class Failed(val message: String) : Result
    }

    fun pageUrl(code: String) = "https://world.openfoodfacts.org/product/$code"

    suspend fun lookup(code: String): Result = withContext(Dispatchers.IO) {
        try {
            val r = fetch(code)
            // Scanners report UPC-A as 12 digits; the database often stores the 13-digit EAN form.
            if (r is Result.NotFound && code.length == 12) fetch("0$code") else r
        } catch (e: Exception) {
            Result.Failed(e.message ?: "network error")
        }
    }

    private fun fetch(code: String): Result {
        val fields = "product_name,brands,ingredients_text_en,ingredients_text,labels_tags,allergens_tags,traces_tags"
        val c = URL("https://world.openfoodfacts.org/api/v2/product/$code.json?fields=$fields").openConnection() as HttpURLConnection
        c.setRequestProperty("User-Agent", "CleanFoodScanner/1.0 (Android)")
        c.connectTimeout = 8000
        c.readTimeout = 8000
        try {
            if (c.responseCode == 404) return Result.NotFound
            val body = c.inputStream.bufferedReader().use { it.readText() }
            val j = JSONObject(body)
            if (j.optInt("status") != 1) return Result.NotFound
            val p = j.getJSONObject("product")
            fun str(k: String) = p.optString(k).takeUnless { it.isBlank() || it == "null" } ?: ""
            fun tags(k: String) = p.optJSONArray(k)?.let { a -> List(a.length()) { a.getString(it) } } ?: emptyList()
            val name = listOf(str("brands"), str("product_name")).filter { it.isNotEmpty() }.joinToString(" – ")
            val ingredients = str("ingredients_text_en").ifEmpty { str("ingredients_text") }
            if (ingredients.isEmpty()) return Result.NoIngredients(name)
            return Result.Found(name, ingredients, tags("allergens_tags"), tags("traces_tags"),
                tags("labels_tags").any { "kosher" in it })
        } finally {
            c.disconnect()
        }
    }
}
