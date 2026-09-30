package com.example.btchat.ui.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Splash — particle ring + Bluetooth logo pulse
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {

    val infinite = rememberInfiniteTransition(label = "splash")

    val rot by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "rotation"
    )
    val pulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glowAlpha by infinite.animateFloat(
        initialValue = 0.3f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    var textVisible by remember { mutableStateOf(false) }
    var taglineVisible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(200)
        textVisible = true
        delay(600)
        taglineVisible = true
        delay(1800)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.background,
                        Color.Black
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // Particle field
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = size.minDimension / 3.5f
            val count = 36
            repeat(count) { i ->
                val angle = Math.toRadians((i * (360f / count) + rot).toDouble())
                val x = cx + (radius * cos(angle)).toFloat()
                val y = cy + (radius * sin(angle)).toFloat()
                val particleRadius = 3f + (i % 4)
                drawCircle(
                    color = MaterialTheme.colorScheme.primary.copy(
                        alpha = 0.3f + (i % 5) * 0.1f
                    ),
                    radius = particleRadius,
                    center = Offset(x, y)
                )
            }
            // Orbiting ring
            drawCircle(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 2f)
            )
            drawCircle(
                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.1f),
                radius = radius * 0.7f,
                center = Offset(cx, cy),
                style = Stroke(width = 1.5f)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            // Glow ring behind logo
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .alpha(glowAlpha)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    Color.Transparent
                                )
                            ),
                            CircleShape
                        )
                )
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulse)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            ),
                            CircleShape
                        )
                )
                Icon(
                    imageVector = Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }

            Spacer(Modifier.height(36.dp))

            AnimatedVisibility(
                visible = textVisible,
                enter = fadeIn(tween(600)) + slideInVertically(initialOffsetY = { it / 2 })
            ) {
                Text(
                    text = "BTChat",
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 2.sp
                )
            }

            Spacer(Modifier.height(8.dp))

            AnimatedVisibility(
                visible = taglineVisible,
                enter = fadeIn(tween(600))
            ) {
                Text(
                    text = "Offline • Encrypted • Bluetooth",
                    fontSize = 13.sp,
                    letterSpacing = 3.sp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                )
            }
        }

        // Bottom loading dots
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 60.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            repeat(3) { i ->
                val dotAlpha by infinite.animateFloat(
                    initialValue = 0.3f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(600, delayMillis = i * 180),
                        repeatMode = RepeatMode.Reverse
                    ), label = "dot$i"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .alpha(dotAlpha)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}
