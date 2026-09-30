package com.example.btchat.ui.home.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.model.Device
import kotlin.math.cos
import kotlin.math.sin

/**
 * Animated Radar Scanner
 * - sweeping cone
 * - concentric rings
 * - orbiting device dots
 */
@Composable
fun RadarScanner(
    isScanning: Boolean,
    devices: List<Device>,
    onDeviceClick: (Device) -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "radar")

    val sweep by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (isScanning) 2200 else 6000, easing = LinearEasing)
        ),
        label = "sweep"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    val glow by infinite.animateFloat(
        initialValue = 0.4f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxRadius = size.minDimension / 2f - 8f

            // Concentric rings
            for (i in 1..4) {
                drawCircle(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    radius = (maxRadius * i / 4f) * pulse,
                    center = Offset(cx, cy),
                    style = Stroke(width = 1.5f)
                )
            }

            // Sweep cone
            val sweepAngle = sweep
            val sweepRad = Math.toRadians(sweepAngle.toDouble())
            val endX = cx + (maxRadius * cos(sweepRad)).toFloat()
            val endY = cy + (maxRadius * sin(sweepRad)).toFloat()

            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        Color.Transparent
                    ),
                    start = Offset(cx, cy),
                    end = Offset(endX, endY)
                ),
                start = Offset(cx, cy),
                end = Offset(endX, endY),
                strokeWidth = 3f
            )

            // Trailing arc
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(
                        Color.Transparent,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy)
                ),
                startAngle = sweepAngle - 60f,
                sweepAngle = 60f,
                useCenter = true,
                topLeft = Offset(cx - maxRadius, cy - maxRadius),
                size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2)
            )
        }

        // Center Bluetooth icon with glow
        Box(
            modifier = Modifier
                .size(76.dp)
                .shadow(28.dp, CircleShape, spotColor = MaterialTheme.colorScheme.primary)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .border(
                    1.5.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = glow),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }

        // Orbiting device avatars
        devices.take(8).forEachIndexed { index, device ->
            val angleOffset = (index * (360f / devices.size.coerceAtLeast(1)))
            val angle = angleOffset + sweep * 0.15f
            val rad = Math.toRadians(angle.toDouble())
            val orbitRadius = 130.dp
            val x = (orbitRadius * cos(rad).toFloat()).value
            val y = (orbitRadius * sin(rad).toFloat()).value

            Box(
                modifier = Modifier
                    .offset(x = x.dp, y = y.dp)
                    .size(52.dp)
                    .shadow(20.dp, CircleShape, spotColor = MaterialTheme.colorScheme.secondary)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.secondary
                            )
                        )
                    )
                    .clickable { onDeviceClick(device) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = device.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
        }
    }
}
