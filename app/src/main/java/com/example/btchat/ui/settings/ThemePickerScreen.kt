package com.example.btchat.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.ui.theme.AppTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemePickerScreen(onBack: () -> Unit) {
    var selected by remember { mutableStateOf(AppTheme.CYBERPUNK) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Themes", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(p),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(AppTheme.values().toList()) { theme ->
                ThemeTile(theme, selected == theme) { selected = theme }
            }
        }
    }
}

@Composable
private fun ThemeTile(theme: AppTheme, isSelected: Boolean, onClick: () -> Unit) {
    val previewBrush = when (theme) {
        AppTheme.AMOLED -> Brush.linearGradient(listOf(Color(0xFF00E5FF), Color(0xFF7C4DFF)))
        AppTheme.CYBERPUNK -> Brush.linearGradient(listOf(Color(0xFF00F0FF), Color(0xFFFF00E5)))
        AppTheme.AURORA -> Brush.linearGradient(listOf(Color(0xFF00E5A0), Color(0xFF8B5CF6)))
        AppTheme.SUNSET -> Brush.linearGradient(listOf(Color(0xFFFF6B6B), Color(0xFFFFD166)))
        AppTheme.OCEAN -> Brush.linearGradient(listOf(Color(0xFF00C2FF), Color(0xFF00E5C0)))
        AppTheme.FOREST -> Brush.linearGradient(listOf(Color(0xFF4CAF50), Color(0xFF00BFA5)))
        AppTheme.ROSE_GOLD -> Brush.linearGradient(listOf(Color(0xFFE8A598), Color(0xFFF4C4B8)))
        AppTheme.GLASS -> Brush.linearGradient(listOf(Color(0xFF80D8FF), Color(0xFFFFB3FF)))
    }

    Column(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = if (isSelected) 2.dp else 0.dp,
                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(80.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(previewBrush),
            contentAlignment = Alignment.Center
        ) {
            Text(theme.emoji, fontSize = 32.sp)
        }
        Spacer(Modifier.height(10.dp))
        Text(theme.label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (isSelected) {
            Spacer(Modifier.height(4.dp))
            Icon(
                Icons.Default.CheckCircle,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
