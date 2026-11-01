package com.retropod.player

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.scale
import kotlin.math.abs
import kotlinx.coroutines.launch

// ============================================================================ songs

private fun letterOf(text: String): Char =
    text.firstOrNull()?.uppercaseChar()?.takeIf { it in 'A'..'Z' } ?: '#'

@Composable
internal fun SongsTab(state: AppState) {
    var moreOpen by remember { mutableStateOf(false) }
    val base = remember(state.songs, state.songFilter, state.favorites, state.recentIds) {
        when (state.songFilter) {
            SongFilter.ALL -> state.songs
            SongFilter.FAVORITES -> state.songs.filter { it.id in state.favorites }
            SongFilter.DOWNLOADED -> state.songs.filter { it.path.contains("/Download", ignoreCase = true) }
            SongFilter.RECENT -> state.recentSongs()
        }
    }
    val indexed = state.songFilter != SongFilter.RECENT && state.sortMode != SortMode.NEWEST
    val sorted = remember(base, state.sortMode, state.songFilter) {
        if (state.songFilter == SongFilter.RECENT) base else when (state.sortMode) {
            SortMode.TITLE -> base.sortedBy { it.title.lowercase() }
            SortMode.ARTIST -> base.sortedBy { it.artist.lowercase() }
            SortMode.ALBUM -> base.sortedBy { it.album.lowercase() }
            SortMode.NEWEST -> base.sortedByDescending { it.addedAt }
        }
    }
    val sortMode = state.sortMode
    val keyOf: (Song) -> Char = { song ->
        when (sortMode) {
            SortMode.ARTIST -> letterOf(song.artist)
            SortMode.ALBUM -> letterOf(song.album)
            else -> letterOf(song.title)
        }
    }
    val grouped = remember(sorted, state.sortMode, indexed) {
        if (indexed) sorted.groupBy(keyOf) else emptyMap()
    }
    val letters = grouped.keys.toList()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        RetroTopBar("Songs", onBack = { state.tab = Tab.HOME }) {
            IconButton(onClick = { state.tab = Tab.SEARCH; state.tick() }) { Icon(Icons.Default.Search, "Search", tint = White) }
            Box {
                IconButton(onClick = { moreOpen = true }) { Icon(Icons.Default.MoreVert, "More", tint = White) }
                DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                    SortMode.values().forEach { mode ->
                        DropdownMenuItem(
                            text = { Text((if (state.sortMode == mode) "● " else "    ") + "Sort by ${mode.label}") },
                            onClick = { state.sortMode = mode; moreOpen = false; state.tick() }
                        )
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Rescan music") }, onClick = { moreOpen = false; state.refreshLibrary() })
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SongFilter.values().forEach { filter ->
                RetroChip(filter.label, state.songFilter == filter, { state.songFilter = filter; state.tick() })
            }
        }
        if (!state.permissionGranted) {
            PermissionCard(onGrant = state.requestPermissions)
        } else if (sorted.isEmpty()) {
            EmptyState(
                if (state.songs.isEmpty()) "Your library is quiet" else "Nothing here yet",
                when (state.songFilter) {
                    SongFilter.FAVORITES -> "Tap the heart on a song to add it to Favorites."
                    SongFilter.RECENT -> "Songs you play will show up here."
                    SongFilter.DOWNLOADED -> "No songs were found in your Download folder."
                    SongFilter.ALL -> "Add audio files to your phone, then rescan from the menu."
                }
            )
        } else {
            Row(Modifier.fillMaxSize().padding(start = 12.dp, end = 4.dp)) {
                LazyColumn(
                    state = listState, modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (indexed) {
                        letters.forEach { letter ->
                            val tracks = grouped[letter].orEmpty()
                            item {
                                Text(
                                    letter.toString(), color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 2.dp)
                                )
                            }
                            items(tracks, key = { it.id }) { song ->
                                SongRow(
                                    song, state.currentSong?.id == song.id, state.isFavorite(song.id),
                                    onClick = { state.playList(sorted, sorted.indexOf(song)) },
                                    onLongClick = { state.menuSong = song; state.tick() },
                                    onFavorite = { state.toggleFavorite(song) }
                                )
                            }
                        }
                    } else {
                        items(sorted, key = { it.id }) { song ->
                            SongRow(
                                song, state.currentSong?.id == song.id, state.isFavorite(song.id),
                                onClick = { state.playList(sorted, sorted.indexOf(song)) },
                                onLongClick = { state.menuSong = song; state.tick() },
                                onFavorite = { state.toggleFavorite(song) }
                            )
                        }
                    }
                }
                if (indexed) {
                    var activeLetter by remember { mutableStateOf<Char?>(null) }
                    val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#"
                    fun jumpToLetter(char: Char) {
                        if (!letters.contains(char)) return
                        var target = 0
                        for (letter in letters) {
                            if (letter == char) break
                            target += 1 + (grouped[letter]?.size ?: 0)
                        }
                        if (activeLetter != char) state.tick()
                        activeLetter = char
                        scope.launch { listState.scrollToItem(target) }
                    }
                    Box(
                        Modifier.width(30.dp).fillMaxHeight().pointerInput(letters) {
                            detectDragGestures(
                                onDragStart = { point ->
                                    jumpToLetter(alphabet[(point.y / size.height * alphabet.length).toInt().coerceIn(0, alphabet.lastIndex)])
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    jumpToLetter(alphabet[(change.position.y / size.height * alphabet.length).toInt().coerceIn(0, alphabet.lastIndex)])
                                },
                                onDragEnd = { activeLetter = null },
                                onDragCancel = { activeLetter = null }
                            )
                        },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            alphabet.forEach { char ->
                                val exists = letters.contains(char)
                                val active = activeLetter == char
                                Text(
                                    char.toString(),
                                    color = if (active) Night else if (exists) White else Muted.copy(alpha = .3f),
                                    fontSize = if (active) 11.sp else 9.sp, fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clip(CircleShape)
                                        .background(if (active) White else Color.Transparent)
                                        .clickable(enabled = exists) { jumpToLetter(char) }
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                        androidx.compose.animation.AnimatedVisibility(
                            visible = activeLetter != null,
                            modifier = Modifier.align(Alignment.CenterStart).offset(x = (-58).dp)
                        ) {
                            Box(Modifier.size(48.dp).glass(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                                Text(activeLetter?.toString() ?: "", color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================ search

@Composable
internal fun SearchTab(state: AppState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val query = state.searchQuery
    val results = remember(query, state.songs) {
        if (query.isBlank()) emptyList() else state.songs.filter {
            it.title.contains(query, true) || it.artist.contains(query, true) || it.album.contains(query, true)
        }
    }
    Column(Modifier.fillMaxSize().imePadding()) {
        RetroTopBar("Search", onBack = { state.tab = Tab.HOME })
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).glass(RoundedCornerShape(16.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Muted)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = query, onValueChange = { state.searchQuery = it }, singleLine = true,
                modifier = Modifier.weight(1f).focusRequester(focus),
                textStyle = TextStyle(color = White, fontSize = 15.sp),
                cursorBrush = SolidColor(Accent),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) Text("Search songs, artists, albums", color = Muted, fontSize = 15.sp)
                        inner()
                    }
                }
            )
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Close, "Clear", tint = Muted,
                    modifier = Modifier.size(20.dp).clickable { state.searchQuery = "" }
                )
            }
        }
        when {
            query.isBlank() -> EmptyState("Find your music", "Search by song, artist or album.")
            results.isEmpty() -> EmptyState("No matches found", "Try another song, artist, or album name.")
            else -> LazyColumn(
                Modifier.weight(1f).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                item {
                    Text(
                        "${results.size} ${if (results.size == 1) "result" else "results"}", color = Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 4.dp)
                    )
                }
                items(results, key = { it.id }) { song ->
                    SongRow(
                        song, state.currentSong?.id == song.id, state.isFavorite(song.id),
                        onClick = { state.playList(results, results.indexOf(song)) },
                        onLongClick = { state.menuSong = song; state.tick() },
                        onFavorite = { state.toggleFavorite(song) }
                    )
                }
            }
        }
    }
}

// ============================================================================ albums + cover flow

@Composable
internal fun AlbumsTab(state: AppState) {
    var mode by remember { mutableStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), contentAlignment = Alignment.Center) {
            SegmentedPill(listOf("Cover Flow", "Albums"), mode, { mode = it; state.tick() })
        }
        if (state.albums.isEmpty()) {
            EmptyState("Your library is quiet", "Add audio files to your phone, then rescan from Settings.")
        } else if (mode == 0) {
            CoverFlowView(state)
        } else {
            AlbumGrid(state)
        }
    }
}

@Composable
private fun CoverFlowView(state: AppState) {
    val albums = state.albums
    val pager = rememberPagerState(pageCount = { albums.size })
    val scope = rememberCoroutineScope()
    var firstPage by remember { mutableStateOf(true) }
    LaunchedEffect(pager.currentPage) { if (firstPage) firstPage = false else state.tick() }
    val page = pager.currentPage.coerceIn(0, albums.lastIndex)
    val album = albums[page]

    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        HorizontalPager(
            state = pager, contentPadding = PaddingValues(horizontal = 72.dp), pageSpacing = 4.dp,
            modifier = Modifier.fillMaxWidth().weight(1f), beyondViewportPageCount = 2
        ) { index ->
            val entry = albums[index]
            val distance = (pager.currentPage - index) + pager.currentPageOffsetFraction
            val amount = abs(distance).coerceIn(0f, 1f)
            Column(
                Modifier.fillMaxSize().padding(top = 14.dp).graphicsLayer {
                    val scaleValue = if (state.animationsEnabled) 1f - amount * 0.2f else 1f
                    scaleX = scaleValue
                    scaleY = scaleValue
                    rotationY = if (state.animationsEnabled) distance.coerceIn(-1f, 1f) * -40f else 0f
                    cameraDistance = 14f * density
                    alpha = 1f - amount * 0.35f
                }.clickable { state.playList(entry.second, 0) },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AlbumArtwork(entry.second.firstOrNull(), index, Modifier.fillMaxWidth().aspectRatio(1f), 20.dp)
                Box(Modifier.fillMaxWidth().height(54.dp).clipToBounds()) {
                    AlbumArtwork(
                        entry.second.firstOrNull(), index,
                        Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).aspectRatio(1f)
                            .graphicsLayer { scaleY = -1f; alpha = .16f },
                        20.dp
                    )
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Night))))
                }
            }
        }
        Text(album.first, color = White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
        Text(
            "${album.second.firstOrNull()?.artist ?: "Unknown artist"} • ${album.second.size} tracks",
            color = Muted, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp)
        )
        Box(Modifier.fillMaxWidth().height(172.dp).padding(horizontal = 28.dp)) {
            ClickDial(
                onStep = { direction ->
                    scope.launch { pager.animateScrollToPage((pager.currentPage + direction).coerceIn(0, albums.lastIndex)) }
                },
                modifier = Modifier.fillMaxSize()
            )
            val playingThis = state.currentSong?.album == album.first
            Row(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 70.dp),
                horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { state.skipPrevious() }) { Icon(Icons.Default.SkipPrevious, "Previous", tint = White, modifier = Modifier.size(30.dp)) }
                RingPlayButton(
                    playing = playingThis && state.isPlaying, size = 66.dp,
                    onClick = { if (playingThis) state.togglePlayPause() else state.playList(album.second, 0, openPlayer = false) }
                )
                IconButton(onClick = { state.skipNext() }) { Icon(Icons.Default.SkipNext, "Next", tint = White, modifier = Modifier.size(30.dp)) }
            }
        }
        Row(Modifier.padding(bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val first = (page - 2).coerceAtLeast(0)
            val last = (first + 3).coerceAtMost(albums.lastIndex)
            for (dot in first..last) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(if (dot == page) White else Color(0x44FFFFFF)))
            }
        }
    }
}

