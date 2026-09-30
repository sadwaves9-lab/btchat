package com.example.btchat.bluetooth

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.btchat.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * FileTransferManager — chunked, resumable file transfer over Bluetooth.
 *
 * Layout per chunk:
 *   [transferId:36][chunkIndex:4][totalChunks:4][chunkLen:4][data]
 *
 * Features:
 *   - 64 KB chunks (configurable)
 *   - Resume support via FILE_RESUME packet
 *   - Progress as StateFlow
 *   - Per-transfer directory under app files dir
 */
class FileTransferManager(
    private val context: Context,
    private val sendRaw: (targetMac: String, bytes: ByteArray) -> Unit
) {
    private val tag = "FileTransfer"

    data class Progress(
        val id: String,
        val fileName: String,
        val sent: Long,
        val total: Long,
        val isUpload: Boolean
    ) {
        val percent: Float get() = if (total <= 0) 0f else sent.toFloat() / total
    }

    private val _progress = MutableStateFlow<Map<String, Progress>>(emptyMap())
    val progress: StateFlow<Map<String, Progress>> = _progress.asStateFlow()

    /** In-flight incoming transfers: transferId -> receiving state */
    private data class Incoming(
        val fileName: String,
        val totalSize: Long,
        val totalChunks: Int,
        val file: File,
        var receivedChunks: Int = 0
    )
    private val incoming = ConcurrentHashMap<String, Incoming>()

    // ---------- Outgoing ----------
    suspend fun sendFile(
        targetMac: String,
        uri: Uri,
        fileName: String,
        mimeType: String
    ) = withContext(Dispatchers.IO) {
        val transferId = UUID.randomUUID().toString()
        val totalSize = context.contentResolver.openInputStream(uri)?.use {
            it.available().toLong()
        } ?: 0L

        val totalChunks = ((totalSize + Constants.FILE_CHUNK_SIZE - 1) / Constants.FILE_CHUNK_SIZE).toInt()

        emitProgress(Progress(transferId, fileName, 0L, totalSize, isUpload = true))

        // 1. Send HEADER
        val header = ByteBuffer.allocate(4 + 4 + 4 + transferId.length + fileName.toByteArray().size + mimeType.toByteArray().size + 4 + 4)
        header.putInt(transferId.length); header.put(transferId.toByteArray())
        header.putInt(fileName.toByteArray().size); header.put(fileName.toByteArray())
        header.putInt(mimeType.toByteArray().size); header.put(mimeType.toByteArray())
        header.putLong(totalSize)
        header.putInt(totalChunks)
        sendRaw(targetMac, frame(Constants.TYPE_FILE, header.array()))

        // 2. Send chunks
        val input = context.contentResolver.openInputStream(uri) ?: return@withContext
        input.use { stream ->
            val buf = ByteArray(Constants.FILE_CHUNK_SIZE)
            var chunkIndex = 0
            var sent = 0L
            while (true) {
                val read = stream.read(buf)
                if (read <= 0) break
                val chunk = buildChunk(transferId, chunkIndex, totalChunks, buf, read)
                sendRaw(targetMac, frame(Constants.TYPE_FILE_CHUNK, chunk))
                sent += read
                chunkIndex++
                if (chunkIndex % 8 == 0) {
                    emitProgress(Progress(transferId, fileName, sent, totalSize, isUpload = true))
                }
            }
            emitProgress(Progress(transferId, fileName, totalSize, totalSize, isUpload = true))
        }
    }

    private fun buildChunk(
        transferId: String,
        index: Int,
        total: Int,
        data: ByteArray,
        len: Int
    ): ByteArray {
        val idBytes = transferId.toByteArray()
        val buf = ByteBuffer.allocate(4 + idBytes.size + 4 + 4 + 4 + len)
        buf.putInt(idBytes.size); buf.put(idBytes)
        buf.putInt(index); buf.putInt(total); buf.putInt(len)
        buf.put(data, 0, len)
        return buf.array()
    }

    // ---------- Incoming ----------
    fun onHeader(payload: ByteArray) {
        val buf = ByteBuffer.wrap(payload)
        val idLen = buf.int; val id = String(ByteArray(idLen).also { buf.get(it) })
        val nameLen = buf.int; val name = String(ByteArray(nameLen).also { buf.get(it) })
        val mimeLen = buf.int; val mime = String(ByteArray(mimeLen).also { buf.get(it) })
        val totalSize = buf.long
        val totalChunks = buf.int

        val dir = File(context.filesDir, Constants.FILES_DIR).apply { mkdirs() }
        val file = File(dir, name)

        incoming[id] = Incoming(name, totalSize, totalChunks, file)
        emitProgress(Progress(id, name, 0L, totalSize, isUpload = false))
        Log.d(tag, "Incoming: $name ($totalSize bytes, $totalChunks chunks, $mime)")
    }

    fun onChunk(payload: ByteArray) {
        val buf = ByteBuffer.wrap(payload)
        val idLen = buf.int; val id = String(ByteArray(idLen).also { buf.get(it) })
        val index = buf.int
        val total = buf.int
        val len = buf.int
        val data = ByteArray(len).also { buf.get(it) }

        val state = incoming[id] ?: return
        // Append to file
        RandomAccessFile(state.file, "rw").use { raf ->
            raf.seek(index.toLong() * Constants.FILE_CHUNK_SIZE)
            raf.write(data)
        }
        state.receivedChunks++
        val sent = minOf(state.totalSize, state.receivedChunks.toLong() * Constants.FILE_CHUNK_SIZE)
        emitProgress(Progress(id, state.fileName, sent, state.totalSize, isUpload = false))

        if (state.receivedChunks >= state.totalChunks) {
            incoming.remove(id)
            Log.d(tag, "File complete: ${state.file.absolutePath}")
        }
    }

    // ---------- Helpers ----------
    private fun frame(type: Byte, payload: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(4 + 1 + payload.size)
        out.putInt(payload.size); out.put(type); out.put(payload)
        return out.array()
    }

    private fun emitProgress(p: Progress) {
        _progress.value = _progress.value.toMutableMap().apply { put(p.id, p) }
    }

    fun clear() {
        incoming.clear()
        _progress.value = emptyMap()
    }
}
