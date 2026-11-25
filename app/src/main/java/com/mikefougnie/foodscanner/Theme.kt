package com.mikefougnie.foodscanner

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val FieldGreen = Color(0xFF1F4D36)
val Unclean = Color(0xFFB3261E)
val AllergenViolet = Color(0xFF5B3FA8)
val AvoidOrange = Color(0xFFA33A12)
val CheckAmber = Color(0xFF8A5200)
val CleanGreen = Color(0xFF22693A)

fun sevColor(sev: Sev?): Color = when (sev) {
    Sev.UNCLEAN -> Unclean
    Sev.ALLERGEN -> AllergenViolet
    Sev.AVOID -> AvoidOrange
    Sev.CHECK -> CheckAmber
    null -> CleanGreen
}

fun verdictColor(name: String): Color = sevColor(Sev.entries.firstOrNull { it.name == name })

@Composable
fun FoodTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) {
        darkColorScheme(primary = Color(0xFF5FB487), onPrimary = Color(0xFF0C1510), secondary = Color(0xFF5FB487))
    } else {
        lightColorScheme(primary = FieldGreen, onPrimary = Color.White, secondary = FieldGreen)
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
