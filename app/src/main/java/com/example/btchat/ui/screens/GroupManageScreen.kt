package com.example.btchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Groups
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

@Composable
fun GroupManageScreen(
    allDevices: List<Pair<String, String>>,
    initialSelected: Set<String>,
    onBack: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var selected by remember { mutableStateOf(initialSelected) }

    Column(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF0A0A15), Color(0xFF14142A), Color(0xFF0A0A15))
                )
            )
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0x30FFFFFF))
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = Color.White)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Groups, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Group Members", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("${selected.size} selected", color = Color(0xFF10B981), fontSize = 12.sp)
            }
            TextButton(onClick = { onSave(selected) }) {
                Text("SAVE", color = Color(0xFF10B981), fontWeight = FontWeight.Bold)
            }
        }

        Spacer(Modifier.height(8.dp))

        if (allDevices.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Groups, null, tint = Color(0xFF8696A0), modifier = Modifier.size(60.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("No paired devices", color = Color.White, fontWeight = FontWeight.Bold)
                    Text("Pair Bluetooth devices first", color = Color(0xFF8696A0), fontSize = 13.sp)
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(allDevices) { (mac, name) ->
                    val isSelected = selected.contains(mac)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isSelected)
                                    Brush.linearGradient(listOf(Color(0x358B5CF6), Color(0x25EC4899)))
                                else Brush.linearGradient(listOf(Color(0x20FFFFFF), Color(0x10FFFFFF)))
                            )
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF8B5CF6) else Color(0x20FFFFFF),
                                RoundedCornerShape(16.dp)
                            )
                            .clickable {
                                selected = if (isSelected) selected - mac else selected + mac
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected)
                                        Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFFEC4899)))
                                    else Brush.linearGradient(listOf(Color(0xFF2A2A40), Color(0xFF1A1A30)))
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(mac, color = Color(0xFF8696A0), fontSize = 11.sp)
                        }
                        if (isSelected) {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).background(Color(0xFF10B981)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        } else {
                            Box(
                                Modifier.size(26.dp).clip(CircleShape).border(2.dp, Color(0x40FFFFFF), CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}
