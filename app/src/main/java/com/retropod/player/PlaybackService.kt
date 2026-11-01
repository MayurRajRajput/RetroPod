package com.retropod.player

import android.content.Context
import android.net.Uri
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
 * Owns playback independently of the Activity. Closing the app UI does not stop
 * playback; the MediaSession notification and lock-screen controls remain active.
 *
 * The last selected item and playback position are checkpointed so a later app
 * launch can restore the song paused at the same position. We deliberately do
 * not auto-resume after process death.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkpoint = object : Runnable {
        override fun run() {
            savePlaybackState()
            if (player?.isPlaying == true) handler.postDelayed(this, 2_000L)
        }
    }

    private val listener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            savePlaybackState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            savePlaybackState()
            handler.removeCallbacks(checkpoint)
            if (player?.isPlaying == true) handler.postDelayed(checkpoint, 2_000L)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            savePlaybackState()
            handler.removeCallbacks(checkpoint)
            if (isPlaying) handler.postDelayed(checkpoint, 2_000L)
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
            addListener(listener)
        }
        player = exoPlayer
        restoreLastTrack(exoPlayer)
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    private fun restoreLastTrack(exoPlayer: ExoPlayer) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val uri = prefs.getString(KEY_URI, null) ?: return
        val title = prefs.getString(KEY_TITLE, "Unknown title") ?: "Unknown title"
        val artist = prefs.getString(KEY_ARTIST, "Unknown artist") ?: "Unknown artist"
        val album = prefs.getString(KEY_ALBUM, "Unknown album") ?: "Unknown album"
        val position = prefs.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
        try {
            val item = MediaItem.Builder()
                .setUri(Uri.parse(uri))
                .setMediaId(prefs.getString(KEY_MEDIA_ID, uri) ?: uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist(artist)
                        .setAlbumTitle(album)
                        .build()
                )
                .build()
            exoPlayer.setMediaItem(item, position)
            exoPlayer.prepare()
            // Restore as paused. User can resume from the app or notification.
        } catch (_: Exception) {
            // A removed/unavailable file should not prevent the player opening.
            prefs.edit().clear().apply()
        }
    }

    private fun savePlaybackState() {
        val p = player ?: return
        val item = p.currentMediaItem ?: return
        val uri = item.localConfiguration?.uri?.toString() ?: return
        val metadata = item.mediaMetadata
        getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_URI, uri)
            .putString(KEY_MEDIA_ID, item.mediaId)
            .putString(KEY_TITLE, metadata.title?.toString() ?: "Unknown title")
            .putString(KEY_ARTIST, metadata.artist?.toString() ?: "Unknown artist")
            .putString(KEY_ALBUM, metadata.albumTitle?.toString() ?: "Unknown album")
            .putLong(KEY_POSITION, p.currentPosition.coerceAtLeast(0L))
            .apply()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // Do not stop active playback when the user dismisses the app from recents.
        savePlaybackState()
        if (player?.isPlaying != true) {
            // Keep the paused item checkpoint; service lifecycle may now finish.
            stopSelf()
        }
    }

    override fun onDestroy() {
        savePlaybackState()
        handler.removeCallbacksAndMessages(null)
        player?.removeListener(listener)
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private companion object {
        const val PREFS = "retropod_playback"
        const val KEY_URI = "last_uri"
        const val KEY_MEDIA_ID = "last_media_id"
        const val KEY_TITLE = "last_title"
        const val KEY_ARTIST = "last_artist"
        const val KEY_ALBUM = "last_album"
        const val KEY_POSITION = "last_position"
    }
}
