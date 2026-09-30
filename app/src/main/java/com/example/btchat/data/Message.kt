package com.example.btchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class MsgStatus {
    SENDING,    // ⏱️ clock icon (message local me hai, BT nahi bheja)
    SENT,       // ✓ single tick (BT pe bhej diya)
    DELIVERED,  // ✓✓ double tick (dusre phone pe pahuncha)
    READ        // ✓✓ blue tick (dusre ne padh liya)
}

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceMac: String,
    val text: String,
    val isSent: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = MsgStatus.SENDING.name,
    val pendingSend: Boolean = false
)
