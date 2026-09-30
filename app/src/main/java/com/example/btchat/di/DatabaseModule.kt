package com.example.btchat.di

import android.content.Context
import androidx.room.Room
import com.example.btchat.database.ChatDatabase
import com.example.btchat.database.DeviceDao
import com.example.btchat.database.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDb(@ApplicationContext ctx: Context): ChatDatabase =
        Room.databaseBuilder(ctx, ChatDatabase::class.java, ChatDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideMessageDao(db: ChatDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideDeviceDao(db: ChatDatabase): DeviceDao = db.deviceDao()
}
