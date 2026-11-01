package com.retropod.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.sin

// ============================================================================ splash

@Composable
internal fun SplashScreen(onDone: () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(1500, easing = FastOutSlowInEasing))
        onDone()
    }
    Box(Modifier.fillMaxSize().background(Night).pointerInput(Unit) { detectTapGestures { } }) {
        Canvas(Modifier.fillMaxSize()) {
            for (line in 0 until 26) {
                val path = Path()
                val baseY = size.height * (0.52f + line * 0.014f)
                path.moveTo(0f, baseY)
                var x = 0f
                while (x <= size.width) {
                    val y = baseY + sin(x / size.width * 6.2f + line * 0.16f) * size.height * 0.07f +
                        sin(x / size.width * 11f - line * 0.1f) * size.height * 0.02f
                    path.lineTo(x, y)
                    x += 12f
                }
                drawPath(path, Color.White.copy(alpha = 0.04f + line * 0.006f), style = Stroke(width = 1.2f))
            }
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            PodLogo(Modifier.size(width = 104.dp, height = 156.dp))
            Spacer(Modifier.height(18.dp))
            Text("RetroPod", color = White, fontSize = 38.sp, fontWeight = FontWeight.Medium)
            Text("2.0", color = Muted, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.GraphicEq, null, tint = White, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(8.dp))
            Text("Music, Timelessly.", color = Muted, fontSize = 14.sp)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.width(150.dp).height(3.dp).clip(CircleShape).background(Color(0x26FFFFFF))) {
                Box(Modifier.fillMaxWidth(progress.value).height(3.dp).background(White))
            }
            Spacer(Modifier.height(54.dp))
        }
    }
}

/** A small iPod-style glyph drawn in code so the splash needs no extra image resources. */
@Composable
internal fun PodLogo(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = Stroke(width = 3.dp.toPx())
        val body = CornerRadius(20.dp.toPx())
        drawRoundRect(Color(0x14FFFFFF), size = size, cornerRadius = body)
        drawRoundRect(White, size = size, cornerRadius = body, style = stroke)
        val screenTop = size.height * 0.09f
        drawRoundRect(
            White, Offset(size.width * 0.14f, screenTop), Size(size.width * 0.72f, size.height * 0.36f),
            CornerRadius(6.dp.toPx()), style = Stroke(width = 2.5f.dp.toPx())
        )
        val wheelCenter = Offset(size.width / 2f, size.height * 0.72f)
        drawCircle(White, size.width * 0.34f, wheelCenter, style = Stroke(width = 2.5f.dp.toPx()))
        drawCircle(Color(0x66FFFFFF), size.width * 0.12f, wheelCenter)
        drawCircle(White, size.width * 0.12f, wheelCenter, style = Stroke(width = 1.5f.dp.toPx()))
    }
}

// ============================================================================ home

@Composable
internal fun HomeTab(state: AppState) {
    val recent = remember(state.recentIds, state.songsById) { state.recentSongs().take(8) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(greeting(), color = White, fontSize = 28.sp, fontWeight = FontWeight.Medium)
                    Text("${state.userName} ♫", color = White.copy(alpha = .86f), fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
                }
                Box(
                    Modifier.size(52.dp).clip(CircleShape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF3A3A3E), Color(0xFF111112))))
                        .border(1.dp, Color(0x40FFFFFF), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        state.userName.trim().take(1).uppercase().ifEmpty { "♫" },
                        color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold
                    )
                }
                IconButton(onClick = { state.settingsPage = null; state.push(Overlay.SETTINGS) }) {
                    Icon(Icons.Default.Settings, "Settings", tint = White)
                }
            }
        }
        if (!state.permissionGranted) {
            item { PermissionCard(onGrant = state.requestPermissions) }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeCard("Songs", formatCount(state.songs.size), Icons.Default.MusicNote, Modifier.weight(1f)) {
                            state.songFilter = SongFilter.ALL; state.tab = Tab.SONGS; state.tick()
                        }
                        HomeCard("Albums", formatCount(state.albums.size), Icons.Default.Album, Modifier.weight(1f)) {
                            state.tab = Tab.ALBUMS; state.tick()
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HomeCard("Playlists", formatCount(state.playlists.size), Icons.Default.QueueMusic, Modifier.weight(1f)) {
                            state.tab = Tab.PLAYLISTS; state.tick()
                        }
                        HomeCard("Favorites", formatCount(state.favorites.size), Icons.Default.Favorite, Modifier.weight(1f)) {
                            state.songFilter = SongFilter.FAVORITES; state.tab = Tab.SONGS; state.tick()
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recently Played", color = White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        "See All", color = Muted, fontSize = 13.sp,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable {
                            state.songFilter = SongFilter.RECENT; state.tab = Tab.SONGS; state.tick()
                        }.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
            item {
                if (recent.isEmpty()) {
                    Text("Songs you play will show up here.", color = Muted, fontSize = 13.sp)
                } else {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 4.dp)) {
                        items(recent, key = { it.id }) { song ->
                            Column(
                                Modifier.width(104.dp).clickable { state.playList(recent, recent.indexOf(song)) },
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                                    AlbumArtwork(song, Math.floorMod(song.album.hashCode(), 4), Modifier.fillMaxSize(), 16.dp)
                                    Box(
                                        Modifier.align(Alignment.Center).size(34.dp).clip(CircleShape)
                                            .background(Color(0x99000000)).border(1.dp, Color(0x80FFFFFF), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) { Icon(Icons.Default.PlayArrow, null, tint = White, modifier = Modifier.size(20.dp)) }
                                }
                                Text(song.title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable { state.playMix() }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, null, tint = White)
                    Spacer(Modifier.width(14.dp))
                    Text("Made for You", color = White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Icon(Icons.Default.AutoAwesome, null, tint = Muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
            }
            if (state.songs.isEmpty()) {
                item {
                    EmptyState("Your library is waiting", "Add music to your phone, then rescan from Settings > Library.")
                }
            }
        }
    }
}

@Composable
private fun HomeCard(
    title: String, count: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Column(
        modifier.glass(RoundedCornerShape(20.dp)).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp).heightIn(min = 82.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(icon, null, tint = White, modifier = Modifier.size(24.dp))
        Spacer(Modifier.height(8.dp))
        Column {
            Text(title, color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(count, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
internal fun PermissionCard(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(Icons.Default.LibraryMusic, null, tint = White, modifier = Modifier.size(44.dp))
        Text("Let the music in", color = White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(
            "Allow audio access so RetroPod can find the music on your phone. Notifications let you control playback from the lock screen.",
            color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
        GradientButton("Allow access", onGrant)
    }
}
