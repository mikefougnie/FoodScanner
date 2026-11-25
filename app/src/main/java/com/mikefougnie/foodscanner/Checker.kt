package com.mikefougnie.foodscanner

data class Hit(val sev: Sev, val name: String, val terms: List<String>, val why: String, val ref: String)
data class Mark(val start: Int, val end: Int, val sev: Sev)
data class Verdict(val sev: Sev?, val title: String, val detail: String)
data class Analysis(val hits: List<Hit>, val marks: List<Mark>, val verdict: Verdict)

object Checker {
    private val freeRe = Regex("""\b[\w-]+[- ]free\b""")
    private val mayRe = Regex(
        """(may contain|processed (in|on)|manufactured (in|on)|made (in|on) (a )?(facility|equipment|line)|shared (facility|equipment|line)|traces? of)""",
        RegexOption.IGNORE_CASE,
    )
    private val kosherWordRe = Regex("""\b(kosher|pareve|parve)\b""", RegexOption.IGNORE_CASE)

    /** True when the label text itself mentions kosher certification. */
    fun mentionsKosher(text: String) = kosherWordRe.containsMatchIn(text)

    private fun mask(s: String, re: Regex) = re.replace(s) { " ".repeat(it.value.length) }

    fun analyze(text: String, s: Settings, kosherSymbol: Boolean, extra: List<Hit> = emptyList()): Analysis {
        // Lowercase char by char so every index still lines up with the original text.
        val lower = mask(buildString(text.length) { text.forEach { append(it.lowercaseChar()) } }, freeRe)
        val mayAt = mayRe.find(text)?.range?.first ?: Int.MAX_VALUE

        fun scan(re: Regex, ex: List<Regex>): List<IntRange> {
            var t = lower
            ex.forEach { t = mask(t, it) }
            return re.findAll(t).map { it.range }.toList()
        }

        val used = mutableListOf<Pair<IntRange, String>>()
        val hits = mutableListOf<Hit>()
        val marks = mutableListOf<Mark>()

        fun add(sev: Sev, name: String, found: List<IntRange>, why: String, ref: String, cls: String) {
            val terms = linkedSetOf<String>()
            for (r in found.sortedBy { it.first }) {
                if (used.any { it.second == cls && r.first <= it.first.last && r.last >= it.first.first }) continue
                used += r to cls
                marks += Mark(r.first, r.last + 1, sev)
                val may = if (sev == Sev.ALLERGEN && r.first > mayAt) " (may contain)" else ""
                terms += text.substring(r.first, r.last + 1).trim() + may
            }
            if (terms.isNotEmpty()) hits += Hit(sev, name, terms.toList(), why, ref)
        }

        for (r in RULES) {
            if (!s.on(r.opt)) continue
            if (r.cert && kosherSymbol) continue
            val cls = when {
                r.opt == "kosher" -> "kosher"
                r.sev == Sev.AVOID -> r.opt ?: "avoid"
                else -> "scripture"
            }
            add(r.sev, r.name, scan(r.re, r.ex), r.why, r.ref, cls)
        }

        if (s.kosher) {
            val meat = scan(MEAT_RE, MEAT_EXCEPTIONS)
            val dairy = scan(DAIRY_RE, DAIRY_EXCEPTIONS)
            val fish = scan(FISH_RE, emptyList())
            if (meat.isNotEmpty() && dairy.isNotEmpty()) {
                add(Sev.UNCLEAN, "Meat with dairy", meat + dairy,
                    "Rabbinic law does not allow meat and dairy together.", "Ex 23:19 (rabbinic reading)", "kmix")
            } else if (meat.isNotEmpty() && !kosherSymbol) {
                add(Sev.CHECK, "Meat or poultry", meat, "Needs kosher slaughter and certification.", "rabbinic law", "kmeat")
            }
            if (meat.isNotEmpty() && fish.isNotEmpty()) {
                add(Sev.CHECK, "Fish with meat", fish, "Kosher custom avoids fish cooked with meat.", "rabbinic law", "kfish")
            }
        }

        for (a in ALLERGENS) {
            if (a.id in s.allergens) add(Sev.ALLERGEN, a.name, scan(a.re, a.ex), "On your allergen list.", "", "a-${a.id}")
        }

        // Extra findings (e.g. allergens listed by Open Food Facts) not already found in the text.
        for (e in extra) {
            val base = e.name.substringBefore(" (")
            if (hits.none { it.sev == e.sev && it.name == base }) hits += e
        }

        return Analysis(hits, marks.sortedBy { it.start }, verdict(hits))
    }

    private fun verdict(hits: List<Hit>): Verdict {
        fun of(sev: Sev) = hits.filter { it.sev == sev }
        val bad = of(Sev.UNCLEAN)
        val allergen = of(Sev.ALLERGEN)
        val avoid = of(Sev.AVOID)
        val check = of(Sev.CHECK)
        val also = (allergen + avoid).map { it.name }.distinct()
        return when {
            bad.isNotEmpty() -> Verdict(Sev.UNCLEAN, "Unclean",
                "Contains " + bad.joinToString(", ") { it.name.lowercase() } + "." +
                    if (also.isNotEmpty()) " Also: " + also.joinToString(", ") + "." else "")
            allergen.isNotEmpty() -> Verdict(Sev.ALLERGEN, "Contains your allergens",
                allergen.map { it.name }.distinct().joinToString(", ") + ".")
            avoid.isNotEmpty() -> Verdict(Sev.AVOID, "Contains things you avoid", avoid.joinToString(", ") { it.name } + ".")
            check.isNotEmpty() -> Verdict(Sev.CHECK, "Check before eating",
                "Nothing plainly unclean, but some ingredients could come from an unclean source.")
            else -> Verdict(null, "No problems found", "Nothing on this label matches what you have chosen to avoid.")
        }
    }

    /** Pull the ingredients list out of raw OCR text from a whole package panel. */
    fun extractIngredients(raw: String): String {
        val flat = raw.replace(Regex("""\s+"""), " ").trim()
        val start = Regex("""(?i)\bingredients?\s*[:;.]?""").find(flat)
        var t = if (start != null) flat.substring(start.range.last + 1) else flat
        val stop = Regex("""(?i)(nutrition facts|distributed by|best (by|before)|keep refrigerated|net wt|serving size)""").find(t)
        if (stop != null && stop.range.first > 20) t = t.substring(0, stop.range.first)
        return t.trim().trimStart(':', ';', '.', ' ')
    }

    /** Allergen findings from Open Food Facts tags, limited to the allergens the person chose. */
    fun offHits(allergenTags: List<String>, traceTags: List<String>, s: Settings): List<Hit> {
        val out = mutableListOf<Hit>()
        for ((tags, may) in listOf(allergenTags to "", traceTags to " (may contain)")) {
            for (tag in tags) {
                val id = OFF_ALLERGENS[tag] ?: continue
                if (id !in s.allergens) continue
                val a = ALLERGENS.first { it.id == id }
                out += Hit(Sev.ALLERGEN, a.name + may, emptyList(), "Listed by Open Food Facts.", "Open Food Facts")
            }
        }
        return out.distinctBy { it.name }
    }
}
