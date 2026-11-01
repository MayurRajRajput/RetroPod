package com.retropod.player

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

private val Night = Color(0xFF08090F)
private val SurfaceNight = Color(0xFF151722)
private val SurfaceRaised = Color(0xFF202333)
private val Accent = Color(0xFFB96CFF)
private val AccentBlue = Color(0xFF766BFF)
private val Muted = Color(0xFF9297A8)
private val White = Color(0xFFF5F3FA)

class MainActivity : ComponentActivity() {
    private var controller by mutableStateOf<MediaController?>(null)
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try { controller = controllerFuture?.get() } catch (_: Exception) { controller = null }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onStop() {
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        super.onStop()
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    private fun readLibrary(): List<Song> {
        if (!hasAudioPermission()) return emptyList()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
        val songs = mutableListOf<Song>()
        val cursor: Cursor? = contentResolver.query(collection, projection, selection, null, sortOrder)
        cursor?.use {
            val idCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = it.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            while (it.moveToNext()) {
                val id = it.getLong(idCol)
                songs += Song(
                    id = id,
                    title = it.getString(titleCol)?.takeIf(String::isNotBlank) ?: "Unknown title",
                    artist = it.getString(artistCol)?.takeIf { v -> v.isNotBlank() && v != "<unknown>" } ?: "Unknown artist",
                    album = it.getString(albumCol)?.takeIf { v -> v.isNotBlank() && v != "<unknown>" } ?: "Unknown album",
                    durationMs = it.getLong(durationCol),
                    uri = ContentUris.withAppendedId(collection, id)
                )
            }
        }
        return songs
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 9, 15)
        window.navigationBarColor = android.graphics.Color.rgb(8, 9, 15)
        window.decorView.systemUiVisibility = 0
        AppContextHolder.context = applicationContext
        setContent {
            RetroPodTheme {
                RetroPodScreen(
                    controller = controller,
                    hasPermission = ::hasAudioPermission,
                    permissionName = audioPermission(),
                    readLibrary = ::readLibrary
                )
            }
        }
    }
}

@Composable
private fun RetroPodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent, onPrimary = Night, secondary = AccentBlue,
            background = Night, surface = SurfaceNight, onSurface = White,
            onBackground = White, surfaceVariant = SurfaceRaised
        ),
        content = content
    )
}

