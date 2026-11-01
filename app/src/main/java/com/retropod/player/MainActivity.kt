package com.retropod.player

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private var openPlayerRequest by mutableIntStateOf(0)
    private var startedFromNotification = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        AppContextHolder.context = applicationContext
        if (intent?.getBooleanExtra(EXTRA_OPEN_PLAYER, false) == true) {
            startedFromNotification = true
            openPlayerRequest = 1
        }
        setContent {
            RetroPodTheme {
                RetroPodApp(
                    controller = controller,
                    openPlayerRequest = openPlayerRequest,
                    showSplash = !startedFromNotification && savedInstanceState == null
                )
            }
        }
    }

    /** Called when the notification is tapped while RetroPod is already running (launchMode=singleTop). */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(EXTRA_OPEN_PLAYER, false)) openPlayerRequest += 1
    }

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        controllerFuture = future
        future.addListener({
            try { controller = future.get() } catch (_: Exception) { controller = null }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }
}

@Composable
private fun RetroPodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Night, secondary = Muted,
            background = Night, surface = SurfaceNight, onSurface = White,
            onBackground = White, surfaceVariant = SurfaceRaised
        ),
        content = content
    )
}

@Composable
private fun RetroPodApp(controller: MediaController?, openPlayerRequest: Int, showSplash: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val state = remember { AppState(context, scope, view) }
    SideEffect { state.controller = controller }

    // ---- activity-result launchers
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.permissionGranted = state.hasAudioPermission()
        if (state.permissionGranted) state.refreshLibrary()
        else state.toast("Audio permission is needed to scan your music.", long = true)
    }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        state.micGranted = granted
        if (!granted) state.toast("Without it the visualizer uses a simulated animation.")
    }
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.importLyrics(uri)
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) state.exportBackup(uri)
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) state.restoreBackup(uri)
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        state.onDeleteResult(result.resultCode == Activity.RESULT_OK)
    }
    state.requestPermissions = {
        val missing = state.missingPermissions()
        if (missing.isNotEmpty()) permissionLauncher.launch(missing)
        else state.refreshLibrary()
    }
    state.requestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    state.pickLyrics = { lyricsPicker.launch(arrayOf("text/plain", "application/octet-stream", "application/x-subrip", "text/*")) }
    state.launchBackup = { backupLauncher.launch("retropod-backup.json") }
    state.launchRestore = { restoreLauncher.launch(arrayOf("application/json", "text/*")) }
    state.launchDelete = { sender -> deleteLauncher.launch(IntentSenderRequest.Builder(sender).build()) }

    // ---- start-up work
    var splashVisible by remember { mutableStateOf(showSplash) }
    LaunchedEffect(Unit) {
        state.loadLyricsFor(null)
        if (state.hasAudioPermission()) state.refreshLibrary()
        val missing = state.missingPermissions()
        if (missing.isNotEmpty()) {
            delay(if (showSplash) 1700L else 200L)
            permissionLauncher.launch(missing)
        }
    }
    LaunchedEffect(controller) { if (controller != null) state.pollLoop() }
    LaunchedEffect(state.currentSong?.id) {
        val song = state.currentSong
        state.loadLyricsFor(song)
        if (song != null) state.pushRecent(song.id)
    }

    // ---- notification tap -> open Now Playing
    var handledOpen by remember { mutableIntStateOf(0) }
    LaunchedEffect(openPlayerRequest, state.queue.size) {
        if (openPlayerRequest > handledOpen && state.queue.isNotEmpty()) {
            handledOpen = openPlayerRequest
            splashVisible = false
            state.openPlayer()
        }
    }

    // ---- back handling
    BackHandler(enabled = !splashVisible) {
        if (!state.handleBack()) {
            val now = System.currentTimeMillis()
            if (now - state.lastBackAt < 2000L) {
                (context as? Activity)?.finish()
            } else {
                state.lastBackAt = now
                state.toast("Press Back again to exit RetroPod")
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Night)) {
        Box(Modifier.fillMaxSize().systemBarsPadding()) {
            MainScaffold(state)
            OverlayHost(state)
            state.menuSong?.let { SongContextMenu(state, it) }
            AppDialogs(state)
        }
        AnimatedVisibility(visible = splashVisible, enter = EnterTransition.None, exit = fadeOut(tween(450))) {
            Box(Modifier.fillMaxSize().systemBarsPadding().background(Night)) {
                SplashScreen(onDone = { splashVisible = false })
            }
        }
    }
}

// ============================================================================ main tabs + nav

@Composable
private fun MainScaffold(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            AnimatedContent(
                targetState = state.tab,
                transitionSpec = {
                    if (state.animationsEnabled) fadeIn(tween(220)) togetherWith fadeOut(tween(140))
                    else EnterTransition.None togetherWith ExitTransition.None
                },
                label = "tabs"
            ) { tab ->
                when (tab) {
                    Tab.HOME -> HomeTab(state)
                    Tab.SONGS -> SongsTab(state)
                    Tab.ALBUMS -> AlbumsTab(state)
                    Tab.PLAYLISTS -> PlaylistsTab(state)
                    Tab.SEARCH -> SearchTab(state)
                }
            }
        }
        MiniPlayer(state)
        BottomNav(state)
    }
}

