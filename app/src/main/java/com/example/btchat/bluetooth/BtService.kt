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
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
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

    private val tag = "BtService"
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val adapter: BluetoothAdapter? =
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private var serverJob: Job? = null

    private data class Conn(val socket: BluetoothSocket, var output: OutputStream)

    private val connections = ConcurrentHashMap<String, Conn>()
    private val connecting = ConcurrentHashMap<String, Boolean>()

    private val _incoming = MutableSharedFlow<BtPacket>(extraBufferCapacity = 64)
    val incoming: SharedFlow<BtPacket> = _incoming.asSharedFlow()

    private val _delivered = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val delivered: SharedFlow<String> = _delivered.asSharedFlow()

    private val _read = MutableSharedFlow<String>(extraBufferCapacity = 32)
    val read: SharedFlow<String> = _read.asSharedFlow()

    private val _connectedList = MutableStateFlow<Set<String>>(emptySet())
    val connectedList: StateFlow<Set<String>> = _connectedList.asStateFlow()

    private val _error = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val error: SharedFlow<String> = _error.asSharedFlow()

    private val uuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // ============================================================
    //  SERVER
    // ============================================================
    fun startServer() {
        if (serverJob?.isActive == true) return
        serverJob = scope.launch {
            try {
                val server: BluetoothServerSocket =
                    adapter?.listenUsingRfcommWithServiceRecord("BTChat", uuid)
                        ?: run { Log.e(tag, "Server socket null"); return@launch }
                Log.d(tag, "Server listening…")
                while (currentCoroutineContext().isActive) {
                    try {
                        val socket = server.accept() ?: continue
                        val mac = socket.remoteDevice?.address ?: continue
                        Log.d(tag, "Accepted: $mac")
                        registerConnection(mac, socket)
                    } catch (e: Exception) {
                        Log.e(tag, "accept failed: ${e.message}")
                        delay(500)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Server failed: ${e.message}")
            }
        }
    }

    // ============================================================
    //  CONNECT (with retry + fallback)
    // ============================================================
    fun connect(device: BluetoothDevice) {
        val mac = device.address
        if (connections.containsKey(mac)) {
            Log.d(tag, "Already connected: $mac")
            return
        }
        if (connecting[mac] == true) {
            Log.d(tag, "Already connecting: $mac")
            return
        }

        connecting[mac] = true

        scope.launch {
            try {
                adapter?.cancelDiscovery()
                var success = false

                // Method 1: Normal RFCOMM
                for (attempt in 1..3) {
                    try {
                        Log.d(tag, "Connect attempt $attempt to $mac")
                        val socket = device.createRfcommSocketToServiceRecord(uuid)
                        socket.connect()
                        registerConnection(mac, socket)
                        success = true
                        break
                    } catch (e: Exception) {
                        Log.e(tag, "Attempt $attempt failed: ${e.message}")
                        delay(800)
                    }
                }

                // Method 2: Insecure fallback
                if (!success) {
                    try {
                        Log.d(tag, "Trying insecure fallback…")
                        val socket = device.createInsecureRfcommSocketToServiceRecord(uuid)
                        socket.connect()
                        registerConnection(mac, socket)
                        success = true
                    } catch (e: Exception) {
                        Log.e(tag, "Insecure failed: ${e.message}")
                    }
                }

                if (!success) {
                    _error.tryEmit("Connect failed to $mac")
                }
            } finally {
                connecting.remove(mac)
            }
        }
    }

    // ============================================================
    //  REGISTER + LISTEN
    // ============================================================
    private fun registerConnection(mac: String, socket: BluetoothSocket) {
        try {
            val out = socket.outputStream
            connections[mac] = Conn(socket, out)
            _connectedList.value = connections.keys.toSet()
            Log.d(tag, "Registered $mac, total=${connections.size}")

            // Start listener
            scope.launch { listenLoop(mac, socket) }
        } catch (e: Exception) {
            Log.e(tag, "registerConnection failed: ${e.message}")
        }
    }

    private suspend fun listenLoop(mac: String, socket: BluetoothSocket) {
        val input: InputStream = try { socket.inputStream } catch (e: Exception) {
            removeConnection(mac); return
        }
        val buf = ByteArray(1_000_000)
        val sb = StringBuilder()
        try {
            while (currentCoroutineContext().isActive) {
                val n = input.read(buf)
                if (n <= 0) break
                sb.append(String(buf, 0, n))
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
                    _incoming.tryEmit(BtPacket(mac, parts[1], "${parts[2]}|${parts[3]}|${parts[4]}"))
                }
            }
            "DLV" -> _delivered.tryEmit(parts[1])
            "RD" -> _read.tryEmit(parts[1])
            "PING" -> sendToMac(mac, "PONG||")
        }
    }

    // ============================================================
    //  SEND
    // ============================================================
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
            conn.output.write(packet.toByteArray())
            conn.output.flush()
            true
        } catch (_: Exception) { removeConnection(mac); false }
    }

    private fun sendToMac(mac: String, raw: String) {
        try {
            connections[mac]?.output?.write(raw.toByteArray())
            connections[mac]?.output?.flush()
        } catch (_: Exception) { removeConnection(mac) }
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
            conn.output.write(packet.toByteArray())
            conn.output.flush()
            true
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

    fun isConnected(mac: String): Boolean = connections.containsKey(mac)

    private fun removeConnection(mac: String) {
        try { connections[mac]?.socket?.close() } catch (_: Exception) { }
        connections.remove(mac)
        _connectedList.value = connections.keys.toSet()
        Log.d(tag, "Removed $mac, total=${connections.size}")
    }

    fun disconnect(mac: String) = removeConnection(mac)

    fun stop() {
        connections.keys.toList().forEach { removeConnection(it) }
        serverJob?.cancel()
        serverJob = null
        _connectedList.value = emptySet()
    }
}
