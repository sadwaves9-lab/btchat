package com.example.btchat.ui.screens

import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.bluetooth.BluetoothScanner
import com.example.btchat.bluetooth.ScanDevice
import com.example.btchat.ui.Grad
import com.example.btchat.ui.Palette
import kotlinx.coroutines.delay

@Composable
fun ScanScreen(
    onBack: () -> Unit,
    onConnect: (ScanDevice) -> Unit
) {
    val ctx = LocalContext.current
    val scanner = remember { BluetoothScanner(ctx) }
    val devices by scanner.devices.collectAsState()
    val isScanning by scanner.isScanning.collectAsState()
    val error by scanner.error.collectAsState()

    DisposableEffect(Unit) {
        scanner.startScan()
        onDispose { scanner.release() }
    }

    Box(Modifier.fillMaxSize().background(Grad.bgMain)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Palette.Cyan.copy(alpha = 0.25f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 0.15f), radius = 500f),
                radius = 500f,
                center = Offset(w * 0.5f, h * 0.15f)
            )
        }

        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            // TOP BAR
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    scanner.stopScan()
                    onBack()
                }) { Icon(Icons.Default.ArrowBack, null, tint = Palette.TextPrimary) }
                Text("Scan Devices", color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { scanner.startScan() }) {
                    Icon(Icons.Default.Refresh, null, tint = Palette.Emerald)
                }
            }

            // RADAR
            Box(
                Modifier.fillMaxWidth().height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                RadarAnim(isScanning)
            }

            // STATUS TEXT
            Text(
                text = when {
                    error != null -> error!!
                    isScanning -> "Scanning… ${devices.size} found"
                    else -> "${devices.size} devices found"
                },
                color = if (error != null) Palette.Rose
                else if (isScanning) Palette.Emerald
                else Palette.TextSecondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))

            // ERROR PANEL
            if (error == "Bluetooth is off") {
                Box(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Palette.Rose.copy(alpha = 0.15f))
                        .border(1.dp, Palette.Rose.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .clickable {
                            try {
                                ctx.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                            } catch (_: Exception) { }
                        }
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.BluetoothDisabled, null, tint = Palette.Rose, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bluetooth is off", color = Palette.TextPrimary, fontWeight = FontWeight.Bold)
                            Text("Tap to open settings", color = Palette.TextSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Palette.Rose)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            // DEVICE LIST
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(devices, key = { it.mac }) { device ->
                    ScanDeviceCard(
                        device = device,
                        onClick = {
                            scanner.stopScan()
                            onConnect(device)
                        }
                    )
                }
                if (devices.isEmpty() && !isScanning) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, null, tint = Palette.TextSecondary, modifier = Modifier.size(56.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No devices found", color = Palette.TextPrimary, fontWeight = FontWeight.Bold)
                                Text("Tap refresh to scan again", color = Palette.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
//  Radar Animation
// ============================================================
@Composable
fun RadarAnim(isScanning: Boolean) {
    val infinite = rememberInfiniteTransition(label = "radar")
    val angle by infinite.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(if (isScanning) 2000 else 6000, easing = LinearEasing)
        ), label = "angle"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.7f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    Box(contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            val cx = size.width / 2
            val cy = size.height / 2
            val maxR = size.minDimension / 2 - 8f

            // Rings
            for (i in 1..3) {
                drawCircle(
                    color = Palette.Cyan.copy(alpha = 0.15f),
                    radius = (maxR * i / 3f) * pulse,
                    center = Offset(cx, cy),
                    style = Stroke(width = 2f)
                )
            }

            // Sweep line
            val rad = Math.toRadians(angle.toDouble())
            val ex = cx + (maxR * kotlin.math.cos(rad)).toFloat()
            val ey = cy + (maxR * kotlin.math.sin(rad)).toFloat()

            drawLine(
                brush = Brush.linearGradient(
                    listOf(Palette.Cyan, Color.Transparent),
                    start = Offset(cx, cy), end = Offset(ex, ey)
                ),
                start = Offset(cx, cy), end = Offset(ex, ey),
                strokeWidth = 4f
            )

            // Sweep arc
            drawArc(
                brush = Brush.sweepGradient(
                    listOf(Color.Transparent, Palette.Cyan.copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(cx, cy)
                ),
                startAngle = angle - 60f,
                sweepAngle = 60f,
                useCenter = true,
                topLeft = Offset(cx - maxR, cy - maxR),
                size = androidx.compose.ui.geometry.Size(maxR * 2, maxR * 2)
            )
        }

        // Center icon
        Box(
            Modifier.size(64.dp).clip(CircleShape).background(Grad.aurora),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.BluetoothSearching, null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
    }
}

// ============================================================
//  Device Card
// ============================================================
@Composable
fun ScanDeviceCard(device: ScanDevice, onClick: () -> Unit) {
    val rssiBars = when {
        device.rssi >= -55 -> 4
        device.rssi >= -70 -> 3
        device.rssi >= -85 -> 2
        device.rssi > -100 -> 1
        else -> 0
    }

    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(Color(0x15FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(18.dp))
            .clickable { onClick() }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (device.isPaired) Grad.aurora else Grad.blueCyan),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (device.isPaired) Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                null, tint = Color.White, modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(device.name, color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                if (device.isPaired) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50))
                            .background(Palette.Emerald.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text("PAIRED", color = Palette.Emerald, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(device.mac, color = Palette.TextSecondary, fontSize = 11.sp)
        }

        // Signal bars
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(4) { i ->
                Box(
                    Modifier
                        .width(3.dp)
                        .height((6 + i * 4).dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (i < rssiBars) Palette.Emerald
                            else Palette.TextSecondary.copy(0.25f)
                        )
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ChevronRight, null, tint = Palette.Emerald)
    }
}
