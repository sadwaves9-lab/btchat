package com.example.btchat.data

import android.content.Context
import android.content.SharedPreferences

object DeviceAlias {
    private const val PREF = "device_aliases"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun get(ctx: Context, mac: String): String? = prefs(ctx).getString(mac, null)

    fun set(ctx: Context, mac: String, name: String) {
        prefs(ctx).edit().putString(mac, name).apply()
    }

    fun remove(ctx: Context, mac: String) {
        prefs(ctx).edit().remove(mac).apply()
    }

    fun displayName(ctx: Context, mac: String, fallback: String): String =
        get(ctx, mac) ?: fallback
}
