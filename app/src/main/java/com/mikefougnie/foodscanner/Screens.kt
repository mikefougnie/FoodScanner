package com.mikefougnie.foodscanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.text.DateFormat
import java.util.Date

// ---------------------------------------------------------------- Result

@Composable
fun ResultScreen(item: Scanned, settings: Settings, onBack: () -> Unit, onChange: (Scanned) -> Unit) {
    var edit by remember(item) { mutableStateOf(item.text) }
    val a = remember(item, settings) {
        Checker.analyze(item.text, settings, item.kosher, Checker.offHits(item.allergenTags, item.traceTags, settings))
    }
    val order = listOf(Sev.UNCLEAN, Sev.ALLERGEN, Sev.AVOID, Sev.CHECK)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(item.product.ifEmpty { "Ingredients" }, style = MaterialTheme.typography.titleLarge)
        }

        Surface(color = sevColor(a.verdict.sev), contentColor = Color.White, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text(a.verdict.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                Text(a.verdict.detail, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
            }
        }

        if (settings.kosher) {
            Row(verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onChange(item.copy(kosher = !item.kosher)) }.padding(4.dp)) {
                Checkbox(checked = item.kosher, onCheckedChange = { onChange(item.copy(kosher = it)) })
                Text("Package has a kosher symbol (skips certification checks)")
            }
        }

        for (h in a.hits.sortedBy { order.indexOf(it.sev) }) {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(h.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                    Text(h.sev.label, color = Color.White, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(sevColor(h.sev))
                            .padding(horizontal = 8.dp, vertical = 2.dp))
                }
                if (h.terms.isNotEmpty()) Text("Found: " + h.terms.joinToString(", "), style = MaterialTheme.typography.bodyMedium)
                Text(listOf(h.why, h.ref).filter { it.isNotEmpty() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(Modifier.padding(top = 10.dp))
            }
        }

        val highlighted = remember(a, item.text) {
            buildAnnotatedString {
                var i = 0
                for (m in a.marks) {
                    if (m.start < i) continue
                    append(item.text.substring(i, m.start))
                    withStyle(SpanStyle(background = sevColor(m.sev), color = Color.White)) {
                        append(item.text.substring(m.start, m.end))
                    }
                    i = m.end
                }
                append(item.text.substring(i))
            }
        }
        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(12.dp)) {
            Text(highlighted, modifier = Modifier.padding(12.dp).fillMaxWidth())
        }

        if (item.source.isNotEmpty()) {
            Text(item.source, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        OutlinedTextField(edit, { edit = it }, label = { Text("Ingredients (edit to fix misreads)") },
            minLines = 4, modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { onChange(item.copy(text = edit.trim())) }, enabled = edit.isNotBlank() && edit != item.text,
            modifier = Modifier.fillMaxWidth().height(48.dp)) { Text("Check again") }
        Spacer(Modifier.height(24.dp))
    }
}

// ---------------------------------------------------------------- Type

@Composable
fun TypeScreen(onCheck: (String) -> Unit, onLookup: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Type or paste", style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(text, { text = it }, label = { Text("Ingredients") },
            placeholder = { Text("Sugar, corn syrup, gelatin, citric acid, carmine (color)") },
            minLines = 6, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onCheck(text.trim()) }, enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Check ingredients") }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        OutlinedTextField(code, { v -> code = v.filter { it.isDigit() } }, label = { Text("Barcode number") },
            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth())
        OutlinedButton(onClick = { onLookup(code) }, enabled = code.length >= 6,
            modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Look up") }
    }
}

// ---------------------------------------------------------------- History

@Composable
fun HistoryScreen(history: List<HistoryEntry>, onOpen: (HistoryEntry) -> Unit, onClear: () -> Unit) {
    val fmt = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Recent checks", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (history.isNotEmpty()) TextButton(onClick = onClear) { Text("Clear") }
        }
        if (history.isEmpty()) {
            Text("Nothing scanned yet.", modifier = Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn {
            items(history) { e ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(e) }.padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(14.dp).clip(CircleShape).background(verdictColor(e.verdict)))
                    Column(Modifier.weight(1f)) {
                        Text(e.product, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text(fmt.format(Date(e.time)), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

// ---------------------------------------------------------------- Settings

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(s: Settings, onChange: (Settings) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        var coffee by remember { mutableStateOf(false) }
        CoffeeButton(onClick = { coffee = true }, modifier = Modifier.padding(top = 12.dp))
        if (coffee) CoffeeDialog(COFFEE_MESSAGE, fromMenu = true, onDismiss = { coffee = false })

        Group("Scripture")
        Toggle("Flag blood", "Lev 17:10–14; Acts 15:29", s.blood) { onChange(s.copy(blood = it)) }
        Toggle("Flag suet and tallow", "Lev 3:17; 7:23", s.fat) { onChange(s.copy(fat = it)) }
        Toggle("Show \"check the source\" items", "Gelatin, emulsifiers, enzymes, natural flavors", s.caution) { onChange(s.copy(caution = it)) }

        Group("Personal observance")
        Toggle("Avoid petroleum-based dyes", "Red 40, Red 3, Yellow 5 and 6, Blue 1 and 2, Green 3", s.dyes) { onChange(s.copy(dyes = it)) }
        Toggle("Avoid other petroleum additives", "BHA, BHT, TBHQ, propyl gallate, paraffin, mineral oil", s.petro) { onChange(s.copy(petro = it)) }
        Toggle("Full kosher", "Adds rabbinic rules: meat with dairy, kosher slaughter, wine and cheese supervision", s.kosher) { onChange(s.copy(kosher = it)) }

        Group("Allergens to flag")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ALLERGENS.forEach { a ->
                val on = a.id in s.allergens
                FilterChip(selected = on, label = { Text(a.name) }, onClick = {
                    onChange(s.copy(allergens = if (on) s.allergens - a.id else s.allergens + a.id))
                })
            }
        }
        Text("\"May contain\" and shared-facility warnings are flagged too. For a serious allergy, always read the package yourself.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Group("What it checks")
        RULES.filter { it.opt != "kosher" }.forEach { r ->
            Text(r.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            Text("${r.why}  ·  ${r.ref}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("This reads only what is printed. It can't see what a maker leaves off the label. When an item says \"check,\" the maker can tell you its source.",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 16.dp))
        if (Support.enabled) Text("Clean Food Scanner is free with no ads. Coffee keeps it that way. Thank you!",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun Group(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp))
}

@Composable
private fun Toggle(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
