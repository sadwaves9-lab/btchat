package com.example.btchat.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.bluetooth.BtService
import com.example.btchat.data.AppDatabase
import com.example.btchat.data.Message
import com.example.btchat.data.MsgStatus
import com.example.btchat.notif.Notifier
import com.example.btchat.update.UpdateChecker
import com.example.btchat.update.UpdateDialog
import com.example.btchat.update.UpdateInfo
import com.example.btchat.update.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// WhatsApp Colors
val BgDark = Color(0xFF0B141A)          // chat background
val BgTopBar = Color(0xFF1F2C34)        // top bar / bubble received
val BubbleSent = Color(0xFF005C4B)      // sent bubble (green)
val BubbleReceived = Color(0xFF1F2C34)  // received bubble (grey)
val TextPrimary = Color(0xFFE9EDEF)
val TextSecondary = Color(0xFF8696A0)
val TickRead = Color(0xFF53BDEB)
val Accent = Color(0xFF00A884)          // WhatsApp green

class MainActivity : ComponentActivity() {

    private lateinit var bt: BtService
    private val notifPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.init(this)
        bt = BtService(this)

        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            perms.add(Manifest.permission.BLUETOOTH_SCAN)
            perms.add(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permLauncher.launch(perms.toTypedArray())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = BgDark, surface = BgTopBar)) {
                Surface(color = BgDark) {
                    App(bt)
                }
            }
        }
    }

    override fun onDestroy() {
        bt.stop()
        super.onDestroy()
    }
}

fun getVersionCode(context: Context): Int = try {
    val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) pInfo.longVersionCode.toInt()
    else @Suppress("DEPRECATION") pInfo.versionCode
} catch (_: Exception) { 1 }

@SuppressLint("MissingPermission")
@Composable
fun App(bt: BtService) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf("home") }
    var selectedMac by remember { mutableStateOf<String?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    val connectedMac by bt.connected.collectAsState()

    val devices = remember { mutableStateListOf<Pair<String, String>>() }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        bt.startServer()
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE)
            as? BluetoothManager)?.adapter
        adapter?.bondedDevices?.forEach { d ->
            devices.add(d.address to (d.name ?: "Unknown"))
        }
    }

    // OTA
    LaunchedEffect(Unit) {
        val info = UpdateChecker.check(getVersionCode(ctx))
        if (info != null) updateInfo = info
    }

    // Incoming messages → save + notify + send delivery
    LaunchedEffect(Unit) {
        bt.incoming.collect { pair ->
            val mac = pair.first
            val text = pair.second
            scope.launch {
                db.messageDao().insert(
                    Message(
                        deviceMac = mac,
                        text = text,
                        isSent = false,
                        status = MsgStatus.DELIVERED.name
                    )
                )
                // Send delivery receipt back
                bt.sendDeliveryReceipt("0")
                // Show notification if not on that chat
                val isCurrentChat = screen == "chat" && selectedMac == mac
                if (!isCurrentChat) {
                    val name = devices.firstOrNull { it.first == mac }?.second ?: "Unknown"
                    Notifier.showMessage(ctx, mac, name, text)
                }
            }
        }
    }

    // Delivered receipts from other phone
    LaunchedEffect(Unit) {
        bt.delivered.collect {
            scope.launch {
                // Mark last sent message as DELIVERED
                val mac = selectedMac ?: return@launch
                db.messageDao().markAllSent(mac, MsgStatus.DELIVERED.name)
            }
        }
    }

    // Read receipts
    LaunchedEffect(Unit) {
        bt.read.collect {
            scope.launch {
                val mac = selectedMac ?: return@launch
                db.messageDao().markAllRead(mac, MsgStatus.READ.name, MsgStatus.READ.name)
            }
        }
    }

    updateInfo?.let { info ->
        UpdateDialog(
            info = info,
            isDownloading = isDownloading,
            progress = downloadProgress,
            onDismiss = { updateInfo = null },
            onUpdate = {
                isDownloading = true
                downloadProgress = 0
                scope.launch {
                    val file = UpdateManager.downloadApk(ctx, info.downloadUrl) { pct ->
                        downloadProgress = pct
                    }
                    isDownloading = false
                    if (file != null) {
                        UpdateManager.installApk(ctx, file)
                        updateInfo = null
                    }
                }
            }
        )
    }

    when (screen) {
        "home" -> HomeScreen(
            devices = devices,
            connectedMac = connectedMac,
            onDeviceClick = { mac, name ->
                selectedMac = mac
                selectedName = name
                screen = "chat"
            }
        )
        "chat" -> ChatScreen(
            mac = selectedMac ?: "",
            name = selectedName ?: "Chat",
            bt = bt,
            db = db,
            scope = scope,
            onBack = { screen = "home" }
        )
    }
}

