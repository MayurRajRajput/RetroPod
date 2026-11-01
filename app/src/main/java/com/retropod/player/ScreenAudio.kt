package com.retropod.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

// ============================================================================ visualizer

@Composable
internal fun VisualizerScreen(state: AppState) {
    var mode by remember { mutableIntStateOf(0) }
    DisposableEffect(state.micGranted) {
        PlaybackService.setVisualizerActive(true)
        onDispose { PlaybackService.setVisualizerActive(false) }
    }
    val frame = rememberFrameClock(true)
    val playing = state.isPlaying
    val accent = Accent

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.pop() }) { Icon(Icons.Default.ChevronLeft, "Back", tint = White, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.weight(1f))
            SegmentedPill(listOf("Visualizer", "Spectrum"), mode, { mode = it; state.tick() })
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(Modifier.fillMaxSize()) {
                val t = frame.value
                val seconds = t / 1_000_000_000f
                if (mode == 0) {
                    val cols = 72
                    val rows = 16
                    for (r in 0 until rows) {
                        val depth = r.toFloat() / (rows - 1)
                        val baseline = size.height * (0.42f + 0.46f * depth)
                        val radius = (1.1f + (1f - depth) * 1.7f).dp.toPx()
                        for (c in 0 until cols) {
                            val level = bandLevel(c * 31 / (cols - 1), 32, t, playing)
                            val wave = 0.5f + 0.5f * sin(c * 0.22f + seconds * 1.7f + r * 0.32f)
                            val lift = size.height * 0.36f * (1f - depth * 0.55f) * level * wave
                            val x = size.width * (c + 0.5f) / cols
                            val alpha = (0.18f + 0.82f * wave * (0.5f + level * 0.5f)) * (1f - depth * 0.35f)
                            drawCircle(Color.White.copy(alpha = alpha.coerceIn(0f, 1f)), radius, Offset(x, baseline - lift))
                        }
                    }
                } else {
                    val bars = 32
                    val gap = 5.dp.toPx()
                    val barWidth = (size.width - gap * (bars + 1)) / bars
                    for (i in 0 until bars) {
                        val level = bandLevel(i, bars, t, playing)
                        val barHeight = (size.height * 0.82f * level).coerceAtLeast(4.dp.toPx())
                        val x = gap + i * (barWidth + gap)
                        val top = size.height * 0.92f - barHeight
                        drawRoundRect(
                            Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.15f)), startY = top, endY = size.height * 0.92f),
                            Offset(x, top), Size(barWidth, barHeight), CornerRadius(barWidth / 2f)
                        )
                        drawRoundRect(White, Offset(x, top - 7.dp.toPx()), Size(barWidth, 3.dp.toPx()), CornerRadius(2.dp.toPx()))
                    }
                }
            }
            if (!state.micGranted) {
                Column(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Showing a simulated animation.", color = Muted, fontSize = 11.sp
                    )
                    TextButton(onClick = { state.requestMic() }) {
                        Text("Enable live audio reaction", color = Accent, fontSize = 12.sp)
                    }
                }
            }
        }
        MiniPlayer(state)
    }
}

// ============================================================================ equalizer

private val EQ_PRESETS = listOf("Custom", "Bass", "Pop", "Rock", "Vocal")

