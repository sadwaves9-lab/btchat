package com.example.btchat.bluetooth

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.btchat.BTChatApp
import com.example.btchat.R
import com.example.btchat.model.ChatMessage
import com.example.btchat.model.MessageStatus
import com.example.btchat.model.MessageType
import com.example.btchat.utils.BluetoothUtils
import com.example.btchat.utils.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * BluetoothService — the heartbeat of BTChat.
 *
 * Responsibilities:
 *  - Runs as a foreground service (never gets killed)
 *  - Owns the BluetoothServerSocket + per-device ConnectionManager
 *  - Broadcasts incoming messages via SharedFlow
 *  - Handles multi-device connections (map: mac -> ConnectionManager)
 *  - Auto-restarts server after Bluetooth toggles
 *
 * Public API:
 *  - startServer()
 *  - connectTo(mac)
 *  - sendMessage(message)
 *  - disconnect(mac)
 *  - disconnectAll()
 */
@SuppressLint("MissingPermission")
class BluetoothService : Service() {

    private val tag = "BTService"

    // ---------- Binder ----------
    inner class LocalBinder : Binder() {
        fun getService(): BluetoothService = this@BluetoothService
    }
    private val binder = LocalBinder()

    // ---------- Core ----------
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var adapter: BluetoothAdapter
    private var server: BluetoothServer? = null

    /** Live connections: mac -> ConnectionManager */
    private val connections = ConcurrentHashMap<String, ConnectionManager>()

    /** Incoming + outgoing message stream */
    private val _messages = MutableSharedFlow<ChatMessage>(replay = 0, extraBufferCapacity = 64)
    val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    /** Service state */
    private val _state = MutableStateFlow(ServiceState.IDLE)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    /** Connected device macs */
    private val _connected = MutableStateFlow<Set<String>>(emptySet())
    val connected: StateFlow<Set<String>> = _connected.asStateFlow()

