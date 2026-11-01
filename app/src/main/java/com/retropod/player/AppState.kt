package com.retropod.player

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.Toast
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

internal enum class Tab { HOME, SONGS, ALBUMS, PLAYLISTS, SEARCH }
internal enum class Overlay { PLAYER, VISUALIZER, EQUALIZER, LYRICS, SLEEP, SETTINGS, PLAYLIST_DETAIL }
internal enum class SongFilter(val label: String) { ALL("All"), FAVORITES("Favorites"), DOWNLOADED("Downloaded"), RECENT("Recent") }
internal enum class SortMode(val label: String) {
    TITLE("Title"), ARTIST("Artist"), ALBUM("Album"), NEWEST("Recently added")
}

private val LIBRARY_BACKUP_PREFS = listOf("retropod_settings", "retropod_library", "retropod_lyrics")

@Stable
internal class AppState(
    val context: Context,
    private val scope: CoroutineScope,
    private val view: View
) {
    val settingsPrefs = context.getSharedPreferences("retropod_settings", Context.MODE_PRIVATE)
    val libraryPrefs = context.getSharedPreferences("retropod_library", Context.MODE_PRIVATE)
    val lyricsPrefs = context.getSharedPreferences("retropod_lyrics", Context.MODE_PRIVATE)

    // ------------------------------------------------------------ connection + library
    var controller by mutableStateOf<MediaController?>(null)
    var permissionGranted by mutableStateOf(false)
    var micGranted by mutableStateOf(false)
    var songs by mutableStateOf(emptyList<Song>())
    val songsById: Map<Long, Song> by derivedStateOf { songs.associateBy { it.id } }
    val albums: List<Pair<String, List<Song>>> by derivedStateOf {
        songs.groupBy { it.album }.toList().sortedBy { it.first.lowercase() }
    }
    private var queueDirty = true

    // ------------------------------------------------------------ settings
    var userName by mutableStateOf("Mayur")
    var hapticsEnabled by mutableStateOf(true)
    var animationsEnabled by mutableStateOf(true)
    var clickSoundsEnabled by mutableStateOf(true)
    var swipeRightForNext by mutableStateOf(true)
    var lockScreenPlayer by mutableStateOf(false)
    var waveformSeek by mutableStateOf(true)
    var resumeSession by mutableStateOf(true)
    var skipSilence by mutableStateOf(false)
    var pauseOnUnplug by mutableStateOf(true)
    var minTrackSeconds by mutableIntStateOf(0)
    var excludedFolders by mutableStateOf("")

    // ------------------------------------------------------------ user library data
    var favorites by mutableStateOf(emptySet<Long>())
    var recentIds by mutableStateOf(emptyList<Long>())
    val playlists = mutableStateMapOf<String, List<Long>>()
    val playlistOrder = mutableStateListOf<String>()

    // ------------------------------------------------------------ playback mirror
    var isPlaying by mutableStateOf(false)
    var position by mutableLongStateOf(0L)
    var duration by mutableLongStateOf(0L)
    var currentIndex by mutableIntStateOf(0)
    var queue by mutableStateOf(emptyList<Song>())
    var shuffleOn by mutableStateOf(false)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var volume by mutableFloatStateOf(1f)
    val currentSong: Song? get() = queue.getOrNull(currentIndex)
    var lyricsRaw by mutableStateOf("")

    // ------------------------------------------------------------ navigation + dialogs
    var tab by mutableStateOf(Tab.HOME)
    var songFilter by mutableStateOf(SongFilter.ALL)
    var sortMode by mutableStateOf(SortMode.TITLE)
    var searchQuery by mutableStateOf("")
    val overlays = mutableStateListOf<Overlay>()
    var selectedPlaylist by mutableStateOf<String?>(null)
    var settingsPage by mutableStateOf<Int?>(null)
    /** 0 = Lyrics, 1 = Queue. Read by the Lyrics & Queue screen when it opens. */
    var lyricsTab by mutableIntStateOf(0)
    /** Vertical drag offset (px) of the Now Playing screen while it is being swiped. */
    var playerDragY by mutableFloatStateOf(0f)
    var menuSong by mutableStateOf<Song?>(null)
    var detailsSong by mutableStateOf<Song?>(null)
    var showCreatePlaylist by mutableStateOf(false)
    var renamePlaylistFrom by mutableStateOf<String?>(null)
    var pendingPlaylistSong by mutableStateOf<Song?>(null)
    var lastBackAt = 0L

    // Launchers are created in the composable root and wired in here.
    var requestPermissions: () -> Unit = {}
    var requestMic: () -> Unit = {}
    var pickLyrics: () -> Unit = {}
    var launchBackup: () -> Unit = {}
    var launchRestore: () -> Unit = {}
    var launchDelete: (IntentSender) -> Unit = {}
    private var pendingDelete: Song? = null

    init {
        ClickSound.init(context)
        reloadFromPrefs()
        permissionGranted = hasAudioPermission()
        micGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    // ================================================================ feedback

    fun toast(message: String, long: Boolean = false) {
        Toast.makeText(context, message, if (long) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }

    /** Haptic tick and optional click sound, each controlled from Settings > Experience. */
    fun tick() {
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (clickSoundsEnabled) ClickSound.play(0.35f)
    }

    /** Louder, wheel-style click used by the Cover Flow dial and cover swipes. */
    fun wheelTick() {
        if (hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (clickSoundsEnabled) ClickSound.play(0.85f)
    }

    // ================================================================ navigation

    fun push(overlay: Overlay) {
        if (overlays.lastOrNull() != overlay) overlays.add(overlay)
        tick()
    }

    fun pop() {
        if (overlays.isNotEmpty()) overlays.removeAt(overlays.lastIndex)
    }

    fun openPlayer() {
        playerDragY = 0f
        if (Overlay.PLAYER in overlays) {
            while (overlays.lastOrNull() != Overlay.PLAYER) overlays.removeAt(overlays.lastIndex)
        } else {
            overlays.add(Overlay.PLAYER)
        }
    }

    fun openQueue() {
        lyricsTab = 1
        if (overlays.lastOrNull() != Overlay.LYRICS) overlays.add(Overlay.LYRICS)
        tick()
    }

    fun openLyrics() {
        lyricsTab = 0
        push(Overlay.LYRICS)
    }

    fun openPlaylist(name: String) {
        selectedPlaylist = name
        push(Overlay.PLAYLIST_DETAIL)
    }

    /** Returns true when the back press was consumed; false means "exit confirmation". */
    fun handleBack(): Boolean {
        return when {
            menuSong != null -> { menuSong = null; true }
            overlays.isNotEmpty() -> {
                if (overlays.last() == Overlay.SETTINGS && settingsPage != null) settingsPage = null else pop()
                true
            }
            tab != Tab.HOME -> { tab = Tab.HOME; true }
            else -> false
        }
    }

    // ================================================================ permissions + library

    fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, audioPermission()) == PackageManager.PERMISSION_GRANTED

    fun missingPermissions(): Array<String> {
        val list = mutableListOf<String>()
        if (!hasAudioPermission()) list += audioPermission()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) list += Manifest.permission.POST_NOTIFICATIONS
        return list.toTypedArray()
    }

    fun refreshLibrary() {
        scope.launch {
            permissionGranted = hasAudioPermission()
            if (!permissionGranted) return@launch
            val minMs = minTrackSeconds * 1000L
            val excluded = excludedFolders.split(",")
            val result = try {
                withContext(Dispatchers.IO) { readLibrary(context, minMs, excluded) }
            } catch (e: Exception) {
                toast("Couldn't read your music library: ${e.localizedMessage}", long = true)
                return@launch
            }
            songs = result
            queueDirty = true
        }
    }

    // ================================================================ playback mirror

    private fun rebuildQueue(c: MediaController) {
        val list = ArrayList<Song>(c.mediaItemCount)
        for (index in 0 until c.mediaItemCount) {
            val item = c.getMediaItemAt(index)
            val uri = item.localConfiguration?.uri ?: Uri.EMPTY
            val id = item.mediaId.toLongOrNull() ?: uri.lastPathSegment?.toLongOrNull() ?: index.toLong()
            list += songsById[id] ?: Song(
                id = id,
                title = item.mediaMetadata.title?.toString() ?: "Unknown title",
                artist = item.mediaMetadata.artist?.toString() ?: "Unknown artist",
                album = item.mediaMetadata.albumTitle?.toString() ?: "Unknown album",
                durationMs = 0L,
                uri = uri
            )
        }
        queue = list
    }

    /** Mirrors the MediaController into Compose state until cancelled. */
    suspend fun pollLoop() {
        val c = controller ?: return
        queueDirty = true
        val listener = object : Player.Listener {
            override fun onTimelineChanged(timeline: Timeline, reason: Int) { queueDirty = true }
        }
        c.addListener(listener)
        try {
            while (true) {
                if (queueDirty) {
                    queueDirty = false
                    rebuildQueue(c)
                }
                isPlaying = c.isPlaying
                position = c.currentPosition.coerceAtLeast(0L)
                duration = c.duration.takeIf { it > 0 } ?: 0L
                currentIndex = c.currentMediaItemIndex.coerceAtLeast(0)
                shuffleOn = c.shuffleModeEnabled
                repeatMode = c.repeatMode
                delay(if (c.isPlaying) 150L else 400L)
            }
        } finally {
            runCatching { c.removeListener(listener) }
        }
    }

    private fun mediaItemOf(song: Song): MediaItem =
        MediaItem.Builder().setUri(song.uri).setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder().setTitle(song.title).setArtist(song.artist).setAlbumTitle(song.album).build()
            ).build()

    fun playList(list: List<Song>, index: Int, openPlayer: Boolean = true) {
        val c = controller ?: run { toast("Player is starting. Try again in a moment."); return }
        if (list.isEmpty()) return
        val safeIndex = index.coerceIn(list.indices)
        c.setMediaItems(list.map(::mediaItemOf), safeIndex, 0L)
        c.prepare()
        c.play()
        pushRecent(list[safeIndex].id)
        if (openPlayer) openPlayer()
        tick()
    }

    fun playMix() {
        if (songs.isEmpty()) { toast("Add some music first."); return }
        val mix = songs.shuffled().take(60)
        playList(mix, 0)
    }

    fun playNext(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playList(listOf(song), 0, openPlayer = false); return }
        c.addMediaItem(c.currentMediaItemIndex + 1, mediaItemOf(song))
        toast("Playing next")
    }

    fun addToQueue(song: Song) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) { playList(listOf(song), 0, openPlayer = false); return }
        c.addMediaItem(mediaItemOf(song))
        toast("Added to queue")
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
        tick()
    }

    fun skipNext() { controller?.seekToNextMediaItem(); tick() }
    fun skipPrevious() { controller?.seekToPreviousMediaItem(); tick() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun seekToIndex(index: Int) { controller?.seekTo(index, 0L); tick() }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        tick()
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        tick()
    }

    fun setVolumeLevel(value: Float) {
        volume = value.coerceIn(0f, 1f)
        PlaybackService.setPlaybackVolume(volume)
        settingsPrefs.edit().putFloat("volume", volume).apply()
    }

    fun removeFromQueue(index: Int) {
        controller?.removeMediaItem(index)
        tick()
    }

    fun moveInQueue(from: Int, to: Int) {
        controller?.moveMediaItem(from, to)
        tick()
    }

    // ================================================================ favorites + recents

    fun isFavorite(id: Long): Boolean = id in favorites

    fun toggleFavorite(song: Song) {
        favorites = if (song.id in favorites) favorites - song.id else favorites + song.id
        libraryPrefs.edit().putString("favorites_json", JSONArray(favorites.toList()).toString()).apply()
        tick()
    }

    fun pushRecent(id: Long) {
        recentIds = (listOf(id) + recentIds.filterNot { it == id }).take(40)
        libraryPrefs.edit().putString("recents_json", JSONArray(recentIds).toString()).apply()
    }

    fun recentSongs(): List<Song> = recentIds.mapNotNull { songsById[it] }

    // ================================================================ playlists

    fun playlistSongs(name: String): List<Song> = playlists[name].orEmpty().mapNotNull { songsById[it] }

    private fun savePlaylists() {
        val obj = JSONObject()
        playlists.forEach { (name, ids) -> obj.put(name, JSONArray(ids)) }
        val order = JSONArray().apply { playlistOrder.filter { it in playlists }.forEach { put(it) } }
        libraryPrefs.edit().putString("playlists_json", obj.toString()).putString("playlist_order", order.toString()).apply()
    }

    fun createPlaylist(rawName: String): Boolean {
        val name = rawName.trim()
        if (name.isEmpty() || playlists.containsKey(name)) return false
        playlists[name] = emptyList()
        playlistOrder.add(name)
        savePlaylists()
        tick()
        return true
    }

    fun renamePlaylist(old: String, rawName: String): Boolean {
        val name = rawName.trim()
        if (name.isEmpty() || (name != old && playlists.containsKey(name))) return false
        val contents = playlists[old].orEmpty()
        playlists.remove(old)
        playlists[name] = contents
        val at = playlistOrder.indexOf(old)
        if (at >= 0) playlistOrder[at] = name else playlistOrder.add(name)
        if (selectedPlaylist == old) selectedPlaylist = name
        savePlaylists()
        tick()
        return true
    }

    fun deletePlaylist(name: String) {
        playlists.remove(name)
        playlistOrder.remove(name)
        savePlaylists()
        tick()
    }

    fun movePlaylist(name: String, delta: Int) {
        val index = playlistOrder.indexOf(name)
        val target = index + delta
        if (index >= 0 && target in playlistOrder.indices) {
            val moved = playlistOrder.removeAt(index)
            playlistOrder.add(target, moved)
            savePlaylists()
            tick()
        }
    }

    fun addToPlaylist(song: Song, name: String) {
        val ids = playlists[name].orEmpty()
        if (song.id !in ids) playlists[name] = ids + song.id
        savePlaylists()
        toast("Added to $name")
        tick()
    }

    fun removeFromPlaylist(name: String, id: Long) {
        playlists[name] = playlists[name].orEmpty().filterNot { it == id }
        savePlaylists()
        tick()
    }

    fun playPlaylist(name: String, shuffled: Boolean = false, openPlayer: Boolean = true) {
        val list = playlistSongs(name)
        if (list.isEmpty()) { toast("This playlist is empty."); return }
        playList(if (shuffled) list.shuffled() else list, 0, openPlayer)
    }

    // ================================================================ lyrics

    fun loadLyricsFor(song: Song?) {
        lyricsRaw = if (song == null) "" else lyricsPrefs.getString(song.id.toString(), "") ?: ""
    }

    fun importLyrics(uri: Uri) {
        val track = currentSong
        if (track == null) { toast("Play a track before importing lyrics."); return }
        try {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to read lyrics file")
            if (text.length > 250_000) error("Lyrics file is too large")
            lyricsPrefs.edit().putString(track.id.toString(), text).apply()
            lyricsRaw = text
            toast("Lyrics imported for this track.")
        } catch (e: Exception) {
            toast("Couldn't import lyrics: ${e.localizedMessage}", long = true)
        }
    }

    fun clearLyrics() {
        val track = currentSong ?: return
        lyricsPrefs.edit().remove(track.id.toString()).apply()
        lyricsRaw = ""
    }

    // ================================================================ settings

    fun reloadFromPrefs() {
        userName = settingsPrefs.getString("user_name", "Mayur") ?: "Mayur"
        hapticsEnabled = settingsPrefs.getBoolean("haptics", true)
        animationsEnabled = settingsPrefs.getBoolean("animations", true)
        clickSoundsEnabled = settingsPrefs.getBoolean("click_sounds", true)
        swipeRightForNext = settingsPrefs.getBoolean("swipe_right_next", true)
        lockScreenPlayer = settingsPrefs.getBoolean("lock_screen_player", false)
        waveformSeek = settingsPrefs.getBoolean("waveform_seek", true)
        resumeSession = settingsPrefs.getBoolean("resume_session", true)
        skipSilence = settingsPrefs.getBoolean("skip_silence", false)
        pauseOnUnplug = settingsPrefs.getBoolean("pause_on_unplug", true)
        minTrackSeconds = settingsPrefs.getInt("min_track_sec", 0)
        excludedFolders = settingsPrefs.getString("excluded_folders", "") ?: ""
        volume = settingsPrefs.getFloat("volume", 1f)
        Pal.accentIndex = settingsPrefs.getInt("accent_index", 0)

        favorites = try {
            val arr = JSONArray(libraryPrefs.getString("favorites_json", "[]") ?: "[]")
            (0 until arr.length()).map { arr.getLong(it) }.toSet()
        } catch (_: Exception) { emptySet() }
        recentIds = try {
            val arr = JSONArray(libraryPrefs.getString("recents_json", "[]") ?: "[]")
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (_: Exception) { emptyList() }

        playlists.clear(); playlistOrder.clear()
        try {
            val obj = JSONObject(libraryPrefs.getString("playlists_json", "{}") ?: "{}")
            obj.keys().forEach { name ->
                val arr = obj.optJSONArray(name) ?: JSONArray()
                playlists[name] = (0 until arr.length()).map { arr.optLong(it) }
            }
            val savedOrder = libraryPrefs.getString("playlist_order", null)
            if (savedOrder != null) {
                val order = JSONArray(savedOrder)
                for (i in 0 until order.length()) {
                    val name = order.optString(i)
                    if (name in playlists && name !in playlistOrder) playlistOrder.add(name)
                }
            }
            playlists.keys.forEach { if (it !in playlistOrder) playlistOrder.add(it) }
        } catch (_: Exception) { playlists.clear(); playlistOrder.clear() }
    }

    fun updateUserName(value: String) { userName = value; settingsPrefs.edit().putString("user_name", value).apply() }
    fun updateHaptics(value: Boolean) { hapticsEnabled = value; settingsPrefs.edit().putBoolean("haptics", value).apply() }
    fun updateAnimations(value: Boolean) { animationsEnabled = value; settingsPrefs.edit().putBoolean("animations", value).apply() }
    fun updateClickSounds(value: Boolean) { clickSoundsEnabled = value; settingsPrefs.edit().putBoolean("click_sounds", value).apply() }
    fun updateSwipeDirection(value: Boolean) { swipeRightForNext = value; settingsPrefs.edit().putBoolean("swipe_right_next", value).apply() }
    fun updateLockScreenPlayer(value: Boolean) { lockScreenPlayer = value; settingsPrefs.edit().putBoolean("lock_screen_player", value).apply() }
    fun updateWaveformSeek(value: Boolean) { waveformSeek = value; settingsPrefs.edit().putBoolean("waveform_seek", value).apply() }
    fun updateResumeSession(value: Boolean) { resumeSession = value; settingsPrefs.edit().putBoolean("resume_session", value).apply() }
    fun updateSkipSilence(value: Boolean) {
        skipSilence = value
        settingsPrefs.edit().putBoolean("skip_silence", value).apply()
        PlaybackService.setSkipSilence(value)
    }
    fun updatePauseOnUnplug(value: Boolean) {
        pauseOnUnplug = value
        settingsPrefs.edit().putBoolean("pause_on_unplug", value).apply()
        PlaybackService.setPauseOnUnplug(value)
    }
    fun updateAccent(index: Int) { Pal.accentIndex = index; settingsPrefs.edit().putInt("accent_index", index).apply() }
    fun updateMinTrackSeconds(value: Int) {
        minTrackSeconds = value
        settingsPrefs.edit().putInt("min_track_sec", value).apply()
        refreshLibrary()
    }
    fun updateExcludedFolders(value: String) {
        excludedFolders = value
        settingsPrefs.edit().putString("excluded_folders", value).apply()
        refreshLibrary()
    }

    // ================================================================ backup + restore

    fun exportBackup(uri: Uri) {
        try {
            val root = JSONObject()
            LIBRARY_BACKUP_PREFS.forEach { prefName ->
                val prefObject = JSONObject()
                context.getSharedPreferences(prefName, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                    val entry = JSONObject()
                    when (value) {
                        is Boolean -> { entry.put("type", "boolean"); entry.put("value", value) }
                        is Int -> { entry.put("type", "int"); entry.put("value", value) }
                        is Long -> { entry.put("type", "long"); entry.put("value", value) }
                        is Float -> { entry.put("type", "float"); entry.put("value", value.toDouble()) }
                        is String -> { entry.put("type", "string"); entry.put("value", value) }
                        is Set<*> -> { entry.put("type", "strings"); entry.put("value", JSONArray(value.toList())) }
                    }
                    prefObject.put(key, entry)
                }
                root.put(prefName, prefObject)
            }
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(root.toString(2)) }
            toast("RetroPod backup saved.")
        } catch (e: Exception) {
            toast("Couldn't save backup: ${e.localizedMessage}", long = true)
        }
    }

    fun restoreBackup(uri: Uri) {
        try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Unable to read backup file")
            val root = JSONObject(raw)
            LIBRARY_BACKUP_PREFS.forEach { prefName ->
                val obj = root.optJSONObject(prefName) ?: return@forEach
                val editor = context.getSharedPreferences(prefName, Context.MODE_PRIVATE).edit().clear()
                obj.keys().forEach { key ->
                    val entry = obj.optJSONObject(key) ?: return@forEach
                    when (entry.optString("type")) {
                        "boolean" -> editor.putBoolean(key, entry.optBoolean("value"))
                        "int" -> editor.putInt(key, entry.optInt("value"))
                        "long" -> editor.putLong(key, entry.optLong("value"))
                        "float" -> editor.putFloat(key, entry.optDouble("value").toFloat())
                        "string" -> editor.putString(key, entry.optString("value"))
                        "strings" -> {
                            val arr = entry.optJSONArray("value") ?: JSONArray()
                            editor.putStringSet(key, (0 until arr.length()).map { arr.optString(it) }.toSet())
                        }
                    }
                }
                editor.apply()
            }
            reloadFromPrefs()
            PlaybackService.setSkipSilence(skipSilence)
            PlaybackService.setPauseOnUnplug(pauseOnUnplug)
            PlaybackService.setPlaybackVolume(volume)
            loadLyricsFor(currentSong)
            refreshLibrary()
            toast("Backup restored.")
        } catch (e: Exception) {
            toast("Invalid RetroPod backup: ${e.localizedMessage}", long = true)
        }
    }

    // ================================================================ song actions

    fun shareSong(song: Song) {
        try {
            val send = Intent(Intent.ACTION_SEND).apply {
                type = "audio/*"
                putExtra(Intent.EXTRA_STREAM, song.uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, "Share track").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            toast("Couldn't share this track.")
        }
    }

    /** Starts the system-confirmed deletion flow for a track on the device. */
    fun requestDelete(song: Song) {
        pendingDelete = song
        try {
            if (Build.VERSION.SDK_INT >= 30) {
                val request = MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
                launchDelete(request.intentSender)
            } else {
                val rows = context.contentResolver.delete(song.uri, null, null)
                if (rows > 0) finishDelete(song) else toast("Couldn't delete this track.")
            }
        } catch (e: SecurityException) {
            if (Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException) {
                launchDelete(e.userAction.actionIntent.intentSender)
            } else {
                toast("Android didn't allow deleting this file.")
            }
        } catch (e: Exception) {
            toast("Couldn't delete this track: ${e.localizedMessage}")
        }
    }

    fun onDeleteResult(ok: Boolean) {
        val song = pendingDelete ?: return
        pendingDelete = null
        if (!ok) return
        if (Build.VERSION.SDK_INT == 29) {
            // Android 10 grants permission for a retry rather than deleting immediately.
            try { context.contentResolver.delete(song.uri, null, null) } catch (_: Exception) { }
        }
        finishDelete(song)
    }

    private fun finishDelete(song: Song) {
        val c = controller
        if (c != null) {
            for (index in c.mediaItemCount - 1 downTo 0) {
                if (c.getMediaItemAt(index).mediaId == song.id.toString()) c.removeMediaItem(index)
            }
        }
        favorites = favorites - song.id
        libraryPrefs.edit().putString("favorites_json", JSONArray(favorites.toList()).toString()).apply()
        playlists.keys.toList().forEach { name ->
            playlists[name] = playlists[name].orEmpty().filterNot { it == song.id }
        }
        savePlaylists()
        songs = songs.filterNot { it.id == song.id }
        toast("Deleted from device")
        refreshLibrary()
    }
}
