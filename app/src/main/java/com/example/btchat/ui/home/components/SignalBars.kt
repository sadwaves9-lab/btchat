package com.example.btchat.ui.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun SignalBars(strength: Int) {
    val infinite = rememberInfiniteTransition(label = "signal")
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        repeat(4) { i ->
            val active = i < strength
            val pulse by infinite.animateFloat(
                initialValue = 0.6f, targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    tween(700, delayMillis = i * 120),
                    repeatMode = RepeatMode.Reverse
                ), label = "bar$i"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((6 + i * 3).dp)
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (active)
                            MaterialTheme.colorScheme.primary.copy(alpha = pulse)
                        else
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                    )
            )
        }
    }
}
