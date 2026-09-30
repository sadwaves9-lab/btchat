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

    private val _incoming = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 16)
    val incoming: SharedFlow<Pair<String, String>> = _incoming.asSharedFlow()

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
                listenLoop(socket)
            } catch (_: Exception) {
                _connected.value = null
            }
        }
    }

    fun send(text: String) {
        scope.launch {
            try {
                output?.write(text.toByteArray())
                output?.flush()
            } catch (_: Exception) { }
        }
    }

    private suspend fun listenLoop(socket: BluetoothSocket) {
        val input: InputStream = socket.inputStream
        val buf = ByteArray(4096)
        try {
            while (currentCoroutineContext().isActive) {
                val n = input.read(buf)
                if (n <= 0) break
                val text = String(buf, 0, n)
                val mac = socket.remoteDevice?.address ?: ""
                _incoming.tryEmit(mac to text)
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
