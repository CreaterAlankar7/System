package com.system.app

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

// Plays res/raw/sys_sound.mp3 (if present) for taps and notifications.
object SysSound {
    private var pool: SoundPool? = null
    private var soundId = 0
    private var loaded = false
    private var pending = false

    @Synchronized
    fun init(c: Context) {
        if (pool != null) return
        val app = c.applicationContext
        val res = app.resources.getIdentifier("sys_sound", "raw", app.packageName)
        if (res == 0) return
        val sp = SoundPool.Builder()
            .setMaxStreams(3)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        sp.setOnLoadCompleteListener { p, id, status ->
            if (status == 0) {
                loaded = true
                if (pending) {
                    pending = false
                    p.play(id, 1f, 1f, 1, 0, 1f)
                }
            }
        }
        soundId = sp.load(app, res, 1)
        pool = sp
    }

    fun play(c: Context) {
        if (pool == null) init(c)
        val p = pool ?: return
        if (loaded) p.play(soundId, 1f, 1f, 1, 0, 1f) else pending = true
    }
}