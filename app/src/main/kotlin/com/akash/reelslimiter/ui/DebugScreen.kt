package com.akash.reelslimiter.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.reelslimiter.detection.Detection
import com.akash.reelslimiter.detection.Screen
import com.akash.reelslimiter.monitor.DetectionMonitor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** M1 debug screen: shows what the service sees right now and every Reels / Not Reels switch. */
@Composable
fun DebugScreen(onOpenAccessibilitySettings: () -> Unit) {
    val connected by DetectionMonitor.serviceConnected.collectAsState()
    val current by DetectionMonitor.current.collectAsState()
    val evalStats by DetectionMonitor.evalStats.collectAsState()
    val transitions by DetectionMonitor.transitions.collectAsState()
    val captureRequested by DetectionMonitor.captureRequested.collectAsState()
    val capture by DetectionMonitor.capture.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Reels Limiter · detector", style = MaterialTheme.typography.headlineSmall) }
        item { ServiceCard(connected, onOpenAccessibilitySettings) }
        item { CurrentCard(current, evalStats) }
        item { CaptureCard(captureRequested, capture) }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Transitions", style = MaterialTheme.typography.titleMedium)
                if (transitions.isNotEmpty()) {
                    OutlinedButton(onClick = DetectionMonitor::clearTransitions) { Text("Clear") }
                }
            }
        }
        if (transitions.isEmpty()) {
            item { Text("None yet. Open Instagram and move between Reels, the feed and DMs.") }
        }
        items(transitions) { transition ->
            Text(
                "${timeFormat.format(Date(transition.atMillis))}  ${label(transition.detection.screen)}  " +
                    transition.detection.reason,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
internal fun ServiceCard(connected: Boolean, onOpenAccessibilitySettings: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (connected) "Service running" else "Service off",
                style = MaterialTheme.typography.titleMedium,
                color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            if (!connected) {
                Text(
                    "Turn on Reels Limiter in Accessibility settings. On Android 13+, first open " +
                        "App info → ⋮ → Allow restricted settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onOpenAccessibilitySettings) { Text("Open Accessibility settings") }
            }
        }
    }
}

@Composable
private fun CurrentCard(current: Detection?, stats: DetectionMonitor.EvalStats?) {
    val isReels = current?.screen == Screen.REELS
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isReels) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(current?.let { label(it.screen) } ?: "—", fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text(current?.reason ?: "Waiting for Instagram events")
            stats?.let {
                Text(
                    "Screen check cost (not watch time): last %.1f ms · avg %.1f · max %.1f · %d nodes · %d checks"
                        .format(it.lastMicros / 1000.0, it.avgMicros / 1000.0, it.maxMicros / 1000.0, it.lastNodes, it.checks),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (isReels) {
                Text(
                    current.reelKey?.let { "Reel on screen: #%08x".format(it) } ?: "Reel on screen: — (mid-swipe)",
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun CaptureCard(captureRequested: Boolean, capture: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Structure capture", style = MaterialTheme.typography.titleMedium)
            Text(
                "Dumps the next Instagram screen the service sees (class, id, flags, bounds). " +
                    "Use it to tune the rules in ReelsDetector.kt. Kept in memory only.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(onClick = DetectionMonitor::requestCapture, enabled = !captureRequested) {
                Text(if (captureRequested) "Waiting: switch to Instagram…" else "Capture next Instagram screen")
            }
            capture?.let {
                Text(
                    it,
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    softWrap = false,
                )
            }
        }
    }
}

private fun label(screen: Screen) = if (screen == Screen.REELS) "REELS" else "NOT REELS"
