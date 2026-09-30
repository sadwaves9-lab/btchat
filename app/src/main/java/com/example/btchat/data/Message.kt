package com.example.btchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MsgStatus { SENDING, SENT, DELIVERED, READ }

enum class MsgKind { TEXT, IMAGE, VIDEO, AUDIO, FILE }

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceMac: String,
    val text: String = "",
    val isSent: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = MsgStatus.SENDING.name,
    val pendingSend: Boolean = false,
    val kind: String = MsgKind.TEXT.name,
    val filePath: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0L,
    val mimeType: String? = null
)
