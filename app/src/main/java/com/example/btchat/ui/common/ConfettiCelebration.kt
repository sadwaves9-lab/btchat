package com.example.btchat.ui.common

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlin.math.random

/**
 * Simple confetti burst — nice for "first connect" moment.
 */
@Composable
fun ConfettiCelebration(trigger: Boolean, onDone: () -> Unit = {}) {
    if (!trigger) return
    var particles by remember { mutableStateOf(generate()) }

    val progress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(2500, easing = LinearEasing),
        label = "confetti"
    )

    LaunchedEffect(trigger) {
        delay(2600)
        particles = emptyList()
        onDone()
    }

    val colors = listOf(
        Color(0xFF00F0FF), Color(0xFFFF00E5), Color(0xFFFFD600),
        Color(0xFF00E676), Color(0xFFFF5252), Color(0xFF9D00FF)
    )

    Canvas(Modifier.fillMaxSize()) {
        particles.forEach { p ->
            val x = p.startX * size.width + (p.dx * progress * size.width)
            val y = p.startY * size.height + (p.dy * progress * size.height)
            drawRect(
                color = colors[p.colorIndex % colors.size],
                topLeft = Offset(x, y),
                size = Size(p.size, p.size * 0.6f)
            )
        }
    }
}

private data class Particle(
    val startX: Float, val startY: Float,
    val dx: Float, val dy: Float,
    val size: Float, val colorIndex: Int
)

private fun generate(): List<Particle> = List(80) {
    Particle(
        startX = 0.5f + (random() - 0.5f) * 0.2f,
        startY = 0.35f,
        dx = (random() - 0.5f) * 1.5f,
        dy = random() * 1.2f + 0.3f,
        size = 6f + random() * 12f,
        colorIndex = (random() * 6).toInt()
    )
}
