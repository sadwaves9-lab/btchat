package com.example.btchat.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateManager {

    suspend fun downloadApk(
        context: Context,
        url: String,
        onProgress: (Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.getExternalFilesDir(null), "updates")
            if (!dir.exists()) dir.mkdirs()
            val outFile = File(dir, "btchat-update.apk")
            if (outFile.exists()) outFile.delete()

            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 60000
            conn.setRequestProperty("User-Agent", "BTChat-Android")
            conn.instanceFollowRedirects = true
            conn.connect()

            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }

            val total = conn.contentLength
            var downloaded = 0

            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        output.write(buf, 0, n)
                        downloaded += n
                        if (total > 0) {
                            val pct = (downloaded * 100 / total)
                            withContext(Dispatchers.Main) {
                                onProgress(pct)
                            }
                        }
                    }
                }
            }
            conn.disconnect()
            outFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun installApk(context: Context, file: File) {
        try {
            val uri: Uri = FileProvider.getUriForFile(
                context,
                context.packageName + ".fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
