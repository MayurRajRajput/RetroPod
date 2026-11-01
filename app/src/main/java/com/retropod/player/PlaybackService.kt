package com.retropod.player

import android.content.Intent
import android.media.audiofx.Equalizer
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Owns playback independently of the Activity. Playback state is checkpointed
 * locally so a process restart restores the queue paused at the last position.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val sleepTimerTask = Runnable {
        player?.pause()
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit().remove(KEY_SLEEP_DEADLINE).apply()
    }
    private val checkpointHandler = Handler(Looper.getMainLooper())
    private val checkpointTask = object : Runnable {
        override fun run() {
            player?.let(::savePlaybackState)
            checkpointHandler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            savePlaybackState(player)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(true)
            volume = 1f
        }
        player = exoPlayer
        activePlayer = exoPlayer
        activeService = this
        restorePlaybackState(exoPlayer)
        restoreSleepTimer()
        exoPlayer.addListener(playerListener)
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
        checkpointHandler.postDelayed(checkpointTask, CHECKPOINT_INTERVAL_MS)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Keep the media service alive when the user dismisses the app task during playback. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current?.isPlaying == true) {
            savePlaybackState(current)
            return
        }
        current?.let(::savePlaybackState)
        stopSelf()
    }

    override fun onDestroy() {
        checkpointHandler.removeCallbacks(checkpointTask)
        checkpointHandler.removeCallbacks(sleepTimerTask)
        player?.let { current ->
            savePlaybackState(current)
            current.removeListener(playerListener)
        }
        activePlayer = null
        activeService = null
        releaseEqualizer()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    /** Persist a wall-clock deadline so the timer survives Activity and service recreation. */
    private fun setSleepTimerMinutes(minutes: Int) {
        checkpointHandler.removeCallbacks(sleepTimerTask)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (minutes <= 0) {
            prefs.edit().remove(KEY_SLEEP_DEADLINE).apply()
            return
        }
        val deadline = System.currentTimeMillis() + minutes.coerceIn(1, 24 * 60) * 60_000L
        prefs.edit().putLong(KEY_SLEEP_DEADLINE, deadline).apply()
        scheduleSleepTimer(deadline)
    }

    private fun restoreSleepTimer() {
        val deadline = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(KEY_SLEEP_DEADLINE, 0L)
        if (deadline <= 0L) return
        if (deadline <= System.currentTimeMillis()) {
            sleepTimerTask.run()
        } else {
            scheduleSleepTimer(deadline)
        }
    }

    private fun scheduleSleepTimer(deadline: Long) {
        checkpointHandler.removeCallbacks(sleepTimerTask)
        checkpointHandler.postDelayed(sleepTimerTask, (deadline - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    private fun savePlaybackState(current: Player) {
        try {
            // Do not erase the last useful checkpoint when the service is created
            // only to reconnect a controller and no queue has been loaded yet.
            if (current.mediaItemCount == 0) return
            val items = org.json.JSONArray()
            for (index in 0 until current.mediaItemCount) {
                val item = current.getMediaItemAt(index)
                val uri = item.localConfiguration?.uri ?: continue
                val obj = org.json.JSONObject()
                    .put("uri", uri.toString())
                    .put("id", item.mediaId)
                    .put("title", item.mediaMetadata.title?.toString() ?: "Unknown title")
                    .put("artist", item.mediaMetadata.artist?.toString() ?: "Unknown artist")
                    .put("album", item.mediaMetadata.albumTitle?.toString() ?: "Unknown album")
                items.put(obj)
            }
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_ITEMS, items.toString())
                .putInt(KEY_INDEX, current.currentMediaItemIndex.coerceAtLeast(0))
                .putLong(KEY_POSITION, current.currentPosition.coerceAtLeast(0L))
                // Intentionally never persist playWhenReady/isPlaying. A process restart is paused.
                .apply()
        } catch (_: Exception) {
            // A checkpoint failure must never interrupt active playback.
        }
    }

    private fun restorePlaybackState(target: ExoPlayer) {
        try {
            val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val raw = prefs.getString(KEY_ITEMS, null) ?: return
            val saved = org.json.JSONArray(raw)
            if (saved.length() == 0) return
            val restored = ArrayList<MediaItem>(saved.length())
            for (i in 0 until saved.length()) {
                val item = saved.getJSONObject(i)
                val uri = android.net.Uri.parse(item.getString("uri"))
                val metadata = MediaMetadata.Builder()
                    .setTitle(item.optString("title", "Unknown title"))
                    .setArtist(item.optString("artist", "Unknown artist"))
                    .setAlbumTitle(item.optString("album", "Unknown album"))
                    .build()
                restored += MediaItem.Builder()
                    .setUri(uri)
                    .setMediaId(item.optString("id", uri.toString()))
                    .setMediaMetadata(metadata)
                    .build()
            }
            if (restored.isEmpty()) return
            val index = prefs.getInt(KEY_INDEX, 0).coerceIn(restored.indices)
            val position = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
            target.setMediaItems(restored, index, position)
            target.prepare()
            target.pause()
        } catch (_: Exception) {
            // Ignore stale/unreadable URI checkpoints and allow an empty player.
        }
    }

    companion object {
        private const val PREFS_NAME = "retropod_playback_state"
        private const val KEY_ITEMS = "media_items"
        private const val KEY_INDEX = "current_index"
        private const val KEY_POSITION = "position_ms"
        private const val KEY_SLEEP_DEADLINE = "sleep_timer_deadline"
        private const val CHECKPOINT_INTERVAL_MS = 1_000L

        @Volatile private var activeService: PlaybackService? = null
        @Volatile private var activePlayer: ExoPlayer? = null

        fun setSleepTimer(minutes: Int) {
            activeService?.setSleepTimerMinutes(minutes)
        }
        @Volatile private var equalizer: Equalizer? = null
        @Volatile private var equalizerSession: Int = 0

        fun setPlaybackVolume(value: Float) {
            activePlayer?.volume = value.coerceIn(0f, 1f)
        }

        /** Applies a real Android system equalizer to the active ExoPlayer audio session. */
        @Synchronized
        fun applyEqualizerPreset(presetIndex: Int): Boolean {
            val player = activePlayer ?: return false
            val session = player.audioSessionId
            if (session == 0) return false
            return try {
                if (equalizer == null || equalizerSession != session) {
                    equalizer?.release()
                    equalizer = Equalizer(0, session).also { it.enabled = true }
                    equalizerSession = session
                }
                val eq = equalizer ?: return false
                val count = eq.numberOfPresets.toInt()
                if (count <= 0) return false
                eq.usePreset(presetIndex.coerceIn(0, count - 1).toShort())
                eq.enabled = true
                true
            } catch (_: Exception) {
                false
            }
        }

        fun equalizerPresetNames(): List<String> {
            return try {
                val player = activePlayer ?: return emptyList()
                val session = player.audioSessionId
                if (session == 0) return emptyList()
                if (equalizer == null || equalizerSession != session) {
                    equalizer?.release()
                    equalizer = Equalizer(0, session).also { it.enabled = true }
                    equalizerSession = session
                }
                val eq = equalizer ?: return emptyList()
                (0 until eq.numberOfPresets.toInt()).map { eq.getPresetName(it.toShort()).toString() }
            } catch (_: Exception) { emptyList() }
        }

        @Synchronized
        fun releaseEqualizer() {
            try { equalizer?.release() } catch (_: Exception) { }
            equalizer = null
            equalizerSession = 0
        }
    }
}
