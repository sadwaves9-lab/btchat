package com.example.btchat.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.btchat.ui.theme.AppTheme
import com.example.btchat.ui.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "btchat_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val NICKNAME = stringPreferencesKey("nickname")
        val AVATAR = stringPreferencesKey("avatar")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val READ_RECEIPT = booleanPreferencesKey("read_receipt")
        val TYPING_IND = booleanPreferencesKey("typing_indicator")
        val NOTIF_SOUND = booleanPreferencesKey("notif_sound")
        val NOTIF_VIBRATE = booleanPreferencesKey("notif_vibrate")
        val E2E = booleanPreferencesKey("e2e")
        val MESH = booleanPreferencesKey("mesh")
    }

    val theme: Flow<AppTheme> = context.dataStore.data.map {
        runCatching { AppTheme.valueOf(it[Keys.THEME] ?: "CYBERPUNK") }.getOrDefault(AppTheme.CYBERPUNK)
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map {
        runCatching { ThemeMode.valueOf(it[Keys.THEME_MODE] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM)
    }

    val nickname: Flow<String> = context.dataStore.data.map { it[Keys.NICKNAME] ?: "You" }
    val avatar: Flow<String?> = context.dataStore.data.map { it[Keys.AVATAR] }
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { it[Keys.DYNAMIC] ?: false }
    val readReceipt: Flow<Boolean> = context.dataStore.data.map { it[Keys.READ_RECEIPT] ?: true }
    val typingIndicator: Flow<Boolean> = context.dataStore.data.map { it[Keys.TYPING_IND] ?: true }
    val notificationSound: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIF_SOUND] ?: true }
    val notificationVibrate: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIF_VIBRATE] ?: true }
    val e2eEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.E2E] ?: true }
    val meshEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.MESH] ?: false }

    suspend fun setTheme(t: AppTheme) = context.dataStore.edit { it[Keys.THEME] = t.name }
    suspend fun setThemeMode(m: ThemeMode) = context.dataStore.edit { it[Keys.THEME_MODE] = m.name }
    suspend fun setNickname(n: String) = context.dataStore.edit { it[Keys.NICKNAME] = n }
    suspend fun setAvatar(path: String?) = context.dataStore.edit {
        if (path == null) it.remove(Keys.AVATAR) else it[Keys.AVATAR] = path
    }
    suspend fun setDynamic(v: Boolean) = context.dataStore.edit { it[Keys.DYNAMIC] = v }
    suspend fun setReadReceipt(v: Boolean) = context.dataStore.edit { it[Keys.READ_RECEIPT] = v }
    suspend fun setTypingIndicator(v: Boolean) = context.dataStore.edit { it[Keys.TYPING_IND] = v }
    suspend fun setNotifSound(v: Boolean) = context.dataStore.edit { it[Keys.NOTIF_SOUND] = v }
    suspend fun setNotifVibrate(v: Boolean) = context.dataStore.edit { it[Keys.NOTIF_VIBRATE] = v }
    suspend fun setE2e(v: Boolean) = context.dataStore.edit { it[Keys.E2E] = v }
    suspend fun setMesh(v: Boolean) = context.dataStore.edit { it[Keys.MESH] = v }
}
