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
import java.util.concurrent.ConcurrentHashMap

@SuppressLint("MissingPermission")
class BtService(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var serverJob: Job? = null

    // Map: MAC address → connection
    private data class Conn(
        val socket: BluetoothSocket,
        var output: OutputStream,
        val job: Job? = null
    )

    private val connections = ConcurrentHashMap<String, Conn>()

    // Incoming messages: <fromMac, text>
    private val _incoming = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 64)
    val incoming: SharedFlow<Pair<String, String>> = _incoming.asSharedFlow()

    private val _delivered = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val delivered: SharedFlow<String> = _delivered.asSharedFlow()

    private val _read = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val read: SharedFlow<String> = _read.asSharedFlow()

    // All currently connected MAC addresses
    private val _connectedList = MutableStateFlow<Set<String>>(emptySet())
    val connectedList: StateFlow<Set<String>> = _connectedList.asStateFlow()

    // For UI backward compat — first connected device
    val connected: StateFlow<String?>
        get() = object : StateFlow<String?> {
            override val value get() = _connectedList.value.firstOrNull()
            override val replayCache get() = listOf(value)
            override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<String?>): Nothing {
                _connectedList.collect { collector.emit(it.firstOrNull()) }
                throw IllegalStateException()
            }
        }

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
                    val mac = socket.remoteDevice?.address ?: continue
                    registerConnection(mac, socket)
                }
            } catch (_: Exception) { }
        }
    }

    fun connect(device: BluetoothDevice) {
        if (connections.containsKey(device.address)) return
        scope.launch {
            try {
                adapter?.cancelDiscovery()
                val socket = device.createRfcommSocketToServiceRecord(uuid)
                socket.connect()
                registerConnection(device.address, socket)
            } catch (_: Exception) { }
        }
    }

    private fun registerConnection(mac: String, socket: BluetoothSocket) {
        val out = socket.outputStream
        val conn = Conn(socket, out)
        connections[mac] = conn
        _connectedList.value = connections.keys.toSet()

        // Start listening
        val job = scope.launch { listenLoop(mac, socket) }
        // fire onConnected callbacks
        onConnectedCallbacks.toList().forEach { it(mac) }
    }

    /**
     * Send to ALL connected devices (group chat).
     */
    fun broadcast(text: String) {
        val packet = "MSG|${System.currentTimeMillis()}|$text\n".toByteArray()
        val macs = connections.keys.toList()
        macs.forEach { mac ->
            try {
                connections[mac]?.output?.write(packet)
                connections[mac]?.output?.flush()
            } catch (_: Exception) {
                removeConnection(mac)
            }
        }
    }

    /**
     * Send to ONE device.
     */
    fun send(mac: String, text: String): Boolean {
        val conn = connections[mac] ?: return false
        return try {
            val packet = "MSG|${System.currentTimeMillis()}|$text\n"
            conn.output.write(packet.toByteArray())
            conn.output.flush()
            true
        } catch (_: Exception) {
            removeConnection(mac)
            false
        }
    }

    fun sendDeliveryReceipt(remoteTs: String) {
        val packet = "DLV|$remoteTs|\n".toByteArray()
        connections.values.forEach {
            try { it.output.write(packet); it.output.flush() } catch (_: Exception) { }
        }
    }

    fun sendReadReceipt(remoteTs: String) {
        val packet = "RD|$remoteTs|\n".toByteArray()
        connections.values.forEach {
            try { it.output.write(packet); it.output.flush() } catch (_: Exception) { }
        }
    }

    private val onConnectedCallbacks = mutableListOf<(String) -> Unit>()
    fun onConnected(cb: (String) -> Unit) { onConnectedCallbacks.add(cb) }

    private suspend fun listenLoop(mac: String, socket: BluetoothSocket) {
        val input: InputStream = socket.inputStream
        val buf = ByteArray(8192)
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
        removeConnection(mac)
    }

    private fun removeConnection(mac: String) {
        try { connections[mac]?.socket?.close() } catch (_: Exception) { }
        connections.remove(mac)
        _connectedList.value = connections.keys.toSet()
    }

    fun disconnect(mac: String) = removeConnection(mac)

    fun stop() {
        connections.keys.toList().forEach { removeConnection(it) }
        serverJob?.cancel()
        _connectedList.value = emptySet()
    }
}
