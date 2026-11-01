package com.retropod.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.LruCache
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

// ======================================================================== artwork

internal object ArtworkCache {
    private val cache = object : LruCache<Long, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Long, value: Bitmap): Int = value.byteCount
    }
    private val missing = HashSet<Long>()

    fun peek(id: Long): Bitmap? = cache.get(id)

    fun load(context: Context, song: Song, maxPx: Int = 640): Bitmap? {
        cache.get(song.id)?.let { return it }
        if (synchronized(missing) { song.id in missing }) return null
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, song.uri)
            val bytes = retriever.embeddedPicture
            if (bytes == null) {
                synchronized(missing) { missing.add(song.id) }
                null
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
                if (bitmap != null) cache.put(song.id, bitmap) else synchronized(missing) { missing.add(song.id) }
                bitmap
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever?.release() }
        }
    }
}

@Composable
internal fun AlbumArtwork(song: Song?, artIndex: Int, modifier: Modifier = Modifier, corner: Dp = 18.dp) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = song?.let { ArtworkCache.peek(it.id) }, song?.id) {
        value = if (song == null) null else ArtworkCache.peek(song.id)
            ?: withContext(Dispatchers.IO) { ArtworkCache.load(context, song) }
    }
    val fallback = when (Math.floorMod(artIndex, 4)) {
        0 -> R.drawable.cover_aurora
        1 -> R.drawable.cover_sunset
        2 -> R.drawable.cover_neon
        else -> R.drawable.cover_mono
    }
    Box(modifier.clip(RoundedCornerShape(corner)).background(SurfaceNight)) {
        val current = bitmap
        if (current != null) {
            val image = remember(current) { current.asImageBitmap() }
            Image(image, contentDescription = "Album artwork", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Image(
                painter = painterResource(fallback), contentDescription = "RetroPod artwork",
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
    }
}

// ======================================================================== frame clock

/** Emits a frame timestamp (ns) while [active]; read it inside draw lambdas to animate without recomposing. */
@Composable
internal fun rememberFrameClock(active: Boolean): State<Long> {
    val time = remember { mutableStateOf(0L) }
    LaunchedEffect(active) {
        if (active) {
            while (true) {
                withFrameNanos { time.value = it }
            }
        }
    }
    return time
}

// ======================================================================== basic building blocks

@Composable
internal fun RetroTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp).heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ChevronLeft, "Back", tint = White, modifier = Modifier.size(30.dp)) }
        } else {
            Spacer(Modifier.width(14.dp))
        }
        Text(
            title, color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        actions()
    }
}

