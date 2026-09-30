package com.example.btchat.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.unit.dp

@Composable
fun TypingIndicator() {
    val infinite = rememberInfiniteTransition(label = "typing")
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { i ->
            val scale by infinite.animateFloat(
                initialValue = 0.6f, targetValue = 1.2f,
                animationSpec = infiniteRepeatable(
                    tween(500, delayMillis = i * 150),
                    repeatMode = RepeatMode.Reverse
                ), label = "dot$i"
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}
