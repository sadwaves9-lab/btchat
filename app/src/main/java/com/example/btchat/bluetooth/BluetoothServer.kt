package com.example.btchat.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.example.btchat.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Accepts incoming RFCOMM connections.
 * One instance runs per BluetoothService.
 */
@SuppressLint("MissingPermission")
class BluetoothServer(
    private val adapter: BluetoothAdapter,
    private val scope: CoroutineScope,
    private val onAccepted: (BluetoothSocket, BluetoothDevice) -> Unit
) {
    private var serverSocket: BluetoothServerSocket? = null
    private var job: Job? = null
    private val tag = "BTServer"

    fun start() {
        job = scope.launch(Dispatchers.IO) {
            try {
                serverSocket = adapter.listenUsingRfcommWithServiceRecord(
                    Constants.SERVICE_NAME,
                    Constants.APP_UUID
                )
                Log.d(tag, "Server socket listening…")

                while (isActive) {
                    val socket: BluetoothSocket = try {
                        serverSocket?.accept() ?: break
                    } catch (e: IOException) {
                        Log.e(tag, "accept() failed", e); break
                    }

                    socket.remoteDevice?.let { dev ->
                        Log.d(tag, "Accepted: ${dev.address}")
                        // Hand off to ConnectionManager via callback
                        onAccepted(socket, dev)
                    } ?: run {
                        runCatching { socket.close() }
                    }
                }
            } catch (e: IOException) {
                Log.e(tag, "Server init failed", e)
            } finally {
                cancel()
            }
        }
    }

    fun cancel() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        job?.cancel()
        job = null
    }
}
