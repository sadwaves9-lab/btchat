package com.example.btchat.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

object PermissionUtils {

    const val REQ_BT = 101
    const val REQ_NOTIF = 102
    const val REQ_MEDIA = 103
    const val REQ_AUDIO = 104
    const val REQ_CAMERA = 105

    fun requiredForBluetooth(): Array<String> = BluetoothUtils.requiredPermissions()

    fun requiredForMedia(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO
            )
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun requiredForNotifications(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        else emptyArray()

    fun allGranted(context: Context, perms: Array<String>): Boolean =
        perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    fun request(activity: Activity, perms: Array<String>, code: Int) {
        ActivityCompat.requestPermissions(activity, perms, code)
    }
}
