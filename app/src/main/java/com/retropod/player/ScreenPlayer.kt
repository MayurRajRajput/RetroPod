package com.retropod.player

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.animateFloatAsState
import androidx.media3.common.Player
import kotlin.math.sin

// ============================================================================ now playing

@Composable
internal fun NowPlayingScreen(state: AppState) {
    val context = LocalContext.current
    val song = state.currentSong
    var moreOpen by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    val frame = rememberFrameClock(state.isPlaying)

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        RetroTopBar("Now Playing", onBack = { state.pop() }) {
            IconButton(onClick = {
                try {
                    context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    try {
                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (_: Exception) {
                        state.toast("No output settings available on this device.")
                    }
                }
            }) { Icon(Icons.Default.Cast, "Output device", tint = White) }
            Box {
                IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "More", tint = White) }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    DropdownMenuItem(text = { Text("Visualizer") }, onClick = { moreOpen = false; state.push(Overlay.VISUALIZER) })
                    DropdownMenuItem(text = { Text("Equalizer") }, onClick = { moreOpen = false; state.push(Overlay.EQUALIZER) })
                    DropdownMenuItem(text = { Text("Lyrics & queue") }, onClick = { moreOpen = false; state.push(Overlay.LYRICS) })
                    DropdownMenuItem(text = { Text("More actions…") }, onClick = { moreOpen = false; state.menuSong = song })
                }
            }
        }

        val count = state.queue.size.coerceAtLeast(1)
        val pager = rememberPagerState(initialPage = state.currentIndex.coerceIn(0, count - 1), pageCount = { count })
        LaunchedEffect(state.currentIndex, count) {
            val target = state.currentIndex.coerceIn(0, count - 1)
            if (pager.currentPage != target) pager.animateScrollToPage(target)
        }
        LaunchedEffect(pager.settledPage) {
            if (state.queue.isNotEmpty() && pager.settledPage != state.currentIndex) state.seekToIndex(pager.settledPage)
        }
        HorizontalPager(
            state = pager, contentPadding = PaddingValues(horizontal = 30.dp), pageSpacing = 4.dp,
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 350.dp)
        ) { page ->
            val distance = (page - pager.currentPage).toFloat()
            val scale by animateFloatAsState(
                if (!state.animationsEnabled || page == pager.currentPage) 1f else .86f, tween(340), label = "playerCover"
            )
            AlbumArtwork(
                state.queue.getOrNull(page), page,
                Modifier.fillMaxWidth().aspectRatio(1f).scale(scale).graphicsLayer {
                    rotationY = if (state.animationsEnabled) distance.coerceIn(-1f, 1f) * -22f else 0f
                    cameraDistance = 16f * density
                },
                24.dp
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(song?.title ?: "Nothing playing", color = White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artist ?: "Choose a song from your library", color = Muted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (song != null) {
                val favorite = state.isFavorite(song.id)
                IconButton(onClick = { state.toggleFavorite(song) }) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, "Favorite", tint = White, modifier = Modifier.size(26.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        val progress = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
        if (state.waveformSeek) {
            WaveSeekBar(
                progress = progress, playing = state.isPlaying, enabled = state.duration > 0,
                onSeekFraction = { fraction -> state.seekTo((fraction * state.duration).toLong()) },
                modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 6.dp)
            )
        } else {
            var drag by remember { mutableStateOf<Float?>(null) }
            Slider(
                value = drag ?: progress, onValueChange = { drag = it },
                onValueChangeFinished = { drag?.let { state.seekTo((it * state.duration).toLong()) }; drag = null },
                enabled = state.duration > 0,
                colors = SliderDefaults.colors(thumbColor = White, activeTrackColor = Accent, inactiveTrackColor = Muted.copy(alpha = .3f))
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(state.position), color = Muted, fontSize = 11.sp)
            Text(formatTime(state.duration), color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.toggleShuffle() }) {
                Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffleOn) Accent else Muted)
            }
            IconButton(onClick = { state.skipPrevious() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = White, modifier = Modifier.size(34.dp)) }
            RingPlayButton(playing = state.isPlaying, size = 76.dp, onClick = { state.togglePlayPause() })
            IconButton(onClick = { state.skipNext() }) { Icon(Icons.Default.SkipNext, "Next", tint = White, modifier = Modifier.size(34.dp)) }
            IconButton(onClick = { state.cycleRepeat() }) {
                Icon(
                    if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                    "Repeat", tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) Muted else Accent
                )
            }
        }
        if (showVolume) {
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.VolumeDown, null, tint = Muted)
                Slider(
                    value = state.volume, onValueChange = { state.setVolumeLevel(it) },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    colors = SliderDefaults.colors(thumbColor = White, activeTrackColor = Accent, inactiveTrackColor = Muted.copy(alpha = .3f))
                )
                Icon(Icons.Default.VolumeUp, null, tint = Muted)
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { state.push(Overlay.EQUALIZER) }) { Icon(Icons.Default.Tune, "Equalizer", tint = White) }
            IconButton(onClick = { showVolume = !showVolume; state.tick() }) { Icon(Icons.Default.VolumeUp, "Volume", tint = if (showVolume) Accent else White) }
            IconButton(onClick = { state.push(Overlay.SLEEP) }) { Icon(Icons.Default.Timer, "Sleep timer", tint = White) }
            IconButton(onClick = { state.push(Overlay.LYRICS) }) { Icon(Icons.Default.QueueMusic, "Lyrics and queue", tint = White) }
        }
        val accent = Accent
        val playing = state.isPlaying
        Canvas(Modifier.fillMaxWidth().height(46.dp)) {
            val time = frame.value / 450_000_000f
            val steps = 64
            val path = Path()
            val faint = Path()
            for (i in 0..steps) {
                val x = size.width * i / steps
                val level = bandLevel(i * 31 / steps, 32, frame.value, playing)
                val amplitude = size.height * 0.42f * level
                val y = size.height / 2f - amplitude * sin(i * 0.38f + time)
                val y2 = size.height / 2f + amplitude * 0.6f * sin(i * 0.3f - time * 0.8f)
                if (i == 0) { path.moveTo(x, y); faint.moveTo(x, y2) } else { path.lineTo(x, y); faint.lineTo(x, y2) }
            }
            drawPath(faint, accent.copy(alpha = 0.28f), style = Stroke(width = 1.5f.dp.toPx(), cap = StrokeCap.Round))
            drawPath(path, accent.copy(alpha = 0.85f), style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(6.dp))
    }
}

