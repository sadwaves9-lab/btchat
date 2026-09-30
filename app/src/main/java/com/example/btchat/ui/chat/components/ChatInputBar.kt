package com.example.btchat.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onEmoji: () -> Unit,
    onAI: () -> Unit,
    onVoice: () -> Unit
) {
    val hasText = value.isNotBlank()

    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onEmoji) {
                Icon(Icons.Default.EmojiEmotions, null, tint = MaterialTheme.colorScheme.primary)
            }

            // Text field
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        "Type a message…",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        fontSize = 15.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 15.sp
                    ),
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.width(6.dp))

            IconButton(onClick = onAttach) {
                Icon(Icons.Default.AttachFile, null, tint = MaterialTheme.colorScheme.onSurface)
            }

            // Send / Voice button
            val infinite = rememberInfiniteTransition(label = "send")
            val pulse by infinite.animateFloat(
                initialValue = 1f, targetValue = 1.06f,
                animationSpec = infiniteRepeatable(tween(900), repeatMode = RepeatMode.Reverse),
                label = "pulse"
            )

            Box(
                Modifier
                    .size(48.dp)
                    .scale(if (hasText) pulse else 1f)
                    .shadow(20.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    )
                    .clickableNoRipple { if (hasText) onSend() else onVoice() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (hasText) Icons.Default.Send else Icons.Default.Mic,
                    contentDescription = null,
                    tint = Color.White
                )
            }
        }
    }
}

private fun Modifier.clickableNoRipple(onClick: () -> Unit) =
    this.then(
        androidx.compose.foundation.clickable(
            interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null,
            onClick = onClick
        )
    )
