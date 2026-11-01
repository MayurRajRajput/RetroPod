package com.retropod.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.retropod.player.ui.components.GlassCard
import com.retropod.player.ui.components.VerticalSlider
import com.retropod.player.ui.components.WaveVisualizer
import com.retropod.player.ui.theme.*

@Composable
fun HomeScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(RetroBlack).padding(16.dp)) {
        Text("Good Morning", color = RetroGray, fontSize = 16.sp)
        Text("Mayur ♫", color = RetroWhite, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
            GlassCard(modifier = Modifier.weight(1f)) {
                Column {
                    Icon(Icons.Default.LibraryMusic, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Songs", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("2,481", color = RetroGray, fontSize = 12.sp)
                }
            }
            GlassCard(modifier = Modifier.weight(1f)) {
                Column {
                    Icon(Icons.Default.Album, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Albums", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("328", color = RetroGray, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Recently Played", color = Color.White, fontWeight = FontWeight.Bold)
            Text("See All", color = RetroGray)
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        // Mock horizontal list
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("Biogenesis", "Starboy", "After Dark").forEach { title ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)).background(RetroSurface))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(title, color = Color.White, fontSize = 12.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        GlassCard(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("+ Made for You", color = Color.White)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RetroGray)
            }
        }
    }
}

@Composable
fun SongsScreen(navController: NavController) {
    val songs = listOf("After Dark", "Blinding Lights", "Biogenesis", "Darkside", "Faded", "Starboy", "Sunflower")
    Column(modifier = Modifier.fillMaxSize().background(RetroBlack)) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.White)
            Text("Songs", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Row {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                Spacer(modifier = Modifier.width(16.dp))
                Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
            }
        }
        
        // Tabs
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("All", color = Color.Black, fontSize = 14.sp)
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, RetroGray, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Favorites", color = RetroGray, fontSize = 14.sp)
            }
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, RetroGray, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Downloaded", color = RetroGray, fontSize = 14.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyColumn {
            items(songs) { song ->
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(RetroSurface))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(song, color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Artist Name • 3:20", color = RetroGray, fontSize = 12.sp)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Default.FavoriteBorder, contentDescription = null, tint = RetroGray)
                }
            }
        }
    }
}

@Composable
fun NowPlayingScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(RetroBlack).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Back", tint = Color.White)
            Text("Now Playing", color = Color.White)
            Icon(Icons.Default.Cast, contentDescription = "Cast", tint = Color.White)
            Icon(Icons.Default.MoreVert, contentDescription = "Options", tint = Color.White)
        }
        Spacer(modifier = Modifier.height(32.dp))
        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            WaveVisualizer()
        }
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Biogenesis", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Silicon Based Lifeforms", color = RetroGray, fontSize = 16.sp)
            }
            Icon(Icons.Default.FavoriteBorder, contentDescription = "Like", tint = Color.White)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Slider(value = 0.3f, onValueChange = {}, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = RetroGray))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("1:34", color = RetroGray, fontSize = 12.sp)
            Text("4:27", color = RetroGray, fontSize = 12.sp)
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Shuffle, contentDescription = "Shuffle", tint = RetroGray)
            Icon(Icons.Default.SkipPrevious, contentDescription = "Prev", tint = Color.White, modifier = Modifier.size(40.dp))
            Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Pause, contentDescription = "Pause", tint = Color.Black, modifier = Modifier.size(32.dp))
            }
            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(40.dp))
            Icon(Icons.Default.Repeat, contentDescription = "Repeat", tint = RetroGray)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Bottom Actions
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Icon(Icons.Default.Tune, contentDescription = "Equalizer", tint = Color.White)
            Icon(Icons.Default.VolumeUp, contentDescription = "Volume", tint = Color.White)
            Icon(Icons.Default.Timer, contentDescription = "Timer", tint = Color.White)
            Icon(Icons.Default.QueueMusic, contentDescription = "Queue", tint = Color.White)
        }
    }
}

