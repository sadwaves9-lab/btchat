package com.example.btchat.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

data class ScanDevice(
    val mac: String,
    val name: String,
    val rssi: Int,
    val isPaired: Boolean,
    val btDevice: BluetoothDevice? = null
)

@SuppressLint("MissingPermission")
class BluetoothScanner(private val context: Context) {

    private val tag = "BTScanner"
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private val found = ConcurrentHashMap<String, ScanDevice>()

    private val _devices = MutableStateFlow<List<ScanDevice>>(emptyList())
    val devices: StateFlow<List<ScanDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)

                    val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                    device?.let { addDevice(it, rssi) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                    Log.d(tag, "Discovery finished")
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    else @Suppress("DEPRECATION") intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val rssi = intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                        addDevice(it, rssi)
                    }
                }
            }
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun startScan() {
        if (_isScanning.value) return
        if (!hasPermission()) {
            _error.value = "Permission needed"
            return
        }
        if (adapter?.isEnabled != true) {
            _error.value = "Bluetooth is off"
            return
        }

        _error.value = null
        found.clear()
        _devices.value = emptyList()

        // Add already paired devices first
        adapter.bondedDevices?.forEach { d ->
            addDevice(d, 0)
        }

        val started = adapter.startDiscovery()
        _isScanning.value = started
        Log.d(tag, "Discovery started: $started")
    }

    fun stopScan() {
        try { adapter?.cancelDiscovery() } catch (_: Exception) { }
        _isScanning.value = false
    }

    private fun addDevice(device: BluetoothDevice, rssi: Int) {
        val mac = try { device.address } catch (_: Exception) { return }
        val name = try { device.name } catch (_: Exception) { null } ?: "Unknown"
        val isPaired = try { device.bondState == BluetoothDevice.BOND_BONDED } catch (_: Exception) { false }

        found[mac] = ScanDevice(
            mac = mac,
            name = name,
            rssi = rssi,
            isPaired = isPaired,
            btDevice = device
        )

        _devices.value = found.values.sortedByDescending { it.rssi }
    }

    fun clear() {
        found.clear()
        _devices.value = emptyList()
    }

    fun release() {
        try { adapter?.cancelDiscovery() } catch (_: Exception) { }
        try { context.unregisterReceiver(receiver) } catch (_: Exception) { }
    }
}
