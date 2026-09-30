package com.example.btchat.repository

import com.example.btchat.database.ChatDatabase
import com.example.btchat.database.DeviceEntity
import com.example.btchat.database.toEntity
import com.example.btchat.database.toModel
import com.example.btchat.model.Device
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceRepository @Inject constructor(
    private val db: ChatDatabase
) {
    private val dao = db.deviceDao()

    fun observeAll(): Flow<List<Device>> =
        dao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeAvailable(): Flow<List<Device>> =
        dao.observeAvailable().map { list -> list.map { it.toModel() } }

    suspend fun save(device: Device) = dao.upsert(device.toEntity())

    suspend fun saveAll(devices: List<Device>) = dao.upsertAll(devices.map { it.toEntity() })

    suspend fun byMac(mac: String): Device? = dao.byMac(mac)?.toModel()

    suspend fun setNickname(mac: String, nick: String?) = dao.setNickname(mac, nick)

    suspend fun setBlocked(mac: String, blocked: Boolean) = dao.setBlocked(mac, blocked)

    suspend fun setFavorite(mac: String, fav: Boolean) = dao.setFavorite(mac, fav)

    suspend fun setUnread(mac: String, count: Int) = dao.setUnread(mac, count)

    suspend fun touch(mac: String) = dao.touch(mac)

    suspend fun delete(mac: String) = dao.delete(mac)
}
