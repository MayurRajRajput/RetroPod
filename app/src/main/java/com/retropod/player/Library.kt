package com.retropod.player

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.sin

/** Reads the device music library. Caller must already hold the audio permission. */
internal fun readLibrary(context: Context, minDurationMs: Long, excludedFolders: List<String>): List<Song> {
    val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    @Suppress("DEPRECATION")
    val projection = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
        MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATA,
        MediaStore.Audio.Media.DATE_ADDED
    )
    val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
    val sortOrder = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
    val songs = mutableListOf<Song>()
    val blocked = excludedFolders.map { it.trim() }.filter { it.isNotEmpty() }
    context.contentResolver.query(collection, projection, selection, null, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        @Suppress("DEPRECATION")
        val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
        val addedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
        while (cursor.moveToNext()) {
            val duration = cursor.getLong(durationCol)
            if (duration < minDurationMs) continue
            val path = if (dataCol >= 0) cursor.getString(dataCol).orEmpty() else ""
            if (blocked.isNotEmpty() && blocked.any { path.contains(it, ignoreCase = true) }) continue
            val id = cursor.getLong(idCol)
            songs += Song(
                id = id,
                title = cursor.getString(titleCol)?.takeIf(String::isNotBlank) ?: "Unknown title",
                artist = cursor.getString(artistCol)?.takeIf { v -> v.isNotBlank() && v != "<unknown>" } ?: "Unknown artist",
                album = cursor.getString(albumCol)?.takeIf { v -> v.isNotBlank() && v != "<unknown>" } ?: "Unknown album",
                durationMs = duration,
                uri = ContentUris.withAppendedId(collection, id),
                path = path,
                addedAt = if (addedCol >= 0) cursor.getLong(addedCol) else 0L
            )
        }
    }
    return songs
}

internal data class LyricLine(val timeMs: Long, val text: String)

internal class ParsedLyrics(val lines: List<LyricLine>, val synced: Boolean)

private val lrcTimePattern = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

/** Parses LRC (timestamped) or plain-text lyrics. Plain lines get timeMs = -1. */
internal fun parseLyrics(raw: String): ParsedLyrics {
    if (raw.isBlank()) return ParsedLyrics(emptyList(), false)
    val timed = mutableListOf<LyricLine>()
    raw.lineSequence().forEach { line ->
        val matches = lrcTimePattern.findAll(line).toList()
        if (matches.isEmpty()) return@forEach
        val text = line.replace(lrcTimePattern, "").trim()
        matches.forEach { match ->
            val minutes = match.groupValues[1].toLongOrNull() ?: 0L
            val seconds = match.groupValues[2].toLongOrNull() ?: 0L
            val fractionRaw = match.groupValues.getOrNull(3).orEmpty()
            val fraction = when (fractionRaw.length) {
                0 -> 0L
                1 -> (fractionRaw.toLongOrNull() ?: 0L) * 100L
                2 -> (fractionRaw.toLongOrNull() ?: 0L) * 10L
                else -> fractionRaw.take(3).toLongOrNull() ?: 0L
            }
            timed += LyricLine(minutes * 60_000L + seconds * 1_000L + fraction, text)
        }
    }
    if (timed.isNotEmpty()) return ParsedLyrics(timed.sortedBy { it.timeMs }, true)
    val plain = raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { LyricLine(-1L, it) }.toList()
    return ParsedLyrics(plain, false)
}

internal fun formatTime(ms: Long): String {
    val seconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    return "%d:%02d".format(Locale.US, seconds / 60, seconds % 60)
}

internal fun formatCount(value: Int): String = "%,d".format(Locale.US, value)

internal fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 5 -> "Good Night"
        hour < 12 -> "Good Morning"
        hour < 17 -> "Good Afternoon"
        hour < 22 -> "Good Evening"
        else -> "Good Night"
    }
}

/**
 * Level (0..1) for one visual band. Uses the live FFT when available, otherwise a gentle
 * synthetic motion while playing so the visuals never look frozen.
 */
internal fun bandLevel(index: Int, count: Int, timeNanos: Long, playing: Boolean): Float {
    if (!playing) return 0.06f
    val spectrum = PlaybackService.spectrum
    if (PlaybackService.spectrumLive && spectrum.isNotEmpty()) {
        val position = index.toFloat() / (count - 1).coerceAtLeast(1) * (spectrum.size - 1)
        val low = position.toInt().coerceIn(0, spectrum.lastIndex)
        val high = (low + 1).coerceAtMost(spectrum.lastIndex)
        val fraction = position - low
        return (spectrum[low] * (1 - fraction) + spectrum[high] * fraction).coerceIn(0.03f, 1f)
    }
    val t = timeNanos / 1_000_000_000f
    return (0.38f + 0.24f * sin(t * 2.6f + index * 0.55f) + 0.14f * sin(t * 4.1f + index * 1.3f)).coerceIn(0.05f, 1f)
}
