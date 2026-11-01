package com.retropod.player

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material.icons.filled.PlayArrow
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
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

// ============================================================================ now playing

@Composable
internal fun NowPlayingScreen(state: AppState) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val song = state.currentSong
    var moreOpen by remember { mutableStateOf(false) }
    var showVolume by remember { mutableStateOf(false) }
    var heightPx by remember { mutableIntStateOf(1) }
    val frame = rememberFrameClock(state.isPlaying)

    // Swipe down = close Now Playing, swipe up = open the queue. The whole screen follows the finger.
    val dragState = rememberDraggableState { delta ->
        val floor = -with(density) { 90.dp.toPx() }
        state.playerDragY = (state.playerDragY + delta).coerceAtLeast(floor)
    }

    Column(
        Modifier.fillMaxSize()
            .onSizeChanged { heightPx = it.height.coerceAtLeast(1) }
            .draggable(
                state = dragState,
                orientation = Orientation.Vertical,
                onDragStopped = { velocity ->
                    val closeAt = heightPx * 0.2f
                    val openAt = -with(density) { 56.dp.toPx() }
                    when {
                        state.playerDragY > closeAt || velocity > 1400f -> {
                            animate(state.playerDragY, heightPx.toFloat(), animationSpec = tween(200)) { value, _ -> state.playerDragY = value }
                            state.pop()
                        }
                        state.playerDragY < openAt || velocity < -1100f -> {
                            state.playerDragY = 0f
                            state.openQueue()
                        }
                        else -> animate(state.playerDragY, 0f, animationSpec = spring()) { value, _ -> state.playerDragY = value }
                    }
                }
            )
    ) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            // ---- top bar: back | Now Playing | equalizer | more
            Box(Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { state.pop() }) {
                        Icon(Icons.Default.ChevronLeft, "Back", tint = White, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { state.push(Overlay.EQUALIZER) }) {
                        Icon(Icons.Default.Tune, "Equalizer", tint = White, modifier = Modifier.rotate(90f))
                    }
                    Box {
                        IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "More", tint = White) }
                        DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                            if (song != null) {
                                val favorite = state.isFavorite(song.id)
                                DropdownMenuItem(
                                    text = { Text(if (favorite) "Remove from favorites" else "Add to favorites") },
                                    onClick = { moreOpen = false; state.toggleFavorite(song) }
                                )
                            }
                            DropdownMenuItem(text = { Text("Queue") }, onClick = { moreOpen = false; state.openQueue() })
                            DropdownMenuItem(text = { Text("Lyrics") }, onClick = { moreOpen = false; state.openLyrics() })
                            DropdownMenuItem(text = { Text("Visualizer") }, onClick = { moreOpen = false; state.push(Overlay.VISUALIZER) })
                            DropdownMenuItem(text = { Text("Sleep timer") }, onClick = { moreOpen = false; state.push(Overlay.SLEEP) })
                            DropdownMenuItem(
                                text = { Text(if (showVolume) "Hide volume" else "Volume") },
                                onClick = { moreOpen = false; showVolume = !showVolume }
                            )
                            DropdownMenuItem(text = { Text("Output device") }, onClick = {
                                moreOpen = false
                                try {
                                    context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                } catch (_: Exception) {
                                    try {
                                        context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                    } catch (_: Exception) {
                                        state.toast("No output settings available on this device.")
                                    }
                                }
                            })
                            DropdownMenuItem(text = { Text("More actions…") }, onClick = { moreOpen = false; state.menuSong = song })
                        }
                    }
                }
                Text(
                    "Now Playing", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            // ---- artwork: swipe right = next song, swipe left = previous (configurable in Settings)
            BoxWithConstraints(
                Modifier.weight(1f).fillMaxWidth().swipeToChangeTrack(state),
                contentAlignment = Alignment.Center
            ) {
                val side = minOf(maxWidth * 0.72f, maxHeight - 12.dp).coerceAtLeast(120.dp)
                Box(Modifier.size(side)) {
                    AlbumArtwork(song, Math.floorMod(state.currentIndex, 4), Modifier.fillMaxSize(), 34.dp)
                    Box(Modifier.fillMaxSize().border(1.dp, Color(0xAAFFFFFF), RoundedCornerShape(34.dp)))
                }
            }

            // ---- title + artist
            Text(
                song?.title ?: "Nothing playing", color = White, fontSize = 26.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)
            )
            Text(
                song?.artist ?: "Choose a song from your library", color = Muted, fontSize = 17.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )
            Spacer(Modifier.height(10.dp))

            // ---- seek bar with glow + times
            val progress = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
            GlowSeekBar(
                progress = progress, playing = state.isPlaying, enabled = state.duration > 0, glow = state.waveformSeek,
                onSeekFraction = { fraction -> state.seekTo((fraction * state.duration).toLong()) },
                modifier = Modifier.fillMaxWidth().height(66.dp)
            )
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatTime(state.position), color = Muted, fontSize = 13.sp)
                Text(formatTime(state.duration), color = Muted, fontSize = 13.sp)
            }
            Spacer(Modifier.height(14.dp))

            // ---- transport
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { state.toggleShuffle() }) {
                    Icon(Icons.Default.Shuffle, "Shuffle", tint = if (state.shuffleOn) Accent else White, modifier = Modifier.size(28.dp))
                }
                IconButton(onClick = { state.skipPrevious() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipPrevious, "Previous", tint = White, modifier = Modifier.size(42.dp))
                }
                BigPlayPause(playing = state.isPlaying, onClick = { state.togglePlayPause() })
                IconButton(onClick = { state.skipNext() }, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Default.SkipNext, "Next", tint = White, modifier = Modifier.size(42.dp))
                }
                IconButton(onClick = { state.cycleRepeat() }) {
                    Icon(
                        if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        "Repeat", tint = if (state.repeatMode == Player.REPEAT_MODE_OFF) White else Accent, modifier = Modifier.size(28.dp)
                    )
                }
            }
            if (showVolume) {
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.VolumeDown, null, tint = Muted)
                    Slider(
                        value = state.volume, onValueChange = { state.setVolumeLevel(it) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        colors = SliderDefaults.colors(thumbColor = White, activeTrackColor = White, inactiveTrackColor = Muted.copy(alpha = .3f))
                    )
                    Icon(Icons.Default.VolumeUp, null, tint = Muted)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        FlowingWaves(playing = state.isPlaying, frame = frame, modifier = Modifier.fillMaxWidth().height(84.dp))
    }
}

