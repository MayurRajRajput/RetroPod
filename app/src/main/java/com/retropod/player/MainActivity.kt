package com.retropod.player

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private val Night = Color(0xFF000000)
private val SurfaceNight = Color(0xFF151515)
private val Mint = Color(0xFFE6E6E6)
private val Muted = Color(0xFF929292)
private val White = Color(0xFFF5F5F5)

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
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
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
                    title = it.getString(titleCol) ?: "Unknown title",
                    artist = it.getString(artistCol)?.takeIf { value -> value != "<unknown>" } ?: "Unknown artist",
                    album = it.getString(albumCol)?.takeIf { value -> value != "<unknown>" } ?: "Unknown album",
                    durationMs = it.getLong(durationCol),
                    uri = ContentUris.withAppendedId(collection, id)
                )
            }
        }
        return songs
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.BLACK
        window.navigationBarColor = android.graphics.Color.BLACK
        setContent {
            RetroPodTheme {
                RetroPodScreen(
                    controller = controller,
                    hasPermission = ::hasAudioPermission,
                    requestPermission = { requestPermissions(arrayOf(audioPermission()), 81) },
                    readLibrary = ::readLibrary
                )
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 81 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) recreate()
    }
}

@Composable
private fun RetroPodTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Mint,
            onPrimary = Night,
            background = Night,
            surface = SurfaceNight,
            onSurface = White,
            onBackground = White
        ),
        content = content
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RetroPodScreen(
    controller: MediaController?,
    hasPermission: () -> Boolean,
    requestPermission: () -> Unit,
    readLibrary: () -> List<Song>
) {
    var songs by remember { mutableStateOf(emptyList<Song>()) }
    var query by remember { mutableStateOf("") }
    var searchOpen by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(0) }
    var showPlayer by remember { mutableStateOf(false) }
    var currentUri by remember { mutableStateOf<String?>(null) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var queueSongs by remember { mutableStateOf(emptyList<Song>()) }
    val scope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var currentTitle by remember { mutableStateOf("Nothing playing") }
    var currentArtist by remember { mutableStateOf("Choose a song from your library") }
    val filtered = remember(songs, query) {
        songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }
    val albums = remember(filtered) { filtered.groupBy { it.album }.entries.toList() }

    LaunchedEffect(Unit) {
        if (hasPermission()) songs = withContext(Dispatchers.IO) { readLibrary() }
    }
    LaunchedEffect(controller) {
        while (true) {
            controller?.let { player ->
                isPlaying = player.isPlaying
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it > 0 } ?: 0L
                currentTitle = player.mediaMetadata.title?.toString() ?: "Nothing playing"
                currentArtist = player.mediaMetadata.artist?.toString() ?: "Choose a song from your library"
                currentUri = player.currentMediaItem?.localConfiguration?.uri?.toString()
                currentIndex = player.currentMediaItemIndex.coerceAtLeast(0)
            }
            delay(350)
        }
    }

    fun playFrom(index: Int, list: List<Song> = filtered) {
        val p = controller ?: return
        if (list.isEmpty()) return
        queueSongs = list
        val items = list.map { song ->
            MediaItem.Builder()
                .setUri(song.uri)
                .setMediaId(song.id.toString())
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .build()
                ).build()
        }
        p.setMediaItems(items, index.coerceIn(items.indices), 0L)
        p.prepare()
        p.play()
        showPlayer = true
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF080808), Color.Black, Color(0xFF050505)))
        )
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 17.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("RetroPod", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold, letterSpacing = .2.sp)
                    Text("YOUR MUSIC", color = Muted, fontSize = 9.sp, letterSpacing = 2.sp)
                }
                IconButton(onClick = { searchOpen = !searchOpen; if (!searchOpen) query = "" }) {
                    Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, "Search", tint = White)
                }

            }

            if (!hasPermission()) {
                EmptyPermission(requestPermission)
            } else if (showPlayer) {
                FullPlayer(
                    songs = queueSongs,
                    currentIndex = currentIndex,
                    controller = controller,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    currentTitle = currentTitle,
                    currentArtist = currentArtist,
                    onBack = { showPlayer = false },
                    onPlayPause = { if (controller?.isPlaying == true) controller?.pause() else controller?.play() },
                    onPrevious = { controller?.seekToPreviousMediaItem() },
                    onNext = { controller?.seekToNextMediaItem() },
                    onSeek = { controller?.seekTo(it) }
                )
            } else {
                if (searchOpen) OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search songs, albums, artists", color = Muted) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Muted) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                            Icon(Icons.Default.Close, null, tint = Muted)
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mint,
                        unfocusedBorderColor = Muted.copy(alpha = .3f),
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        cursorColor = Mint
                    )
                )
                Spacer(Modifier.height(14.dp))
                TabRow(
                    selectedTabIndex = tab.coerceIn(0, 1),
                    containerColor = Color.Transparent,
                    contentColor = Mint,
                    divider = {}
                ) {
                    Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("ALBUMS", letterSpacing = 1.5.sp) })
                    Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("SONGS", letterSpacing = 1.5.sp) })
                }
                if (tab == 0) {
                    if (albums.isEmpty()) {
                        EmptyLibrary { scope.launch { songs = withContext(Dispatchers.IO) { readLibrary() } } }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(15.dp)
                        ) {
                            itemsIndexed(albums) { albumIndex, entry ->
                                val albumSongs = entry.value
                                AlbumRow(
                                    album = entry.key,
                                    artist = albumSongs.firstOrNull()?.artist ?: "Unknown artist",
                                    songCount = albumSongs.size,
                                    representative = albumSongs.firstOrNull(),
                                    artIndex = albumIndex,
                                    onClick = { playFrom(0, albumSongs); showPlayer = true }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(top = 10.dp, bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        itemsIndexed(filtered) { index, song ->
                            SongRow(song, index, onClick = { playFrom(index) })
                        }
                    }
                }
                MiniPlayer(
                    title = currentTitle,
                    artist = currentArtist,
                    playing = isPlaying,
                    onOpen = { showPlayer = true },
                    onPlayPause = { if (controller?.isPlaying == true) controller.pause() else controller?.play() },
                    onNext = { controller?.seekToNextMediaItem() }
                )
            }

        }
    }
}

