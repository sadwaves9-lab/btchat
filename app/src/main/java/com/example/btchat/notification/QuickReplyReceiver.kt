package com.example.btchat.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.example.btchat.bluetooth.BluetoothService
import com.example.btchat.model.ChatMessage
import com.example.btchat.model.MessageType
import com.example.btchat.utils.Constants

/**
 * Handles inline reply + mark-read from notification.
 */
class QuickReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val mac = intent.getStringExtra(EXTRA_MAC) ?: return

        when (intent.action) {
            ACTION_MARK_READ -> {
                Log.d(TAG, "Mark read for $mac")
            }
            else -> {
                val reply = RemoteInput.getResultsFromIntent(intent)
                    ?.getCharSequence(KEY_REPLY)
                    ?.toString()
                    ?.trim()
                if (!reply.isNullOrEmpty()) {
                    val msg = ChatMessage(
                        senderMac = "me",
                        receiverMac = mac,
                        text = reply,
                        type = MessageType.TEXT
                    )
                    BluetoothService.send(context, msg)
                }
            }
        }
    }

    companion object {
        private const val TAG = "QuickReplyReceiver"
        const val EXTRA_MAC = "extra_mac"
        const val KEY_REPLY = "key_reply"
        const val ACTION_MARK_READ = "com.example.btchat.MARK_READ"
    }
}
