package com.mikefougnie.foodscanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckerTest {
    private val all = Settings(dyes = true, petro = true, kosher = true,
        allergens = setOf("milk", "wheat", "gluten", "soy", "peanut", "egg", "treenut"))

    private fun names(text: String, s: Settings = Settings(), kosher: Boolean = false) =
        Checker.analyze(text, s, kosher).hits.associate { it.name to it.terms }

    @Test fun porkAndShellfishAreUnclean() {
        val a = Checker.analyze("Pork, shrimp, sugar", Settings(), false)
        assertEquals(Sev.UNCLEAN, a.verdict.sev)
        assertTrue(names("Pork, shrimp, sugar").keys.containsAll(listOf("Pork", "Shellfish and sea creatures")))
    }

    @Test fun cleanFoodPasses() {
        val a = Checker.analyze("Enriched flour, vegetable shortening (palm), beef gelatin, salt, graham crackers, horseradish", Settings(), false)
        assertNull(a.verdict.sev)
    }

    @Test fun exceptionsAreNotFlagged() {
        val n = names("Blood orange juice, crab apple, gummy bears, turkey bacon, pork-free seasoning, eggplant")
        assertTrue(n.toString(), n.isEmpty())
    }

    @Test fun carmineButNotIndigoCarmine() {
        val n = names("Carmine (color), indigo carmine", Settings(dyes = true))
        assertEquals(listOf("Carmine"), n["Insect-derived"])
        assertEquals(listOf("indigo carmine"), n["Petroleum-based dyes"])
    }

    @Test fun cautionItems() {
        val n = names("Sugar, gelatin, mono and diglycerides, enzymes, natural flavors")
        assertEquals(listOf("gelatin"), n["Gelatin or collagen"])
        assertEquals(listOf("mono and diglycerides"), n["Fats and emulsifiers"])
        assertEquals(Sev.CHECK, Checker.analyze("gelatin", Settings(), false).verdict.sev)
    }

    @Test fun dyesAndAllergensWithMayContain() {
        val n = names("Enriched wheat flour, sugar, Red 40 Lake, FD&C Yellow #5, soy lecithin, TBHQ. May contain peanuts and tree nuts.", all)
        assertEquals(listOf("Red 40", "FD&C Yellow #5"), n["Petroleum-based dyes"])
        assertEquals(listOf("TBHQ"), n["Petroleum-based additives"])
        assertEquals(listOf("peanuts (may contain)"), n["Peanuts"])
        assertEquals(listOf("wheat", "flour"), n["Wheat"])
    }

    @Test fun allergenExceptions() {
        val n = names("Rice flour, coconut milk, cocoa butter, buckwheat, water chestnuts", all)
        assertFalse(n.toString(), n.containsKey("Milk") || n.containsKey("Wheat") || n.containsKey("Tree nuts"))
    }

    @Test fun kosherMeatWithDairy() {
        val a = Checker.analyze("Beef, cheddar cheese (milk, salt, enzymes), spices", all, false)
        assertEquals(Sev.UNCLEAN, a.verdict.sev)
        assertTrue(a.hits.any { it.name == "Meat with dairy" })
    }

    @Test fun kosherSymbolSkipsCertificationChecks() {
        val text = "Chicken broth, salmon, wine vinegar"
        assertTrue(names(text, all).containsKey("Wine or grape juice"))
        assertTrue(names(text, all).containsKey("Meat or poultry"))
        val certified = names(text, all, kosher = true)
        assertFalse(certified.containsKey("Wine or grape juice"))
        assertFalse(certified.containsKey("Meat or poultry"))
        assertTrue(certified.containsKey("Fish with meat"))
    }

    @Test fun marksLineUpWithText() {
        val text = "Water, PORK, Gelatin"
        val a = Checker.analyze(text, Settings(), false)
        assertEquals(listOf("PORK", "Gelatin"), a.marks.map { text.substring(it.start, it.end) })
    }

    @Test fun extractIngredientsFromPanel() {
        val raw = "Nutrition stuff\nINGREDIENTS: Sugar, corn syrup,\ngelatin. CONTAINS: MILK.\nDistributed by Acme Foods"
        assertEquals("Sugar, corn syrup, gelatin. CONTAINS: MILK.", Checker.extractIngredients(raw))
    }

    @Test fun openFoodFactsAllergens() {
        val hits = Checker.offHits(listOf("en:milk"), listOf("en:peanuts", "en:soybeans"), Settings(allergens = setOf("milk", "peanut")))
        assertEquals(listOf("Milk", "Peanuts (may contain)"), hits.map { it.name })
    }
}