@Composable
private fun EmptyPermission(onGrant: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(bottom = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.LibraryMusic, null, tint = Mint, modifier = Modifier.size(54.dp))
        Spacer(Modifier.height(18.dp))
        Text("LET THE MUSIC IN", color = White, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Text("Allow audio access to find music stored on your phone.", color = Muted, modifier = Modifier.padding(18.dp))
        Button(onClick = onGrant, colors = ButtonDefaults.buttonColors(containerColor = Mint)) {
            Text("ALLOW MUSIC ACCESS", color = Night)
        }
    }
}

@Composable
private fun EmptyLibrary(onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(top = 70.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.MusicOff, null, tint = Mint, modifier = Modifier.size(45.dp))
        Spacer(Modifier.height(14.dp))
        Text("YOUR LIBRARY IS QUIET", color = White, fontWeight = FontWeight.Bold)
        Text("Add audio files to your phone, then refresh.", color = Muted, modifier = Modifier.padding(12.dp))
        OutlinedButton(onClick = onRefresh) { Text("REFRESH LIBRARY") }
    }
}

@Composable
private fun AlbumRow(
    album: String,
    artist: String,
    songCount: Int,
    representative: Song?,
    artIndex: Int,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Color.White.copy(alpha = .055f))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(representative, artIndex, Modifier.size(88.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(album, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("$songCount ${if (songCount == 1) "TRACK" else "TRACKS"}", color = Mint, fontSize = 9.sp, letterSpacing = 1.5.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Muted)
    }
}

@Composable
private fun SongRow(song: Song, index: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArtwork(song, Math.floorMod(song.album.hashCode(), 4), Modifier.size(46.dp).clip(RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, color = White, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${song.artist}  •  ${song.album}", color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(song.durationMs), color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun MiniPlayer(
    title: String,
    artist: String,
    playing: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(SurfaceNight)
            .clickable(onClick = onOpen).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(10.dp)).background(Brush.linearGradient(listOf(Mint, Color(0xFF426C76)))),
            contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Album, null, tint = Night)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artist, color = Muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onPlayPause) {
            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Mint)
        }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = White) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FullPlayer(
    songs: List<Song>,
    currentIndex: Int,
    controller: MediaController?,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    currentTitle: String,
    currentArtist: String,
    onBack: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit
) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.KeyboardArrowDown, null, tint = White) }
            Text("NOW PLAYING", color = Muted, fontSize = 10.sp, letterSpacing = 3.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.MoreHoriz, null, tint = Muted)
        }
        Spacer(Modifier.height(12.dp))
        val safeCount = songs.size.coerceAtLeast(1)
        val pager = rememberPagerState(
            initialPage = currentIndex.coerceIn(0, safeCount - 1),
            pageCount = { safeCount }
        )
        LaunchedEffect(currentIndex, safeCount) {
            if (pager.currentPage != currentIndex.coerceIn(0, safeCount - 1)) {
                pager.animateScrollToPage(currentIndex.coerceIn(0, safeCount - 1))
            }
        }
        LaunchedEffect(pager.currentPage) {
            if (songs.isNotEmpty() && pager.currentPage != currentIndex) {
                controller?.seekTo(pager.currentPage, 0L)
            }
        }
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 34.dp),
            pageSpacing = 14.dp,
            modifier = Modifier.fillMaxWidth().height(260.dp)
        ) { page ->
            val song = songs.getOrNull(page)
            val artIndex = (song?.album?.hashCode()?.ushr(1) ?: page) % 4
            val pageScale by animateFloatAsState(
                targetValue = if (page == pager.currentPage) 1f else .86f,
                animationSpec = tween(420, easing = FastOutSlowInEasing),
                label = "coverFlowScale"
            )
            AlbumArtwork(song, artIndex, Modifier.fillMaxSize().scale(pageScale))
        }
        val currentSong = songs.getOrNull(currentIndex)
        Spacer(Modifier.height(25.dp))
        Text(currentTitle, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(currentArtist, color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(22.dp))
        Slider(
            value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
            onValueChange = { fraction -> if (duration > 0) onSeek((fraction * duration).toLong()) },
            colors = SliderDefaults.colors(thumbColor = Mint, activeTrackColor = Mint, inactiveTrackColor = Muted.copy(alpha = .3f))
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(position), color = Muted, fontSize = 10.sp)
            Text(formatTime(duration), color = Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            IconButton(onClick = onPrevious, modifier = Modifier.size(58.dp)) {
                Icon(Icons.Default.SkipPrevious, null, tint = White, modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.width(24.dp))
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(78.dp).clip(CircleShape)
                    .background(Color(0xFF171717))
                    .then(Modifier)
            ) {
                AnimatedContent(isPlaying, label = "playPauseIcon") { playing ->
                    Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = White, modifier = Modifier.size(42.dp))
                }
            }
            Spacer(Modifier.width(24.dp))
            IconButton(onClick = onNext, modifier = Modifier.size(58.dp)) {
                Icon(Icons.Default.SkipNext, null, tint = White, modifier = Modifier.size(34.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AlbumArtwork(song: Song?, artIndex: Int, modifier: Modifier = Modifier) {
    val embedded by produceState<Bitmap?>(initialValue = null, key1 = song?.uri) {
        value = song?.let { selected ->
            withContext(Dispatchers.IO) {
                try {
                    val retriever = MediaMetadataRetriever()
                    retriever.setDataSource(AppContextHolder.context, selected.uri)
                    val bytes = retriever.embeddedPicture
                    retriever.release()
                    if (bytes != null) BitmapFactory.decodeByteArray(bytes, 0, bytes.size) else null
                } catch (_: Exception) { null }
            }
        }
    }
    val fallback = listOf(
        R.drawable.cover_aurora, R.drawable.cover_sunset, R.drawable.cover_neon, R.drawable.cover_mono
    )[Math.floorMod(artIndex, 4)]
    Box(modifier.clip(RoundedCornerShape(24.dp)).background(SurfaceNight)) {
        if (embedded != null) {
            Image(embedded!!.asImageBitmap(), contentDescription = "Album artwork", modifier = Modifier.fillMaxSize())
        } else {
            Image(painter = androidx.compose.ui.res.painterResource(fallback), contentDescription = "RetroPod artwork", modifier = Modifier.fillMaxSize())
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .12f)))))
    }
}

object AppContextHolder {
    lateinit var context: android.content.Context
}

private fun formatTime(ms: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
