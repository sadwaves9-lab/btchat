package com.example.btchat.bluetooth

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Reassembles TCP-style length-prefixed frames from a raw byte stream.
 * Frame layout: 
 */
class PacketAccumulator {

    private val buffer = ByteArrayOutputStream(64 * 1024)

    @Synchronized
    fun append(data: ByteArray, length: Int) {
        buffer.write(data, 0, length)
    }

    /** Returns the next full frame (including the 4-byte length prefix) or null. */
    @Synchronized
    fun next(): ByteArray? {
        val bytes = buffer.toByteArray()
        if (bytes.size < 4) return null

        val len = ByteBuffer.wrap(bytes, 0, 4).int
        val total = 4 + len
        if (len < 0 || total > bytes.size) return null

        val frame = bytes.copyOfRange(0, total)
        // Rewrite the buffer with the remainder
        buffer.reset()
        if (bytes.size > total) buffer.write(bytes, total, bytes.size - total)
        return frame
    }

    @Synchronized
    fun clear() = buffer.reset()
}
