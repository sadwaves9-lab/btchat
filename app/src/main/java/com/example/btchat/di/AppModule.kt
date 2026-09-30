package com.example.btchat.di

import android.content.Context
import com.example.btchat.bluetooth.BluetoothService
import com.example.btchat.bluetooth.DeviceScanner
import com.example.btchat.bluetooth.FileTransferManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDeviceScanner(@ApplicationContext ctx: Context): DeviceScanner =
        DeviceScanner(ctx)

    @Provides
    @Singleton
    fun provideFileTransferManager(@ApplicationContext ctx: Context): FileTransferManager =
        FileTransferManager(ctx) { _, _ -> /* wired in service */ }
}
