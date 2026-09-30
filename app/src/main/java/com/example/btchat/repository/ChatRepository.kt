package com.example.btchat.repository

import com.example.btchat.database.ChatDatabase
import com.example.btchat.database.MessageEntity
import com.example.btchat.database.toEntity
import com.example.btchat.database.toModel
import com.example.btchat.model.ChatMessage
import com.example.btchat.model.MessageStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChatRepository @Inject constructor(
    private val db: ChatDatabase
) {
    private val dao = db.messageDao()

    fun observeConversation(cid: String): Flow<List<ChatMessage>> =
        dao.observeConversation(cid).map { list -> list.map { it.toModel() } }

    fun observeStarred(): Flow<List<ChatMessage>> =
        dao.observeStarred().map { list -> list.map { it.toModel() } }

    suspend fun save(message: ChatMessage, cid: String) {
        dao.insert(message.toEntity(cid))
    }

    suspend fun saveAll(messages: List<ChatMessage>, cid: String) {
        dao.insertAll(messages.map { it.toEntity(cid) })
    }

    suspend fun updateStatus(id: String, status: MessageStatus) {
        dao.updateStatus(id, status.name)
    }

    suspend fun star(id: String, star: Boolean) = dao.star(id, star)

    suspend fun softDelete(id: String) = dao.softDelete(id)

    suspend fun hardDelete(id: String) = dao.hardDelete(id)

    suspend fun clearConversation(cid: String) = dao.clearConversation(cid)

    suspend fun search(cid: String, q: String): List<ChatMessage> =
        dao.search(cid, q).map { it.toModel() }

    suspend fun unread(cid: String): Int = dao.unreadCount(cid)

    suspend fun pending(): List<ChatMessage> = dao.pending().map { it.toModel() }

    suspend fun markSynced(id: String) = dao.markSynced(id)

    suspend fun page(cid: String, limit: Int = 50, offset: Int = 0): List<ChatMessage> =
        dao.page(cid, limit, offset).map { it.toModel() }

    companion object {
        /** Stable conversation id between two MACs (sorted, joined). */
        fun conversationId(a: String, b: String): String =
            listOf(a, b).sorted().joinToString("|")
    }
}
