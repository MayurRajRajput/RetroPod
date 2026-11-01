package com.retropod.player

import android.Manifest
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.audiofx.Equalizer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

/** Intent extra that tells MainActivity to open the Now Playing screen (set by the notification). */
const val EXTRA_OPEN_PLAYER = "com.retropod.player.OPEN_PLAYER"

internal data class EqInfo(
    val bandCount: Int,
    val minLevel: Int,
    val maxLevel: Int,
    val centerFreqHz: List<Int>,
    val levels: List<Int>,
    val enabled: Boolean
)

/**
 * Owns playback independently of the Activity. Playback state is checkpointed
 * locally so a process restart restores the queue paused at the last position.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private var player: ExoPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val artworkExecutor = Executors.newSingleThreadExecutor()
    private val artworkAttempted = HashSet<String>()
    private var equalizer: Equalizer? = null
    private var visualizer: Visualizer? = null
    private var visualizerWanted = false
    private var effectSession = 0

    private val sleepTimerTask = Runnable {
        player?.pause()
        prefs().edit().remove(KEY_SLEEP_DEADLINE).remove(KEY_SLEEP_TOTAL).apply()
    }
    private val queueSaveTask = Runnable { saveQueueNow() }
    private val checkpointTask = object : Runnable {
        override fun run() {
            if (player?.isPlaying == true) savePosition()
            mainHandler.postDelayed(this, CHECKPOINT_INTERVAL_MS)
        }
    }
    /** Wakes the lock-screen player when the screen turns on (power button) while music is playing. */
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_ON) showLockScreenPlayer()
        }
    }
    private val playerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            scheduleQueueSave()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            savePosition()
            loadArtworkFor(mediaItem)
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            savePosition()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            rebuildAudioEffects(audioSessionId)
        }
    }

    private fun prefs() = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    private fun settings() = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)

    override fun onCreate() {
        super.onCreate()
        val settings = settings()
        val exoPlayer = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            setHandleAudioBecomingNoisy(settings.getBoolean("pause_on_unplug", true))
            setWakeMode(C.WAKE_MODE_LOCAL)
            skipSilenceEnabled = settings.getBoolean("skip_silence", false)
            volume = settings.getFloat("volume", 1f).coerceIn(0f, 1f)
        }
        player = exoPlayer
        activePlayer = exoPlayer
        activeService = this
        if (settings.getBoolean("resume_session", true)) restorePlaybackState(exoPlayer)
        restoreSleepTimer()
        exoPlayer.addListener(playerListener)

        // Tapping the media notification (or the lock-screen card) opens RetroPod's Now Playing screen.
        val openIntent = Intent(this, MainActivity::class.java).apply {
            // A custom action (not MAIN/LAUNCHER) so Android always delivers this intent to the running activity.
            action = EXTRA_OPEN_PLAYER
            putExtra(EXTRA_OPEN_PLAYER, true)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivity = PendingIntent.getActivity(
            this, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivity)
            .build()

        rebuildAudioEffects(exoPlayer.audioSessionId)
        loadArtworkFor(exoPlayer.currentMediaItem)
        mainHandler.postDelayed(checkpointTask, CHECKPOINT_INTERVAL_MS)
        ContextCompat.registerReceiver(this, screenReceiver, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    private fun showLockScreenPlayer() {
        try {
            if (!settings().getBoolean("lock_screen_player", false)) return
            if (player?.isPlaying != true) return
            val keyguard = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager ?: return
            if (!keyguard.isKeyguardLocked) return
            startActivity(
                Intent(this, LockScreenActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
        } catch (_: Exception) {
            // Android may refuse the launch (for example without "Display over other apps"); playback is unaffected.
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    /** Keep the media service alive when the user dismisses the app task during playback. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val current = player
        if (current?.isPlaying == true) {
            savePosition()
            return
        }
        saveQueueNow()
        savePosition()
        stopSelf()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenReceiver) }
        mainHandler.removeCallbacks(checkpointTask)
        mainHandler.removeCallbacks(sleepTimerTask)
        mainHandler.removeCallbacks(queueSaveTask)
        player?.let { current ->
            saveQueueNow()
            savePosition()
            current.removeListener(playerListener)
        }
        stopVisualizer()
        releaseEqualizerInstance()
        activePlayer = null
        activeService = null
        artworkExecutor.shutdown()
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }

    // ---------------------------------------------------------------- sleep timer

    private fun setSleepTimerMinutes(minutes: Int) {
        mainHandler.removeCallbacks(sleepTimerTask)
        val edit = prefs().edit()
        if (minutes <= 0) {
            edit.remove(KEY_SLEEP_DEADLINE).remove(KEY_SLEEP_TOTAL).apply()
            return
        }
        val totalMs = minutes.coerceIn(1, 24 * 60) * 60_000L
        val deadline = System.currentTimeMillis() + totalMs
        edit.putLong(KEY_SLEEP_DEADLINE, deadline).putLong(KEY_SLEEP_TOTAL, totalMs).apply()
        scheduleSleepTimer(deadline)
    }

    private fun restoreSleepTimer() {
        val deadline = prefs().getLong(KEY_SLEEP_DEADLINE, 0L)
        if (deadline <= 0L) return
        if (deadline <= System.currentTimeMillis()) sleepTimerTask.run() else scheduleSleepTimer(deadline)
    }

    private fun scheduleSleepTimer(deadline: Long) {
        mainHandler.removeCallbacks(sleepTimerTask)
        mainHandler.postDelayed(sleepTimerTask, (deadline - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    // ---------------------------------------------------------------- persistence

    private fun scheduleQueueSave() {
        mainHandler.removeCallbacks(queueSaveTask)
        mainHandler.postDelayed(queueSaveTask, 1_500L)
    }

    private fun saveQueueNow() {
        val current = player ?: return
        try {
            if (current.mediaItemCount == 0) return
            val items = org.json.JSONArray()
            for (index in 0 until current.mediaItemCount) {
                val item = current.getMediaItemAt(index)
                val uri = item.localConfiguration?.uri ?: continue
                items.put(
                    org.json.JSONObject()
                        .put("uri", uri.toString())
                        .put("id", item.mediaId)
                        .put("title", item.mediaMetadata.title?.toString() ?: "Unknown title")
                        .put("artist", item.mediaMetadata.artist?.toString() ?: "Unknown artist")
                        .put("album", item.mediaMetadata.albumTitle?.toString() ?: "Unknown album")
                )
            }
            prefs().edit().putString(KEY_ITEMS, items.toString()).apply()
        } catch (_: Exception) {
            // A checkpoint failure must never interrupt active playback.
        }
    }

    private fun savePosition() {
        val current = player ?: return
        try {
            if (current.mediaItemCount == 0) return
            prefs().edit()
                .putInt(KEY_INDEX, current.currentMediaItemIndex.coerceAtLeast(0))
                .putLong(KEY_POSITION, current.currentPosition.coerceAtLeast(0L))
                .putBoolean(KEY_SHUFFLE, current.shuffleModeEnabled)
                .putInt(KEY_REPEAT, current.repeatMode)
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun restorePlaybackState(target: ExoPlayer) {
        try {
            val saved = prefs()
            val raw = saved.getString(KEY_ITEMS, null) ?: return
            val array = org.json.JSONArray(raw)
            if (array.length() == 0) return
            val restored = ArrayList<MediaItem>(array.length())
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val uri = Uri.parse(item.getString("uri"))
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
            val index = saved.getInt(KEY_INDEX, 0).coerceIn(restored.indices)
            val position = saved.getLong(KEY_POSITION, 0L).coerceAtLeast(0L)
            target.shuffleModeEnabled = saved.getBoolean(KEY_SHUFFLE, false)
            target.repeatMode = saved.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)
            target.setMediaItems(restored, index, position)
            target.prepare()
            target.pause()
        } catch (_: Exception) {
            // Ignore stale/unreadable URI checkpoints and allow an empty player.
        }
    }

    // ---------------------------------------------------------------- artwork for the notification

    private fun loadArtworkFor(item: MediaItem?) {
        val target = item ?: return
        if (target.mediaMetadata.artworkData != null) return
        val key = target.mediaId
        if (!artworkAttempted.add(key)) return
        if (artworkAttempted.size > 400) artworkAttempted.clear()
        val uri = target.localConfiguration?.uri ?: return
        val context = applicationContext
        try {
            artworkExecutor.execute {
                val bytes = extractArtwork(context, uri) ?: return@execute
                mainHandler.post {
                    val current = player ?: return@post
                    for (i in 0 until current.mediaItemCount) {
                        val existing = current.getMediaItemAt(i)
                        if (existing.mediaId == key) {
                            if (existing.mediaMetadata.artworkData != null) break
                            val metadata = existing.mediaMetadata.buildUpon()
                                .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                                .build()
                            current.replaceMediaItem(i, existing.buildUpon().setMediaMetadata(metadata).build())
                            break
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun extractArtwork(context: Context, uri: Uri): ByteArray? {
        var retriever: MediaMetadataRetriever? = null
        return try {
            retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val raw = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 640 && bounds.outHeight / (sample * 2) >= 640) sample *= 2
            val bitmap = BitmapFactory.decodeByteArray(raw, 0, raw.size, BitmapFactory.Options().apply { inSampleSize = sample })
                ?: return null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
            bitmap.recycle()
            out.toByteArray()
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever?.release() }
        }
    }

    // ---------------------------------------------------------------- audio effects (EQ + visualizer)

    private fun rebuildAudioEffects(session: Int) {
        if (session == effectSession && equalizer != null) return
        effectSession = session
        releaseEqualizerInstance()
        if (session != 0 && session != C.AUDIO_SESSION_ID_UNSET) {
            try {
                val eq = Equalizer(0, session)
                equalizer = eq
                applyStoredEqualizer(eq)
            } catch (_: Exception) {
                equalizer = null
            }
        }
        if (visualizerWanted) startVisualizer()
    }

    private fun releaseEqualizerInstance() {
        try { equalizer?.release() } catch (_: Exception) { }
        equalizer = null
    }

    private fun applyStoredEqualizer(eq: Equalizer) {
        val s = settings()
        eq.setEnabled(s.getBoolean(KEY_EQ_ENABLED, true))
        val range = eq.getBandLevelRange()
        val saved = s.getString(KEY_EQ_LEVELS, null)?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
        val bands = eq.getNumberOfBands().toInt()
        for (band in 0 until bands) {
            val level = (saved?.getOrNull(band) ?: 0).coerceIn(range[0].toInt(), range[1].toInt())
            eq.setBandLevel(band.toShort(), level.toShort())
        }
    }

    private fun currentEqInfo(): EqInfo? {
        val eq = equalizer ?: return null
        return try {
            val bands = eq.getNumberOfBands().toInt()
            val range = eq.getBandLevelRange()
            EqInfo(
                bandCount = bands,
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                centerFreqHz = (0 until bands).map { eq.getCenterFreq(it.toShort()) / 1000 },
                levels = (0 until bands).map { eq.getBandLevel(it.toShort()).toInt() },
                enabled = eq.getEnabled()
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun persistEqLevels(custom: Boolean) {
        val info = currentEqInfo() ?: return
        val joined = info.levels.joinToString(",")
        val edit = settings().edit().putString(KEY_EQ_LEVELS, joined)
        if (custom) edit.putString(KEY_EQ_CUSTOM, joined)
        edit.apply()
    }

    private fun setEqLevels(levels: List<Int>, custom: Boolean) {
        val eq = equalizer ?: return
        try {
            val range = eq.getBandLevelRange()
            levels.forEachIndexed { band, level ->
                if (band < eq.getNumberOfBands()) {
                    eq.setBandLevel(band.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                }
            }
            persistEqLevels(custom)
        } catch (_: Exception) {
        }
    }

    private fun startVisualizer() {
        stopVisualizer()
        if (!visualizerWanted) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            spectrumLive = false
            return
        }
        val session = player?.audioSessionId ?: return
        if (session == 0 || session == C.AUDIO_SESSION_ID_UNSET) return
        try {
            val v = Visualizer(session)
            v.setCaptureSize(Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024))
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {}
                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) publishSpectrum(fft)
                    }
                },
                Visualizer.getMaxCaptureRate() / 2,
                false,
                true
            )
            v.setEnabled(true)
            visualizer = v
            spectrumLive = true
        } catch (_: Exception) {
            visualizer = null
            spectrumLive = false
        }
    }

    private fun stopVisualizer() {
        try {
            visualizer?.setEnabled(false)
            visualizer?.release()
        } catch (_: Exception) {
        }
        visualizer = null
        spectrumLive = false
    }

    private fun publishSpectrum(fft: ByteArray) {
        val bins = fft.size / 2
        if (bins < 4) return
        val previous = spectrum
        val next = FloatArray(SPECTRUM_BANDS)
        for (band in 0 until SPECTRUM_BANDS) {
            val start = (1 + (bins - 2) * (band.toFloat() / SPECTRUM_BANDS).pow(2.2f)).toInt().coerceIn(1, bins - 1)
            val end = (1 + (bins - 2) * ((band + 1).toFloat() / SPECTRUM_BANDS).pow(2.2f)).toInt()
                .coerceIn(start + 1, bins)
            var sum = 0f
            for (k in start until end) {
                val re = fft[2 * k].toInt().toFloat()
                val im = fft[2 * k + 1].toInt().toFloat()
                sum += hypot(re, im)
            }
            val magnitude = (sum / (end - start) / 90f).coerceIn(0f, 1f)
            val boosted = magnitude.pow(0.7f)
            next[band] = if (boosted > previous[band]) boosted else previous[band] * 0.82f + boosted * 0.18f
        }
        spectrum = next
    }

    companion object {
        private const val PREFS_NAME = "retropod_playback_state"
        private const val SETTINGS_PREFS = "retropod_settings"
        private const val KEY_ITEMS = "media_items"
        private const val KEY_INDEX = "current_index"
        private const val KEY_POSITION = "position_ms"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_SLEEP_DEADLINE = "sleep_timer_deadline"
        private const val KEY_SLEEP_TOTAL = "sleep_timer_total"
        private const val KEY_EQ_ENABLED = "eq_enabled"
        private const val KEY_EQ_LEVELS = "eq_levels"
        private const val KEY_EQ_CUSTOM = "eq_custom"
        private const val CHECKPOINT_INTERVAL_MS = 1_000L
        const val SPECTRUM_BANDS = 32

        @Volatile private var activeService: PlaybackService? = null
        @Volatile private var activePlayer: ExoPlayer? = null

        /** Latest FFT magnitudes (0..1) when the live visualizer is available. */
        @Volatile var spectrum: FloatArray = FloatArray(SPECTRUM_BANDS)
        @Volatile var spectrumLive: Boolean = false

        /** Starts (minutes > 0) or cancels (0) the service-owned sleep timer. Returns false if the service is not running. */
        fun setSleepTimer(minutes: Int): Boolean {
            val service = activeService ?: return false
            service.setSleepTimerMinutes(minutes)
            return true
        }

        fun sleepDeadline(context: Context): Long =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_SLEEP_DEADLINE, 0L)

        fun sleepTotal(context: Context): Long =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getLong(KEY_SLEEP_TOTAL, 0L)

        fun setPlaybackVolume(value: Float) {
            activePlayer?.volume = value.coerceIn(0f, 1f)
        }

        fun setSkipSilence(enabled: Boolean) {
            activePlayer?.skipSilenceEnabled = enabled
        }

        fun setPauseOnUnplug(enabled: Boolean) {
            activePlayer?.setHandleAudioBecomingNoisy(enabled)
        }

        fun setVisualizerActive(active: Boolean) {
            val service = activeService ?: return
            service.visualizerWanted = active
            if (active) service.startVisualizer() else service.stopVisualizer()
        }

        internal fun eqInfo(): EqInfo? = activeService?.currentEqInfo()

        fun setEqEnabled(enabled: Boolean) {
            val service = activeService ?: return
            try {
                service.equalizer?.setEnabled(enabled)
                service.settings().edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
            } catch (_: Exception) {
            }
        }

        fun setEqBand(band: Int, level: Int) {
            val service = activeService ?: return
            val eq = service.equalizer ?: return
            try {
                val range = eq.getBandLevelRange()
                eq.setBandLevel(band.toShort(), level.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
                service.persistEqLevels(custom = true)
            } catch (_: Exception) {
            }
        }

        /** Applies a named preset ("Bass", "Pop", "Rock", "Vocal", "Custom") and returns the resulting band levels. */
        internal fun applyEqPreset(name: String): List<Int>? {
            val service = activeService ?: return null
            val info = service.currentEqInfo() ?: return null
            val levels: List<Int> = if (name == "Custom") {
                val saved = service.settings().getString(KEY_EQ_CUSTOM, null)
                    ?.split(",")?.mapNotNull { it.trim().toIntOrNull() }
                if (saved != null && saved.size == info.bandCount) saved else List(info.bandCount) { 0 }
            } else {
                val shape = when (name) {
                    "Bass" -> floatArrayOf(.9f, .7f, .35f, 0f, -.1f, -.15f, -.2f)
                    "Pop" -> floatArrayOf(-.2f, .15f, .5f, .6f, .35f, 0f, -.2f)
                    "Rock" -> floatArrayOf(.6f, .35f, -.25f, -.4f, 0f, .5f, .7f)
                    "Vocal" -> floatArrayOf(-.4f, -.3f, 0f, .5f, .6f, .35f, 0f)
                    else -> floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)
                }
                List(info.bandCount) { band ->
                    val position = if (info.bandCount == 1) 0f else band * (shape.size - 1).toFloat() / (info.bandCount - 1)
                    val low = position.toInt().coerceIn(0, shape.lastIndex)
                    val high = (low + 1).coerceAtMost(shape.lastIndex)
                    val fraction = position - low
                    val value = shape[low] * (1 - fraction) + shape[high] * fraction
                    val limit = if (value >= 0) info.maxLevel else abs(info.minLevel)
                    (value * limit * 0.8f).roundToInt()
                }
            }
            service.setEqLevels(levels, custom = name == "Custom")
            return levels
        }
    }
}
