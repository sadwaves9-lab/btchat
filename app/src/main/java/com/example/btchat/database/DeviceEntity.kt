package com.example.btchat.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.btchat.model.Device
import com.example.btchat.model.DeviceState

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val mac: String,
    val name: String,
    val nickname: String? = null,
    val state: String,
    val signalStrength: Int = 2,
    val lastSeen: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val isBlocked: Boolean = false,
    val unreadCount: Int = 0
)

fun DeviceEntity.toModel() = Device(
    name = nickname ?: name,
    mac = mac,
    state = runCatching { DeviceState.valueOf(state) }.getOrDefault(DeviceState.AVAILABLE),
    signalStrength = signalStrength,
    lastSeen = lastSeen,
    nickname = nickname
)

fun Device.toEntity() = DeviceEntity(
    mac = mac,
    name = name,
    nickname = nickname,
    state = state.name,
    signalStrength = signalStrength,
    lastSeen = lastSeen
)
