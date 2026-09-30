package com.example.btchat.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE conversationId = :cid AND isDeleted = 0 ORDER BY timestamp ASC")
    fun observeConversation(cid: String): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid AND isDeleted = 0 ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun page(cid: String, limit: Int, offset: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE isStarred = 1 ORDER BY timestamp DESC")
    fun observeStarred(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :cid AND (text LIKE '%' || :q || '%' OR fileName LIKE '%' || :q || '%') ORDER BY timestamp DESC")
    suspend fun search(cid: String, q: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): MessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(m: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(m: List<MessageEntity>)

    @Update
    suspend fun update(m: MessageEntity)

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: String)

    @Query("UPDATE messages SET isStarred = :star WHERE id = :id")
    suspend fun star(id: String, star: Boolean)

    @Query("UPDATE messages SET isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("DELETE FROM messages WHERE conversationId = :cid")
    suspend fun clearConversation(cid: String)

    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :cid AND senderMac != 'me' AND status != 'READ'")
    suspend fun unreadCount(cid: String): Int

    @Query("SELECT * FROM messages WHERE synced = 0 ORDER BY timestamp ASC")
    suspend fun pending(): List<MessageEntity>

    @Query("UPDATE messages SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: String)
}
