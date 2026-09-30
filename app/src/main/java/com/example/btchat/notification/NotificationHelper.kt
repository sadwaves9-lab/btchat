package com.example.btchat.notification

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.example.btchat.BTChatApp
import com.example.btchat.R
import com.example.btchat.model.ChatMessage
import com.example.btchat.utils.Constants
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context.NOTIFICATION_SERVICE

@Singleton
class NotificationHelper @Inject constructor(
    private val context: Context
) {
    private val nm: NotificationManager =
        context.getSystemService(NOTIFICATION_SERVICE) as NotificationManager

    /** Message notification with inline reply action. */
    fun showMessage(
        peerMac: String,
        peerName: String,
        message: ChatMessage,
        avatar: Bitmap? = null,
        sound: Boolean = true,
        vibrate: Boolean = true
    ) {
        val openIntent = Intent().apply {
            setClassName(context.packageName, "com.example.btchat.ui.MainActivity")
            putExtra("open_chat_mac", peerMac)
        }
        val openPi = PendingIntent.getActivity(
            context, peerMac.hashCode(), openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Inline reply
        val replyIntent = Intent(context, QuickReplyReceiver::class.java).apply {
            putExtra(QuickReplyReceiver.EXTRA_MAC, peerMac)
        }
        val replyPi = PendingIntent.getBroadcast(
            context, peerMac.hashCode() + 1, replyIntent,
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val remoteInput = RemoteInput.Builder(QuickReplyReceiver.KEY_REPLY)
            .setLabel("Reply…")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_send, "Reply", replyPi
        ).addRemoteInput(remoteInput).build()

        // Mark read
        val readIntent = Intent(context, QuickReplyReceiver::class.java).apply {
            action = QuickReplyReceiver.ACTION_MARK_READ
            putExtra(QuickReplyReceiver.EXTRA_MAC, peerMac)
        }
        val readPi = PendingIntent.getBroadcast(
            context, peerMac.hashCode() + 2, readIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val markReadAction = NotificationCompat.Action.Builder(
            R.drawable.ic_done_all, "Mark read", readPi
        ).build()

        val builder = NotificationCompat.Builder(context, BTChatApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_btchat_logo)
            .setContentTitle(peerName)
            .setContentText(message.text.ifBlank { "${message.type} message" })
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(replyAction)
            .addAction(markReadAction)
            .setStyle(
                NotificationCompat.MessagingStyle("You")
                    .addMessage(message.text, message.timestamp, peerName)
            )

        if (avatar != null) {
            builder.setLargeIcon(avatar)
        }
        if (sound) builder.setDefaults(NotificationCompat.DEFAULT_SOUND)
        if (vibrate) builder.setVibrate(longArrayOf(0, 80, 60, 120))

        nm.notify(Constants.NOTIF_ID_MESSAGE_BASE + peerMac.hashCode(), builder.build())
    }

    /** File transfer progress notification. */
    fun showTransfer(
        id: String,
        fileName: String,
        percent: Int,
        isUpload: Boolean
    ) {
        val builder = NotificationCompat.Builder(context, BTChatApp.CHANNEL_TRANSFERS)
            .setSmallIcon(R.drawable.ic_btchat_logo)
            .setContentTitle(if (isUpload) "Sending…" else "Receiving…")
            .setContentText("$fileName — $percent%")
            .setOnlyAlertOnce(true)
            .setOngoing(percent < 100)
            .setProgress(100, percent, false)

        nm.notify(Constants.NOTIF_ID_TRANSFER_BASE + id.hashCode(), builder.build())
    }

    fun cancelMessage(mac: String) {
        nm.cancel(Constants.NOTIF_ID_MESSAGE_BASE + mac.hashCode())
    }

    fun cancelTransfer(id: String) {
        nm.cancel(Constants.NOTIF_ID_TRANSFER_BASE + id.hashCode())
    }

    fun cancelAll() = nm.cancelAll()

    private fun assetToBitmap(): Bitmap? = null
}
