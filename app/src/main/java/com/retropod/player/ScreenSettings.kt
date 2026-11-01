package com.retropod.player

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class SettingsEntry(val icon: ImageVector, val title: String, val subtitle: String)

private val SETTINGS_ENTRIES = listOf(
    SettingsEntry(Icons.Default.Palette, "Appearance", "Name, accent, player style"),
    SettingsEntry(Icons.Default.PlayArrow, "Playback", "Resume, skip silence, headphones"),
    SettingsEntry(Icons.Default.VolumeUp, "Audio", "Equalizer, volume, output"),
    SettingsEntry(Icons.Default.LibraryMusic, "Library", "Scan, filter, exclude folders"),
    SettingsEntry(Icons.Default.AutoAwesome, "Experience", "Haptics, click sound, animations"),
    SettingsEntry(Icons.Default.Backup, "Backup & Restore", "Playlists, favorites, settings"),
    SettingsEntry(Icons.Default.Info, "About", "RetroPod 2.0")
)

@Composable
internal fun SettingsScreen(state: AppState) {
    val page = state.settingsPage
    Column(Modifier.fillMaxSize()) {
        RetroTopBar(
            if (page == null) "Settings" else SETTINGS_ENTRIES[page].title,
            onBack = { if (page == null) state.pop() else state.settingsPage = null }
        )
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp)) {
            when (page) {
                null -> {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp))) {
                        SETTINGS_ENTRIES.forEachIndexed { index, entry ->
                            Row(
                                Modifier.fillMaxWidth().clickable { state.settingsPage = index; state.tick() }.padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(entry.icon, null, tint = White, modifier = Modifier.size(24.dp))
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    Text(entry.subtitle, color = Muted, fontSize = 12.sp)
                                }
                                Icon(Icons.Default.ChevronRight, null, tint = Muted)
                            }
                            if (index != SETTINGS_ENTRIES.lastIndex) HorizontalDivider(color = Color(0x14FFFFFF), modifier = Modifier.padding(start = 56.dp))
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "PURE BLACK • LOCAL MUSIC • NO DISTRACTIONS", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                0 -> AppearancePage(state)
                1 -> PlaybackPage(state)
                2 -> AudioPage(state)
                3 -> LibraryPage(state)
                4 -> ExperiencePage(state)
                5 -> BackupPage(state)
                else -> AboutPage()
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 14.dp).glass(RoundedCornerShape(20.dp)).padding(vertical = 4.dp)) { content() }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 15.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = Accent, checkedThumbColor = Night)
        )
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 15.sp)
            Text(subtitle, color = Muted, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun AppearancePage(state: AppState) {
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your name", color = White, fontSize = 15.sp)
            OutlinedTextField(
                value = state.userName, onValueChange = { state.updateUserName(it.take(24)) }, singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Accent, unfocusedBorderColor = Muted
                )
            )
            Text("Shown in the Home greeting.", color = Muted, fontSize = 12.sp)
        }
    }
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Accent colour", color = White, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Pal.accents.forEachIndexed { index, (name, color) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(color)
                                .border(if (Pal.accentIndex == index) 3.dp else 1.dp, if (Pal.accentIndex == index) White else Color(0x44FFFFFF), CircleShape)
                                .clickable { state.updateAccent(index); state.tick() }
                        )
                        Text(name, color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
    SettingsCard {
        ToggleRow("Waveform seek bar", "Use the animated wave in Now Playing instead of a plain slider", state.waveformSeek, state::updateWaveformSeek)
    }
}

@Composable
private fun PlaybackPage(state: AppState) {
    SettingsCard {
        ToggleRow("Resume last session", "Reopen your queue paused at the last position", state.resumeSession, state::updateResumeSession)
        HorizontalDivider(color = Color(0x14FFFFFF))
        ToggleRow("Skip silence", "Trim silent gaps inside tracks", state.skipSilence, state::updateSkipSilence)
        HorizontalDivider(color = Color(0x14FFFFFF))
        ToggleRow("Pause when headphones unplug", "Stops playback if the output disconnects", state.pauseOnUnplug, state::updatePauseOnUnplug)
    }
    Text("Tracks on the same album play back-to-back without gaps when the file format allows it.", color = Muted, fontSize = 12.sp)
}

