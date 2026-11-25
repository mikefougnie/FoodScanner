package com.mikefougnie.foodscanner

/**
 * The ingredient rules. Scripture rules follow Leviticus 11 and Deuteronomy 14;
 * personal-observance, allergen and full-kosher rules are switched on in Settings.
 * Every pattern runs against a lowercased copy of the ingredient text that keeps
 * the original character positions, so matches can be highlighted in place.
 */
enum class Sev(val label: String) { UNCLEAN("unclean"), ALLERGEN("allergen"), AVOID("avoid"), CHECK("check") }

data class Rule(
    val sev: Sev,
    val name: String,
    val re: Regex,
    val ex: List<Regex> = emptyList(),
    val why: String,
    val ref: String,
    val opt: String? = null,
    /** Skipped when the package carries a kosher symbol. */
    val cert: Boolean = false,
)

data class Allergen(val id: String, val name: String, val re: Regex, val ex: List<Regex> = emptyList())

data class Settings(
    val blood: Boolean = true,
    val fat: Boolean = false,
    val caution: Boolean = true,
    val dyes: Boolean = false,
    val petro: Boolean = false,
    val kosher: Boolean = false,
    val allergens: Set<String> = emptySet(),
) {
    fun on(opt: String?): Boolean = when (opt) {
        null -> true
        "blood" -> blood
        "fat" -> fat
        "caution" -> caution
        "dyes" -> dyes
        "petro" -> petro
        "kosher" -> kosher
        else -> false
    }
}

/** Whole-word alternation. */
private fun w(alternatives: String) = Regex("""\b(""" + alternatives + """)\b""")
private fun x(vararg patterns: String) = patterns.map { Regex(it) }

private const val N = """(?:no\.? ?|# ?)?"""

private const val SHELL = """shrimps?|prawns?|crabs?|crabmeat|lobsters?|langoustines?|crawfish|crayfish|clams?|oysters?|mussels?|scallops?|squid|calamari|octopus|cuttlefish|abalone|conch|whelks?|sea urchins?|krill|cockles?|periwinkles?|escargots?|snails?"""
private val SHELL_EX = x("""\bcrab ?apples?\b""", """\bimitation crab\b""", """\b(oyster|lobster) mushrooms?\b""")

private const val FISH = """fish|anchov(y|ies)|cod|salmon|tuna|tilapia|pollock|haddock|halibut|sardines?|trout|mackerel|herring|bass|flounder|sole|snapper|mahi[- ]?mahi|surimi|fish sauce|fish oil|worcestershire|catfish|eels?|sharks?|swordfish|sturgeon"""

private const val DAIRY = """nonfat dry milk|milk solids|milkfat|buttermilk|milk|butter|cream|cheeses?|whey|sodium caseinate|caseinates?|casein|lactose|lactalbumin|yogh?urt|ghee|curds?|kefir"""
private val DAIRY_EX = x(
    """\b(coconut|almond|oat|soy|rice|cashew|hemp|pea) (milk|cream|yogh?urt|cheese)\b""",
    """\b(cocoa|peanut|shea|nut|apple|almond|cashew|seed|sunflower|mango) butter\b""",
    """\bcream of tartar\b""",
    """\bbutternut\b""",
)

private const val MEAT = """beef fat|chicken fat|beef|veal|chicken|turkey|lamb|mutton|goat|venison|duck|goose|poultry|bison|buffalo|meats|meat|sausages?|pepperoni|salami|bologna|hot ?dogs?|meatballs?|tallow|schmaltz"""
private val MEAT_EX = x("""\b(coconut|nut|crab|imitation) meat\b""", """\b(plant[- ]based|meatless|vegan|veggie) \w+""")

private const val WHEAT = """wheat|flour|semolina|durum|spelt|farina|bulgur|couscous|seitan|einkorn|emmer|kamut|graham|farro"""
private val FLOUR_EX = x("""\b(rice|corn|almond|coconut|oat|potato|tapioca|chickpea|cassava|buckwheat|soy|sorghum|teff|quinoa|bean|banana|millet|arrowroot) flour\b""")

