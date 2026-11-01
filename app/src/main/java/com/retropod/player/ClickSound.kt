package com.retropod.player

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

/**
 * Short iPod-style tick played through a SoundPool. It does not depend on the system
 * "touch sounds" setting, so the Cover Flow wheel always clicks when the option is on.
 */
internal object ClickSound {
    private var pool: SoundPool? = null
    private var soundId = 0
    private var ready = false

    fun init(context: Context) {
        if (pool != null) return
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val created = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attributes).build()
        created.setOnLoadCompleteListener { _, _, status -> ready = status == 0 }
        soundId = created.load(context.applicationContext, R.raw.wheel_click, 1)
        pool = created
    }

    fun play(volume: Float = 0.6f) {
        val active = pool ?: return
        if (!ready) return
        active.play(soundId, volume, volume, 1, 0, 1f)
    }
}