private enum class LibraryTab { HOME, SONGS, ALBUMS, PLAYLISTS }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RetroPodScreen(
    controller: MediaController?,
    hasPermission: () -> Boolean,
    permissionName: String,
    readLibrary: () -> List<Song>
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var permissionGranted by remember { mutableStateOf(hasPermission()) }
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var tab by remember { mutableStateOf(LibraryTab.HOME) }
    var showPlayer by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showCreatePlaylist by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var selectedPlaylist by remember { mutableStateOf<String?>(null) }
    var menuSong by remember { mutableStateOf<Song?>(null) }
    var showSongMenu by remember { mutableStateOf(false) }
    var showTrackDetails by remember { mutableStateOf<Song?>(null) }
    var hapticsEnabled by remember { mutableStateOf(context.getSharedPreferences("retropod_settings", Context.MODE_PRIVATE).getBoolean("haptics", true)) }
    var animationsEnabled by remember { mutableStateOf(context.getSharedPreferences("retropod_settings", Context.MODE_PRIVATE).getBoolean("animations", true)) }
    var volume by remember { mutableFloatStateOf(1f) }
    var shuffleEnabled by remember { mutableStateOf(false) }
    var repeatEnabled by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentTitle by remember { mutableStateOf("Nothing playing") }
    var currentArtist by remember { mutableStateOf("Choose a song from your library") }
    var queueSongs by remember { mutableStateOf(emptyList<Song>()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var recentIds by remember { mutableStateOf(emptyList<Long>()) }
    val playlists: SnapshotStateMap<String, MutableList<Long>> = remember { mutableStateMapOf() }

    fun haptic() {
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun loadPlaylists() {
        playlists.clear()
        val prefs = context.getSharedPreferences("retropod_library", Context.MODE_PRIVATE)
        val raw = prefs.getString("playlists_json", "{}") ?: "{}"
        try {
            val obj = JSONObject(raw)
            obj.keys().forEach { name ->
                val arr = obj.optJSONArray(name) ?: JSONArray()
                val ids = mutableListOf<Long>()
                for (i in 0 until arr.length()) ids += arr.optLong(i)
                playlists[name] = ids
            }
        } catch (_: Exception) { /* keep an empty library if stored data is damaged */ }
    }

    fun savePlaylists() {
        val obj = JSONObject()
        playlists.forEach { (name, ids) ->
            val arr = JSONArray()
            ids.forEach { id -> arr.put(id) }
            obj.put(name, arr)
        }
        context.getSharedPreferences("retropod_library", Context.MODE_PRIVATE)
            .edit().putString("playlists_json", obj.toString()).apply()
    }

    fun refreshLibrary() {
        scope.launch {
            songs = withContext(Dispatchers.IO) { readLibrary() }
            permissionGranted = hasPermission()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { permissionGranted = true; refreshLibrary() }
        else Toast.makeText(context, "Audio permission is needed to scan your music.", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(Unit) {
        loadPlaylists()
        permissionGranted = hasPermission()
        if (permissionGranted) refreshLibrary()
    }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let { player ->
                isPlaying = player.isPlaying
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it > 0 } ?: 0L
                currentTitle = player.mediaMetadata.title?.toString() ?: "Nothing playing"
                currentArtist = player.mediaMetadata.artist?.toString() ?: "Choose a song from your library"
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                
            }
            delay(300)
        }
    }

    val filtered = remember(songs, query) {
        if (query.isBlank()) songs else songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }
    val albums = remember(songs) { songs.groupBy { it.album }.toList().sortedBy { it.first.lowercase() } }
    val currentSong = queueSongs.getOrNull(currentIndex)

    fun playFrom(index: Int, list: List<Song> = filtered) {
        val player = controller ?: run {
            Toast.makeText(context, "Player is starting. Try again in a moment.", Toast.LENGTH_SHORT).show()
            return
        }
        if (list.isEmpty()) return
        val safeIndex = index.coerceIn(list.indices)
        queueSongs = list
        val items = list.map { song ->
            MediaItem.Builder().setUri(song.uri).setMediaId(song.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist)
                        .setAlbumTitle(song.album).build()
                ).build()
        }
        player.setMediaItems(items, safeIndex, 0L)
        player.prepare()
        player.play()
        currentIndex = safeIndex
        recentIds = (listOf(list[safeIndex].id) + recentIds.filterNot { it == list[safeIndex].id }).take(30)
        showPlayer = true
        haptic()
    }

    fun addSongToPlaylist(song: Song, name: String) {
        val ids = (playlists[name] ?: emptyList()).toMutableList()
        if (song.id !in ids) ids.add(song.id)
        playlists[name] = ids
        savePlaylists()
        Toast.makeText(context, "Added to $name", Toast.LENGTH_SHORT).show()
        haptic()
    }

    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF171126), Night, Night)))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            if (showPlayer) {
                FullPlayer(
                    songs = queueSongs, currentIndex = currentIndex, controller = controller,
                    isPlaying = isPlaying, position = position, duration = duration,
                    currentTitle = currentTitle, currentArtist = currentArtist,
                    volume = volume, onVolume = { volume = it; PlaybackService.setPlaybackVolume(it) },
                    shuffle = shuffleEnabled, repeat = repeatEnabled,
                    onShuffle = { shuffleEnabled = !shuffleEnabled; controller?.shuffleModeEnabled = shuffleEnabled; haptic() },
                    onRepeat = { repeatEnabled = !repeatEnabled; controller?.repeatMode = if (repeatEnabled) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF; haptic() },
                    onBack = { showPlayer = false; haptic() },
                    onPlayPause = { if (controller?.isPlaying == true) controller.pause() else controller?.play(); haptic() },
                    onPrevious = { controller?.seekToPreviousMediaItem(); haptic() },
                    onNext = { controller?.seekToNextMediaItem(); haptic() },
                    onSeek = { controller?.seekTo(it) },
                    animationsEnabled = animationsEnabled
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 14.dp, top = 12.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(
                        Brush.linearGradient(listOf(Accent, AccentBlue))
                    ), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.GraphicEq, null, tint = White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("RetroPod", color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (searchOpen) {
                        BasicTextField(
                            value = query, onValueChange = { query = it },
                            singleLine = true,
                            modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                                .background(SurfaceRaised).padding(horizontal = 12.dp, vertical = 10.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = White, fontSize = 14.sp),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty()) Text("Search music", color = Muted, fontSize = 14.sp)
                                    inner()
                                }
                            }
                        )
                        IconButton(onClick = { query = ""; searchOpen = false }) { Icon(Icons.Default.Close, null, tint = White) }
                    } else {
                        IconButton(onClick = { searchOpen = true; haptic() }) {
                            Icon(Icons.Default.Search, null, tint = White)
                        }
                    }
                    IconButton(onClick = { showSettings = true; haptic() }) {
                        Icon(Icons.Default.Settings, null, tint = White)
                    }
                }

                if (!permissionGranted) {
                    EmptyPermission(onGrant = { permissionLauncher.launch(permissionName) })
                } else {
                    when (tab) {
                        LibraryTab.HOME -> Box(Modifier.weight(1f).fillMaxWidth()) { HomeScreen(
                            songs = songs, albums = albums, recentIds = recentIds,
                            onPlay = { song -> playFrom(songs.indexOf(song), songs) },
                            onOpenSongs = { tab = LibraryTab.SONGS },
                            onOpenAlbums = { tab = LibraryTab.ALBUMS },
                            onOpenPlaylists = { tab = LibraryTab.PLAYLISTS }
                        ) }
                        LibraryTab.SONGS -> Box(Modifier.weight(1f).fillMaxWidth()) { SongsScreen(
                            songs = filtered, allSongs = songs,
                            onPlay = { song -> playFrom(filtered.indexOf(song), filtered) },
                            onMore = { menuSong = it; showSongMenu = true }
                        ) }
                        LibraryTab.ALBUMS -> Box(Modifier.weight(1f).fillMaxWidth()) { AlbumsScreen(
                            albums = albums,
                            onAlbum = { entry -> playFrom(0, entry.second) },
                            animationsEnabled = animationsEnabled
                        ) }
                        LibraryTab.PLAYLISTS -> Box(Modifier.weight(1f).fillMaxWidth()) { PlaylistsScreen(
                            playlists = playlists, songs = songs,
                            onCreate = { showCreatePlaylist = true },
                            onPlay = { name ->
                                val list = playlists[name].orEmpty().mapNotNull { id -> songs.find { it.id == id } }
                                if (list.isNotEmpty()) playFrom(0, list)
                                else Toast.makeText(context, "This playlist is empty.", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { name -> playlists.remove(name); savePlaylists(); haptic() },
                            onMore = { song -> menuSong = song; showSongMenu = true }
                        ) }
                    }

                    MiniPlayer(
                        title = currentTitle, artist = currentArtist, playing = isPlaying,
                        onOpen = { if (queueSongs.isNotEmpty()) showPlayer = true },
                        onPlayPause = { if (controller?.isPlaying == true) controller.pause() else controller?.play(); haptic() },
                        onNext = { controller?.seekToNextMediaItem(); haptic() }
                    )
                    NavigationBar(
                        containerColor = Color(0xFF0D0E16),
                        contentColor = Accent,
                        tonalElevation = 0.dp,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        val tabs = listOf(
                            Triple(LibraryTab.HOME, Icons.Default.Home, "Home"),
                            Triple(LibraryTab.SONGS, Icons.Default.MusicNote, "Songs"),
                            Triple(LibraryTab.ALBUMS, Icons.Default.Album, "Albums"),
                            Triple(LibraryTab.PLAYLISTS, Icons.Default.QueueMusic, "Playlists")
                        )
                        tabs.forEach { (destination, icon, label) ->
                            NavigationBarItem(
                                selected = tab == destination,
                                onClick = { tab = destination; haptic() },
                                icon = { Icon(icon, contentDescription = label) },
                                label = null,
                                alwaysShowLabel = false,
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = Accent,
                                    unselectedIconColor = Muted,
                                    indicatorColor = Accent.copy(alpha = .16f)
                                )
                            )
                        }
                    }
                }
            }
        }

        if (showSettings) {
            SettingsDialog(
                haptics = hapticsEnabled, onHaptics = {
                    hapticsEnabled = it
                    context.getSharedPreferences("retropod_settings", Context.MODE_PRIVATE).edit().putBoolean("haptics", it).apply()
                },
                animations = animationsEnabled, onAnimations = {
                    animationsEnabled = it
                    context.getSharedPreferences("retropod_settings", Context.MODE_PRIVATE).edit().putBoolean("animations", it).apply()
                },
                onRescan = { refreshLibrary(); showSettings = false },
                onDismiss = { showSettings = false }
            )
        }
        if (showCreatePlaylist) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylist = false; newPlaylistName = "" },
                containerColor = SurfaceNight,
                title = { Text("Create playlist", color = White) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistName, onValueChange = { newPlaylistName = it },
                        singleLine = true, label = { Text("Playlist name") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White, unfocusedTextColor = White,
                            focusedBorderColor = Accent, unfocusedBorderColor = Muted
                        )
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val name = newPlaylistName.trim()
                        if (name.isNotEmpty() && !playlists.containsKey(name)) {
                            playlists[name] = mutableListOf()
                            savePlaylists()
                            showCreatePlaylist = false
                            newPlaylistName = ""
                            tab = LibraryTab.PLAYLISTS
                            haptic()
                        } else Toast.makeText(context, "Enter a unique playlist name.", Toast.LENGTH_SHORT).show()
                    }) { Text("Create", color = Accent) }
                },
                dismissButton = { TextButton(onClick = { showCreatePlaylist = false }) { Text("Cancel", color = Muted) } }
            )
        }
        if (showSongMenu && menuSong != null) {
            SongActionsDialog(
                song = menuSong!!, playlistNames = playlists.keys.toList(),
                onAdd = { name -> addSongToPlaylist(menuSong!!, name); showSongMenu = false },
                onDetails = { showTrackDetails = menuSong; showSongMenu = false },
                onShare = {
                    val song = menuSong!!
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = "audio/*"; putExtra(android.content.Intent.EXTRA_STREAM, song.uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(android.content.Intent.createChooser(send, "Share track"))
                    showSongMenu = false
                },
                onDismiss = { showSongMenu = false },
                onCreatePlaylist = { showSongMenu = false; showCreatePlaylist = true }
            )
        }
        showTrackDetails?.let { song ->
            AlertDialog(
                onDismissRequest = { showTrackDetails = null },
                containerColor = SurfaceNight,
                title = { Text("Track details", color = White) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(song.title, color = White, fontWeight = FontWeight.Bold)
                        Text("Artist: ${song.artist}", color = Muted)
                        Text("Album: ${song.album}", color = Muted)
                        Text("Duration: ${formatTime(song.durationMs)}", color = Muted)
                    }
                },
                confirmButton = { TextButton(onClick = { showTrackDetails = null }) { Text("Done", color = Accent) } }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    songs: List<Song>, albums: List<Pair<String, List<Song>>>, recentIds: List<Long>,
    onPlay: (Song) -> Unit, onOpenSongs: () -> Unit, onOpenAlbums: () -> Unit,
    onOpenPlaylists: () -> Unit
) {
    val recent = recentIds.mapNotNull { id -> songs.find { it.id == id } }.distinctBy { it.id }.take(6)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Your library", color = White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Text("${songs.size} songs  •  ${albums.size} albums", color = Muted, fontSize = 13.sp)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickCard("Songs", Icons.Default.MusicNote, songs.size.toString(), onOpenSongs, Modifier.weight(1f))
                QuickCard("Albums", Icons.Default.Album, albums.size.toString(), onOpenAlbums, Modifier.weight(1f))
                QuickCard("Playlists", Icons.Default.QueueMusic, "Your mix", onOpenPlaylists, Modifier.weight(1f))
            }
        }
        item { SectionHeader("Recently played", if (recent.isEmpty()) "Start listening" else "${recent.size} tracks") }
        if (recent.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF30204C), SurfaceNight)))
                    .padding(22.dp)) {
                    Column {
                        Icon(Icons.Default.GraphicEq, null, tint = Accent, modifier = Modifier.size(30.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Your next favorite song is waiting.", color = White, fontWeight = FontWeight.SemiBold)
                        Text("Play something from Songs or Albums to get started.", color = Muted, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(recent) { song -> SongRow(song, onClick = { onPlay(song) }, onMore = {}) }
        }
        item { SectionHeader("Explore your music", "From your library") }
        items(songs.takeLast(8).reversed()) { song -> SongRow(song, onClick = { onPlay(song) }, onMore = {}) }
    }
}

@Composable
private fun QuickCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, detail: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).background(SurfaceNight)
            .clickable(onClick = onClick).padding(12.dp).animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, null, tint = Accent, modifier = Modifier.size(21.dp))
        Text(title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        Text(detail, color = Muted, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun SectionHeader(title: String, detail: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(detail, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun SongsScreen(
    songs: List<Song>, allSongs: List<Song>, onPlay: (Song) -> Unit, onMore: (Song) -> Unit
) {
    val grouped = remember(songs) { songs.groupBy { it.title.firstOrNull()?.uppercaseChar()?.takeIf { c -> c in 'A'..'Z' } ?: '#' } }
    val letters = grouped.keys.sorted()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxSize().padding(start = 16.dp, end = 7.dp)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(top = 5.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (songs.isEmpty()) item { EmptyLibraryMessage() }
            letters.forEach { letter ->
                val tracks = grouped[letter].orEmpty()
                item { Text(letter.toString(), color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp, bottom = 3.dp)) }
                items(tracks, key = { it.id }) { song ->
                    SongRow(song, onClick = { onPlay(song) }, onMore = { onMore(song) })
                }
            }
        }
        Column(
            Modifier.width(22.dp).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ("ABCDEFGHIJKLMNOPQRSTUVWXYZ#").forEach { char ->
                val exists = letters.contains(char)
                Text(
                    char.toString(),
                    color = if (exists) Accent else Muted.copy(alpha = .25f),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = exists) {
                            var target = 0
                            for (letter in letters) {
                                if (letter == char) break
                                target += 1 + (grouped[letter]?.size ?: 0)
                            }
                            scope.launch { listState.animateScrollToItem(target) }
                        }
                        .padding(vertical = 1.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsScreen(
    albums: List<Pair<String, List<Song>>>,
    onAlbum: (Pair<String, List<Song>>) -> Unit,
    animationsEnabled: Boolean
) {
    if (albums.isEmpty()) {
        EmptyLibraryMessage()
        return
    }
    val pager = rememberPagerState(pageCount = { albums.size })
    Column(Modifier.fillMaxSize().padding(top = 10.dp)) {
        HorizontalPager(
            state = pager, contentPadding = PaddingValues(horizontal = 42.dp),
            pageSpacing = 15.dp, modifier = Modifier.fillMaxWidth().weight(1f),
            beyondViewportPageCount = 1
        ) { page ->
            val entry = albums[page]
            val scale by animateFloatAsState(
                if (!animationsEnabled || pager.currentPage == page) 1f else .84f,
                animationSpec = tween(350, easing = FastOutSlowInEasing), label = "albumSwipeScale"
            )
            Column(
                Modifier.fillMaxSize().scale(scale).clickable { onAlbum(entry) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AlbumArtwork(entry.second.firstOrNull(), page, Modifier.fillMaxWidth().aspectRatio(1f))
                Spacer(Modifier.height(20.dp))
                Text(entry.first, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${entry.second.firstOrNull()?.artist ?: "Unknown artist"} • ${entry.second.size} tracks", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text("TAP TO PLAY ALBUM", color = Accent, fontSize = 10.sp, letterSpacing = 1.5.sp)
            }
        }
        Text("${pager.currentPage + 1} / ${albums.size}", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(14.dp))
    }
}

@Composable
private fun PlaylistsScreen(
    playlists: Map<String, List<Long>>, songs: List<Song>,
    onCreate: () -> Unit, onPlay: (String) -> Unit, onDelete: (String) -> Unit,
    onMore: (Song) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Your playlists", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${playlists.size} collections", color = Muted, fontSize = 12.sp)
            }
            FilledIconButton(onClick = onCreate, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent)) {
                Icon(Icons.Default.Add, "Create playlist", tint = Night)
            }
        }
        if (playlists.isEmpty()) {
            EmptyLibraryMessage(title = "No playlists yet", detail = "Tap + to create your first playlist.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
                playlists.keys.sorted().forEach { name ->
                    val tracks = playlists[name].orEmpty().mapNotNull { id -> songs.find { it.id == id } }
                    item(key = name) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(SurfaceNight)
                                .clickable { onPlay(name) }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(Accent.copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.QueueMusic, null, tint = Accent)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(name, color = White, fontWeight = FontWeight.SemiBold)
                                Text("${tracks.size} songs", color = Muted, fontSize = 11.sp)
                            }
                            IconButton(onClick = { onDelete(name) }) { Icon(Icons.Default.DeleteOutline, "Delete playlist", tint = Muted) }
                        }
                    }
                    items(tracks, key = { "${name}_${it.id}" }) { song ->
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(song.title, color = White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(song.artist, color = Muted, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onMore(song) }) { Icon(Icons.Default.MoreVert, null, tint = Muted) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRow(song: Song, onClick: () -> Unit, onMore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(song, Math.floorMod(song.album.hashCode(), 4), Modifier.size(43.dp))
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = White, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist}  •  ${song.album}", color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(song.durationMs), color = Muted, fontSize = 10.sp)
        Box {
            IconButton(onClick = onMore) { Icon(Icons.Default.MoreVert, "More actions", tint = Muted) }
        }
    }
}

@Composable
private fun MiniPlayer(title: String, artist: String, playing: Boolean, onOpen: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)
            .clip(RoundedCornerShape(18.dp)).background(SurfaceRaised)
            .clickable(onClick = onOpen).padding(horizontal = 11.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Accent, AccentBlue))), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.MusicNote, null, tint = White)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Accent) }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = White) }
    }
}

