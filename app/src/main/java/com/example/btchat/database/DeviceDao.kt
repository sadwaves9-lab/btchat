package com.example.btchat.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY lastSeen DESC")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE isBlocked = 0 ORDER BY lastSeen DESC")
    fun observeAvailable(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE mac = :mac LIMIT 1")
    suspend fun byMac(mac: String): DeviceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(devices: List<DeviceEntity>)

    @Query("UPDATE devices SET nickname = :nick WHERE mac = :mac")
    suspend fun setNickname(mac: String, nick: String?)

    @Query("UPDATE devices SET isBlocked = :blocked WHERE mac = :mac")
    suspend fun setBlocked(mac: String, blocked: Boolean)

    @Query("UPDATE devices SET isFavorite = :fav WHERE mac = :mac")
    suspend fun setFavorite(mac: String, fav: Boolean)

    @Query("UPDATE devices SET unreadCount = :count WHERE mac = :mac")
    suspend fun setUnread(mac: String, count: Int)

    @Query("UPDATE devices SET lastSeen = :time WHERE mac = :mac")
    suspend fun touch(mac: String, time: Long = System.currentTimeMillis())

    @Query("DELETE FROM devices WHERE mac = :mac")
    suspend fun delete(mac: String)
}
