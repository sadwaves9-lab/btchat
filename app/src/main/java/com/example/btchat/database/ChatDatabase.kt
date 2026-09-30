package com.example.btchat.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.btchat.utils.Constants

@Database(
    entities = [MessageEntity::class, DeviceEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun messageDao(): MessageDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        const val NAME = Constants.DB_NAME
    }
}
