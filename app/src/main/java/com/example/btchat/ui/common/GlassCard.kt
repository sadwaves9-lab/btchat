package com.example.btchat.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Glassmorphism Card — translucent + border + soft gradient
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    borderAlpha: Float = 0.18f,
    fillAlpha: Float = 0.08f,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val interaction = remember { MutableInteractionSource() }

    val clickModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = interaction,
            indication = rememberRipple(color = Color.White),
            onClick = onClick
        )
    } else Modifier

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = fillAlpha + 0.04f),
                        Color.White.copy(alpha = fillAlpha)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = borderAlpha), shape)
            .then(clickModifier),
        content = content
    )
}
