package com.akash.reelslimiter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.akash.reelslimiter.counting.LimitSettings
import com.akash.reelslimiter.data.CooldownRepository
import com.akash.reelslimiter.data.SettingsRepository

/** Edit the reel limit and the pause length; saved on the device and applied immediately. */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val repository = remember { SettingsRepository.get(context) }
    val settings by repository.state.collectAsState()
    val cooldown by remember { CooldownRepository.get(context).state }.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        NumberSetting(
            title = "Reels per session",
            description = "Reels pauses after this many reels in one session.",
            value = settings.sessionLimit,
            unit = "reels",
            range = LimitSettings.SESSION_LIMIT_RANGE,
            presets = listOf(5, 10, 20, 30, 50),
            step = 5,
            onChange = { repository.update(settings.copy(sessionLimit = it)) },
        )

        NumberSetting(
            title = "Pause length",
            description = "How long the countdown runs before Reels is available again.",
            value = settings.cooldownMinutes,
            unit = "min",
            range = LimitSettings.COOLDOWN_MINUTES_RANGE,
            presets = listOf(1, 5, 8, 10, 15),
            step = 1,
            onChange = { repository.update(settings.copy(cooldownMinutes = it)) },
        )

        Text(
            if (cooldown.endAtMillis > System.currentTimeMillis()) {
                "A pause is running now; the new pause length applies from the next one."
            } else {
                "Changes apply right away."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberSetting(
    title: String,
    description: String,
    value: Int,
    unit: String,
    range: IntRange,
    presets: List<Int>,
    step: Int,
    onChange: (Int) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = { onChange((value - step).coerceIn(range)) },
                    enabled = value > range.first,
                ) { Text("−$step", fontSize = 18.sp) }
                Text(
                    "$value $unit",
                    modifier = Modifier.widthIn(min = 120.dp),
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                FilledTonalButton(
                    onClick = { onChange((value + step).coerceIn(range)) },
                    enabled = value < range.last,
                ) { Text("+$step", fontSize = 18.sp) }
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { preset ->
                    FilterChip(
                        selected = value == preset,
                        onClick = { onChange(preset) },
                        label = { Text("$preset") },
                    )
                }
            }
        }
    }
}
