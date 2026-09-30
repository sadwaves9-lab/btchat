package com.example.btchat.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {

    fun chatTimestamp(ts: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = ts }

        val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
            now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

        return when {
            sameDay -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) ->
                SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(ts))
            else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(ts))
        }
    }

    fun lastSeen(ts: Long): String {
        val diff = System.currentTimeMillis() - ts
        return when {
            diff < 60_000 -> "just now"
            diff < 3600_000 -> "${TimeUnit.MILLISECONDS.toMinutes(diff)}m ago"
            diff < 86_400_000 -> "${TimeUnit.MILLISECONDS.toHours(diff)}h ago"
            else -> "${TimeUnit.MILLISECONDS.toDays(diff)}d ago"
        }
    }

    fun duration(ms: Long): String {
        val s = ms / 1000
        return when {
            s < 60 -> "${s}s"
            s < 3600 -> "${s / 60}m ${s % 60}s"
            else -> "${s / 3600}h ${(s % 3600) / 60}m"
        }
    }
}
