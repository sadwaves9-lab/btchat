package com.example.btchat.bluetooth

import com.example.btchat.model.ChatMessage
import com.example.btchat.model.MessageStatus
import com.example.btchat.model.MessageType
import com.example.btchat.utils.Constants
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

enum class PacketType { TEXT, IMAGE, VOICE, FILE, TYPING, READ, DELIVERY, PING, PONG, HANDSHAKE, GOODBYE, UNKNOWN }

data class ParsedPacket(
    val type: PacketType,
    val message: ChatMessage? = null,
    val meta: String? = null
)

/**
 * PacketCodec — wire format.
 *
 * Layout: [LEN:4][TYPE:1][PAYLOAD...]
 *  LEN  = payload length (big-endian int)
 *  TYPE = packet type byte
 *  PAYLOAD = varies per type
 *
 *  TEXT payload: [ID_LEN:2][ID][TS:8][TEXT_UTF8...]
 */
object PacketCodec {

    // ---------- Encode ----------
    fun encode(msg: ChatMessage): ByteArray {
        val textBytes = msg.text.toByteArray(StandardCharsets.UTF_8)
        val idBytes = msg.id.toByteArray(StandardCharsets.UTF_8)

        val payload = ByteBuffer.allocate(2 + idBytes.size + 8 + 2 + textBytes.size)
        payload.putShort(idBytes.size.toShort())
        payload.put(idBytes)
        payload.putLong(msg.timestamp)
        payload.putShort(textBytes.size.toShort())
        payload.put(textBytes)

        return frame(typeToByte(msg.type), payload.array())
    }

    fun handshake(model: String, version: Int): ByteArray {
        val text = "v=$version;model=$model".toByteArray(StandardCharsets.UTF_8)
        return frame(Constants.TYPE_HANDSHAKE, text)
    }

    fun typing(isTyping: Boolean): ByteArray {
        return frame(Constants.TYPE_TYPING, byteArrayOf(if (isTyping) 1 else 0))
    }

    fun readReceipt(messageId: String): ByteArray {
        return frame(Constants.TYPE_READ, messageId.toByteArray(StandardCharsets.UTF_8))
    }

    fun deliveryReceipt(messageId: String): ByteArray {
        return frame(Constants.TYPE_DELIVERY, messageId.toByteArray(StandardCharsets.UTF_8))
    }

    fun ping(): ByteArray = frame(Constants.TYPE_PING, byteArrayOf())
    fun pong(): ByteArray = frame(Constants.TYPE_PONG, byteArrayOf())
    fun goodbye(): ByteArray = frame(Constants.TYPE_HANDSHAKE, "bye".toByteArray())

    // ---------- Decode ----------
    fun decode(packet: ByteArray, fromMac: String): ParsedPacket? {
        if (packet.size < 5) return null
        val buf = ByteBuffer.wrap(packet)
        val len = buf.int
        if (len + 4 > packet.size) return null
        val typeByte = buf.get()
        val payload = ByteArray(len)
        if (len > 0) buf.get(payload)

        return when (typeByte) {
            Constants.TYPE_TEXT -> {
                val p = ByteBuffer.wrap(payload)
                val idLen = p.short.toInt()
                val idBytes = ByteArray(idLen); p.get(idBytes)
                val id = String(idBytes, StandardCharsets.UTF_8)
                val ts = p.long
                val textLen = p.short.toInt()
                val textBytes = ByteArray(textLen); p.get(textBytes)
                val text = String(textBytes, StandardCharsets.UTF_8)

                ParsedPacket(
                    type = PacketType.TEXT,
                    message = ChatMessage(
                        id = id,
                        senderMac = fromMac,
                        receiverMac = "me",
                        text = text,
                        type = MessageType.TEXT,
                        timestamp = ts,
                        status = MessageStatus.DELIVERED
                    )
                )
            }
            Constants.TYPE_TYPING -> {
                val isTyping = payload.isNotEmpty() && payload[0].toInt() == 1
                ParsedPacket(type = PacketType.TYPING, meta = if (isTyping) "1" else "0")
            }
            Constants.TYPE_READ -> ParsedPacket(
                type = PacketType.READ,
                meta = String(payload, StandardCharsets.UTF_8)
            )
            Constants.TYPE_DELIVERY -> ParsedPacket(
                type = PacketType.DELIVERY,
                meta = String(payload, StandardCharsets.UTF_8)
            )
            Constants.TYPE_PING -> ParsedPacket(type = PacketType.PING)
            Constants.TYPE_PONG -> ParsedPacket(type = PacketType.PONG)
            Constants.TYPE_HANDSHAKE -> ParsedPacket(
                type = PacketType.HANDSHAKE,
                meta = String(payload, StandardCharsets.UTF_8)
            )
            else -> ParsedPacket(type = PacketType.UNKNOWN)
        }
    }

    // ---------- Helpers ----------
    private fun frame(type: Byte, payload: ByteArray): ByteArray {
        val out = ByteBuffer.allocate(4 + 1 + payload.size)
        out.putInt(payload.size)
        out.put(type)
        out.put(payload)
        return out.array()
    }

    private fun typeToByte(t: MessageType): Byte = when (t) {
        MessageType.TEXT -> Constants.TYPE_TEXT
        MessageType.IMAGE -> Constants.TYPE_IMAGE
        MessageType.VIDEO -> Constants.TYPE_VIDEO
        MessageType.VOICE -> Constants.TYPE_VOICE
        MessageType.FILE -> Constants.TYPE_FILE
        MessageType.LOCATION -> Constants.TYPE_LOCATION
        MessageType.TYPING -> Constants.TYPE_TYPING
        MessageType.READ_RECEIPT -> Constants.TYPE_READ
        MessageType.DELIVERY_RECEIPT -> Constants.TYPE_DELIVERY
        MessageType.HANDSHAKE -> Constants.TYPE_HANDSHAKE
        MessageType.FILE_CHUNK -> Constants.TYPE_FILE_CHUNK
        else -> Constants.TYPE_TEXT
    }
}
