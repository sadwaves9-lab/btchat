package com.example.btchat.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.btchat.model.MessageType
import com.example.btchat.ui.chat.components.ChatInputBar
import com.example.btchat.ui.chat.components.MessageBubble
import com.example.btchat.ui.chat.components.TypingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    deviceMac: String,
    deviceName: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(deviceMac, deviceName) {
        viewModel.setPeer(deviceMac, deviceName)
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty())
            listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(40.dp)
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
                                deviceName.take(1).uppercase(),
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(deviceName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (state.isConnected) Color(0xFF00E676) else Color.Gray
                                        )
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (state.isPeerTyping) "typing…" else if (state.isConnected) "Online" else "Offline",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.Videocam, null) }
                    IconButton(onClick = {}) { Icon(Icons.Default.Call, null) }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            ChatInputBar(
                value = state.input,
                onValueChange = viewModel::updateInput,
                onSend = viewModel::sendMessage,
                onAttach = {},
                onEmoji = {},
                onAI = {},
                onVoice = {}
            )
        }
    ) { padding ->

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(state.messages, key = { it.id }) { msg ->
                if (msg.type == MessageType.SYSTEM) {
                    SystemBubble(msg.text)
                } else {
                    val isSent = msg.senderMac == "me"
                    MessageBubble(message = msg, isSent = isSent, onLongPress = {})
                }
            }
            item {
                AnimatedVisibility(state.isPeerTyping) { TypingIndicator() }
            }
        }
    }
}

@Composable
private fun SystemBubble(text: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        ) {
            Text(
                text = text,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
