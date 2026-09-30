package com.example.btchat.ui.qr

/**
 * Parses BTChat QR payloads.
 */
data class PairRequest(val mac: String, val name: String, val version: Int)

object QRParser {

    fun parse(raw: String): PairRequest? {
        if (!raw.startsWith("btchat://pair")) return null
        val query = raw.substringAfter("?", "")
        val params = query.split("&").mapNotNull {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) parts[0] to parts[1] else null
        }.toMap()

        val mac = params["mac"] ?: return null
        val name = params["name"]?.let { java.net.URLDecoder.decode(it, "UTF-8") } ?: "Unknown"
        val v = params["v"]?.toIntOrNull() ?: 3
        return PairRequest(mac, name, v)
    }
}
