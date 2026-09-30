package com.example.btchat.update

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val changelog: String,
    val downloadUrl: String,
    val fileSize: Long
)