@Composable
private fun AudioPage(state: AppState) {
    SettingsCard {
        ActionRow(Icons.Default.Tune, "Equalizer", "Bands and presets for the active player") { state.push(Overlay.EQUALIZER) }
        HorizontalDivider(color = Color(0x14FFFFFF))
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text("Player volume", color = White, fontSize = 15.sp)
            Slider(
                value = state.volume, onValueChange = { state.setVolumeLevel(it) },
                colors = SliderDefaults.colors(thumbColor = White, activeTrackColor = Accent, inactiveTrackColor = Muted.copy(alpha = .3f))
            )
        }
        HorizontalDivider(color = Color(0x14FFFFFF))
        ActionRow(Icons.Default.VolumeUp, "Output device", "Cast, Bluetooth and speakers") {
            try {
                state.context.startActivity(Intent(Settings.ACTION_CAST_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
                try {
                    state.context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    state.toast("No output settings available on this device.")
                }
            }
        }
    }
}

@Composable
private fun LibraryPage(state: AppState) {
    var folders by remember { mutableStateOf(state.excludedFolders) }
    SettingsCard {
        ActionRow(Icons.Default.Refresh, "Rescan music", "Refresh songs and albums on this device") {
            state.refreshLibrary(); state.toast("Scanning your library…")
        }
    }
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Hide short tracks", color = White, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Off", 30 to "< 30s", 60 to "< 60s").forEach { (seconds, label) ->
                    RetroChip(label, state.minTrackSeconds == seconds, { state.updateMinTrackSeconds(seconds); state.tick() })
                }
            }
            Text("Useful for hiding ringtones and voice notes.", color = Muted, fontSize = 12.sp)
        }
    }
    SettingsCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Exclude folders", color = White, fontSize = 15.sp)
            OutlinedTextField(
                value = folders, onValueChange = { folders = it }, singleLine = false,
                placeholder = { Text("WhatsApp, Recordings", color = Muted) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Accent, unfocusedBorderColor = Muted
                )
            )
            Text("Separate folder names with commas. Any song whose path contains one is hidden.", color = Muted, fontSize = 12.sp)
            TextButton(onClick = { state.updateExcludedFolders(folders); state.toast("Library updated.") }) { Text("Apply", color = Accent) }
        }
    }
}

@Composable
private fun ExperiencePage(state: AppState) {
    SettingsCard {
        ToggleRow("Haptic feedback", "Subtle tactile response on controls and gestures", state.hapticsEnabled, state::updateHaptics)
        HorizontalDivider(color = Color(0x14FFFFFF))
        ToggleRow("Interface click sounds", "Play a soft click on controls and the Cover Flow dial", state.clickSoundsEnabled, state::updateClickSounds)
        HorizontalDivider(color = Color(0x14FFFFFF))
        ToggleRow("Smooth animations", "Cover flow depth and screen transitions", state.animationsEnabled, state::updateAnimations)
    }
    SettingsCard {
        if (state.micGranted) {
            Column(Modifier.padding(16.dp)) {
                Text("Live visualizer", color = White, fontSize = 15.sp)
                Text("Reacting to the audio output.", color = Muted, fontSize = 12.sp)
            }
        } else {
            ActionRow(Icons.Default.AutoAwesome, "Enable live visualizer", "Android requires audio-capture permission to read the output") {
                state.requestMic()
            }
        }
    }
}

@Composable
private fun BackupPage(state: AppState) {
    SettingsCard {
        ActionRow(Icons.Default.Upload, "Export backup", "Save settings, favorites, playlists and lyrics as JSON") { state.launchBackup() }
        HorizontalDivider(color = Color(0x14FFFFFF))
        ActionRow(Icons.Default.Download, "Restore backup", "Import a previously exported RetroPod backup") { state.launchRestore() }
    }
}

@Composable
private fun AboutPage() {
    SettingsCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PodLogo(Modifier.size(width = 44.dp, height = 66.dp))
            Spacer(Modifier.height(6.dp))
            Text("RetroPod 2.0", color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text("Version 2.7.0", color = Muted, fontSize = 13.sp)
            Text("A local music player, made for the love of music.", color = Muted, fontSize = 13.sp)
            Text("Music, Timelessly.", color = White, fontSize = 13.sp)
        }
    }
}
