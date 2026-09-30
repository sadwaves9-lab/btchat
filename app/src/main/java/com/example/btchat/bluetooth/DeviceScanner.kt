package com.example.btchat.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.btchat.model.Device
import com.example.btchat.model.DeviceState
import com.example.btchat.utils.BluetoothUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * DeviceScanner — discovers nearby BT Classic + BLE devices.
 *
 * Emits a live list of Devices.
 */
@SuppressLint("MissingPermission")
class DeviceScanner(private val context: Context) {

    private val tag = "DeviceScanner"
    private val adapter: BluetoothAdapter? = BluetoothUtils.adapter(context)

    private val found = ConcurrentHashMap<String, Device>()

    private val _devices = MutableStateFlow<List<Device>>(emptyList())
    val devices: StateFlow<List<Device>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val handler = Handler(Looper.getMainLooper())

    // ---------- Classic discovery receiver ----------
    private val classicReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val dev = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE)
                    dev?.let { addDevice(it, rssi) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> stopInternal()
            }
        }
    }

    // ---------- BLE callback ----------
    private val bleCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            addDevice(result.device, result.rssi.toShort())
        }
    }

    // ---------- Public API ----------
    fun start() {
        if (_isScanning.value) return
        found.clear(); _devices.value = emptyList()

        // Register receiver
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(classicReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(classicReceiver, filter)
        }

        // Classic discovery
        runCatching { adapter?.startDiscovery() }

        // BLE discovery (fast)
        runCatching {
            adapter?.bluetoothLeScanner?.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                bleCallback
            )
        }

        _isScanning.value = true

        // Auto-stop after 20s
        handler.postDelayed({ stop() }, 20_000L)
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        runCatching { adapter?.cancelDiscovery() }
        runCatching { adapter?.bluetoothLeScanner?.stopScan(bleCallback) }
        runCatching { context.unregisterReceiver(classicReceiver) }
        stopInternal()
    }

    private fun stopInternal() {
        _isScanning.value = false
    }

    // ---------- Add ----------
    private fun addDevice(btDevice: BluetoothDevice, rssi: Short) {
        val mac = btDevice.address ?: return
        val name = try { btDevice.name ?: "Unknown" } catch (_: SecurityException) { "Unknown" }
        val strength = if (rssi == Short.MIN_VALUE) 2 else BluetoothUtils.rssiToBars(rssi)
        val state = if (btDevice.bondState == BluetoothDevice.BOND_BONDED) DeviceState.PAIRED else DeviceState.AVAILABLE

        val device = Device(
            name = name, mac = mac, state = state, signalStrength = strength,
            lastSeen = System.currentTimeMillis()
        )
        found[mac] = device
        _devices.value = found.values.sortedByDescending { it.signalStrength }
    }

    fun clear() { found.clear(); _devices.value = emptyList() }
}
