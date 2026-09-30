package com.example.btchat.ui.common

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Neon Gradient Button — animated press scale + glow
 */
@Composable
fun NeonButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    gradient: Brush? = null,
    height: Int = 54
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "scale")

    val brush = gradient ?: Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.primary,
            MaterialTheme.colorScheme.tertiary
        )
    )

    Box(
        modifier = modifier
            .height(height.dp)
            .scale(scale)
            .shadow(
                elevation = if (enabled) 24.dp else 0.dp,
                shape = RoundedCornerShape(50),
                ambientColor = MaterialTheme.colorScheme.primary,
                spotColor = MaterialTheme.colorScheme.primary
            )
            .clip(RoundedCornerShape(50))
            .background(if (enabled) brush else Brush.linearGradient(listOf(Color.Gray, Color.DarkGray)))
            .androidxClickable(interaction, enabled, onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun Modifier.androidxClickable(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    onClick: () -> Unit
) = androidx.compose.foundation.clickable(
    interactionSource = interaction,
    indication = null,
    enabled = enabled,
    onClick = onClick
)
