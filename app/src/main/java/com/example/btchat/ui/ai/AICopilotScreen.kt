package com.example.btchat.ui.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AICopilotScreen(
    onBack: () -> Unit,
    viewModel: AICopilotViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty())
            listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.tertiary,
                                            MaterialTheme.colorScheme.primary
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text("AI Copilot", fontWeight = FontWeight.Bold)
                            Text(
                                "Offline • On-device",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                actions = {
                    IconButton(onClick = viewModel::clear) {
                        Icon(Icons.Default.DeleteSweep, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 4.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.input,
                        onValueChange = viewModel::updateInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Ask AI…") },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledIconButton(
                        onClick = viewModel::send,
                        enabled = !state.busy && state.input.isNotBlank()
                    ) {
                        Icon(Icons.Default.Send, null)
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(p),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages) { m ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (m.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        Modifier
                            .widthIn(max = 300.dp)
                            .clip(
                                if (m.isUser)
                                    RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                                else
                                    RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                            )
                            .background(
                                if (m.isUser)
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary
                                        )
                                    )
                                else
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.surfaceVariant,
                                            MaterialTheme.colorScheme.surface
                                        )
                                    )
                            )
                            .padding(12.dp)
                    ) {
                        Text(
                            m.text,
                            color = if (m.isUser) Color.White
                            else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            item {
                AnimatedVisibility(state.busy) {
                    Row(
                        Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Thinking…", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
