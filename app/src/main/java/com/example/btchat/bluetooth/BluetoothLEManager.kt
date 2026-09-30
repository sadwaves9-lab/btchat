package com.example.btchat.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.example.btchat.utils.Constants

/**
 * BluetoothLEManager — lightweight discovery + advertising.
 *
 * Used only for FAST discovery. Actual data still goes over RFCOMM.
 */
@SuppressLint("MissingPermission")
class BluetoothLEManager(private val context: Context) {

    private val tag = "BLEManager"
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val scanner: BluetoothLeScanner? get() = adapter?.bluetoothLeScanner
    private val advertiser: BluetoothLeAdvertiser? get() = adapter?.bluetoothLeAdvertiser

    private var onFound: ((ScanResult) -> Unit)? = null

    fun startScan(onFound: (ScanResult) -> Unit) {
        this.onFound = onFound
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        runCatching { scanner?.startScan(null, settings, callback) }
            .onFailure { Log.e(tag, "startScan failed", it) }
    }

    fun stopScan() {
        runCatching { scanner?.stopScan(callback) }
    }

    fun startAdvertising(deviceName: String) {
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(Constants.APP_UUID))
            .build()

        runCatching { advertiser?.startAdvertising(settings, data, advertiseCallback) }
            .onFailure { Log.e(tag, "startAdvertising failed", it) }
    }

    fun stopAdvertising() {
        runCatching { advertiser?.stopAdvertising(advertiseCallback) }
    }

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.let { onFound?.invoke(it) }
        }
        override fun onScanFailed(errorCode: Int) {
            Log.e(tag, "onScanFailed: $errorCode")
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            Log.d(tag, "BLE advertising started")
        }
        override fun onStartFailure(errorCode: Int) {
            Log.e(tag, "BLE advertising failed: $errorCode")
        }
    }
}
