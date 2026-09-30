package com.example.btchat.utils

import java.util.UUID

/**
 * BTChat Ultra — Global Constants
 * All magic numbers, UUIDs, timeouts and protocol markers.
 */
object Constants {

    // ---------- Bluetooth ----------
    /** Standard SPP UUID — keep this so any BTChat device can find each other */
    val APP_UUID: UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    const val SERVICE_NAME = "BTChat"
    const val DISCOVERABLE_DURATION = 300 // seconds

    // ---------- Connection ----------
    const val MAX_RETRIES = 5
    const val BASE_RETRY_DELAY_MS = 1500L
    const val MAX_RETRY_DELAY_MS = 30_000L
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val SOCKET_KEEPALIVE_MS = 5_000L

    // ---------- Protocol ----------
    const val PROTOCOL_VERSION = 3
    const val MSG_HEADER_SIZE = 24     // bytes: type(2) + idLen(2) + size(4) + reserved(16)

    // Message type bytes
    const val TYPE_TEXT: Byte = 0x01
    const val TYPE_IMAGE: Byte = 0x02
    const val TYPE_VIDEO: Byte = 0x03
    const val TYPE_VOICE: Byte = 0x04
    const val TYPE_FILE: Byte = 0x05
    const val TYPE_LOCATION: Byte = 0x06
    const val TYPE_TYPING: Byte = 0x10
    const val TYPE_READ: Byte = 0x11
    const val TYPE_DELIVERY: Byte = 0x12
    const val TYPE_HANDSHAKE: Byte = 0x20
    const val TYPE_HANDSHAKE_ACK: Byte = 0x21
    const val TYPE_FILE_CHUNK: Byte = 0x30
    const val TYPE_FILE_ACK: Byte = 0x31
    const val TYPE_FILE_RESUME: Byte = 0x32
    const val TYPE_PING: Byte = 0x40
    const val TYPE_PONG: Byte = 0x41
    const val TYPE_MESH_FORWARD: Byte = 0x50
    const val TYPE_MESH_ROUTE: Byte = 0x51

    // ---------- Buffer ----------
    const val BUFFER_SIZE = 16 * 1024          // 16 KB read buffer
    const val FILE_CHUNK_SIZE = 64 * 1024      // 64 KB per chunk

    // ---------- Notification ----------
    const val NOTIF_ID_SERVICE = 1001
    const val NOTIF_ID_MESSAGE_BASE = 2000
    const val NOTIF_ID_TRANSFER_BASE = 3000

    // ---------- Preferences ----------
    const val PREFS_NAME = "btchat_prefs"
    const val PREF_THEME = "theme"
    const val PREF_THEME_MODE = "theme_mode"
    const val PREF_NICKNAME = "nickname"
    const val PREF_AVATAR = "avatar"
    const val PREF_LAST_DEVICE = "last_device"

    // ---------- Storage ----------
    const val DB_NAME = "btchat.db"
    const val FILES_DIR = "btchat_files"
    const val IMAGES_DIR = "btchat_images"
    const val VOICE_DIR = "btchat_voice"
}
