package com.retropod.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import android.media.audiofx.Equalizer

class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null

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
        activePlayer = exoPlayer
        mediaSession = MediaSession.Builder(this, exoPlayer).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        activePlayer = null
        releaseEqualizer()
        mediaSession?.let {
            it.player.release()
            it.release()
        }
        mediaSession = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var activePlayer: ExoPlayer? = null
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
