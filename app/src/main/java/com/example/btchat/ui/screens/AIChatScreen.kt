package com.example.btchat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class AIMessage(val text: String, val isUser: Boolean)

/**
 * Offline AI — rule-based + pattern matching for now.
 * Future: plug TFLite / Gemini Nano.
 */
@Composable
fun AIChatScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val messages = remember {
        mutableStateListOf(
            AIMessage("Hi! Main BTChat AI hun. Kuch bhi poochho — translate, summary, jokes, ya coding help.", false)
        )
    }
    var input by remember { mutableStateOf("") }
    var thinking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

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
        // Top bar
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
                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = Color.White)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("AI Chat", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Offline · On-device", color = Color(0xFF10B981), fontSize = 12.sp)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { msg ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                ) {
                    Column(
                        Modifier
                            .widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (msg.isUser)
                                    Brush.linearGradient(listOf(Color(0xFF3B82F6), Color(0xFF06B6D4)))
                                else Brush.linearGradient(listOf(Color(0x25FFFFFF), Color(0x15FFFFFF)))
                            )
                            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            msg.text,
                            color = if (msg.isUser) Color.White else Color(0xFFF5F5FA),
                            fontSize = 15.sp
                        )
                    }
                }
            }
            if (thinking) {
                item {
                    Row(Modifier.padding(8.dp)) {
                        Text("AI is typing…", color = Color(0xFF8696A0), fontSize = 13.sp)
                    }
                }
            }
        }

        // Suggestions
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SuggestionChip("Translate") { input = "Translate hello to hindi" }
            SuggestionChip("Summary") { input = "Summarize: BTChat is an offline Bluetooth messenger app" }
            SuggestionChip("Joke") { input = "Tell me a joke" }
        }

        // Input
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0x30FFFFFF))
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x20FFFFFF))
                    .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = input,
                    onValueChange = { input = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                    decorationBox = { inner ->
                        if (input.isEmpty()) {
                            Text("Ask AI anything…", color = Color(0xFF8696A0), fontSize = 15.sp)
                        }
                        inner()
                    }
                )
            }
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(48.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF06B6D4))))
                    .clickable {
                        val t = input.trim()
                        if (t.isEmpty() || thinking) return@clickable
                        messages.add(AIMessage(t, true))
                        input = ""
                        thinking = true
                        scope.launch {
                            delay(700)
                            val reply = generateAIReply(t)
                            messages.add(AIMessage(reply, false))
                            thinking = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0x20FFFFFF))
            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(50))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color(0xFFE9EDEF), fontSize = 12.sp)
    }
}

private fun generateAIReply(text: String): String {
    val t = text.lowercase()
    return when {
        "translate" in t -> {
            if ("hindi" in t) "Hello = नमस्ते"
            else if ("english" in t) "Namaste = Hello"
            else "Kis bhasha me translate karna hai? Format: translate <text> to <language>"
        }
        "summarize" in t || "summary" in t -> {
            val content = text.substringAfter(":", "").trim().ifEmpty { text }
            "Summary: ${content.take(120)}…"
        }
        "joke" in t -> listOf(
            "Why do programmers prefer dark mode? Because light attracts bugs. 😄",
            "Programmer: It works on my machine. QA: Then we'll ship your machine. 😅",
            "There are 10 types of people: those who understand binary and those who don't. 🤓"
        ).random()
        "hi" in t || "hello" in t || "hey" in t -> "Hello! Kaise ho? Kya help chahiye?"
        "time" in t -> "Abhi time: ${java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
        "date" in t -> "Aaj: ${java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date())}"
        "your name" in t -> "Main BTChat AI hun — offline, on-device. Koi data bahar nahi jaata."
        "who are you" in t -> "Main BTChat AI hun — offline, on-device. Koi data bahar nahi jaata."
        "kya kar sakte" in t || "features" in t ->
            "Main ye kar sakta hun:\n• Translate (hindi/english)\n• Summarize text\n• Joke sunao\n• Time/date batao\n• Basic coding help"
        "code" in t || "kotlin" in t ->
            "Kotlin example:\n```\nfun main() {\n    println(\"Hello BTChat\")\n}\n```"
        else -> "Interesting! Ye offline AI hai — simple rules use karti hai. Advanced model aane wala hai. Try: translate, summarize, joke"
    }
}
