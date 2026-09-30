package com.example.btchat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "messages")
data class Message(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val deviceMac: String,
    val text: String,
    val isSent: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