    // ---------- Lifecycle ----------
    override fun onCreate() {
        super.onCreate()
        adapter = BluetoothUtils.adapter(this)
            ?: throw IllegalStateException("No Bluetooth adapter")
        registerBtReceiver()
        startForegroundInternal()
        startServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CONNECT -> {
                intent.getStringExtra(EXTRA_MAC)?.let { connectTo(it) }
            }
            ACTION_DISCONNECT -> {
                intent.getStringExtra(EXTRA_MAC)?.let { disconnect(it) }
            }
            ACTION_SEND -> {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra<ChatMessage>(EXTRA_MESSAGE)?.let { sendMessage(it) }
            }
            ACTION_STOP -> {
                disconnectAll()
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        runCatching { unregisterReceiver(btReceiver) }
        scope.cancel()
        disconnectAll()
        server?.cancel()
        server = null
    }

    // ---------- Server ----------
    fun startServer() {
        if (server != null) return
        _state.value = ServiceState.LISTENING
        server = BluetoothServer(adapter, scope) { socket, device ->
            Log.d(tag, "Incoming connection from ${device.address}")
            val cm = ConnectionManager(this, socket, device, scope, ::onMessage, ::onDisconnected)
            connections[device.address] = cm
            updateConnected()
            cm.start()
        }.also { it.start() }
    }

    // ---------- Client ----------
    fun connectTo(mac: String) {
        val device: BluetoothDevice = try {
            adapter.getRemoteDevice(mac)
        } catch (e: IllegalArgumentException) {
            Log.e(tag, "Invalid MAC: $mac"); return
        }

        if (connections.containsKey(mac) && connections[mac]?.isAlive == true) return

        _state.value = ServiceState.CONNECTING
        scope.launch {
            val cm = ConnectionManager.connect(this@BluetoothService, device, scope, ::onMessage, ::onDisconnected)
            if (cm != null) {
                connections[mac] = cm
                updateConnected()
                _state.value = ServiceState.CONNECTED
                // Send handshake
                cm.send(buildHandshake())
            } else {
                _state.value = ServiceState.ERROR
            }
        }
    }

    // ---------- Send ----------
    fun sendMessage(message: ChatMessage) {
        val target = message.receiverMac
        val cm = connections[target] ?: run {
            Log.w(tag, "No connection to $target")
            emitFailed(message)
            return
        }
        scope.launch {
            try {
                cm.send(PacketCodec.encode(message))
                _messages.emit(message.copy(status = MessageStatus.SENT))
            } catch (e: IOException) {
                emitFailed(message)
            }
        }
    }

    fun broadcast(message: ChatMessage) {
        scope.launch {
            connections.values.forEach { cm ->
                runCatching { cm.send(PacketCodec.encode(message)) }
            }
            _messages.emit(message.copy(status = MessageStatus.SENT))
        }
    }

    // ---------- Disconnect ----------
    fun disconnect(mac: String) {
        connections.remove(mac)?.close()
        updateConnected()
    }

    fun disconnectAll() {
        connections.values.forEach { runCatching { it.close() } }
        connections.clear()
        updateConnected()
        _state.value = ServiceState.IDLE
    }

    // ---------- Incoming ----------
    private fun onMessage(mac: String, message: ChatMessage) {
        scope.launch {
            _messages.emit(message)
            // Auto read-receipt for received text
            if (message.type == MessageType.TEXT) {
                connections[mac]?.send(PacketCodec.readReceipt(message.id))
            }
        }
    }

    private fun onDisconnected(mac: String, wasError: Boolean) {
        connections.remove(mac)
        updateConnected()
        if (connections.isEmpty()) _state.value = ServiceState.LISTENING
    }

    private fun updateConnected() {
        _connected.value = connections.keys.toSet()
    }

    private fun emitFailed(message: ChatMessage) {
        scope.launch { _messages.emit(message.copy(status = MessageStatus.FAILED)) }
    }

    // ---------- Handshake ----------
    private fun buildHandshake(): ByteArray =
        PacketCodec.handshake(Build.MODEL ?: "Android", Constants.PROTOCOL_VERSION)

    // ---------- Foreground notification ----------
    private fun startForegroundInternal() {
        val notif = buildServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Constants.NOTIF_ID_SERVICE,
                notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
            )
        } else {
            startForeground(Constants.NOTIF_ID_SERVICE, notif)
        }
    }

    private fun buildServiceNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent().setClassName(packageName, "com.example.btchat.ui.MainActivity"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, BTChatApp.CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_btchat_logo)
            .setContentTitle("BTChat running")
            .setContentText("Ready for offline Bluetooth chat")
            .setContentIntent(open)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    // ---------- BT state receiver ----------
    private val btReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
                    when (state) {
                        BluetoothAdapter.STATE_OFF -> {
                            disconnectAll()
                            server?.cancel(); server = null
                            _state.value = ServiceState.IDLE
                        }
                        BluetoothAdapter.STATE_ON -> startServer()
                    }
                }
            }
        }
    }

    private fun registerBtReceiver() {
        val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(btReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(btReceiver, filter)
        }
    }

    // ---------- State machine ----------
    enum class ServiceState { IDLE, LISTENING, CONNECTING, CONNECTED, ERROR }

    companion object {
        const val ACTION_CONNECT = "com.example.btchat.CONNECT"
        const val ACTION_DISCONNECT = "com.example.btchat.DISCONNECT"
        const val ACTION_SEND = "com.example.btchat.SEND"
        const val ACTION_STOP = "com.example.btchat.STOP"
        const val EXTRA_MAC = "extra_mac"
        const val EXTRA_MESSAGE = "extra_message"

        fun start(context: Context) {
            val i = Intent(context, BluetoothService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun connect(context: Context, mac: String) {
            val i = Intent(context, BluetoothService::class.java).apply {
                action = ACTION_CONNECT; putExtra(EXTRA_MAC, mac)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun send(context: Context, message: ChatMessage) {
            val i = Intent(context, BluetoothService::class.java).apply {
                action = ACTION_SEND; putExtra(EXTRA_MESSAGE, message)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(i)
            else context.startService(i)
        }

        fun stop(context: Context) {
            val i = Intent(context, BluetoothService::class.java).apply { action = ACTION_STOP }
            context.startService(i)
        }
    }
}
