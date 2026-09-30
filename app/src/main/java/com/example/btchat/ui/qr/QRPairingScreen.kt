package com.example.btchat.ui.qr

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.utils.BluetoothUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QRPairingScreen(
    onBack: () -> Unit,
    onPaired: (String, String) -> Unit
) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }

    val adapter = remember { BluetoothUtils.adapter(ctx) }
    val mac = remember { runCatching { adapter?.address }.getOrDefault("00:00:00:00:00:00") }
    val name = remember { runCatching { adapter?.name }.getOrDefault("My BTChat") }

    val qrBitmap by remember {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val payload = QRGenerator.buildPairPayload(mac, name)
            qrBitmap = QRGenerator.generate(payload, 720)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("QR Pair", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(p)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("My QR") },
                    icon = { Icon(Icons.Default.QrCode2, null) }
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Scan") },
                    icon = { Icon(Icons.Default.QrCodeScanner, null) }
                )
            }

            Spacer(Modifier.height(24.dp))

            when (tab) {
                0 -> MyQRPanel(qrBitmap, name, mac)
                1 -> ScanPanel { req ->
                    onPaired(req.mac, req.name)
                }
            }
        }
    }
}

@Composable
private fun MyQRPanel(bmp: Bitmap?, name: String, mac: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(280.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "My QR",
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CircularProgressIndicator()
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(name, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(mac, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        Spacer(Modifier.height(20.dp))
        Text(
            "Ask the other device to scan this code",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
        )
    }
}

@Composable
private fun ScanPanel(onScanned: (PairRequest) -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceVariant,
                        MaterialTheme.colorScheme.surface
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.QrCodeScanner,
                null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(16.dp))
            Text("Camera preview goes here", fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Use zxing-android-embedded CaptureActivity",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = {
                onScanned(PairRequest("AA:BB:CC:11:22:99", "Demo Device", 3))
            }) { Text("Simulate Scan") }
        }
    }
}