@Composable
fun SettingsScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(RetroBlack).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp)) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.White)
            Spacer(modifier = Modifier.width(16.dp))
            Text("Settings", color = Color.White, fontSize = 24.sp)
        }
        val settings = listOf(
            "Appearance" to "Theme, accent, player style",
            "Playback" to "Crossfade, gapless, resume",
            "Audio" to "Equalizer, volume, audio effects",
            "Library" to "Scan, filter, exclude folders",
            "Experience" to "Haptics, click sound, animations",
            "Backup & Restore" to "Playlists, favorites, settings",
            "About" to "RetroPod 2.0"
        )
        settings.forEach { setting ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = Color.White) // Placeholder icon
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(setting.first, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(setting.second, color = RetroGray, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RetroGray)
            }
        }
    }
}

@Composable
fun EqualizerScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(RetroBlack).padding(24.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.White)
            Text("Equalizer", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Switch(checked = true, onCheckedChange = {})
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 6.dp)) {
                Text("Custom", color = Color.Black, fontSize = 12.sp)
            }
            listOf("Bass", "Pop", "Rock", "Vocal").forEach {
                Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, RetroGray, RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(it, color = RetroGray, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("60", "150", "400", "1K", "2.4K", "6K", "14K").forEach { label ->
                VerticalSlider(label = label, value = 0.5f, onValueChange = {})
            }
        }
    }
}

@Composable
fun CoverFlowScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(RetroBlack), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, RetroGray, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Cover Flow", color = RetroGray, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(RetroSurface).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Albums", color = Color.White, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        // Mock Cover Flow Center Item
        Box(modifier = Modifier.size(200.dp).background(RetroSurface))
        Spacer(modifier = Modifier.height(32.dp))
        Text("Biogenesis", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Silicon Based Lifeforms", color = RetroGray, fontSize = 16.sp)
        Spacer(modifier = Modifier.height(48.dp))
        // Mock Scroll Wheel
        Box(modifier = Modifier.size(200.dp).clip(CircleShape).border(2.dp, RetroSurface, CircleShape), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size(60.dp).clip(CircleShape).background(Color.White)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun SleepTimerScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(RetroBlack).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Back", tint = Color.White)
            Text("Sleep Timer", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = RetroBlack) // Spacer
        }
        Spacer(modifier = Modifier.height(48.dp))
        
        // Large Circular Timer
        Box(modifier = Modifier.size(250.dp).clip(CircleShape).border(2.dp, RetroSurface, CircleShape), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("30", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold)
                Text("Minutes", color = RetroGray, fontSize = 20.sp)
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            listOf("Off", "15m", "30m", "45m").forEach { label ->
                val isSelected = label == "30m"
                Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(if(isSelected) Color.White else RetroBlack).border(1.dp, RetroGray, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(label, color = if(isSelected) Color.Black else RetroGray, fontSize = 14.sp)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = {}, colors = ButtonDefaults.buttonColors(containerColor = RetroSurface), modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text("Start Timer", color = Color.White)
        }
    }
}

@Composable
fun LyricsScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize().background(RetroBlack).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(RetroSurface).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Lyrics", color = Color.White, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, RetroGray, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("Queue", color = RetroGray, fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.height(48.dp))
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
            Text("I've been losing sleep", color = RetroGray, fontSize = 20.sp)
            Text("Cause of you", color = RetroGray, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(24.dp))
            Text("And I can't get enough", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Of the way you move", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(24.dp))
            Text("It's like a dark paradise", color = RetroGray, fontSize = 20.sp)
            Text("No one else can see", color = RetroGray, fontSize = 20.sp)
            Text("But you and me", color = RetroGray, fontSize = 20.sp)
        }
    }
}

@Composable
fun PlaylistMenuOverlay() {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
        GlassCard(modifier = Modifier.fillMaxWidth(0.8f)) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(RetroSurface))
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("Biogenesis", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Silicon Based Lifeforms", color = RetroGray, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = RetroSurface)
                Spacer(modifier = Modifier.height(16.dp))
                val options = listOf("Play Next", "Add to Queue", "Add to Playlist", "Add to Favorites", "Song Details", "Share", "Delete from Device")
                options.forEach { option ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row {
                            Icon(Icons.Default.Menu, contentDescription = null, tint = Color.White) // Placeholder icons
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(option, color = Color.White)
                        }
                        if (option == "Add to Playlist") Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RetroGray)
                    }
                }
            }
        }
    }
}