val ALLERGENS = listOf(
    Allergen("milk", "Milk", w(DAIRY), DAIRY_EX),
    Allergen("egg", "Eggs", w("""egg whites?|egg yolks?|eggs?|albumin|albumen|ovalbumin|mayonnaise|meringue|lysozyme|ovomucoid|eggnog""")),
    Allergen("fish", "Fish", w(FISH), x("""\bshellfish\b""")),
    Allergen("shellfish", "Shellfish", w("$SHELL|shellfish|crustaceans?|molluscs?"), SHELL_EX),
    Allergen("peanut", "Peanuts", w("""peanut butter|peanut oil|peanuts?|groundnuts?|arachis|monkey nuts?""")),
    Allergen("treenut", "Tree nuts", w("""almonds?|walnuts?|pecans?|cashews?|pistachios?|hazelnuts?|filberts?|macadamia nuts?|macadamias?|brazil nuts?|pine nuts?|chestnuts?|praline|marzipan|gianduja|nut butter|nut paste|tree nuts?"""), x("""\bwater chestnuts?\b""")),
    Allergen("wheat", "Wheat", w(WHEAT), FLOUR_EX),
    Allergen("gluten", "Gluten", w("""$WHEAT|barley|rye|malt vinegar|malted|malt|triticale|brewer'?s yeast|gluten"""), FLOUR_EX),
    Allergen("soy", "Soy", w("""soybeans?|soya|soy|tofu|edamame|miso|tempeh|tamari|shoyu""")),
    Allergen("sesame", "Sesame", w("""sesame|tahini|benne|gingelly""")),
    Allergen("corn", "Corn", w("""corn syrup|corn ?starch|cornstarch|cornmeal|cornflour|popcorn|corn|maize|polenta|hominy|masa|grits""")),
    Allergen("mustard", "Mustard", w("""mustard""")),
    Allergen("sulfite", "Sulfites", w("""sulfites?|sulphites?|sulfur dioxide|sulphur dioxide|(sodium|potassium) (meta)?bisulfite|sodium sulfite|e ?22[0-8]""")),
)

