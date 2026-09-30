package com.example.btchat.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Device(
    val name: String,
    val mac: String,
    val state: DeviceState = DeviceState.AVAILABLE,
    val signalStrength: Int = 2,
    val lastSeen: Long = System.currentTimeMillis(),
    val nickname: String? = null
) : Parcelable

enum class DeviceState {
    AVAILABLE,
    PAIRED,
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    BLOCKED
}
