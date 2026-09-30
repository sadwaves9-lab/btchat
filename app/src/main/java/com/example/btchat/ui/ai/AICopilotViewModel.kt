package com.example.btchat.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AIMessage(val text: String, val isUser: Boolean, val ts: Long = System.currentTimeMillis())

data class AICopilotState(
    val messages: List<AIMessage> = listOf(
        AIMessage(
            "Hi! I can translate, summarize and suggest replies — all offline. Try:\n" +
                "• translate: <text> to hindi\n" +
                "• summarize: <long text>\n" +
                "• reply: <incoming message>",
            isUser = false
        )
    ),
    val input: String = "",
    val busy: Boolean = false
)

@HiltViewModel
class AICopilotViewModel @Inject constructor() : ViewModel() {

    private val _state = MutableStateFlow(AICopilotState())
    val state: StateFlow<AICopilotState> = _state.asStateFlow()

    fun updateInput(v: String) {
        _state.value = _state.value.copy(input = v)
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return
        val history = _state.value.messages + AIMessage(text, true)
        _state.value = _state.value.copy(messages = history, input = "", busy = true)

        viewModelScope.launch {
            val reply = when {
                text.startsWith("translate:", true) -> {
                    val body = text.substringAfter(":").trim()
                    val target = when {
                        "hindi" in body.lowercase() -> "hi"
                        "urdu" in body.lowercase() -> "ur"
                        "arabic" in body.lowercase() -> "ar"
                        "spanish" in body.lowercase() -> "es"
                        "french" in body.lowercase() -> "fr"
                        else -> "hi"
                    }
                    val cleaned = body.replace(Regex("to\\s+\\w+$"), "").trim()
                    runCatching { AITranslate.translate(cleaned, target) }
                        .getOrDefault("Translation failed")
                }
                text.startsWith("summarize:", true) -> {
                    val body = text.substringAfter(":").trim()
                    AISmartReply.summarize(body)
                }
                text.startsWith("reply:", true) -> {
                    val body = text.substringAfter(":").trim()
                    AISmartReply.suggest(body).joinToString("\n") { "• $it" }
                }
                else -> "Try:\n• translate: hello to hindi\n• summarize: <text>\n• reply: <msg>"
            }
            _state.value = _state.value.copy(
                messages = _state.value.messages + AIMessage(reply, false),
                busy = false
            )
        }
    }

    fun clear() {
        _state.value = AICopilotState()
    }
}
