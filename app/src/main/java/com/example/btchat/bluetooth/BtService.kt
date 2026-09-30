package com.example.btchat.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")
class BtService(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var serverJob: Job? = null
    private var connectedSocket: BluetoothSocket? = null
    private var output: OutputStream? = null

    // Pair<String, String> = <mac, text>
    private val _incoming = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 32)
    val incoming: SharedFlow<Pair<String, String>> = _incoming.asSharedFlow()

    // Delivery receipts: message delivered to other phone
    private val _delivered = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val delivered: SharedFlow<String> = _delivered.asSharedFlow()

    // Read receipts from other phone
    private val _read = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val read: SharedFlow<String> = _read.asSharedFlow()

    private val _connected = MutableStateFlow<String?>(null)
    val connected: StateFlow<String?> = _connected.asStateFlow()

    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    fun hasPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun startServer() {
        serverJob?.cancel()
        serverJob = scope.launch {
            try {
                val server: BluetoothServerSocket =
                    adapter?.listenUsingRfcommWithServiceRecord("BTChat", uuid) ?: return@launch
                while (currentCoroutineContext().isActive) {
                    val socket = server.accept() ?: continue
                    connectedSocket = socket
                    output = socket.outputStream
                    _connected.value = socket.remoteDevice?.address
                    listenLoop(socket)
                }
            } catch (_: Exception) { }
        }
    }

    fun connect(device: BluetoothDevice) {
        scope.launch {
            try {
                adapter?.cancelDiscovery()
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                connectedSocket = socket
                output = socket.outputStream
                _connected.value = device.address
                // Send any pending messages for this device
                onConnectedCallbacks.forEach { it(device.address) }
                listenLoop(socket)
            } catch (_: Exception) {
                _connected.value = null
            }
        }
    }

    fun send(text: String): Boolean {
        val out = output ?: return false
        return try {
            // Protocol: "MSG|<timestamp>|<text>"
            val packet = "MSG|${System.currentTimeMillis()}|$text\n"
            out.write(packet.toByteArray())
            out.flush()
            true
        } catch (_: Exception) {
            false
        }
    }

    fun sendDeliveryReceipt(remoteTs: String) {
        scope.launch {
            try {
                val out = output ?: return@launch
                val packet = "DLV|$remoteTs|\n"
                out.write(packet.toByteArray())
                out.flush()
            } catch (_: Exception) { }
        }
    }

    fun sendReadReceipt(remoteTs: String) {
        scope.launch {
            try {
                val out = output ?: return@launch
                val packet = "RD|$remoteTs|\n"
                out.write(packet.toByteArray())
                out.flush()
            } catch (_: Exception) { }
        }
    }

    // Callback on reconnect: send pending messages
    private val onConnectedCallbacks = mutableListOf<(String) -> Unit>()
    fun onConnected(cb: (String) -> Unit) { onConnectedCallbacks.add(cb) }

    private suspend fun listenLoop(socket: BluetoothSocket) {
        val input: InputStream = socket.inputStream
        val buf = ByteArray(8192)
        val mac = socket.remoteDevice?.address ?: ""
        try {
            while (currentCoroutineContext().isActive) {
                val n = input.read(buf)
                if (n <= 0) break
                val raw = String(buf, 0, n)
                raw.split("\n").forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split("|", limit = 4)
                    if (parts.size < 3) return@forEach
                    when (parts[0]) {
                        "MSG" -> _incoming.tryEmit(mac to parts[2])
                        "DLV" -> _delivered.tryEmit(parts[1])
                        "RD" -> _read.tryEmit(parts[1])
                    }
                }
            }
        } catch (_: Exception) { }
        _connected.value = null
    }

    fun stop() {
        try { connectedSocket?.close() } catch (_: Exception) { }
        connectedSocket = null
        output = null
        serverJob?.cancel()
        _connected.value = null
    }
}
