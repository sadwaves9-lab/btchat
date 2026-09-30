package com.example.btchat.notification

import android.content.Context
import com.example.btchat.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin facade around NotificationHelper with repository-aware behavior.
 */
@Singleton
class MessageNotification @Inject constructor(
    private val helper: NotificationHelper,
    private val context: Context
) {
    fun notifyIncoming(
        peerMac: String,
        peerName: String,
        message: ChatMessage,
        sound: Boolean = true,
        vibrate: Boolean = true
    ) {
        helper.showMessage(
            peerMac = peerMac,
            peerName = peerName,
            message = message,
            sound = sound,
            vibrate = vibrate
        )
    }

    fun cancel(peerMac: String) = helper.cancelMessage(peerMac)
}
