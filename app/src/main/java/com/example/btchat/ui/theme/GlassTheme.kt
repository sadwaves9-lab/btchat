package com.example.btchat.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// ===== Beautiful Color Palettes =====
object Palette {
    val DeepPurple = Color(0xFF6B46C1)
    val RoyalBlue = Color(0xFF3B82F6)
    val Cyan = Color(0xFF06B6D4)
    val Pink = Color(0xFFEC4899)
    val Rose = Color(0xFFF43F5E)
    val Orange = Color(0xFFF97316)
    val Amber = Color(0xFFF59E0B)
    val Emerald = Color(0xFF10B981)
    val Teal = Color(0xFF14B8A6)
    val Indigo = Color(0xFF6366F1)
    val Violet = Color(0xFF8B5CF6)
    val Fuchsia = Color(0xFFD946EF)

    val BgDeep = Color(0xFF0A0A15)
    val BgDark = Color(0xFF12121E)
    val BgSurface = Color(0xFF1A1A2E)
    val Glass = Color(0x15FFFFFF)
    val GlassBorder = Color(0x30FFFFFF)
    val TextPrimary = Color(0xFFF5F5FA)
    val TextSecondary = Color(0xFFA0A0B8)
}

// ===== Beautiful Gradients =====
object Grad {
    val purplePink = Brush.linearGradient(listOf(Palette.DeepPurple, Palette.Pink))
    val blueCyan = Brush.linearGradient(listOf(Palette.RoyalBlue, Palette.Cyan))
    val aurora = Brush.linearGradient(
        listOf(Palette.Violet, Palette.Cyan, Palette.Emerald)
    )
    val sunset = Brush.linearGradient(
        listOf(Palette.Orange, Palette.Rose, Palette.Pink)
    )
    val ocean = Brush.linearGradient(
        listOf(Palette.Indigo, Palette.RoyalBlue, Palette.Teal)
    )
    val glass = Brush.verticalGradient(
        listOf(Color(0x25FFFFFF), Color(0x08FFFFFF))
    )
    val bgMain = Brush.linearGradient(
        colors = listOf(
            Color(0xFF0A0A15),
            Color(0xFF14142A),
            Color(0xFF0D0D1F)
        )
    )
}

// ===== Glass Card =====
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 20.dp,
    borderAlpha: Float = 0.2f,
    fillAlpha: Float = 0.08f
) {
    Modifier
    val shape = RoundedCornerShape(cornerRadius)
    modifier
        .clip(shape)
        .background(
            Brush.verticalGradient(
                listOf(
                    Color.White.copy(alpha = fillAlpha + 0.05f),
                    Color.White.copy(alpha = fillAlpha)
                )
            )
        )
        .border(1.dp, Color.White.copy(alpha = borderAlpha), shape)
}