@Composable
internal fun EqualizerScreen(state: AppState) {
    var refresh by remember { mutableIntStateOf(0) }
    var preset by remember { mutableStateOf(state.settingsPrefs.getString("eq_preset", "Custom") ?: "Custom") }
    var enabled by remember { mutableStateOf(true) }
    val info = remember(refresh) { PlaybackService.eqInfo() }
    val levels = remember(info) { mutableStateListOf<Int>().also { list -> info?.levels?.let { list.addAll(it) } } }
    LaunchedEffect(info) { if (info != null) enabled = info.enabled }
    LaunchedEffect(Unit) {
        // The audio session can take a moment to exist the first time playback starts.
        if (PlaybackService.eqInfo() == null) {
            delay(600)
            refresh++
        }
    }
    val frame = rememberFrameClock(state.isPlaying)
    val accent = Accent

    Column(Modifier.fillMaxSize()) {
        RetroTopBar("Equalizer", onBack = { state.pop() }) {
            Switch(
                checked = enabled, enabled = info != null,
                onCheckedChange = { enabled = it; PlaybackService.setEqEnabled(it); state.tick() },
                colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Night),
                modifier = Modifier.padding(end = 12.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EQ_PRESETS.forEach { name ->
                RetroChip(name, preset == name, {
                    val applied = PlaybackService.applyEqPreset(name)
                    if (applied != null) {
                        preset = name
                        state.settingsPrefs.edit().putString("eq_preset", name).apply()
                        levels.clear(); levels.addAll(applied)
                        state.tick()
                    } else {
                        state.toast("Start playback to enable the equalizer.")
                    }
                })
            }
        }
        if (info == null) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
            ) {
                Text("Equalizer not ready", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text(
                    "Start playing a song, then come back. If your device doesn't support audio effects, the equalizer stays unavailable.",
                    color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp)
                )
                TextButton(onClick = { refresh++ }) { Text("Try again", color = Accent) }
            }
        } else {
            val range = (info.maxLevel - info.minLevel).coerceAtLeast(1)
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (band in 0 until info.bandCount) {
                    val level = levels.getOrElse(band) { 0 }
                    val fraction = (level - info.minLevel).toFloat() / range
                    val freq = info.centerFreqHz.getOrElse(band) { 0 }
                    Column(Modifier.weight(1f).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "%+.1f".format(level / 100f), color = Muted, fontSize = 10.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        VerticalBandSlider(
                            value = fraction, enabled = enabled,
                            onChange = { newFraction ->
                                val newLevel = (info.minLevel + newFraction * range).toInt()
                                    .coerceIn(info.minLevel, info.maxLevel)
                                if (band < levels.size) levels[band] = newLevel
                                PlaybackService.setEqBand(band, newLevel)
                                if (preset != "Custom") {
                                    preset = "Custom"
                                    state.settingsPrefs.edit().putString("eq_preset", "Custom").apply()
                                }
                            },
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        Text(
                            if (freq >= 1000) "%.1fK".format(freq / 1000f).replace(".0K", "K") else "$freq",
                            color = White, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 24.dp)) {
            val time = frame.value / 500_000_000f
            val path = Path()
            val steps = 60
            for (i in 0..steps) {
                val x = size.width * i / steps
                val level = bandLevel(i * 31 / steps, 32, frame.value, state.isPlaying)
                val y = size.height / 2f - size.height * 0.4f * level * sin(i * 0.35f + time)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, accent.copy(alpha = 0.8f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        MiniPlayer(state)
    }
}

// ============================================================================ sleep timer

@Composable
internal fun SleepTimerScreen(state: AppState) {
    val context = state.context
    var selected by remember { mutableIntStateOf(30) }
    var showCustom by remember { mutableStateOf(false) }
    var customText by remember { mutableStateOf("20") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val deadline = PlaybackService.sleepDeadline(context)
    val total = PlaybackService.sleepTotal(context).coerceAtLeast(1L)
    val active = deadline > now
    val remaining = (deadline - now).coerceAtLeast(0L)
    val accent = Accent

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        RetroTopBar("Sleep Timer", onBack = { state.pop() })
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(230.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 7.dp.toPx()
                val inset = stroke
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                drawArc(Color(0x22FFFFFF), 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                val sweep = if (active) 360f * (remaining.toFloat() / total).coerceIn(0f, 1f) else 360f * 0.92f
                drawArc(accent, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (active) {
                    val totalSeconds = remaining / 1000
                    Text("%d:%02d".format(totalSeconds / 60, totalSeconds % 60), color = White, fontSize = 42.sp, fontWeight = FontWeight.Medium)
                    Text("remaining", color = Muted, fontSize = 15.sp)
                } else {
                    Text(if (selected > 0) "$selected" else "Off", color = White, fontSize = 54.sp, fontWeight = FontWeight.Medium)
                    if (selected > 0) Text("Minutes", color = White, fontSize = 17.sp)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(0 to "Off", 15 to "15m", 30 to "30m", 45 to "45m").forEach { (minutes, label) ->
                    RetroChip(label, !active && selected == minutes, { selected = minutes; state.tick() })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(60 to "60m", 90 to "90m").forEach { (minutes, label) ->
                    RetroChip(label, !active && selected == minutes, { selected = minutes; state.tick() })
                }
                RetroChip("Custom", !active && selected !in listOf(0, 15, 30, 45, 60, 90), { showCustom = true })
            }
        }
        Spacer(Modifier.height(26.dp))
        GradientButton(
            if (active) "Cancel Timer" else "Start Timer",
            onClick = {
                if (active) {
                    PlaybackService.setSleepTimer(0)
                    state.toast("Sleep timer cancelled.")
                } else if (selected > 0) {
                    if (PlaybackService.setSleepTimer(selected)) state.toast("Music will stop in $selected minutes.")
                    else state.toast("Start playing music first.")
                } else {
                    PlaybackService.setSleepTimer(0)
                    state.toast("Choose a duration first.")
                }
                state.tick()
            },
            modifier = Modifier.padding(horizontal = 24.dp)
        )
        Spacer(Modifier.height(40.dp))
    }
    if (showCustom) {
        AlertDialog(
            onDismissRequest = { showCustom = false },
            containerColor = SurfaceNight,
            title = { Text("Custom timer", color = White) },
            text = {
                OutlinedTextField(
                    value = customText, onValueChange = { customText = it.filter(Char::isDigit).take(4) },
                    singleLine = true, label = { Text("Minutes (1–1440)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White, unfocusedTextColor = White,
                        focusedBorderColor = Accent, unfocusedBorderColor = Muted
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val minutes = customText.toIntOrNull()
                    if (minutes != null && minutes in 1..1440) { selected = minutes; showCustom = false }
                    else state.toast("Enter a number between 1 and 1440.")
                }) { Text("Set", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { showCustom = false }) { Text("Cancel", color = Muted) } }
        )
    }
}
