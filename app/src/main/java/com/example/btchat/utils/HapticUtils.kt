package com.example.btchat.utils

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

object HapticUtils {

    private fun vibrator(ctx: Context): Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun tap(ctx: Context) = vibrate(ctx, 20)
    fun send(ctx: Context) = vibrate(ctx, 30)
    fun receive(ctx: Context) = pattern(ctx, longArrayOf(0, 40, 40, 40))
    fun error(ctx: Context) = pattern(ctx, longArrayOf(0, 80, 60, 80))

    private fun vibrate(ctx: Context, ms: Long) {
        val v = vibrator(ctx) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION") v.vibrate(ms)
        }
    }

    private fun pattern(ctx: Context, pattern: LongArray) {
        val v = vibrator(ctx) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION") v.vibrate(pattern, -1)
        }
    }
}
