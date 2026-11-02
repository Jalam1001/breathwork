package com.javed.breathwork.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.javed.breathwork.domain.Pacer
import com.javed.breathwork.domain.PhaseKind
import com.javed.breathwork.domain.Techniques
import com.javed.breathwork.domain.Visual
import com.javed.breathwork.engine.RunState
import com.javed.breathwork.engine.SessionState
import kotlin.math.min

@Composable
fun SessionScreen(s: SessionState, onPause: () -> Unit, onResume: () -> Unit, onEnd: () -> Unit, onDone: () -> Unit) {
    val view = LocalView.current
    val active = s.isActive
    DisposableEffect(active) {
        view.keepScreenOn = active
        onDispose { view.keepScreenOn = false }
    }
    BackHandler { if (s.run == RunState.FINISHED) onDone() else onPause() }
    val tl = s.timeline ?: return
    val tech = tl.config.technique
    val seg = s.segment

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(tech.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (s.run == RunState.FINISHED) {
            Spacer(Modifier.weight(1f))
            Text(if (s.completed) "Session complete" else "Session ended", style = MaterialTheme.typography.headlineMedium)
            Text("${formatClock(s.elapsedMs)} of breathing", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onDone, modifier = Modifier.heightIn(min = 56.dp).fillMaxWidth()) { Text("Done") }
            Spacer(Modifier.weight(1f))
            return@Column
        }

        val label: String; val count: Int
        if (s.run == RunState.LEAD_IN) { label = "Get ready"; count = ceilSeconds(s.leadInMs) }
        else { label = seg?.step?.label ?: ""; count = ceilSeconds(s.phaseRemainingMs) }
        val hasTopUp = tl.config.cycleSteps.any { it.kind == PhaseKind.TOP_UP }
        val kind = if (s.run == RunState.LEAD_IN) PhaseKind.HOLD_EMPTY else seg?.step?.kind ?: PhaseKind.HOLD_EMPTY
        val frac = if (s.run == RunState.LEAD_IN) 0f else s.phaseFraction

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val primary = MaterialTheme.colorScheme.primary; val track = MaterialTheme.colorScheme.surfaceVariant
            val secondary = MaterialTheme.colorScheme.secondary
            Canvas(Modifier.fillMaxSize(0.9f).aspectRatio(1f).semantics { contentDescription = "Breathing pacer: $label" }) {
                val r = min(size.width, size.height) / 2f
                if (tech.visual == Visual.SQUARE) {
                    val side = r * 1.5f; val tl0 = Offset(center.x - side / 2, center.y - side / 2)
                    drawRect(track, tl0, Size(side, side), style = Stroke(10.dp.toPx()))
                    val sideIdx = if (s.run == RunState.LEAD_IN) 0 else seg?.indexInCycle ?: 0
                    // highlight current side
                    val (x0, y0) = Pacer.boxPoint(sideIdx, 0f); val (x1, y1) = Pacer.boxPoint(sideIdx, 1f)
                    drawLine(primary.copy(alpha = 0.45f), Offset(tl0.x + x0 * side, tl0.y + y0 * side), Offset(tl0.x + x1 * side, tl0.y + y1 * side), 10.dp.toPx())
                    val (px, py) = Pacer.boxPoint(sideIdx, frac)
                    drawCircle(primary, 16.dp.toPx(), Offset(tl0.x + px * side, tl0.y + py * side))
                    val fill = if (s.run == RunState.LEAD_IN) Pacer.MIN else Pacer.scale(kind, frac, false)
                    drawCircle(primary.copy(alpha = 0.15f), side / 2.4f * fill, center)
                } else {
                    drawCircle(track, r, center, style = Stroke(3.dp.toPx()))
                    if (hasTopUp) drawCircle(secondary.copy(alpha = 0.6f), r * Pacer.MID, center, style = Stroke(2.dp.toPx()))
                    drawCircle(track.copy(alpha = 0.6f), r * Pacer.MIN, center, style = Stroke(2.dp.toPx()))
                    val sc = Pacer.scale(kind, frac, hasTopUp)
                    drawCircle(primary.copy(alpha = 0.30f), r * sc, center)
                    drawCircle(primary, r * sc, center, style = Stroke(6.dp.toPx()))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text("$count", fontSize = 56.sp, fontWeight = FontWeight.Light)
            }
        }

        if (seg != null && tech.id == Techniques.hyper.id && s.run != RunState.LEAD_IN) {
            val breath = seg.indexInCycle / 2 + 1; val n = tl.config.hyper.breaths
            Text(if (breath <= n) "Round ${seg.cycle + 1} of ${tl.cycles} · breath $breath of $n" else "Round ${seg.cycle + 1} of ${tl.cycles}",
                style = MaterialTheme.typography.titleMedium)
        } else if (seg != null) {
            Text("${if (tech.mode == com.javed.breathwork.domain.Mode.ROUNDS) "Sigh" else "Breath"} ${seg.cycle + 1} of ${tl.cycles}", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "Session progress ${(s.progress * 100).toInt()} percent" })
        Spacer(Modifier.height(4.dp))
        Text("${formatClock(s.remainingMs)} left", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.run == RunState.PAUSED) Button(onClick = onResume, Modifier.weight(1f).heightIn(min = 64.dp)) { Text("Resume", style = MaterialTheme.typography.titleMedium) }
            else FilledTonalButton(onClick = onPause, Modifier.weight(1f).heightIn(min = 64.dp)) { Text("Pause", style = MaterialTheme.typography.titleMedium) }
            OutlinedButton(onClick = onEnd, Modifier.weight(1f).heightIn(min = 64.dp)) { Text("End", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