@Composable
private fun BigPlayPause(playing: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(80.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = { (scaleIn(tween(180), initialScale = .6f) + fadeIn(tween(180))) togetherWith (scaleOut(tween(120), targetScale = .6f) + fadeOut(tween(120))) },
            label = "bigPlayPause"
        ) { isPlaying ->
            if (isPlaying) {
                Canvas(Modifier.size(width = 40.dp, height = 46.dp)) {
                    val bar = size.width * 0.36f
                    val radius = CornerRadius(bar / 2f)
                    drawRoundRect(White, Offset(0f, 0f), Size(bar, size.height), radius)
                    drawRoundRect(White, Offset(size.width - bar, 0f), Size(bar, size.height), radius)
                }
            } else {
                Icon(Icons.Default.PlayArrow, "Play", tint = White, modifier = Modifier.size(68.dp))
            }
        }
    }
}

/** Thin seek bar with a soft, layered glow that rises over the part of the track already played. */
@Composable
private fun GlowSeekBar(
    progress: Float, playing: Boolean, enabled: Boolean, glow: Boolean,
    onSeekFraction: (Float) -> Unit, modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "seekGlow")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(3600, easing = LinearEasing)), label = "seekGlowPhase")
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeekFraction)
    val shown = (dragFraction ?: progress).coerceIn(0f, 1f)
    Canvas(
        modifier
            .pointerInput(enabled) {
                detectTapGestures { tap ->
                    val pad = 11.dp.toPx()
                    if (enabled && size.width > 0) seek(((tap.x - pad) / (size.width - pad * 2)).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                detectDragGestures(
                    onDragStart = { point ->
                        val pad = 11.dp.toPx()
                        if (enabled && size.width > 0) dragFraction = ((point.x - pad) / (size.width - pad * 2)).coerceIn(0f, 1f)
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val pad = 11.dp.toPx()
                        if (enabled && size.width > 0) dragFraction = ((change.position.x - pad) / (size.width - pad * 2)).coerceIn(0f, 1f)
                    },
                    onDragEnd = { dragFraction?.let { seek(it) }; dragFraction = null },
                    onDragCancel = { dragFraction = null }
                )
            }
    ) {
        val pad = 11.dp.toPx()
        val trackWidth = size.width - pad * 2
        val baseY = size.height - 14.dp.toPx()
        val cursor = pad + trackWidth * shown
        val thickness = 5.dp.toPx()

        if (glow && enabled && cursor - pad > 28.dp.toPx()) {
            val maxHeight = baseY - 4.dp.toPx()
            val amp = if (playing) 1f else 0.6f
            val ph = if (playing) phase else 0.2f
            for (layer in 0 until 3) {
                val path = Path()
                path.moveTo(pad, baseY)
                val steps = 56
                for (i in 0..steps) {
                    val u = i / steps.toFloat()
                    val x = pad + (cursor - pad) * u
                    val envelope = sin(PI.toFloat() * u).coerceAtLeast(0f).pow(1.4f)
                    val wave = 0.55f + 0.45f * sin(u * (6f + layer * 2.6f) + ph * 2f * PI.toFloat() * (1f + layer * 0.35f) + layer * 1.7f)
                    path.lineTo(x, baseY - maxHeight * envelope * wave * (0.92f - layer * 0.22f) * amp)
                }
                path.lineTo(cursor, baseY)
                path.close()
                drawPath(
                    path,
                    Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.50f - layer * 0.12f), Color.Transparent),
                        startY = baseY - maxHeight, endY = baseY
                    )
                )
            }
        }
        drawLine(Color(0x8CFFFFFF), Offset(pad, baseY), Offset(pad + trackWidth, baseY), thickness, StrokeCap.Round)
        drawLine(Color.White, Offset(pad, baseY), Offset(cursor, baseY), thickness, StrokeCap.Round)
        if (enabled) {
            drawCircle(Color.White, 11.dp.toPx(), Offset(cursor, baseY))
            drawCircle(Color(0xFF1B2238), 7.dp.toPx(), Offset(cursor, baseY))
        }
    }
}

