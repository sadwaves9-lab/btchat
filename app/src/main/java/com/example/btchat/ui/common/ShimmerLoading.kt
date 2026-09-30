package com.example.btchat.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

/**
 * Shimmer skeleton loader — WhatsApp/Telegram style
 */
@Composable
fun ShimmerBox(
    modifier: Modifier = Modifier,
    cornerRadius: Int = 12
) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = -400f, targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "translate"
    )
    val base = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    val highlight = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(base, highlight, base),
                    start = Offset(translate - 300f, 0f),
                    end = Offset(translate, 300f)
                )
            )
    )
}

@Composable
fun DeviceCardSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
    ) {
        ShimmerBox(Modifier.size(52.dp), cornerRadius = 26)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(16.dp))
            Spacer(Modifier.height(8.dp))
            ShimmerBox(Modifier.fillMaxWidth(0.3f).height(12.dp))
        }
    }
}
