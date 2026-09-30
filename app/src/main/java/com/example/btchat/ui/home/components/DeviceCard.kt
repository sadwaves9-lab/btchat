package com.example.btchat.ui.home.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.model.Device
import com.example.btchat.model.DeviceState

/**
 * Glassmorphic device card with status + signal
 */
@Composable
fun DeviceCard(
    device: Device,
    onClick: () -> Unit
) {
    val isConnected = device.state == DeviceState.CONNECTED
    val statusColor by animateColorAsState(
        targetValue = when (device.state) {
            DeviceState.CONNECTED -> Color(0xFF00E676)
            DeviceState.CONNECTING -> Color(0xFFFFC107)
            DeviceState.PAIRED -> Color(0xFF00C2FF)
            else -> Color.Gray
        },
        animationSpec = tween(500), label = "status"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                device.name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            )
            // Online dot
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                when (device.state) {
                    DeviceState.CONNECTED -> "Connected"
                    DeviceState.CONNECTING -> "Connecting…"
                    DeviceState.PAIRED -> device.mac
                    else -> "Available"
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            SignalBars(strength = device.signalStrength)
            Spacer(Modifier.height(6.dp))
            Icon(
                imageVector = if (isConnected) Icons.Default.Link else Icons.Default.LinkOff,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
