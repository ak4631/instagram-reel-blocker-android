package com.akash.reelslimiter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.reelslimiter.counting.CounterEngine
import com.akash.reelslimiter.data.SettingsRepository
import com.akash.reelslimiter.data.CooldownRepository
import com.akash.reelslimiter.data.CounterRepository
import com.akash.reelslimiter.detection.Screen
import com.akash.reelslimiter.monitor.DetectionMonitor
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Home: today's live reel count, the session against its limit, and any active pause. */
@Composable
fun HomeScreen(onOpenAccessibilitySettings: () -> Unit) {
    val context = LocalContext.current
    val counter by remember { CounterRepository.get(context).state }.collectAsState()
    val cooldown by remember { CooldownRepository.get(context).state }.collectAsState()
    val settings by remember { SettingsRepository.get(context).state }.collectAsState()
    val policy = settings.toPolicy()
    val connected by DetectionMonitor.serviceConnected.collectAsState()
    val current by DetectionMonitor.current.collectAsState()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Ticks the cooldown countdown, day rollover and session expiry while the screen stays open.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }

    val today = if (counter.day == CounterEngine.dayKey(now)) counter.dailyCount else 0
    val sessionActive = counter.lastReelsAtMillis > 0 &&
        now - counter.lastReelsAtMillis <= CounterEngine.DEFAULT_SESSION_GAP_MILLIS
    val paused = cooldown.endAtMillis > now
    // A finished cooldown resets the session even before the service gets its next event.
    val cooldownFinished = cooldown.endAtMillis in 1..now
    val session = if (sessionActive && !cooldownFinished) counter.sessionCount else 0

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!connected) ServiceCard(connected = false, onOpenAccessibilitySettings)

        if (paused) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Reels paused", style = MaterialTheme.typography.titleMedium)
                    Text(formatRemaining(cooldown.endAtMillis - now), fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text(cooldown.message, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Reels today", style = MaterialTheme.typography.titleMedium)
                Text("$today", fontSize = 72.sp, fontWeight = FontWeight.Bold)
                if (current?.screen == Screen.REELS) {
                    Text("Watching Reels now", color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("This session", policy.sessionLimit?.let { "$session / $it" } ?: "$session", Modifier.weight(1f))
            StatCard(
                "Last on Reels",
                if (counter.lastReelsAtMillis > 0) timeFormat.format(Date(counter.lastReelsAtMillis)) else "—",
                Modifier.weight(1f),
            )
        }

        Text(
            "A reel counts after 1.5 s on screen; replays don't count twice. At the session limit Reels " +
                "pauses for ${pauseLength(policy.cooldownMillis)}, then a new session starts. DMs and feed are never blocked.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun pauseLength(millis: Long): String {
    val seconds = millis / 1_000
    return if (seconds % 60 == 0L) "${seconds / 60} min" else "$seconds s"
}

private fun formatRemaining(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0L) + 999) / 1_000
    val h = seconds / 3_600
    return if (h > 0) "%d:%02d:%02d".format(h, seconds % 3_600 / 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
