package com.example.btchat.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val REPO = "sadwaves9-lab/btchat"
    private const val API_URL = "https://api.github.com/repos/$REPO/releases/latest"

    suspend fun check(currentVersionCode: Int): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val conn = URL(API_URL).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", "BTChat-Android")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                return@withContext null
            }

            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val obj = JSONObject(jsonText)
            val tagName = obj.optString("tag_name", "")
            val changelog = obj.optString("body", "No changelog")
            val remoteVersionCode = parseVersionCode(tagName)

            if (remoteVersionCode <= currentVersionCode) {
                return@withContext null
            }

            val assets = obj.optJSONArray("assets") ?: return@withContext null
            var downloadUrl = ""
            var fileSize = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                if (name.endsWith(".apk")) {
                    downloadUrl = asset.optString("browser_download_url", "")
                    fileSize = asset.optLong("size", 0L)
                    break
                }
            }

            if (downloadUrl.isEmpty()) return@withContext null

            UpdateInfo(
                versionCode = remoteVersionCode,
                versionName = tagName,
                changelog = changelog,
                downloadUrl = downloadUrl,
                fileSize = fileSize
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun parseVersionCode(tag: String): Int {
        val cleaned = tag.replace("v", "").replace("V", "")
        val firstPart = cleaned.split(".").firstOrNull() ?: return 0
        return firstPart.toIntOrNull() ?: 0
    }
}
