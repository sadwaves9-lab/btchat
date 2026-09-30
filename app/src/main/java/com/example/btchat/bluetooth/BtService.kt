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

data class BtPacket(val fromMac: String, val kind: String, val text: String)

@SuppressLint("MissingPermission")
class BtService(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var serverJob: Job? = null

    private data class Conn(val socket: BluetoothSocket, var output: OutputStream)

    private val connections = ConcurrentHashMap<String, Conn>()

    private val _incoming = MutableSharedFlow<BtPacket>(extraBufferCapacity = 64)
    val incoming: SharedFlow<BtPacket> = _incoming.asSharedFlow()

    private val _delivered = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val delivered: SharedFlow<String> = _delivered.asSharedFlow()

    private val _read = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val read: SharedFlow<String> = _read.asSharedFlow()

    private val _connectedList = MutableStateFlow<Set<String>>(emptySet())
    val connectedList: StateFlow<Set<String>> = _connectedList.asStateFlow()

    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

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
        connections[mac] = Conn(socket, out)
        _connectedList.value = connections.keys.toSet()
        scope.launch { listenLoop(mac, socket) }
    }

    // Protocol:
    // MSG|ts|text\n
    // FILE|<kind>|<name>|<size>|<b64>\n
    // DLV|ts|\n
    // RD|ts|\n
    fun broadcast(text: String) {
        val packet = "MSG|${System.currentTimeMillis()}|$text\n".toByteArray()
        connections.keys.toList().forEach { mac ->
            try {
                connections[mac]?.output?.write(packet)
                connections[mac]?.output?.flush()
            } catch (_: Exception) { removeConnection(mac) }
        }
    }

    fun send(mac: String, text: String): Boolean {
        val conn = connections[mac] ?: return false
        return try {
            val packet = "MSG|${System.currentTimeMillis()}|$text\n"
            conn.output.write(packet.toByteArray()); conn.output.flush(); true
        } catch (_: Exception) { removeConnection(mac); false }
    }

    fun broadcastFile(kind: String, name: String, size: Long, base64: String) {
        val packet = "FILE|$kind|$name|$size|$base64\n".toByteArray()
        connections.keys.toList().forEach { mac ->
            try {
                connections[mac]?.output?.write(packet)
                connections[mac]?.output?.flush()
            } catch (_: Exception) { removeConnection(mac) }
        }
    }

    fun sendFile(mac: String, kind: String, name: String, size: Long, base64: String): Boolean {
        val conn = connections[mac] ?: return false
        return try {
            val packet = "FILE|$kind|$name|$size|$base64\n"
            conn.output.write(packet.toByteArray()); conn.output.flush(); true
        } catch (_: Exception) { removeConnection(mac); false }
    }

    fun sendDeliveryReceipt(remoteTs: String) {
        val packet = "DLV|$remoteTs|\n".toByteArray()
        connections.values.forEach { try { it.output.write(packet); it.output.flush() } catch (_: Exception) { } }
    }

    fun sendReadReceipt(remoteTs: String) {
        val packet = "RD|$remoteTs|\n".toByteArray()
        connections.values.forEach { try { it.output.write(packet); it.output.flush() } catch (_: Exception) { } }
    }

    private suspend fun listenLoop(mac: String, socket: BluetoothSocket) {
        val input: InputStream = socket.inputStream
        val buf = ByteArray(1_000_000)  // 1MB for big files
        val sb = StringBuilder()
        try {
            while (currentCoroutineContext().isActive) {
                val n = input.read(buf)
                if (n <= 0) break
                sb.append(String(buf, 0, n))
                // Process complete lines (each ends with \n)
                var idx = sb.indexOf("\n")
                while (idx >= 0) {
                    val line = sb.substring(0, idx)
                    sb.delete(0, idx + 1)
                    handleLine(mac, line)
                    idx = sb.indexOf("\n")
                }
            }
        } catch (_: Exception) { }
        removeConnection(mac)
    }

    private fun handleLine(mac: String, line: String) {
        if (line.isBlank()) return
        val parts = line.split("|", limit = 5)
        if (parts.size < 3) return
        when (parts[0]) {
            "MSG" -> _incoming.tryEmit(BtPacket(mac, "TEXT", parts[2]))
            "FILE" -> {
                if (parts.size >= 5) {
                    // kind|name|size|b64
                    val kind = parts[1]
                    val name = parts[2]
                    val size = parts[3].toLongOrNull() ?: 0L
                    val b64 = parts[4]
                    _incoming.tryEmit(BtPacket(mac, kind, "$name|$size|$b64"))
                }
            }
            "DLV" -> _delivered.tryEmit(parts[1])
            "RD" -> _read.tryEmit(parts[1])
        }
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
