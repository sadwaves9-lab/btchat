package com.example.btchat.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class User(
    val nickname: String = "You",
    val avatarPath: String? = null,
    val status: String = "Available",
    val mac: String = ""
) : Parcelable