// ============================================================================ lyrics + queue

@Composable
internal fun LyricsQueueScreen(state: AppState) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { state.pop() }) { Icon(Icons.Default.ChevronLeft, "Back", tint = White, modifier = Modifier.size(30.dp)) }
            Spacer(Modifier.weight(1f))
            SegmentedPill(listOf("Lyrics", "Queue"), tab, { tab = it; state.tick() })
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(48.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (tab == 0) LyricsPane(state) else QueuePane(state)
        }
        MiniPlayer(state)
    }
}

@Composable
private fun LyricsPane(state: AppState) {
    val parsed = remember(state.lyricsRaw) { parseLyrics(state.lyricsRaw) }
    if (parsed.lines.isEmpty()) {
        Column(
            Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("No lyrics for this track", color = White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
            Text(
                "Import a .lrc file for synced lyrics, or a plain .txt file.",
                color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 10.dp)
            )
            GradientButton("Import lyrics", { state.pickLyrics() }, Modifier.padding(horizontal = 30.dp))
        }
        return
    }
    val active = if (parsed.synced) parsed.lines.indexOfLast { it.timeMs <= state.position } else -1
    val listState = rememberLazyListState()
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem((active - 2).coerceAtLeast(0))
    }
    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 30.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(parsed.lines) { index, line ->
            val isActive = index == active
            val near = active >= 0 && kotlin.math.abs(index - active) <= 1
            Text(
                line.text.ifBlank { "♪" },
                color = when {
                    !parsed.synced -> White.copy(alpha = .9f)
                    isActive -> White
                    near -> Muted
                    else -> Muted.copy(alpha = .55f)
                },
                fontSize = if (isActive) 22.sp else 18.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.fillMaxWidth().clickable(enabled = parsed.synced) { state.seekTo(line.timeMs) }
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { state.pickLyrics() }) { Text("Replace lyrics", color = Accent, fontSize = 12.sp) }
                TextButton(onClick = { state.clearLyrics() }) { Text("Remove", color = Muted, fontSize = 12.sp) }
            }
        }
    }
}

@Composable
private fun QueuePane(state: AppState) {
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        if (state.currentIndex in state.queue.indices) listState.scrollToItem(state.currentIndex)
    }
    if (state.queue.isEmpty()) {
        EmptyState("Your queue is empty", "Play a song to start building a queue.")
        return
    }
    LazyColumn(
        state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(state.queue) { index, song ->
            val current = index == state.currentIndex
            val upcoming = index > state.currentIndex
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .background(if (current) Color(0x16FFFFFF) else Color.Transparent)
                    .clickable { state.seekToIndex(index) }.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (current) "♫" else "${index + 1}", color = if (current) Accent else Muted, fontSize = 12.sp, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = if (current) Accent else White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (upcoming) {
                    IconButton(onClick = { state.moveInQueue(index, index - 1) }, enabled = index - 1 > state.currentIndex, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, "Move up", tint = if (index - 1 > state.currentIndex) White else Muted.copy(alpha = .3f))
                    }
                    IconButton(onClick = { state.moveInQueue(index, index + 1) }, enabled = index < state.queue.lastIndex, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, "Move down", tint = if (index < state.queue.lastIndex) White else Muted.copy(alpha = .3f))
                    }
                    IconButton(onClick = { state.removeFromQueue(index) }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Default.Close, "Remove from queue", tint = Muted)
                    }
                }
            }
        }
    }
}
