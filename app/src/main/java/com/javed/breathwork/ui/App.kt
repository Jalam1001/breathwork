package com.javed.breathwork.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.javed.breathwork.AppContainer
import com.javed.breathwork.data.HistoryEntry
import com.javed.breathwork.data.Store
import com.javed.breathwork.data.Streaks
import com.javed.breathwork.domain.*
import com.javed.breathwork.engine.CueSettings
import com.javed.breathwork.engine.RunState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun App(c: AppContainer, onBeforeStart: () -> Unit = {}) {
    val session by c.controller.state.collectAsState()
    var screen by rememberSaveable { mutableStateOf("home") }
    var asked by rememberSaveable { mutableStateOf(false) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        if (session.run != RunState.IDLE) {
            SessionScreen(session, c.controller::pause, c.controller::resume, c.controller::end, onDone = { c.controller.reset() })
            return@Surface
        }
        BackHandler(enabled = screen != "home") { screen = "home" }
        when {
            screen == "home" -> HomeScreen(c, onOpen = { screen = "t:$it" }, onHistory = { screen = "history" })
            screen == "history" -> HistoryScreen(c, onBack = { screen = "home" })
            screen.startsWith("t:") -> DetailScreen(c, Techniques.byId(screen.removePrefix("t:")), onBack = { screen = "home" },
                onStart = { cfg -> if (!asked) { asked = true; onBeforeStart() }; c.start(cfg) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Bar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(title = { Text(title, Modifier.semantics { heading() }) }, actions = actions,
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } })
}

@Composable
fun PurposeChip(p: Purpose) {
    val color = when (p) {
        Purpose.CALM -> MaterialTheme.colorScheme.primary; Purpose.FOCUS -> MaterialTheme.colorScheme.secondary
        Purpose.ENERGY -> MaterialTheme.colorScheme.tertiary; Purpose.SLEEP -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = color.copy(alpha = 0.18f)) {
        Text(p.label, Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun HomeScreen(c: AppContainer, onOpen: (String) -> Unit, onHistory: () -> Unit) {
    val history by c.store.history.collectAsState(initial = emptyList())
    val streak = Streaks.current(history.map { it.epochDay }.toSet(), Store.today())
    val todayMin = history.filter { it.epochDay == Store.today() }.sumOf { it.seconds } / 60
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Bar("BreathWork") { IconButton(onClick = onHistory) { Icon(Icons.Default.DateRange, contentDescription = "History") } }
        LazyColumn(Modifier.testTag("techniques"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Breathing as practice. Pick a technique, set a length, and follow the shape, the sound, or the vibration.",
                    style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(8.dp))
                Text("Streak: $streak day${if (streak == 1) "" else "s"} · Today: $todayMin min",
                    style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            items(Techniques.all, key = { it.id }) { t ->
                ElevatedCard(onClick = { onOpen(t.id) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); PurposeChip(t.purpose)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(t.summary, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(if (t.id == Techniques.hyper.id) "30 breaths (1.5s in · 1s out) · exhale · hold 15s" else t.timingText(),
                            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Text("Techniques discussed on the Huberman Lab podcast and in Balban et al. 2023 (Cell Reports Medicine). " +
                    "BreathWork is independent and not affiliated with or endorsed by Huberman Lab. Not medical advice.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailScreen(c: AppContainer, t: Technique, onBack: () -> Unit, onStart: (SessionConfig) -> Unit) {
    val scope = rememberCoroutineScope()
    val cues by c.store.cues.collectAsState(initial = CueSettings())
    val acked by c.store.hyperAcknowledged.collectAsState(initial = false)
    var variant by rememberSaveable { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableIntStateOf(t.defaultMinutes) }
    var custom by rememberSaveable { mutableStateOf(false) }
    var customText by rememberSaveable { mutableStateOf("") }
    var rounds by rememberSaveable { mutableIntStateOf(t.defaultRounds) }
    var breaths by rememberSaveable { mutableIntStateOf(30) }
    var hold by rememberSaveable { mutableIntStateOf(15) }
    var showWarning by rememberSaveable { mutableStateOf(false) }
    val customMin = customText.toIntOrNull()
    val validCustom = customMin != null && customMin in 1..120
    val cfg = SessionConfig(t, variant, if (custom) (customMin ?: 1).coerceIn(1, 120) else minutes, rounds, HyperParams(breaths, hold))
    val tl = remember(cfg) { Timeline(cfg) }
    fun setCues(n: CueSettings) { scope.launch { c.store.setCues(n) } }

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Bar(t.name, onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PurposeChip(t.purpose); Text(t.summary, style = MaterialTheme.typography.bodyMedium)
            }
            Text("How to", style = MaterialTheme.typography.titleSmall)
            Text(t.howTo, style = MaterialTheme.typography.bodyMedium)
            Text("Source: ${t.source}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (t.variants.size > 1) {
                Text("Pattern", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    t.variants.forEachIndexed { i, v -> FilterChip(selected = variant == i, onClick = { variant = i }, label = { Text(v.name) }) }
                }
            }
            if (t.id != Techniques.hyper.id) Text(t.timingText(cfg.variant), style = MaterialTheme.typography.labelLarge)

            if (t.mode == Mode.TIMED) {
                Text("Duration", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(1, 3, 5, 10, 15, 20).forEach { m ->
                        FilterChip(selected = !custom && minutes == m, onClick = { custom = false; minutes = m }, label = { Text("$m min") },
                            modifier = Modifier.semantics { contentDescription = "$m minutes" })
                    }
                    FilterChip(selected = custom, onClick = { custom = true }, label = { Text("Custom") })
                }
                if (custom) OutlinedTextField(customText, { customText = it.filter(Char::isDigit).take(3) }, label = { Text("Minutes (1–120)") },
                    isError = customText.isNotEmpty() && !validCustom, singleLine = true,
                    supportingText = { if (customText.isNotEmpty() && !validCustom) Text("Choose 1–120 minutes") })
            } else {
                Text(if (t.id == Techniques.hyper.id) "Rounds" else "Sighs", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..5).forEach { r -> FilterChip(selected = rounds == r, onClick = { rounds = r }, label = { Text("$r") },
                        modifier = Modifier.semantics { contentDescription = "$r ${if (t.id == Techniques.hyper.id) "rounds" else "sighs"}" }) }
                }
                if (t.id == Techniques.hyper.id) {
                    Text("Breaths per round", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(20, 25, 30).forEach { b -> FilterChip(selected = breaths == b, onClick = { breaths = b }, label = { Text("$b breaths") }) }
                    }
                    Text("Empty-lung hold", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(15, 30).forEach { h -> FilterChip(selected = hold == h, onClick = { hold = h }, label = { Text("$h s hold") }) }
                    }
                }
            }
            Text("Session: ${tl.cycles} ${if (t.mode == Mode.ROUNDS) "round" else "breath"}${if (tl.cycles == 1) "" else "s"} · ${formatClock(tl.totalMs)}" +
                if (t.mode == Mode.TIMED) " (whole breaths only)" else "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)

            HorizontalDivider()
            Text("Cues", style = MaterialTheme.typography.titleSmall)
            Toggle("Tones (rising in, falling out)", cues.tones) { setCues(cues.copy(tones = it)) }
            Toggle("Soft tick each second of a hold", cues.holdTicks, enabled = cues.tones) { setCues(cues.copy(holdTicks = it)) }
            Toggle("Spoken cues (Inhale / Hold / Exhale)", cues.voice) { setCues(cues.copy(voice = it)) }
            Toggle("Vibration (1 long = in, 2 short = out)", cues.haptics) { setCues(cues.copy(haptics = it)) }
            Text("Volume ${(cues.volume * 100).roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
            Slider(cues.volume, { setCues(cues.copy(volume = it)) }, valueRange = 0f..1f, modifier = Modifier.semantics { contentDescription = "Cue volume" })
        }
        Button(
            onClick = { if (t.needsWarning && !acked) showWarning = true else onStart(cfg) },
            enabled = !(t.mode == Mode.TIMED && custom && !validCustom),
            modifier = Modifier.fillMaxWidth().padding(16.dp).heightIn(min = 56.dp),
        ) { Text("Start ${t.name}", style = MaterialTheme.typography.titleMedium) }
    }

    if (showWarning) {
        var checked by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showWarning = false },
            title = { Text("Safety first") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cyclic hyperventilation can cause light-headedness, tingling, or fainting.")
                    Text("• Never do it in or near water, while driving, or standing.\n• Sit or lie down somewhere safe.\n• Stop at once if you feel dizzy, faint, or unwell.\n" +
                        "• Not for use if you are pregnant, or have a heart condition, epilepsy or seizures, high blood pressure, or panic disorder.")
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(checked, role = Role.Checkbox) { checked = it }, verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked, null); Spacer(Modifier.width(8.dp)); Text("I have read and understand this")
                    }
                }
            },
            confirmButton = { TextButton(enabled = checked, onClick = { showWarning = false; scope.launch { c.store.acknowledgeHyper() }; onStart(cfg) }) { Text("Start") } },
            dismissButton = { TextButton(onClick = { showWarning = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Toggle(label: String, value: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(value, onChange, enabled = enabled, modifier = Modifier.semantics { contentDescription = label })
    }
}

@Composable
fun HistoryScreen(c: AppContainer, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val history by c.store.history.collectAsState(initial = emptyList())
    val streak = Streaks.current(history.map { it.epochDay }.toSet(), Store.today())
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Bar("History", onBack) { if (history.isNotEmpty()) TextButton(onClick = { scope.launch { c.store.clearHistory() } }) { Text("Clear") } }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Current streak: $streak day${if (streak == 1) "" else "s"}", style = MaterialTheme.typography.titleMedium)
            Text("Sessions: ${history.size} · Total: ${history.sumOf { it.seconds } / 60} min", style = MaterialTheme.typography.bodyMedium)
            Text("Stored only on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (history.isEmpty()) Text("No sessions yet.", Modifier.padding(16.dp))
        LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp)) {
            items(history.reversed()) { e: HistoryEntry ->
                ListItem(headlineContent = { Text(Techniques.byId(e.techniqueId).name) },
                    supportingContent = { Text("${java.time.LocalDate.ofEpochDay(e.epochDay)} · ${formatClock(e.seconds * 1000L)}${if (e.completed) "" else " (ended early)"}") })
            }
        }
    }
}
