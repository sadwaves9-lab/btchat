package com.example.btchat.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val senderMac: String,
    val receiverMac: String,
    val text: String = "",
    val type: MessageType = MessageType.TEXT,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENDING,
    val filePath: String? = null,
    val fileSize: Long = 0L,
    val fileName: String? = null,
    val replyToId: String? = null,
    val isStarred: Boolean = false,
    val isDeleted: Boolean = false,
    val isEncrypted: Boolean = true,
    val meta: String? = null
) : Parcelable

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ,
    FAILED
}
