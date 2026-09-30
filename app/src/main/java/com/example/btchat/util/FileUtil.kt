package com.example.btchat.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream

object FileUtil {

    fun queryName(ctx: Context, uri: Uri): String {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (c.moveToFirst() && i >= 0) return c.getString(i) ?: "file"
        }
        return uri.lastPathSegment ?: "file"
    }

    fun querySize(ctx: Context, uri: Uri): Long {
        ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val i = c.getColumnIndex(OpenableColumns.SIZE)
            if (c.moveToFirst() && i >= 0) return c.getLong(i)
        }
        return 0L
    }

    fun copyToInternal(ctx: Context, uri: Uri, subDir: String): File? {
        return try {
            val dir = File(ctx.filesDir, subDir).apply { mkdirs() }
            val name = queryName(ctx, uri)
            val out = File(dir, "${System.currentTimeMillis()}_$name")
            ctx.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            }
            out
        } catch (e: Exception) { null }
    }

    fun humanSize(bytes: Long): String {
        val kb = 1024.0
        return when {
            bytes < kb -> "$bytes B"
            bytes < kb * kb -> "%.1f KB".format(bytes / kb)
            bytes < kb * kb * kb -> "%.1f MB".format(bytes / kb / kb)
            else -> "%.2f GB".format(bytes / kb / kb / kb)
        }
    }

    fun kindFromMime(mime: String?): String = when {
        mime == null -> "FILE"
        mime.startsWith("image/") -> "IMAGE"
        mime.startsWith("video/") -> "VIDEO"
        mime.startsWith("audio/") -> "AUDIO"
        else -> "FILE"
    }
}
