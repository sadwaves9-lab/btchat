package com.example.btchat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onThemeClick: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(p),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item { ProfileHeader() }
            item { SectionLabel("Appearance") }
            item {
                SettingItem(Icons.Default.Palette, "Theme", "Choose from 8 themes", onThemeClick)
            }
            item {
                SettingItem(Icons.Default.DarkMode, "Dark Mode", "Auto / Light / Dark") {}
            }
            item { SectionLabel("Chat") }
            item { SettingItem(Icons.Default.Wallpaper, "Wallpaper", "Per-chat backgrounds") {} }
            item { SettingItem(Icons.Default.Notifications, "Notifications", "Sound, vibration, LED") {} }
            item { SettingItem(Icons.Default.Lock, "Privacy", "Read receipts, typing, E2E keys") {} }
            item { SectionLabel("AI") }
            item { SettingItem(Icons.Default.AutoAwesome, "AI Copilot", "Smart reply, translate", {}) {} }
            item { SectionLabel("Advanced") }
            item { SettingItem(Icons.Default.Mesh, "Mesh Mode", "Multi-hop offline routing") {} }
            item { SettingItem(Icons.Default.Security, "Encryption", "AES-256 + ECDH") {} }
            item { SettingItem(Icons.Default.Storage, "Storage", "Cache, media, database") {} }
            item { SectionLabel("About") }
            item { SettingItem(Icons.Default.Info, "Version", "2.0.0-Ultra") {} }
            item { SettingItem(Icons.Default.Code, "Open Source", "MIT License") {} }
        }
    }
}

@Composable
private fun ProfileHeader() {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(72.dp)
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
            Text("Y", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(16.dp))
        Column {
            Text("You", fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                "Available",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun SettingItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurface.copy(0.55f),
                fontSize = 12.sp
            )
        }
        Icon(
            Icons.Default.ChevronRight,
            null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.4f)
        )
    }
}
