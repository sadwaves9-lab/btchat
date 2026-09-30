package com.example.btchat.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.btchat.model.ChatMessage
import com.example.btchat.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ConnectionManager — owns one BluetoothSocket to one remote device.
 *
 * Features:
 *  - Read loop (with 16KB buffer) → decodes packets → callbacks
 *  - Write queue (thread-safe)
 *  - Auto-reconnect with exponential backoff (max 5 tries)
 *  - Keep-alive ping every 5s
 *  - Clean shutdown
 */
@SuppressLint("MissingPermission")
class ConnectionManager private constructor(
    private val context: Context,
    private var socket: BluetoothSocket,
    val device: BluetoothDevice,
    private val scope: CoroutineScope,
    private val onMessage: (mac: String, ChatMessage) -> Unit,
    private val onDisconnected: (mac: String, wasError: Boolean) -> Unit
) {
    private val tag = "ConnMgr/${device.address}"
    private var input: InputStream? = null
    private var output: OutputStream? = null
    private val alive = AtomicBoolean(true)

    private var readJob: Job? = null
    private var pingJob: Job? = null

    val isAlive: Boolean get() = alive.get() && socket.isConnected

    /** Start the read loop (called after successful connect/accept). */
    fun start() {
        try {
            input = socket.inputStream
            output = socket.outputStream
        } catch (e: IOException) {
            Log.e(tag, "Streams failed", e)
            close(); return
        }
        startReadLoop()
        startKeepAlive()
    }

    // ---------- Read loop ----------
    private fun startReadLoop() {
        readJob = scope.launch(Dispatchers.IO) {
            val buffer = ByteArray(Constants.BUFFER_SIZE)
            val acc = PacketAccumulator()

            while (isActive && alive.get()) {
                val n = try {
                    input?.read(buffer) ?: -1
                } catch (e: IOException) {
                    Log.e(tag, "read() failed", e); -1
                }
                if (n <= 0) { handleDrop(); return@launch }

                acc.append(buffer, n)
                var packet: ByteArray?
                while (acc.next()?.also { packet = it } != null) {
                    packet?.let { handlePacket(it) }
                }
            }
        }
    }

    private fun handlePacket(packet: ByteArray) {
        val parsed = PacketCodec.decode(packet, device.address) ?: return
        when (parsed.type) {
            PacketType.PING -> scope.launch { runCatching { send(PacketCodec.pong()) } }
            PacketType.PONG -> { /* latency hook if needed */ }
            PacketType.HANDSHAKE -> {
                // Save remote nickname if present
                Log.d(tag, "Handshake from ${parsed.meta ?: "unknown"}")
            }
            else -> parsed.message?.let { onMessage(device.address, it) }
        }
    }

    // ---------- Keep-alive ----------
    private fun startKeepAlive() {
        pingJob = scope.launch(Dispatchers.IO) {
            while (isActive && alive.get()) {
                delay(Constants.SOCKET_KEEPALIVE_MS)
                runCatching { send(PacketCodec.ping()) }
            }
        }
    }

    // ---------- Send ----------
    @Synchronized
    fun send(bytes: ByteArray) {
        if (!alive.get()) throw IOException("Connection closed")
        val out = output ?: throw IOException("No output stream")
        out.write(bytes)
        out.flush()
    }

    // ---------- Shutdown ----------
    private fun handleDrop() {
        if (!alive.getAndSet(false)) return
        val wasError = true
        cleanup()
        onDisconnected(device.address, wasError)
        attemptReconnect()
    }

    fun close() {
        if (!alive.getAndSet(false)) return
        // Graceful close: try to send a final goodbye packet
        runCatching { send(PacketCodec.goodbye()) }
        cleanup()
        onDisconnected(device.address, false)
    }

    private fun cleanup() {
        readJob?.cancel(); readJob = null
        pingJob?.cancel(); pingJob = null
        runCatching { input?.close() }
        runCatching { output?.close() }
        runCatching { socket.close() }
        input = null; output = null
    }

    // ---------- Reconnect ----------
    private var retryCount = 0
    private fun attemptReconnect() {
        if (!alive.get() && retryCount < Constants.MAX_RETRIES) {
            scope.launch(Dispatchers.IO) {
                retryCount++
                val delayMs = minOf(
                    Constants.BASE_RETRY_DELAY_MS * (1L shl (retryCount - 1)),
                    Constants.MAX_RETRY_DELAY_MS
                )
                Log.d(tag, "Reconnecting in ${delayMs}ms (try $retryCount)")
                delay(delayMs)
                val fresh = connect(context, device, scope, onMessage, onDisconnected)
                if (fresh != null) {
                    socket = fresh.socket
                    input = fresh.input
                    output = fresh.output
                    alive.set(true)
                    retryCount = 0
                    startReadLoop()
                    startKeepAlive()
                } else {
                    attemptReconnect()
                }
            }
        }
    }

    companion object {
        private const val TAG = "ConnMgr"

        /** Client-side connect. Returns null on failure after retries. */
        @SuppressLint("MissingPermission")
        fun connect(
            context: Context,
            device: BluetoothDevice,
            scope: CoroutineScope,
            onMessage: (String, ChatMessage) -> Unit,
            onDisconnected: (String, Boolean) -> Unit
        ): ConnectionManager? {
            var lastError: Exception? = null
            repeat(Constants.MAX_RETRIES) { attempt ->
                try {
                    val socket = device.createRfcommSocketToServiceRecord(Constants.APP_UUID)
                    // Try to cancel discovery if running
                    (context.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)
                        ?.adapter?.cancelDiscovery()

                    socket.connect()
                    val cm = ConnectionManager(context, socket, device, scope, onMessage, onDisconnected)
                    cm.start()
                    Log.d(TAG, "Connected to ${device.address}")
                    return cm
                } catch (e: IOException) {
                    lastError = e
                    Log.w(TAG, "Connect attempt ${attempt + 1} failed", e)
                    // Try the insecure fallback
                    try {
                        val fallback = device.createInsecureRfcommSocketToServiceRecord(Constants.APP_UUID)
                        fallback.connect()
                        val cm = ConnectionManager(context, fallback, device, scope, onMessage, onDisconnected)
                        cm.start()
                        return cm
                    } catch (e2: IOException) {
                        lastError = e2
                        Thread.sleep(Constants.BASE_RETRY_DELAY_MS * (attempt + 1))
                    }
                }
            }
            Log.e(TAG, "All connect attempts failed", lastError)
            return null
        }

        /** Server-side accept wrapper. */
        @SuppressLint("MissingPermission")
        fun fromAccepted(
            context: Context,
            socket: BluetoothSocket,
            device: BluetoothDevice,
            scope: CoroutineScope,
            onMessage: (String, ChatMessage) -> Unit,
            onDisconnected: (String, Boolean) -> Unit
        ): ConnectionManager =
            ConnectionManager(context, socket, device, scope, onMessage, onDisconnected)
    }
}
