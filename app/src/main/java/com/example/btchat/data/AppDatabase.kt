package com.example.btchat.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE deviceMac = :mac ORDER BY timestamp ASC")
    fun messagesFor(mac: String): Flow<List<Message>>

    @Insert
    suspend fun insert(message: Message): Long

    @Query("UPDATE messages SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("UPDATE messages SET status = :status, pendingSend = 0 WHERE pendingSend = 1 AND deviceMac = :mac")
    suspend fun markAllSent(mac: String, status: String)

    @Query("UPDATE messages SET status = :status WHERE deviceMac = :mac AND isSent = 1 AND status != :readStatus")
    suspend fun markAllRead(mac: String, status: String, readStatus: String)

    @Query("SELECT * FROM messages WHERE pendingSend = 1 AND deviceMac = :mac ORDER BY timestamp ASC")
    suspend fun pendingFor(mac: String): List<Message>
}

@Database(entities = [Message::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "btchat.db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
    }
}