val RULES = listOf(
    // ---- Scripture: unclean ----
    Rule(Sev.UNCLEAN, "Pork",
        w("""pork rinds?|pork|pigs?|swine|hogs?|porcine|lardons?|lard|bacon|hams?|prosciutto|pancetta|guanciale|mortadella|chicharr[oó]n(es)?|cracklings?|jam[oó]n|speck|capicola|coppa"""),
        x("""\b(imitation|artificial|turkey|beef|chicken|vegan|plant[- ]based|meatless|soy|veggie) (bacon|ham|pork)\b""", """\b(bacon|ham|pork)[- ]flavou?r(ed|ing|s)?\b"""),
        "The swine divides the hoof but does not chew the cud.", "Lev 11:7; Deut 14:8"),
    Rule(Sev.UNCLEAN, "Shellfish and sea creatures", w(SHELL), SHELL_EX,
        "Whatever in the waters has not fins and scales is an abomination.", "Lev 11:9–12; Deut 14:9–10"),
    Rule(Sev.UNCLEAN, "Fish without scales",
        w("""catfish|eels?|sharks?|swordfish|monkfish|sturgeon|caviar|paddlefish|skate|stingrays?|lampreys?|puffer ?fish|fugu|bullheads?"""),
        x("""\b(salmon|trout|whitefish|lumpfish) (caviar|roe)\b"""),
        "Fish must have both fins and scales.", "Lev 11:9–12; Deut 14:9–10"),
    Rule(Sev.UNCLEAN, "Unclean land animals",
        w("""rabbits?|hares?|horse ?meat|horse|camels?|kangaroo|bear meat|squirrels?|donkey|mule|dog meat|cat meat"""),
        x("""\bhorse ?radish\b"""),
        "Clean animals both divide the hoof and chew the cud.", "Lev 11:3–8, 26–27"),
    Rule(Sev.UNCLEAN, "Reptiles and amphibians",
        w("""frog legs?|frogs?|turtle meat|turtle soup|alligator|crocodile|snake meat|iguana"""),
        why = "Creeping things are unclean.", ref = "Lev 11:29–31, 41–43"),
    Rule(Sev.UNCLEAN, "Insect-derived",
        w("""carminic acid|carmine|cochineal|e ?120|natural red ?4|crimson lake|shellac|confectioner'?s glaze|resinous glaze|pharmaceutical glaze|e ?904|lac resin"""),
        x("""\bindigo carmine\b"""),
        "Made from scale insects. Only locusts, crickets and grasshoppers are clean.", "Lev 11:20–23"),
    Rule(Sev.UNCLEAN, "Unclean birds", w("""ostrich|emu|vultures?|swans?"""),
        why = "Listed among the birds not to be eaten.", ref = "Lev 11:13–19; Deut 14:11–19"),
    Rule(Sev.UNCLEAN, "Blood",
        w("""blood sausage|blood plasma|blood meal|blood|black pudding|plasma protein|ha?emoglobin|morcilla|boudin noir"""),
        x("""\bblood oranges?\b"""),
        "You shall eat the blood of no manner of flesh.", "Lev 17:10–14; Acts 15:29", opt = "blood"),
    Rule(Sev.UNCLEAN, "Suet and tallow", w("""suet|tallow|beef fat|mutton fat|beef dripping|dripping"""),
        why = "Fat of ox, sheep or goat.", ref = "Lev 3:17; 7:23", opt = "fat"),

    // ---- Scripture: check the source ----
    Rule(Sev.CHECK, "Gelatin or collagen",
        w("""gelatine?|e ?441|hydroly[sz]ed collagen|collagen peptides|collagen"""),
        x("""\b(beef|bovine|fish) (gelatine?|collagen( peptides)?)\b""", """\b(gelatine?|collagen)\s*\((beef|bovine|fish)[^)]*\)"""),
        "Most gelatin sold is from pigs unless labeled beef or fish.", "check the source", opt = "caution"),
    Rule(Sev.CHECK, "Fats and emulsifiers",
        w("""mono-? ?and diglycerides|mono ?& ?diglycerides|monoglycerides|diglycerides|e ?47[0-9][a-f]?|glycerine?|glycerol|e ?422|stearic acid|e ?570|(magnesium|calcium) stearate|e ?572|polysorbate ?\d*|sodium stearoyl lactylate|e ?481|animal fat|shortening"""),
        x("""\b(vegetable|plant|soy|palm) (glycerine?|glycerol|mono-? ?and diglycerides|mono ?& ?diglycerides|stearic acid|shortening|mono ?glycerides)\b""",
          """\b(glycerine?|mono-? ?and diglycerides|mono ?& ?diglycerides|stearic acid|shortening)\s*\((from )?(vegetable|plant|soy|palm|canola|sunflower)[^)]*\)"""),
        "Can come from pork fat. Often plant-based; look for \"vegetable.\"", "check the source", opt = "caution"),
    Rule(Sev.CHECK, "Enzymes",
        w("""pepsin|animal rennet|rennet|lipase|enzymes?|l-cysteine|cysteine|e ?920|trypsin|pancreatin"""),
        x("""\b(microbial|vegetable|plant|fungal|non-animal) (rennet|enzymes?|lipase)\b"""),
        "Pepsin, lipase and some L-cysteine can come from pigs.", "check the source", opt = "caution"),
    Rule(Sev.CHECK, "Meat of unstated animal",
        w("""sausages?|pepperoni|salami|chorizo|hot ?dogs?|frankfurters?|wieners?|bologna|bratwurst|kielbasa|liverwurst|p[aâ]t[eé]|meatballs?|meats|meat|broth|stock|bouillon|gravy|natural casings?|casings?"""),
        x("""\b(chicken|beef|turkey|vegetable|veggie|fish|mushroom|lamb) (broth|stock|bouillon|gravy|meat|meatballs?)\b""",
          """\b(coconut|nut|crab) meat\b""", """\bcellulose casings?\b""",
          """\b(beef|chicken|turkey) (sausages?|hot ?dogs?|frankfurters?|pepperoni|salami|bologna)\b"""),
        "Often pork, or stuffed in pork casings. Check which animal.", "check the source", opt = "caution"),
    Rule(Sev.CHECK, "Pork or seafood flavoring",
        w("""(bacon|ham|pork)[- ]flavou?r(ed|ing|s)?|(imitation|artificial) bacon|imitation crab|surimi|seafood (extract|flavou?r(ing)?)"""),
        why = "May be made from the real thing, or may be plant-based.", ref = "check the source", opt = "caution"),
    Rule(Sev.CHECK, "Bone-derived", w("""bone phosphate|e ?542|bone char|bone meal"""),
        why = "May come from pigs.", ref = "check the source", opt = "caution"),
    Rule(Sev.CHECK, "Natural flavors", w("""natural flavou?rings?|natural flavou?rs?"""),
        x("""\bnatural (vanilla|lemon|lime|orange|fruit|mint)\b"""),
        "Can occasionally contain animal extracts.", "check the source", opt = "caution"),

    // ---- Personal observance ----
    Rule(Sev.AVOID, "Petroleum-based dyes",
        w("""red ?${N}40|red ?${N}3|yellow ?${N}[56]|blue ?${N}[12]|green ?${N}3|citrus red ?${N}2|orange b|fd ?& ?c [a-z]+ ?${N}\d+|allura red( ac)?|tartrazine|sunset yellow( fcf)?|brilliant blue( fcf)?|indigo ?carmine|indigotine|erythrosine?|fast green( fcf)?|ponceau( 4r)?|quinoline yellow|azorubine|carmoisine|e ?1(02|04|10|22|24|27|29|31|32|33|42|43|51|55)"""),
        why = "Synthetic color made from petroleum.", ref = "your setting", opt = "dyes"),
    Rule(Sev.AVOID, "Petroleum-based additives",
        w("""bha|bht|tbhq|butylated hydroxy(anisole|toluene)|tert-?butylhydroquinone|propyl gallate|e ?3(10|19|20|21)|paraffin|petrolatum|mineral oil|petroleum|microcrystalline wax|polyethylene"""),
        why = "Made from petroleum.", ref = "your setting", opt = "petro"),

    // ---- Full kosher (simple rules; meat-with-dairy is in Checker) ----
    Rule(Sev.CHECK, "Wine or grape juice",
        w("""wine vinegar|grape (juice )?concentrate|grape juice|grape must|wine|brandy|cognac|balsamic( vinegar)?"""),
        why = "Grape wine and juice need kosher supervision.", ref = "rabbinic law", opt = "kosher", cert = true),
    Rule(Sev.CHECK, "Cheese", w("""cheeses?|parmesan|cheddar|mozzarella|romano|swiss cheese"""), DAIRY_EX,
        "Cheese needs kosher supervision because of rennet.", "rabbinic law", opt = "kosher", cert = true),
)

internal val MEAT_RE = w(MEAT)
internal val MEAT_EXCEPTIONS = MEAT_EX
internal val DAIRY_RE = w(DAIRY)
internal val DAIRY_EXCEPTIONS = DAIRY_EX
internal val FISH_RE = w(FISH)

/** Open Food Facts allergen tags mapped to our allergen ids. */
val OFF_ALLERGENS = mapOf(
    "en:milk" to "milk", "en:eggs" to "egg", "en:fish" to "fish", "en:crustaceans" to "shellfish",
    "en:molluscs" to "shellfish", "en:peanuts" to "peanut", "en:nuts" to "treenut", "en:gluten" to "gluten",
    "en:soybeans" to "soy", "en:sesame-seeds" to "sesame", "en:mustard" to "mustard",
    "en:sulphur-dioxide-and-sulphites" to "sulfite",
)
