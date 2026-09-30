package com.example.btchat.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BTChatTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                subtitle?.let {
                    Text(
                        it,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        navigationIcon = {
            onBack?.let {
                IconButton(onClick = it) { Icon(Icons.Default.ArrowBack, null) }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}