@Composable
private fun FullPlayer(
    songs: List<Song>, currentIndex: Int, controller: MediaController?, isPlaying: Boolean,
    position: Long, duration: Long, currentTitle: String, currentArtist: String,
    volume: Float, onVolume: (Float) -> Unit, shuffle: Boolean, repeat: Boolean,
    onShuffle: () -> Unit, onRepeat: () -> Unit, onBack: () -> Unit,
    onPlayPause: () -> Unit, onPrevious: () -> Unit, onNext: () -> Unit,
    onSeek: (Long) -> Unit, animationsEnabled: Boolean
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, "Close player", tint = White) }
            Text("NOW PLAYING", color = Muted, fontSize = 10.sp, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
            IconButton(onClick = onRepeat) { Icon(Icons.Default.Repeat, "Repeat", tint = if (repeat) Accent else Muted) }
        }
        Spacer(Modifier.height(8.dp))
        val safeCount = songs.size.coerceAtLeast(1)
        val pager = rememberPagerState(initialPage = currentIndex.coerceIn(0, safeCount - 1), pageCount = { safeCount })
        LaunchedEffect(currentIndex, safeCount) {
            if (pager.currentPage != currentIndex.coerceIn(0, safeCount - 1)) pager.animateScrollToPage(currentIndex.coerceIn(0, safeCount - 1))
        }
        LaunchedEffect(pager.currentPage) {
            if (songs.isNotEmpty() && pager.currentPage != currentIndex) controller?.seekTo(pager.currentPage, 0L)
        }
        HorizontalPager(
            state = pager, contentPadding = PaddingValues(horizontal = 38.dp),
            pageSpacing = 12.dp, modifier = Modifier.fillMaxWidth().weight(1f, fill = false).heightIn(max = 330.dp)
        ) { page ->
            val song = songs.getOrNull(page)
            val scale by animateFloatAsState(if (!animationsEnabled || page == pager.currentPage) 1f else .87f, tween(360), label = "playerCover")
            AlbumArtwork(song, page, Modifier.fillMaxWidth().aspectRatio(1f).scale(scale))
        }
        Spacer(Modifier.height(18.dp))
        Text(currentTitle, color = White, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(currentArtist, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 5.dp))
        Spacer(Modifier.height(18.dp))
        val progressTarget = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val animatedProgress by animateFloatAsState(
            targetValue = progressTarget,
            animationSpec = tween(240, easing = FastOutSlowInEasing),
            label = "playbackProgress"
        )
        Slider(
            value = animatedProgress,
            onValueChange = { if (duration > 0) onSeek((it * duration).toLong()) },
            colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Muted.copy(alpha = .3f))
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Muted, fontSize = 10.sp)
            Text(formatTime(duration), color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) { Icon(Icons.Default.Shuffle, "Shuffle", tint = if (shuffle) Accent else Muted) }
            IconButton(onClick = onPrevious) { Icon(Icons.Default.SkipPrevious, "Previous", tint = White, modifier = Modifier.size(31.dp)) }
            FilledIconButton(
                onClick = onPlayPause, modifier = Modifier.size(68.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Accent)
            ) {
                AnimatedContent(isPlaying, label = "playPause") { playing ->
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Night, modifier = Modifier.size(37.dp))
                }
            }
            IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, "Next", tint = White, modifier = Modifier.size(31.dp)) }
            IconButton(onClick = onRepeat) { Icon(Icons.Default.Repeat, "Repeat", tint = if (repeat) Accent else Muted) }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.VolumeDown, null, tint = Muted)
            Slider(
                value = volume.coerceIn(0f, 1f), onValueChange = onVolume,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(thumbColor = Accent, activeTrackColor = Accent, inactiveTrackColor = Muted.copy(alpha = .3f))
            )
            Icon(Icons.Default.VolumeUp, null, tint = Muted)
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun SettingsDialog(
    haptics: Boolean, onHaptics: (Boolean) -> Unit,
    animations: Boolean, onAnimations: (Boolean) -> Unit,
    onRescan: () -> Unit, onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceNight,
        title = { Text("Settings", color = White, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingSwitch("Haptic feedback", "Vibration on taps and gestures", haptics, onHaptics)
                SettingSwitch("Smooth animations", "Album and player transitions", animations, onAnimations)
                Row(Modifier.fillMaxWidth().clickable(onClick = onRescan), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, null, tint = Accent)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Rescan music", color = White)
                        Text("Refresh songs and albums on this device", color = Muted, fontSize = 11.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Muted)
                }
                Text("RetroPod 2.0 • Local music player", color = Muted, fontSize = 10.sp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done", color = Accent) } }
    )
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = White)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SongActionsDialog(
    song: Song, playlistNames: List<String>, onAdd: (String) -> Unit,
    onDetails: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = SurfaceNight,
        title = { Text(song.title, color = White, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onDetails) { Icon(Icons.Default.Info, null, tint = Accent); Spacer(Modifier.width(10.dp)); Text("Track details", color = White) }
                TextButton(onClick = onShare) { Icon(Icons.Default.Share, null, tint = Accent); Spacer(Modifier.width(10.dp)); Text("Share", color = White) }
                Divider(color = Muted.copy(alpha = .2f))
                Text("Add to playlist", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(start = 12.dp, top = 5.dp))
                playlistNames.forEach { name ->
                    TextButton(onClick = { onAdd(name) }) {
                        Icon(Icons.Default.QueueMusic, null, tint = Accent)
                        Spacer(Modifier.width(10.dp))
                        Text(name, color = White)
                    }
                }
                TextButton(onClick = onCreatePlaylist) {
                    Icon(Icons.Default.Add, null, tint = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text("Create new playlist", color = White)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = Muted) } }
    )
}

