package com.example.btchat.ui.chat.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.model.ChatMessage
import com.example.btchat.model.MessageStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * WhatsApp-style message bubble
 * - gradient for sent, flat for received
 * - delivered/read ticks
 * - reply preview
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: ChatMessage,
    isSent: Boolean,
    onLongPress: () -> Unit
) {
    val shape = if (isSent)
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 6.dp)
    else
        RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp, bottomStart = 6.dp, bottomEnd = 20.dp)

    val interaction = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(1f, label = "scale")

    val bgBrush = if (isSent)
        Brush.linearGradient(listOf(Color(0xFF2E7CF6), Color(0xFF6E4BFF)))
    else
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
            )
        )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = if (isSent) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .scale(scale)
                .clip(shape)
                .background(bgBrush)
                .combinedClickable(
                    interactionSource = interaction,
                    indication = null,
                    onLongClick = onLongPress,
                    onClick = {}
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {

            // Reply preview
            if (message.replyToId != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .padding(8.dp)
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .height(28.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Replied message",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
                Spacer(Modifier.height(6.dp))
            }

            // Text
            Text(
                text = message.text,
                color = if (isSent) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                lineHeight = 21.sp
            )

            Spacer(Modifier.height(4.dp))

            // Meta row
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.isEncrypted) {
                    Icon(
                        Icons.Default.Lock,
                        null,
                        tint = Color.White.copy(0.55f),
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    formatTime(message.timestamp),
                    fontSize = 10.sp,
                    color = if (isSent) Color.White.copy(0.75f)
                    else MaterialTheme.colorScheme.onSurface.copy(0.55f)
                )
                if (isSent) {
                    Spacer(Modifier.width(4.dp))
                    StatusIcon(message.status)
                }
            }
        }
    }
}

@Composable
private fun StatusIcon(status: MessageStatus) {
    val (icon, color) = when (status) {
        MessageStatus.SENDING -> Icons.Default.Schedule to Color.White.copy(0.6f)
        MessageStatus.SENT -> Icons.Default.Check to Color.White.copy(0.85f)
        MessageStatus.DELIVERED -> Icons.Default.DoneAll to Color.White.copy(0.85f)
        MessageStatus.READ -> Icons.Default.DoneAll to Color(0xFF00E5FF)
        MessageStatus.FAILED -> Icons.Default.ErrorOutline to Color(0xFFFF5252)
    }
    Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
}

private fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
