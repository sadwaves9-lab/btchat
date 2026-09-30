package com.example.btchat.model

data class FilePacket(
    val id: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val totalChunks: Int,
    val chunkIndex: Int,
    val data: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is FilePacket) return false
        return id == other.id && chunkIndex == other.chunkIndex
    }
    override fun hashCode(): Int = id.hashCode() * 31 + chunkIndex
}
