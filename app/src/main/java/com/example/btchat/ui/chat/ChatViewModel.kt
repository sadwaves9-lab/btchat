package com.example.btchat.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.btchat.bluetooth.BluetoothService
import com.example.btchat.model.ChatMessage
import com.example.btchat.model.MessageStatus
import com.example.btchat.model.MessageType
import com.example.btchat.repository.ChatRepository
import com.example.btchat.repository.DeviceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isConnected: Boolean = false,
    val isPeerTyping: Boolean = false,
    val peerName: String = "",
    val peerMac: String = "",
    val replyingTo: ChatMessage? = null,
    val editing: ChatMessage? = null,
    val searchQuery: String = "",
    val searchResults: List<ChatMessage> = emptyList()
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val app: Application,
    private val chatRepo: ChatRepository,
    private val deviceRepo: DeviceRepository
) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    private var conversationId: String = ""
    private var observeJob: Job? = null
    private var typingJob: Job? = null
    private var lastTypingSent = 0L

    fun setPeer(mac: String, name: String) {
        conversationId = ChatRepository.conversationId("me", mac)
        _state.value = _state.value.copy(peerMac = mac, peerName = name)

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            chatRepo.observeConversation(conversationId).collect { list ->
                _state.value = _state.value.copy(messages = list)
            }
        }

        viewModelScope.launch {
            deviceRepo.touch(mac)
        }
    }

    fun updateInput(text: String) {
        _state.value = _state.value.copy(input = text)
        maybeSendTyping()
    }

    private fun maybeSendTyping() {
        val now = System.currentTimeMillis()
        if (now - lastTypingSent < 1500) return
        lastTypingSent = now
        // Handled by service when connected
    }

    fun sendMessage() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return

        val reply = _state.value.replyingTo
        val msg = ChatMessage(
            senderMac = "me",
            receiverMac = _state.value.peerMac,
            text = text,
            type = MessageType.TEXT,
            status = MessageStatus.SENDING,
            replyToId = reply?.id
        )

        viewModelScope.launch {
            chatRepo.save(msg, conversationId)
            _state.value = _state.value.copy(input = "", replyingTo = null)
            BluetoothService.send(app, msg)
        }
    }

    fun onMessageStatus(id: String, status: MessageStatus) {
        viewModelScope.launch { chatRepo.updateStatus(id, status) }
    }

    fun starMessage(id: String, star: Boolean) {
        viewModelScope.launch { chatRepo.star(id, star) }
    }

    fun deleteMessage(id: String) {
        viewModelScope.launch { chatRepo.softDelete(id) }
    }

    fun setReplyTo(m: ChatMessage?) {
        _state.value = _state.value.copy(replyingTo = m)
    }

    fun setEditing(m: ChatMessage?) {
        _state.value = _state.value.copy(
            editing = m,
            input = m?.text ?: ""
        )
    }

    fun search(q: String) {
        _state.value = _state.value.copy(searchQuery = q)
        if (q.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { chatRepo.search(conversationId, q) }
            _state.value = _state.value.copy(searchResults = r)
        }
    }

    fun clearChat() {
        viewModelScope.launch { chatRepo.clearConversation(conversationId) }
    }

    override fun onCleared() {
        observeJob?.cancel()
        typingJob?.cancel()
        super.onCleared()
    }
}
