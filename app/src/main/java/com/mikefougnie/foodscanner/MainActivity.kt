package com.mikefougnie.foodscanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = Store(applicationContext)
        setContent { FoodTheme { App(store) } }
    }
}

/** One thing being checked: ingredient text plus what we know about the product. */
data class Scanned(
    val text: String,
    val product: String = "",
    val kosher: Boolean = false,
    val allergenTags: List<String> = emptyList(),
    val traceTags: List<String> = emptyList(),
    val source: String = "",
)

@Suppress("DEPRECATION")
enum class Tab(val label: String, val icon: ImageVector) {
    SCAN("Scan", Icons.Filled.Search),
    TYPE("Type", Icons.Filled.Edit),
    HISTORY("History", Icons.Filled.List),
    SETTINGS("Settings", Icons.Filled.Settings),
}

@Composable
fun App(store: Store) {
    var tab by rememberSaveable { mutableStateOf(Tab.SCAN) }
    var settings by remember { mutableStateOf(store.loadSettings()) }
    var history by remember { mutableStateOf(store.loadHistory()) }
    var current by remember { mutableStateOf<Scanned?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    val snack = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun say(msg: String) { scope.launch { snack.showSnackbar(msg) } }

    fun open(s: Scanned, save: Boolean = true) {
        current = s
        if (save) {
            val extra = Checker.offHits(s.allergenTags, s.traceTags, settings)
            val v = Checker.analyze(s.text, settings, s.kosher, extra).verdict
            history = store.addHistory(
                HistoryEntry(s.product.ifEmpty { s.text.take(40) + "…" }, s.text, v.sev?.name ?: "OK",
                    System.currentTimeMillis(), s.kosher)
            )
        }
    }

    fun lookup(code: String) {
        if (busy != null || current != null) return
        busy = "Looking up $code…"
        scope.launch {
            when (val r = OpenFoodFacts.lookup(code)) {
                is OpenFoodFacts.Result.Found -> open(
                    Scanned(r.ingredients, r.name.ifEmpty { "Barcode $code" }, r.kosher, r.allergens, r.traces,
                        "Ingredients from Open Food Facts (${OpenFoodFacts.pageUrl(code)}). Compare with the package.")
                )
                is OpenFoodFacts.Result.NoIngredients ->
                    say("Found ${r.name.ifEmpty { code }}, but no ingredients are listed. Switch to Label and read the package.")
                OpenFoodFacts.Result.NotFound ->
                    say("Barcode $code isn't in Open Food Facts yet. Switch to Label and read the ingredients.")
                is OpenFoodFacts.Result.Failed ->
                    say("Couldn't reach the product database. Check your connection, or read the label instead.")
            }
            busy = null
        }
    }

    fun labelText(raw: String?) {
        busy = null
        if (raw.isNullOrBlank()) { say("Couldn't read any text. Hold steady with the list filling the frame."); return }
        val text = Checker.extractIngredients(raw)
        if (text.length < 4) { say("Couldn't find an ingredients list. Fill the frame with it and try again."); return }
        open(Scanned(text, kosher = Checker.mentionsKosher(raw), source = "Read from the label. Fix any misread words below and tap Check again."))
    }

    BackHandler(enabled = current != null) { current = null }

    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        bottomBar = {
            if (current == null) NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(t.icon, contentDescription = null) }, label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val c = current
            if (c != null) {
                ResultScreen(c, settings, onBack = { current = null }, onChange = { current = it })
            } else when (tab) {
                Tab.SCAN -> ScanScreen(
                    paused = busy != null,
                    onBarcode = ::lookup,
                    onReadLabel = { busy = "Reading the label…" },
                    onLabelText = ::labelText,
                )
                Tab.TYPE -> TypeScreen(
                    onCheck = { open(Scanned(it, kosher = Checker.mentionsKosher(it))) },
                    onLookup = ::lookup,
                )
                Tab.HISTORY -> HistoryScreen(
                    history,
                    onOpen = { open(Scanned(it.text, it.product, it.kosher), save = false) },
                    onClear = { store.clearHistory(); history = emptyList() },
                )
                Tab.SETTINGS -> SettingsScreen(settings) { settings = it; store.saveSettings(it) }
            }
            busy?.let { msg ->
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Text(msg, color = Color.White, style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
                    }
                }
            }
        }
    }
}