@Composable
private fun AlbumGrid(state: AppState) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(state.albums, key = { it.first }) { entry ->
            Column(Modifier.clickable { state.playList(entry.second, 0) }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                AlbumArtwork(entry.second.firstOrNull(), entry.first.hashCode(), Modifier.fillMaxWidth().aspectRatio(1f), 18.dp)
                Text(entry.first, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${entry.second.firstOrNull()?.artist ?: "Unknown artist"} • ${entry.second.size}",
                    color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================================================ playlists

@Composable
internal fun PlaylistsTab(state: AppState) {
    Column(Modifier.fillMaxSize()) {
        RetroTopBar("Playlists", onBack = { state.tab = Tab.HOME }) {
            IconButton(onClick = { state.showCreatePlaylist = true; state.tick() }) { Icon(Icons.Default.Add, "Create playlist", tint = White) }
        }
        if (state.playlistOrder.isEmpty()) {
            EmptyState("No playlists yet", "Tap + to create your first collection.")
        } else {
            LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(state.playlistOrder.toList(), key = { _, name -> name }) { index, name ->
                    PlaylistRow(state, name, index)
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(state: AppState, name: String, index: Int) {
    var menuOpen by remember { mutableStateOf(false) }
    val tracks = state.playlistSongs(name)
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp)).clickable { state.openPlaylist(name) }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(SurfaceRaised), contentAlignment = Alignment.Center) {
            if (tracks.isNotEmpty()) AlbumArtwork(tracks.first(), index, Modifier.fillMaxSize(), 12.dp)
            else Icon(Icons.Default.QueueMusic, null, tint = Muted, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${state.playlists[name].orEmpty().size} songs", color = Muted, fontSize = 12.sp)
        }
        Box {
            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, "Playlist options", tint = Muted) }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Shuffle play") }, onClick = { menuOpen = false; state.playPlaylist(name, true) })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; state.renamePlaylistFrom = name })
                DropdownMenuItem(text = { Text("Move up") }, onClick = { menuOpen = false; state.movePlaylist(name, -1) })
                DropdownMenuItem(text = { Text("Move down") }, onClick = { menuOpen = false; state.movePlaylist(name, 1) })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; state.deletePlaylist(name) })
            }
        }
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(Color(0x14FFFFFF)).border(1.dp, Color(0x40FFFFFF), CircleShape)
                .clickable { state.playPlaylist(name) },
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.PlayArrow, "Play playlist", tint = White) }
    }
}

