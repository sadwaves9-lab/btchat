package com.example.btchat.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.btchat.model.ChatMessage
import com.example.btchat.model.MessageStatus
import com.example.btchat.model.MessageType

/**
 * Room entity — persisted chat message.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index("conversationId"),
        Index("timestamp"),
        Index("status"),
        Index(value = ["remoteId"], unique = false)
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,     // sorted pair: "macA|macB"
    val senderMac: String,
    val receiverMac: String,
    val text: String,
    val type: String,               // enum name
    val timestamp: Long,
    val status: String,
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val replyToId: String? = null,
    val isStarred: Boolean = false,
    val isDeleted: Boolean = false,
    val isEncrypted: Boolean = true,
    val meta: String? = null,
    val synced: Boolean = true
)

fun MessageEntity.toModel() = ChatMessage(
    id = id,
    senderMac = senderMac,
    receiverMac = receiverMac,
    text = text,
    type = runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.TEXT),
    timestamp = timestamp,
    status = runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.SENT),
    filePath = filePath,
    fileName = fileName,
    fileSize = fileSize,
    replyToId = replyToId,
    isStarred = isStarred,
    isDeleted = isDeleted,
    isEncrypted = isEncrypted,
    meta = meta
)

fun ChatMessage.toEntity(conversationId: String) = MessageEntity(
    id = id,
    conversationId = conversationId,
    senderMac = senderMac,
    receiverMac = receiverMac,
    text = text,
    type = type.name,
    timestamp = timestamp,
    status = status.name,
    filePath = filePath,
    fileName = fileName,
    fileSize = fileSize,
    replyToId = replyToId,
    isStarred = isStarred,
    isDeleted = isDeleted,
    isEncrypted = isEncrypted,
    meta = meta
)
