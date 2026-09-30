package com.example.btchat.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/* ============================================================
 *  BTChat Ultra — 8 Premium Color Themes
 * ============================================================ */

// 🌑 AMOLED Black
object AmoledColors {
    val Primary       = Color(0xFF00E5FF)
    val Secondary     = Color(0xFF7C4DFF)
    val Tertiary      = Color(0xFF00FFA3)
    val Background    = Color(0xFF000000)
    val Surface       = Color(0xFF0A0A0A)
    val SurfaceVariant= Color(0xFF161616)
    val OnPrimary     = Color(0xFF000000)
    val OnSurface     = Color(0xFFEAEAEA)
    val Outline       = Color(0xFF2A2A2A)
    val Error         = Color(0xFFFF5252)
    val Success       = Color(0xFF00E676)
    val Warning       = Color(0xFFFFAB40)
}

// 🌈 Cyberpunk Neon
object CyberpunkColors {
    val Primary       = Color(0xFF00F0FF)
    val Secondary     = Color(0xFFFF00E5)
    val Tertiary      = Color(0xFF9D00FF)
    val Background    = Color(0xFF07070C)
    val Surface       = Color(0xFF10101A)
    val SurfaceVariant= Color(0xFF181828)
    val OnPrimary     = Color(0xFF000000)
    val OnSurface     = Color(0xFFF0F0FF)
    val Outline       = Color(0xFF2D2D48)
    val Error         = Color(0xFFFF1744)
    val Success       = Color(0xFF00FFB2)
    val Warning       = Color(0xFFFFD600)
}

// 🌌 Aurora
object AuroraColors {
    val Primary       = Color(0xFF00E5A0)
    val Secondary     = Color(0xFF00B4FF)
    val Tertiary      = Color(0xFF8B5CF6)
    val Background    = Color(0xFF0B1220)
    val Surface       = Color(0xFF141C2E)
    val SurfaceVariant= Color(0xFF1D2740)
    val OnPrimary     = Color(0xFF001A12)
    val OnSurface     = Color(0xFFECF2FF)
    val Outline       = Color(0xFF2C3A5A)
    val Error         = Color(0xFFFF5C7A)
    val Success       = Color(0xFF4CFFB0)
    val Warning       = Color(0xFFFFC857)
}

// 🌅 Sunset
object SunsetColors {
    val Primary       = Color(0xFFFF6B6B)
    val Secondary     = Color(0xFFFFA24C)
    val Tertiary      = Color(0xFFFFD166)
    val Background    = Color(0xFF1A0F1A)
    val Surface       = Color(0xFF241424)
    val SurfaceVariant= Color(0xFF331C33)
    val OnPrimary     = Color(0xFF2B0A0A)
    val OnSurface     = Color(0xFFFFF0EC)
    val Outline       = Color(0xFF4D2A4D)
    val Error         = Color(0xFFFF3D68)
    val Success       = Color(0xFF9AE66E)
    val Warning       = Color(0xFFFFE066)
}

// 🌊 Ocean Deep
object OceanColors {
    val Primary       = Color(0xFF00C2FF)
    val Secondary     = Color(0xFF1E90FF)
    val Tertiary      = Color(0xFF00E5C0)
    val Background    = Color(0xFF061423)
    val Surface       = Color(0xFF0D1F33)
    val SurfaceVariant= Color(0xFF152A44)
    val OnPrimary     = Color(0xFF001A2B)
    val OnSurface     = Color(0xFFE6F1FF)
    val Outline       = Color(0xFF1F3A5A)
    val Error         = Color(0xFFFF6B6B)
    val Success       = Color(0xFF00E676)
    val Warning       = Color(0xFFFFC107)
}

// 🌲 Forest
object ForestColors {
    val Primary       = Color(0xFF4CAF50)
    val Secondary     = Color(0xFF8BC34A)
    val Tertiary      = Color(0xFF00BFA5)
    val Background    = Color(0xFF0A140C)
    val Surface       = Color(0xFF132016)
    val SurfaceVariant= Color(0xFF1C2E22)
    val OnPrimary     = Color(0xFF001A05)
    val OnSurface     = Color(0xFFEAF5EC)
    val Outline       = Color(0xFF2A4230)
    val Error         = Color(0xFFEF5350)
    val Success       = Color(0xFF69F0AE)
    val Warning       = Color(0xFFFFCA28)
}

// 🌹 Rose Gold
object RoseGoldColors {
    val Primary       = Color(0xFFE8A598)
    val Secondary     = Color(0xFFF4C4B8)
    val Tertiary      = Color(0xFFD4A5A5)
    val Background    = Color(0xFF1A1214)
    val Surface       = Color(0xFF251B1E)
    val SurfaceVariant= Color(0xFF342629)
    val OnPrimary     = Color(0xFF2B1414)
    val OnSurface     = Color(0xFFFFF1EC)
    val Outline       = Color(0xFF4A3438)
    val Error         = Color(0xFFFF6B7A)
    val Success       = Color(0xFFA7D8A0)
    val Warning       = Color(0xFFFFD0A0)
}

// 💎 Glassmorphism (Glass-like translucent)
object GlassColors {
    val Primary       = Color(0xFF80D8FF)
    val Secondary     = Color(0xFFB388FF)
    val Tertiary      = Color(0xFF80FFEA)
    val Background    = Color(0xFF10131A)
    val Surface       = Color(0x33FFFFFF) // translucent
    val SurfaceVariant= Color(0x1AFFFFFF)
    val OnPrimary     = Color(0xFF001A2B)
    val OnSurface     = Color(0xFFF5F8FF)
    val Outline       = Color(0x33FFFFFF)
    val Error         = Color(0xFFFF7A85)
    val Success       = Color(0xFF7CFFB2)
    val Warning       = Color(0xFFFFD97A)
}

/* ============================================================
 *  Gradients — used for buttons, backgrounds, headers
 * ============================================================ */
object Gradients {
    val cyberpunk = Brush.linearGradient(listOf(CyberpunkColors.Primary, CyberpunkColors.Secondary))
    val aurora    = Brush.linearGradient(listOf(AuroraColors.Primary, AuroraColors.Secondary, AuroraColors.Tertiary))
    val sunset    = Brush.linearGradient(listOf(SunsetColors.Primary, SunsetColors.Secondary))
    val ocean     = Brush.linearGradient(listOf(OceanColors.Primary, OceanColors.Tertiary))
    val forest    = Brush.linearGradient(listOf(ForestColors.Primary, ForestColors.Tertiary))
    val roseGold  = Brush.linearGradient(listOf(RoseGoldColors.Primary, RoseGoldColors.Secondary))
    val glass     = Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF)))
    val amoled    = Brush.linearGradient(listOf(AmoledColors.Primary, AmoledColors.Secondary))
}

/* ============================================================
 *  Chat bubble gradients
 * ============================================================ */
object BubbleColors {
    val sentBrush = Brush.linearGradient(
        listOf(Color(0xFF2E7CF6), Color(0xFF6E4BFF))
    )
    val receivedDark = Color(0xFF1E2430)
    val receivedLight = Color(0xFFEFF2F7)
}