@Composable
internal fun PlaylistDetailScreen(state: AppState) {
    val name = state.selectedPlaylist
    if (name == null || !state.playlists.containsKey(name)) {
        LaunchedEffect(Unit) { state.pop() }
        return
    }
    val tracks = state.playlistSongs(name)
    Column(Modifier.fillMaxSize()) {
        RetroTopBar(name, onBack = { state.pop() }) {
            IconButton(onClick = { state.renamePlaylistFrom = name }) { Icon(Icons.Default.Edit, "Rename", tint = White) }
            IconButton(onClick = { state.deletePlaylist(name); state.pop() }) { Icon(Icons.Default.Delete, "Delete playlist", tint = White) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.weight(1f).glass(RoundedCornerShape(50)).clickable { state.playPlaylist(name) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = White); Spacer(Modifier.width(6.dp)); Text("Play", color = White, fontWeight = FontWeight.Medium)
            }
            Row(
                Modifier.weight(1f).glass(RoundedCornerShape(50)).clickable { state.playPlaylist(name, true) }.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Shuffle, null, tint = White); Spacer(Modifier.width(6.dp)); Text("Shuffle", color = White, fontWeight = FontWeight.Medium)
            }
        }
        if (tracks.isEmpty()) {
            EmptyState("This playlist is empty", "Long-press any song and choose Add to Playlist.")
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 12.dp), contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(tracks, key = { _, song -> song.id }) { index, song ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { state.playList(tracks, index) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArtwork(song, Math.floorMod(song.album.hashCode(), 4), Modifier.size(46.dp), 10.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, color = Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { state.menuSong = song }) { Icon(Icons.Default.MoreVert, "Track actions", tint = Muted) }
                        IconButton(onClick = { state.removeFromPlaylist(name, song.id) }) {
                            Icon(Icons.Default.RemoveCircleOutline, "Remove from playlist", tint = Muted)
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================ context menu

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, trailing: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = White, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (trailing) Icon(Icons.Default.ChevronRight, null, tint = Muted, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun SongContextMenu(state: AppState, song: Song) {
    var showPlaylists by remember(song.id) { mutableStateOf(false) }
    var confirmDelete by remember(song.id) { mutableStateOf(false) }
    Box(
        Modifier.fillMaxSize().background(Color(0xB8000000)).pointerInput(Unit) { detectTapGestures { state.menuSong = null } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.padding(horizontal = 26.dp).fillMaxWidth().glass(RoundedCornerShape(24.dp))
                .pointerInput(Unit) { detectTapGestures { } }.padding(vertical = 6.dp)
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AlbumArtwork(song, Math.floorMod(song.album.hashCode(), 4), Modifier.size(50.dp), 10.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(song.title, color = White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = Color(0x22FFFFFF))
            MenuItem(Icons.Default.SkipNext, "Play Next") { state.playNext(song); state.menuSong = null }
            MenuItem(Icons.Default.QueueMusic, "Add to Queue") { state.addToQueue(song); state.menuSong = null }
            MenuItem(Icons.Default.PlaylistAdd, "Add to Playlist", trailing = true) { showPlaylists = !showPlaylists }
            if (showPlaylists) {
                Column(Modifier.padding(start = 30.dp)) {
                    state.playlistOrder.toList().forEach { name ->
                        MenuItem(Icons.Default.QueueMusic, name) { state.addToPlaylist(song, name); state.menuSong = null }
                    }
                    MenuItem(Icons.Default.Add, "New playlist…") {
                        state.pendingPlaylistSong = song
                        state.menuSong = null
                        state.showCreatePlaylist = true
                    }
                }
            }
            val favorite = state.isFavorite(song.id)
            MenuItem(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, if (favorite) "Remove from Favorites" else "Add to Favorites") {
                state.toggleFavorite(song); state.menuSong = null
            }
            MenuItem(Icons.Default.Info, "Song Details") { state.detailsSong = song; state.menuSong = null }
            MenuItem(Icons.Default.Share, "Share") { state.shareSong(song); state.menuSong = null }
            MenuItem(Icons.Default.Delete, "Delete from Device") { confirmDelete = true }
        }
    }
    if (confirmDelete) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = SurfaceNight,
            title = { Text("Delete from device?", color = White) },
            text = { Text("“${song.title}” will be removed from your phone. This can't be undone.", color = Muted) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = false; state.menuSong = null; state.requestDelete(song) }) {
                    Text("Delete", color = Accent)
                }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = Muted) } }
        )
    }
}
