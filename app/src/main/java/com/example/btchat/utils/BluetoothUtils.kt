package com.example.btchat.utils

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Bluetooth helpers — safe, permission-aware.
 */
object BluetoothUtils {

    fun adapter(context: Context): BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    fun isBluetoothOn(context: Context): Boolean =
        adapter(context)?.isEnabled == true

    fun isBluetoothSupported(context: Context): Boolean =
        adapter(context) != null

    fun pairedDevices(context: Context): Set<BluetoothDevice> {
        if (!hasConnectPermission(context)) return emptySet()
        return adapter(context)?.bondedDevices ?: emptySet()
    }

    fun deviceName(device: BluetoothDevice): String {
        return try {
            if (hasConnectPermission(null)) device.name ?: "Unknown" else "Unknown"
        } catch (_: SecurityException) { "Unknown" }
    }

    fun safeMac(device: BluetoothDevice): String = try { device.address } catch (_: Exception) { "" }

    // ---------- Permissions ----------
    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADVERTISE
        )
    } else {
        arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )
    }

    fun hasConnectPermission(context: Context?): Boolean {
        val ctx = context ?: return true
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        return ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun hasScanPermission(context: Context): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    /** Map RSSI to 1..4 bars */
    fun rssiToBars(rssi: Short): Int = when {
        rssi >= -55 -> 4
        rssi >= -70 -> 3
        rssi >= -85 -> 2
        else -> 1
    }
}