@Composable
fun HomeScreen(
    devices: List<Pair<String, String>>,
    connectedMac: String?,
    onDeviceClick: (String, String) -> Unit
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(BgDark)
    ) {
        // WhatsApp top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgTopBar)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "BTChat",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Bluetooth,
                null,
                tint = if (connectedMac != null) Accent else TextSecondary
            )
        }

        // Status banner
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFF111B21))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (connectedMac != null) Accent else Color.Gray)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (connectedMac != null) "Connected: $connectedMac" else "Not connected",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "PAIRED DEVICES",
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            fontSize = 12.sp,
            color = TextSecondary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 4.dp)
        ) {
            items(devices) { pair ->
                val mac = pair.first
                val name = pair.second
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDeviceClick(mac, name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Accent.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name.take(1).uppercase(),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(mac, color = TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    mac: String,
    name: String,
    bt: BtService,
    db: AppDatabase,
    scope: CoroutineScope,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val messages by db.messageDao().messagesFor(mac).collectAsState(initial = emptyList())
    val connectedMac by bt.connected.collectAsState()
    val listState = rememberLazyListState()

    // Auto scroll to bottom
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    LaunchedEffect(mac) {
        if (connectedMac != mac) {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE)
                as? BluetoothManager)?.adapter
            try {
                val device = adapter?.getRemoteDevice(mac)
                device?.let { bt.connect(it) }
            } catch (_: Exception) { }
        }
    }

    // When connected: send pending messages + mark all as read
    LaunchedEffect(connectedMac) {
        if (connectedMac == mac) {
            // Send pending
            val pending = db.messageDao().pendingFor(mac)
            pending.forEach { p ->
                val ok = bt.send(p.text)
                if (ok) {
                    db.messageDao().updateStatus(p.id, MsgStatus.SENT.name)
                }
            }
            db.messageDao().markAllSent(mac, MsgStatus.SENT.name)
            // Mark received as read
            db.messageDao().markAllRead(mac, MsgStatus.READ.name, MsgStatus.READ.name)
            bt.sendReadReceipt("0")
        }
    }

    Column(Modifier.fillMaxSize().background(BgDark)) {

        // WhatsApp-style top bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgTopBar)
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Accent.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (connectedMac == mac) "online" else "offline",
                    color = if (connectedMac == mac) Accent else TextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Default.MoreVert, null, tint = TextPrimary)
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg)
            }
        }

        // Input bar
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgTopBar)
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message", color = TextSecondary) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2A3942),
                    unfocusedContainerColor = Color(0xFF2A3942),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Accent
                )
            )
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Accent)
                    .clickable {
                        val t = input.trim()
                        if (t.isEmpty()) return@clickable
                        val connected = connectedMac == mac
                        val status = if (connected) MsgStatus.SENT else MsgStatus.SENDING
                        val pending = !connected
                        scope.launch {
                            val newId = db.messageDao().insert(
                                Message(
                                    deviceMac = mac,
                                    text = t,
                                    isSent = true,
                                    status = status.name,
                                    pendingSend = pending
                                )
                            )
                            if (connected) {
                                bt.send(t)
                            }
                        }
                        input = ""
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Send, null, tint = Color.White)
            }
        }
    }
}

@Composable
fun MessageBubble(msg: Message) {
    val isSent = msg.isSent
    val bubbleColor = if (isSent) BubbleSent else BubbleReceived
    val shape = if (isSent) {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 12.dp, bottomEnd = 2.dp)
    } else {
        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 2.dp, bottomEnd = 12.dp)
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                msg.text,
                color = TextPrimary,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(2.dp))
            Row(
                Modifier.align(Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    formatTime(msg.timestamp),
                    color = TextSecondary,
                    fontSize = 11.sp
                )
                if (isSent) {
                    Spacer(Modifier.width(4.dp))
                    StatusTick(msg.status)
                }
            }
        }
    }
}

@Composable
fun StatusTick(statusStr: String) {
    val status = try { MsgStatus.valueOf(statusStr) } catch (_: Exception) { MsgStatus.SENT }
    when (status) {
        MsgStatus.SENDING -> Icon(
            Icons.Default.Schedule, null,
            tint = TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        MsgStatus.SENT -> Icon(
            Icons.Default.Check, null,
            tint = TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        MsgStatus.DELIVERED -> Icon(
            Icons.Default.DoneAll, null,
            tint = TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        MsgStatus.READ -> Icon(
            Icons.Default.DoneAll, null,
            tint = TickRead,
            modifier = Modifier.size(14.dp)
        )
    }
}

fun formatTime(ts: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
