package com.example.btchat.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/* ============================================================
 *  BTChat Ultra — Theme System
 *  8 hand-crafted themes + Material You dynamic color
 * ============================================================ */

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class AppTheme(val label: String, val emoji: String) {
    AMOLED("AMOLED Black", "🌑"),
    CYBERPUNK("Cyberpunk Neon", "🌈"),
    AURORA("Aurora", "🌌"),
    SUNSET("Sunset", "🌅"),
    OCEAN("Ocean Deep", "🌊"),
    FOREST("Forest", "🌲"),
    ROSE_GOLD("Rose Gold", "🌹"),
    GLASS("Glassmorphism", "💎")
}

data class ExtendedColors(
    val success: Color,
    val warning: Color,
    val bubbleSent: Color,
    val bubbleReceived: Color,
    val onlineDot: Color,
    val shimmer: Color
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(
        success = Color(0xFF00E676),
        warning = Color(0xFFFFC107),
        bubbleSent = Color(0xFF2E7CF6),
        bubbleReceived = Color(0xFF1E2430),
        onlineDot = Color(0xFF00E676),
        shimmer = Color(0x33FFFFFF)
    )
}

/* ---------- Private schemes ---------- */

private fun amoledScheme() = darkColorScheme(
    primary = AmoledColors.Primary, onPrimary = AmoledColors.OnPrimary,
    secondary = AmoledColors.Secondary, tertiary = AmoledColors.Tertiary,
    background = AmoledColors.Background, surface = AmoledColors.Surface,
    surfaceVariant = AmoledColors.SurfaceVariant, onSurface = AmoledColors.OnSurface,
    outline = AmoledColors.Outline, error = AmoledColors.Error
)

private fun cyberpunkScheme() = darkColorScheme(
    primary = CyberpunkColors.Primary, onPrimary = CyberpunkColors.OnPrimary,
    secondary = CyberpunkColors.Secondary, tertiary = CyberpunkColors.Tertiary,
    background = CyberpunkColors.Background, surface = CyberpunkColors.Surface,
    surfaceVariant = CyberpunkColors.SurfaceVariant, onSurface = CyberpunkColors.OnSurface,
    outline = CyberpunkColors.Outline, error = CyberpunkColors.Error
)

private fun auroraScheme() = darkColorScheme(
    primary = AuroraColors.Primary, onPrimary = AuroraColors.OnPrimary,
    secondary = AuroraColors.Secondary, tertiary = AuroraColors.Tertiary,
    background = AuroraColors.Background, surface = AuroraColors.Surface,
    surfaceVariant = AuroraColors.SurfaceVariant, onSurface = AuroraColors.OnSurface,
    outline = AuroraColors.Outline, error = AuroraColors.Error
)

private fun sunsetScheme() = darkColorScheme(
    primary = SunsetColors.Primary, onPrimary = SunsetColors.OnPrimary,
    secondary = SunsetColors.Secondary, tertiary = SunsetColors.Tertiary,
    background = SunsetColors.Background, surface = SunsetColors.Surface,
    surfaceVariant = SunsetColors.SurfaceVariant, onSurface = SunsetColors.OnSurface,
    outline = SunsetColors.Outline, error = SunsetColors.Error
)

private fun oceanScheme() = darkColorScheme(
    primary = OceanColors.Primary, onPrimary = OceanColors.OnPrimary,
    secondary = OceanColors.Secondary, tertiary = OceanColors.Tertiary,
    background = OceanColors.Background, surface = OceanColors.Surface,
    surfaceVariant = OceanColors.SurfaceVariant, onSurface = OceanColors.OnSurface,
    outline = OceanColors.Outline, error = OceanColors.Error
)

private fun forestScheme() = darkColorScheme(
    primary = ForestColors.Primary, onPrimary = ForestColors.OnPrimary,
    secondary = ForestColors.Secondary, tertiary = ForestColors.Tertiary,
    background = ForestColors.Background, surface = ForestColors.Surface,
    surfaceVariant = ForestColors.SurfaceVariant, onSurface = ForestColors.OnSurface,
    outline = ForestColors.Outline, error = ForestColors.Error
)

private fun roseGoldScheme() = darkColorScheme(
    primary = RoseGoldColors.Primary, onPrimary = RoseGoldColors.OnPrimary,
    secondary = RoseGoldColors.Secondary, tertiary = RoseGoldColors.Tertiary,
    background = RoseGoldColors.Background, surface = RoseGoldColors.Surface,
    surfaceVariant = RoseGoldColors.SurfaceVariant, onSurface = RoseGoldColors.OnSurface,
    outline = RoseGoldColors.Outline, error = RoseGoldColors.Error
)

private fun glassScheme() = darkColorScheme(
    primary = GlassColors.Primary, onPrimary = GlassColors.OnPrimary,
    secondary = GlassColors.Secondary, tertiary = GlassColors.Tertiary,
    background = GlassColors.Background, surface = GlassColors.Surface,
    surfaceVariant = GlassColors.SurfaceVariant, onSurface = GlassColors.OnSurface,
    outline = GlassColors.Outline, error = GlassColors.Error
)

/* ---------- Main Theme ---------- */

@Composable
fun BTChatTheme(
    appTheme: AppTheme = AppTheme.CYBERPUNK,
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> systemDark
    }

    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        appTheme == AppTheme.AMOLED -> amoledScheme()
        appTheme == AppTheme.CYBERPUNK -> cyberpunkScheme()
        appTheme == AppTheme.AURORA -> auroraScheme()
        appTheme == AppTheme.SUNSET -> sunsetScheme()
        appTheme == AppTheme.OCEAN -> oceanScheme()
        appTheme == AppTheme.FOREST -> forestScheme()
        appTheme == AppTheme.ROSE_GOLD -> roseGoldScheme()
        appTheme == AppTheme.GLASS -> glassScheme()
        else -> if (useDark) cyberpunkScheme() else lightColorScheme()
    }

    val extended = ExtendedColors(
        success = when (appTheme) {
            AppTheme.CYBERPUNK -> CyberpunkColors.Success
            AppTheme.AURORA -> AuroraColors.Success
            AppTheme.SUNSET -> SunsetColors.Success
            AppTheme.OCEAN -> OceanColors.Success
            AppTheme.FOREST -> ForestColors.Success
            AppTheme.ROSE_GOLD -> RoseGoldColors.Success
            AppTheme.GLASS -> GlassColors.Success
            else -> AmoledColors.Success
        },
        warning = when (appTheme) {
            AppTheme.CYBERPUNK -> CyberpunkColors.Warning
            AppTheme.AURORA -> AuroraColors.Warning
            AppTheme.SUNSET -> SunsetColors.Warning
            AppTheme.OCEAN -> OceanColors.Warning
            AppTheme.FOREST -> ForestColors.Warning
            AppTheme.ROSE_GOLD -> RoseGoldColors.Warning
            AppTheme.GLASS -> GlassColors.Warning
            else -> AmoledColors.Warning
        },
        bubbleSent = colorScheme.primary,
        bubbleReceived = colorScheme.surfaceVariant,
        onlineDot = Color(0xFF00E676),
        shimmer = Color(0x33FFFFFF)
    )

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !useDark
        }
    }

    CompositionLocalProvider(LocalExtendedColors provides extended) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = BTChatTypography,
            shapes = BTChatShapes,
            content = content
        )
    }
}

/* ---------- Convenience accessor ---------- */
object BTChatThemeExt {
    val colors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}