/** Decorative flowing lines along the bottom edge of Now Playing. They drift while music plays. */
@Composable
private fun FlowingWaves(playing: Boolean, frame: State<Long>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val seconds = frame.value / 1_000_000_000f
        val amp = if (playing) 1f else 0.7f
        val steps = 90
        for (layer in 0 until 4) {
            val path = Path()
            val baseY = size.height * (0.55f + layer * 0.08f)
            for (i in 0..steps) {
                val u = i / steps.toFloat()
                val x = size.width * u
                val envelope = 0.3f + 0.7f * sin(PI.toFloat() * u)
                val y = baseY + size.height * 0.24f * amp * envelope *
                    sin(u * (4.2f + layer * 0.9f) + seconds * (0.7f + layer * 0.2f) + layer * 1.3f)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            val alpha = if (layer == 0) 0.9f else 0.28f - layer * 0.04f
            drawPath(path, Color.White.copy(alpha = alpha), style = Stroke(width = (if (layer == 0) 1.8f else 1.1f).dp.toPx(), cap = StrokeCap.Round))
        }
    }
}

// ============================================================================ lyrics + queue

@Composable
internal fun LyricsQueueScreen(state: AppState) {
    var tab by remember { mutableIntStateOf(state.lyricsTab) }
    val closeDrag = rememberDraggableState { }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)
                // Swipe down on the header to go back to Now Playing.
                .draggable(closeDrag, Orientation.Vertical, onDragStopped = { velocity -> if (velocity > 900f) state.pop() }),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