@Composable
internal fun RetroChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.clip(shape)
            .background(if (selected) Color(0x30FFFFFF) else Color(0x10FFFFFF))
            .border(1.dp, if (selected) Color(0x60FFFFFF) else Color(0x18FFFFFF), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (selected) White else Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** Two-option pill switcher such as "Cover Flow | Albums". */
@Composable
internal fun SegmentedPill(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier.glass(shape).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, label ->
            Box(
                Modifier.clip(shape)
                    .background(if (index == selected) Color(0x2EFFFFFF) else Color.Transparent)
                    .border(1.dp, if (index == selected) Color(0x55FFFFFF) else Color.Transparent, shape)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (index == selected) White else Muted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun SongRow(
    song: Song, current: Boolean, favorite: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit, onFavorite: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (current) Color(0x14FFFFFF) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(song, Math.floorMod(song.album.hashCode(), 4), Modifier.size(46.dp), 10.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title, color = if (current) Accent else White, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${song.artist}  •  ${formatTime(song.durationMs)}", color = Muted, fontSize = 11.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onFavorite, modifier = Modifier.size(38.dp)) {
            Icon(
                if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (favorite) "Remove from favorites" else "Add to favorites",
                tint = if (favorite) White else Muted, modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun MiniPlayer(state: AppState, modifier: Modifier = Modifier) {
    if (state.queue.isEmpty()) return
    val song = state.currentSong
    val fraction = if (state.duration > 0) (state.position.toFloat() / state.duration).coerceIn(0f, 1f) else 0f
    Column(
        modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
            .glass(RoundedCornerShape(18.dp)).clickable { state.openPlayer() }
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArtwork(song, 3, Modifier.size(42.dp), 10.dp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(song?.title ?: "Nothing playing", color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song?.artist ?: "", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { state.togglePlayPause() }) {
                AnimatedContent(
                    targetState = state.isPlaying,
                    transitionSpec = { (scaleIn(tween(160), initialScale = .6f) + fadeIn(tween(160))) togetherWith (scaleOut(tween(120), targetScale = .6f) + fadeOut(tween(120))) },
                    label = "miniPlayPause"
                ) { playing ->
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, if (playing) "Pause" else "Play", tint = White, modifier = Modifier.size(28.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Color(0x22FFFFFF))) {
            Box(Modifier.fillMaxWidth(fraction).height(2.dp).background(Accent))
        }
    }
}

/** Outlined circular play/pause control with a pulsing glow and a morphing icon. */
@Composable
internal fun RingPlayButton(playing: Boolean, size: Dp, onClick: () -> Unit, glow: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "ringGlow")
    val pulse by transition.animateFloat(
        0.45f, 1f, infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse"
    )
    val glowAlpha = if (playing && glow) pulse * 0.34f else 0.10f
    val accent = Accent
    Box(
        Modifier.size(size)
            .drawBehind {
                val radius = this.size.minDimension * 0.85f
                drawCircle(Brush.radialGradient(listOf(accent.copy(alpha = glowAlpha), Color.Transparent), radius = radius), radius = radius)
            }
            .clip(CircleShape)
            .background(Color(0x12FFFFFF))
            .border(2.dp, accent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = playing,
            transitionSpec = { (scaleIn(tween(200), initialScale = .5f) + fadeIn(tween(200))) togetherWith (scaleOut(tween(140), targetScale = .5f) + fadeOut(tween(140))) },
            label = "ringPlayPause"
        ) { isPlaying ->
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = White, modifier = Modifier.size(size * 0.46f)
            )
        }
    }
}

@Composable
internal fun GradientButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier.fillMaxWidth().heightIn(min = 52.dp).clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0xFF4A4A4E), Color(0xFF232325))), shape)
            .border(1.dp, Color(0x55FFFFFF), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun EmptyState(title: String, detail: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, color = White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(detail, color = Muted, fontSize = 12.sp)
    }
}

// ======================================================================== wave seek bar

private fun waveShape(index: Int): Float =
    0.18f + 0.82f * abs(sin(index * 0.43f)) * (0.58f + 0.42f * abs(sin(index * 0.17f + 1.2f)))

/** Animated waveform progress bar. Drag or tap to seek; the wave gently pulses while playing. */
@Composable
internal fun WaveSeekBar(
    progress: Float, playing: Boolean, enabled: Boolean,
    onSeekFraction: (Float) -> Unit, modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "wave")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "phase")
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val seek by rememberUpdatedState(onSeekFraction)
    val shown = (dragFraction ?: progress).coerceIn(0f, 1f)
    val accent = Accent
    Canvas(
        modifier
            .pointerInput(enabled) {
                detectTapGestures { tap -> if (enabled && size.width > 0) seek((tap.x / size.width).coerceIn(0f, 1f)) }
            }
            .pointerInput(enabled) {
                detectDragGestures(
                    onDragStart = { point -> if (enabled && size.width > 0) dragFraction = (point.x / size.width).coerceIn(0f, 1f) },
                    onDrag = { change, _ ->
                        change.consume()
                        if (enabled && size.width > 0) dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = { dragFraction?.let { seek(it) }; dragFraction = null },
                    onDragCancel = { dragFraction = null }
                )
            }
    ) {
        val count = 64
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(1.5f)
        val centerY = size.height / 2f
        val playedUntil = shown * size.width
        for (i in 0 until count) {
            val pulse = if (playing) 1f + 0.16f * sin(phase * 2f * PI.toFloat() + i * 0.5f) else 1f
            val barHeight = (size.height * (0.12f + 0.80f * waveShape(i)) * pulse).coerceIn(2f, size.height)
            val x = i * (barWidth + gap) + barWidth / 2f
            val color = if (x <= playedUntil) accent else Muted.copy(alpha = 0.38f)
            drawLine(color, Offset(x, centerY - barHeight / 2f), Offset(x, centerY + barHeight / 2f), barWidth, StrokeCap.Round)
        }
        if (enabled) {
            val cursor = playedUntil.coerceIn(0f, size.width)
            drawCircle(Color.Black, 9.dp.toPx(), Offset(cursor, centerY))
            drawCircle(White, 7.dp.toPx(), Offset(cursor, centerY))
        }
    }
}

// ======================================================================== equalizer slider

/** Vertical slider; [value] runs 0 (bottom) to 1 (top). The fill starts at the centre line like a gain control. */
@Composable
internal fun VerticalBandSlider(
    value: Float, enabled: Boolean, onChange: (Float) -> Unit, modifier: Modifier = Modifier
) {
    val update by rememberUpdatedState(onChange)
    val accent = Accent
    Canvas(
        modifier
            .pointerInput(enabled) {
                detectTapGestures { tap ->
                    if (enabled && size.height > 0) update((1f - tap.y / size.height).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                detectDragGestures { change, _ ->
                    change.consume()
                    if (enabled && size.height > 0) update((1f - change.position.y / size.height).coerceIn(0f, 1f))
                }
            }
    ) {
        val radius = 10.dp.toPx()
        val trackTop = radius
        val trackBottom = size.height - radius
        val x = size.width / 2f
        val thumbY = trackBottom - (trackBottom - trackTop) * value.coerceIn(0f, 1f)
        val midY = (trackTop + trackBottom) / 2f
        val dim = if (enabled) 1f else 0.4f
        drawLine(Color(0x33FFFFFF), Offset(x, trackTop), Offset(x, trackBottom), 3.dp.toPx(), StrokeCap.Round)
        drawLine(accent.copy(alpha = dim), Offset(x, midY), Offset(x, thumbY), 3.dp.toPx(), StrokeCap.Round)
        drawLine(Color(0x44FFFFFF), Offset(x - 7.dp.toPx(), midY), Offset(x + 7.dp.toPx(), midY), 1.dp.toPx())
        drawCircle(Color.Black, radius + 2.dp.toPx(), Offset(x, thumbY))
        drawCircle(White.copy(alpha = dim), radius, Offset(x, thumbY))
    }
}

// ======================================================================== click-wheel dial

/**
 * iPod-style rotary dial. The ring is centred 62% of the way down the canvas so transport buttons can sit
 * inside it. Dragging around the ring emits +/-1 steps while the ticks scroll with the finger.
 */
@Composable
internal fun ClickDial(onStep: (Int) -> Unit, modifier: Modifier = Modifier) {
    var rotation by remember { mutableStateOf(0f) }
    val step by rememberUpdatedState(onStep)
    val accent = Accent
    Canvas(
        modifier.pointerInput(Unit) {
            var last = 0f
            var carry = 0f
            fun angleOf(p: Offset): Float =
                Math.toDegrees(atan2((p.y - size.height * 0.62f).toDouble(), (p.x - size.width / 2f).toDouble())).toFloat()
            detectDragGestures(
                onDragStart = { point -> last = angleOf(point); carry = 0f },
                onDrag = { change, _ ->
                    change.consume()
                    val now = angleOf(change.position)
                    var delta = now - last
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    last = now
                    rotation += delta
                    carry += delta
                    while (carry >= 18f) { carry -= 18f; step(1) }
                    while (carry <= -18f) { carry += 18f; step(-1) }
                }
            )
        }
    ) {
        val center = Offset(size.width / 2f, size.height * 0.62f)
        val radius = minOf(size.width * 0.5f, size.height * 0.62f) * 0.98f
        val spacing = 9f
        val offset = ((rotation % spacing) + spacing) % spacing
        for (i in -12..12) {
            val angle = -90f + i * spacing + offset
            val distanceFromTop = abs(angle + 90f)
            if (distanceFromTop > 84f) continue
            val fade = (1f - distanceFromTop / 84f).coerceIn(0f, 1f)
            val rad = Math.toRadians(angle.toDouble())
            val inner = Offset(center.x + (radius * 0.86f * cos(rad)).toFloat(), center.y + (radius * 0.86f * sin(rad)).toFloat())
            val outer = Offset(center.x + (radius * 0.98f * cos(rad)).toFloat(), center.y + (radius * 0.98f * sin(rad)).toFloat())
            drawLine(White.copy(alpha = 0.12f + 0.8f * fade), inner, outer, 2.dp.toPx(), StrokeCap.Round)
        }
        drawLine(accent, Offset(center.x, center.y - radius * 0.99f), Offset(center.x, center.y - radius * 0.78f), 3.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
internal fun animatedFloat(target: Float, durationMs: Int = 300): Float {
    val value by animateFloatAsState(target, tween(durationMs, easing = FastOutSlowInEasing), label = "animatedFloat")
    return value
}
