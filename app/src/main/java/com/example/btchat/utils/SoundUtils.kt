package com.example.btchat.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.btchat.R

object SoundUtils {

    private var pool: SoundPool? = null
    private var sendId = 0
    private var receiveId = 0
    private var connectId = 0
    private var loaded = false

    fun init(ctx: Context) {
        if (loaded) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        pool = SoundPool.Builder().setMaxStreams(3).setAudioAttributes(attrs).build()
        pool?.setOnLoadCompleteListener { _, _, _ -> loaded = true }
        sendId = pool?.load(ctx, R.raw.send, 1) ?: 0
        receiveId = pool?.load(ctx, R.raw.receive, 1) ?: 0
        connectId = pool?.load(ctx, R.raw.connect, 1) ?: 0
    }

    fun playSend() = pool?.play(sendId, 0.6f, 0.6f, 1, 0, 1f)
    fun playReceive() = pool?.play(receiveId, 0.8f, 0.8f, 1, 0, 1f)
    fun playConnect() = pool?.play(connectId, 0.7f, 0.7f, 1, 0, 1f)

    fun release() {
        pool?.release(); pool = null; loaded = false
    }
}