@Composable
private fun BottomNav(state: AppState) {
    val items: List<Pair<Tab, ImageVector>> = listOf(
        Tab.HOME to Icons.Default.Home,
        Tab.SONGS to Icons.Default.MusicNote,
        Tab.ALBUMS to Icons.Default.Album,
        Tab.PLAYLISTS to Icons.Default.QueueMusic,
        Tab.SEARCH to Icons.Default.Search
    )
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (tab, icon) ->
            val selected = state.tab == tab
            Box(
                Modifier.size(50.dp).clip(CircleShape)
                    .background(if (selected) Color(0x24FFFFFF) else Color.Transparent)
                    .border(1.dp, if (selected) Color(0x66FFFFFF) else Color.Transparent, CircleShape)
                    .clickable {
                        if (tab == Tab.SONGS && state.tab != Tab.SONGS) state.songFilter = SongFilter.ALL
                        state.tab = tab
                        state.tick()
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = tab.name.lowercase(), tint = if (selected) White else Muted, modifier = Modifier.size(25.dp))
            }
        }
    }
}

// ============================================================================ overlays

@Composable
private fun OverlayHost(state: AppState) {
    val top = state.overlays.lastOrNull()
    AnimatedContent(
        targetState = top,
        transitionSpec = {
            if (!state.animationsEnabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else if (targetState != null) {
                (slideInVertically(tween(320)) { it / 4 } + fadeIn(tween(260))) togetherWith fadeOut(tween(200))
            } else {
                fadeIn(tween(1)) togetherWith (slideOutVertically(tween(280)) { it / 4 } + fadeOut(tween(240)))
            }
        },
        label = "overlays"
    ) { overlay ->
        if (overlay == null) {
            Spacer(Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(Night).pointerInput(Unit) { detectTapGestures { } }
            ) {
                when (overlay) {
                    Overlay.PLAYER -> NowPlayingScreen(state)
                    Overlay.VISUALIZER -> VisualizerScreen(state)
                    Overlay.EQUALIZER -> EqualizerScreen(state)
                    Overlay.LYRICS -> LyricsQueueScreen(state)
                    Overlay.SLEEP -> SleepTimerScreen(state)
                    Overlay.SETTINGS -> SettingsScreen(state)
                    Overlay.PLAYLIST_DETAIL -> PlaylistDetailScreen(state)
                }
            }
        }
    }
}

// ============================================================================ dialogs

@Composable
private fun AppDialogs(state: AppState) {
    if (state.showCreatePlaylist) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { state.showCreatePlaylist = false; state.pendingPlaylistSong = null },
            containerColor = SurfaceNight,
            title = { Text("Create playlist", color = White) },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("Playlist name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Accent, unfocusedBorderColor = Muted
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = name.trim()
                    if (state.createPlaylist(trimmed)) {
                        state.pendingPlaylistSong?.let { state.addToPlaylist(it, trimmed) }
                        state.pendingPlaylistSong = null
                        state.showCreatePlaylist = false
                        if (state.overlays.isEmpty()) state.tab = Tab.PLAYLISTS
                    } else state.toast("Enter a unique playlist name.")
                }) { Text("Create", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { state.showCreatePlaylist = false; state.pendingPlaylistSong = null }) { Text("Cancel", color = Muted) }
            }
        )
    }
    state.renamePlaylistFrom?.let { oldName ->
        var newName by remember(oldName) { mutableStateOf(oldName) }
        AlertDialog(
            onDismissRequest = { state.renamePlaylistFrom = null },
            containerColor = SurfaceNight,
            title = { Text("Rename playlist", color = White) },
            text = {
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it.take(40) }, singleLine = true, label = { Text("New name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = Accent, unfocusedBorderColor = Muted
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (state.renamePlaylist(oldName, newName)) state.renamePlaylistFrom = null
                    else state.toast("Choose a unique playlist name.")
                }) { Text("Save", color = Accent) }
            },
            dismissButton = { TextButton(onClick = { state.renamePlaylistFrom = null }) { Text("Cancel", color = Muted) } }
        )
    }
    state.detailsSong?.let { song ->
        AlertDialog(
            onDismissRequest = { state.detailsSong = null },
            containerColor = SurfaceNight,
            title = { Text("Song details", color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text(song.title, color = White, fontWeight = FontWeight.Bold)
                    Text("Artist: ${song.artist}", color = Muted)
                    Text("Album: ${song.album}", color = Muted)
                    Text("Duration: ${formatTime(song.durationMs)}", color = Muted)
                    if (song.path.isNotBlank()) Text("Location: ${song.path}", color = Muted, fontSize = 12.sp)
                }
            },
            confirmButton = { TextButton(onClick = { state.detailsSong = null }) { Text("Done", color = Accent) } }
        )
    }
}
