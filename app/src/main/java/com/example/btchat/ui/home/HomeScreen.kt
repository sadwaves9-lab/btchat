package com.example.btchat.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.btchat.model.Device
import com.example.btchat.ui.common.NeonButton
import com.example.btchat.ui.home.components.DeviceCard
import com.example.btchat.ui.home.components.RadarScanner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onDeviceClick: (String, String) -> Unit,
    onSettingsClick: () -> Unit,
    onAIClick: () -> Unit,
    onQRClick: () -> Unit,
    onGroupClick: () -> Unit,
    onFilesClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BTChat", fontWeight = FontWeight.Bold, fontSize = 22.sp)
                        Text(
                            "Offline Bluetooth Messenger",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onQRClick) {
                        Icon(Icons.Default.QrCodeScanner, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onAIClick) {
                        Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.tertiary)
                    }
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.toggleScan() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                icon = {
                    Icon(
                        if (state.isScanning) Icons.Default.Stop else Icons.Default.Search,
                        null
                    )
                },
                text = { Text(if (state.isScanning) "Stop" else "Scan") }
            )
        }
    ) { padding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {

            // Quick action chips
            item {
                QuickActionsRow(
                    onQR = onQRClick,
                    onGroup = onGroupClick,
                    onFiles = onFilesClick,
                    onAI = onAIClick
                )
            }

            // Radar
            item {
                RadarScanner(
                    isScanning = state.isScanning,
                    devices = state.pairedDevices + state.discoveredDevices,
                    onDeviceClick = { d -> onDeviceClick(d.mac, d.name) }
                )
            }

            // Scanning label
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = if (state.isScanning) "Scanning for devices…" else "Tap Scan to discover",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            // Paired devices
            if (state.pairedDevices.isNotEmpty()) {
                item {
                    SectionHeader("Paired Devices")
                }
                items(state.pairedDevices, key = { it.mac }) { device ->
                    DeviceCard(device) { onDeviceClick(device.mac, device.name) }
                }
            }

            // Discovered
            if (state.discoveredDevices.isNotEmpty()) {
                item { SectionHeader("Discovered") }
                items(state.discoveredDevices, key = { it.mac }) { device ->
                    DeviceCard(device) { onDeviceClick(device.mac, device.name) }
                }
            }

            // Empty state
            item {
                AnimatedVisibility(
                    visible = !state.isScanning &&
                        state.pairedDevices.isEmpty() &&
                        state.discoveredDevices.isEmpty()
                ) {
                    EmptyDevices()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun QuickActionsRow(
    onQR: () -> Unit,
    onGroup: () -> Unit,
    onFiles: () -> Unit,
    onAI: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickAction("QR Pair", Icons.Default.QrCode, onQR, Modifier.weight(1f))
        QuickAction("Group", Icons.Default.Groups, onGroup, Modifier.weight(1f))
        QuickAction("Files", Icons.Default.Folder, onFiles, Modifier.weight(1f))
        QuickAction("AI", Icons.Default.AutoAwesome, onAI, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        onClick = onClick
    ) {
        Column(
            Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
private fun EmptyDevices() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.BluetoothSearching,
                null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(16.dp))
            Text("No devices yet", fontWeight = FontWeight.Bold)
            Text(
                "Make sure Bluetooth is on and the other device is discoverable",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
        }
    }
}