@Composable
private fun EmptyPermission(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LibraryMusic, null, tint = Accent, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(18.dp))
        Text("LET THE MUSIC IN", color = White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Allow audio access to find music stored on your phone.", color = Muted, modifier = Modifier.padding(18.dp))
        Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
            Text("ALLOW MUSIC ACCESS", color = Night)
        }
    }
}

@Composable
private fun EmptyLibraryMessage(title: String = "Your library is quiet", detail: String = "Add audio files to your phone, then refresh.") {
    Column(
        Modifier.fillMaxWidth().padding(35.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MusicOff, null, tint = Accent, modifier = Modifier.size(43.dp))
        Spacer(Modifier.height(12.dp))
        Text(title, color = White, fontWeight = FontWeight.Bold)
        Text(detail, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(10.dp))
    }
}

@Composable
private fun AlbumArtwork(song: Song?, artIndex: Int, modifier: Modifier = Modifier) {
    val embedded by produceState<Bitmap?>(initialValue = null, key1 = song?.uri) {
        value = song?.let { selected ->
            withContext(Dispatchers.IO) {
                var retriever: MediaMetadataRetriever? = null
                try {
                    retriever = MediaMetadataRetriever()
                    retriever.setDataSource(AppContextHolder.context, selected.uri)
                    val bytes = retriever.embeddedPicture
                    if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
                } catch (_: Exception) { null }
                finally { runCatching { retriever?.release() } }
            }
        }
    }
    val fallback = listOf(
        R.drawable.cover_aurora, R.drawable.cover_sunset, R.drawable.cover_neon, R.drawable.cover_mono
    )[Math.floorMod(artIndex, 4)]
    Box(modifier.clip(RoundedCornerShape(22.dp)).background(SurfaceNight)) {
        if (embedded != null) {
            Image(embedded!!.asImageBitmap(), contentDescription = "Album artwork", modifier = Modifier.fillMaxSize())
        } else {
            Image(painter = androidx.compose.ui.res.painterResource(fallback), contentDescription = "RetroPod artwork", modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .15f)))))
    }
}

object AppContextHolder {
    lateinit var context: Context
}

private fun formatTime(ms: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
