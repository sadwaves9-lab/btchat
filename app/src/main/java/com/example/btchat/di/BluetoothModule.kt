package com.example.btchat.di

import android.bluetooth.BluetoothAdapter
import android.content.Context
import com.example.btchat.utils.BluetoothUtils
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object BluetoothModule {

    @Provides
    @Singleton
    fun provideBluetoothAdapter(@ApplicationContext ctx: Context): BluetoothAdapter? =
        BluetoothUtils.adapter(ctx)
}
