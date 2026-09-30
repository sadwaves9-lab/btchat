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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val BgDark = Color(0xFF0B141A)
val BgTopBar = Color(0xFF1F2C34)
val BubbleSent = Color(0xFF005C4B)
val BubbleReceived = Color(0xFF1F2C34)
val TextPrimary = Color(0xFFE9EDEF)
val TextSecondary = Color(0xFF8696A0)
val TickRead = Color(0xFF53BDEB)
val Accent = Color(0xFF00A884)
val GroupColor = Color(0xFF6E4BFF)

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
                Surface(color = BgDark) { App(bt) }
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

const val GROUP_ID = "GROUP_CHAT"

@SuppressLint("MissingPermission")
@Composable
fun App(bt: BtService) {
    val ctx = LocalContext.current
    val db = remember { AppDatabase.get(ctx) }
    val scope = rememberCoroutineScope()

    var screen by remember { mutableStateOf("home") }
    var selectedMac by remember { mutableStateOf<String?>(null) }
    var selectedName by remember { mutableStateOf<String?>(null) }
    val connectedSet by bt.connectedList.collectAsState()

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

    LaunchedEffect(Unit) {
        val info = UpdateChecker.check(getVersionCode(ctx))
        if (info != null) updateInfo = info
    }

    // Incoming messages
    LaunchedEffect(Unit) {
        bt.incoming.collect { pair ->
            val mac = pair.first
            val text = pair.second
            scope.launch {
                // Save to individual chat
                db.messageDao().insert(
                    Message(deviceMac = mac, text = text, isSent = false, status = MsgStatus.DELIVERED.name)
                )
                // Save to group chat
                val senderName = devices.firstOrNull { it.first == mac }?.second ?: mac
                db.messageDao().insert(
                    Message(deviceMac = GROUP_ID, text = "$senderName: $text", isSent = false, status = MsgStatus.DELIVERED.name)
                )
                bt.sendDeliveryReceipt("0")
                val isCurrentChat = (screen == "chat" && selectedMac == mac) ||
                        (screen == "group")
                if (!isCurrentChat) {
                    val name = devices.firstOrNull { it.first == mac }?.second ?: "Unknown"
                    Notifier.showMessage(ctx, mac, name, text)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        bt.delivered.collect {
            scope.launch {
                val mac = selectedMac ?: return@launch
                db.messageDao().markAllSent(mac, MsgStatus.DELIVERED.name)
                db.messageDao().markAllSent(GROUP_ID, MsgStatus.DELIVERED.name)
            }
        }
    }

    LaunchedEffect(Unit) {
        bt.read.collect {
            scope.launch {
                val mac = selectedMac ?: return@launch
                db.messageDao().markAllRead(mac, MsgStatus.READ.name, MsgStatus.READ.name)
                db.messageDao().markAllRead(GROUP_ID, MsgStatus.READ.name, MsgStatus.READ.name)
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
                isDownloading = true; downloadProgress = 0
                scope.launch {
                    val file = UpdateManager.downloadApk(ctx, info.downloadUrl) { pct -> downloadProgress = pct }
                    isDownloading = false
                    if (file != null) { UpdateManager.installApk(ctx, file); updateInfo = null }
                }
            }
        )
    }

    when (screen) {
        "home" -> HomeScreen(
            devices = devices,
            connectedSet = connectedSet,
            onDeviceClick = { mac, name ->
                selectedMac = mac; selectedName = name; screen = "chat"
            },
            onGroupClick = {
                screen = "group"
            }
        )
        "chat" -> ChatScreen(
            mac = selectedMac ?: "", name = selectedName ?: "Chat",
            bt = bt, db = db, scope = scope,
            onBack = { screen = "home" },
            isGroup = false
        )
        "group" -> ChatScreen(
            mac = GROUP_ID, name = "Group Chat",
            bt = bt, db = db, scope = scope,
            onBack = { screen = "home" },
            isGroup = true
        )
    }
}

@Composable
fun HomeScreen(
    devices: List<Pair<String, String>>,
    connectedSet: Set<String>,
    onDeviceClick: (String, String) -> Unit,
    onGroupClick: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(BgDark)) {
        Row(
            Modifier.fillMaxWidth().background(BgTopBar).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BTChat", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Text("${connectedSet.size} online", fontSize = 12.sp, color = Accent, fontWeight = FontWeight.Bold)
        }

        // Group Chat button
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GroupColor.copy(alpha = 0.15f))
                .clickable { onGroupClick() }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(GroupColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Groups, null, tint = Color.White)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Group Chat", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Chat with all connected devices", color = TextSecondary, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
        }

        Text(
            "PAIRED DEVICES",
            Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
            fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(devices) { pair ->
                val mac = pair.first
                val name = pair.second
                val online = connectedSet.contains(mac)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onDeviceClick(mac, name) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Accent.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(name.take(1).uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        }
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(if (online) Accent else Color.Gray)
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Text(if (online) "online" else mac, color = if (online) Accent else TextSecondary, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun ChatScreen(
    mac: String, name: String,
    bt: BtService, db: AppDatabase, scope: CoroutineScope,
    onBack: () -> Unit,
    isGroup: Boolean
) {
    val ctx = LocalContext.current
    var input by remember { mutableStateOf("") }
    val messages by db.messageDao().messagesFor(mac).collectAsState(initial = emptyList())
    val connectedSet by bt.connectedList.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    // Auto connect to all paired devices if group
    LaunchedEffect(isGroup) {
        if (isGroup) {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.bondedDevices?.forEach { d ->
                if (!connectedSet.contains(d.address)) {
                    bt.connect(d)
                }
            }
        }
    }

    // When not group, connect to specific
    LaunchedEffect(mac) {
        if (!isGroup && !connectedSet.contains(mac)) {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            try { adapter?.getRemoteDevice(mac)?.let { bt.connect(it) } } catch (_: Exception) { }
        }
    }

    Column(Modifier.fillMaxSize().background(BgDark)) {
        // Top bar
        Row(
            Modifier.fillMaxWidth().background(BgTopBar).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(if (isGroup) GroupColor else Accent.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (isGroup) Icon(Icons.Default.Groups, null, tint = Color.White)
                else Text(name.take(1).uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (isGroup) "${connectedSet.size} participants" else if (connectedSet.contains(mac)) "online" else "offline",
                    color = if (isGroup || connectedSet.contains(mac)) Accent else TextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Default.MoreVert, null, tint = TextPrimary)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                MessageBubble(msg, isGroup)
            }
        }

        Row(
            Modifier.fillMaxWidth().background(BgTopBar).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (isGroup) "Message group" else "Message", color = TextSecondary) },
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
                Modifier.size(48.dp).clip(CircleShape)
                    .background(if (isGroup) GroupColor else Accent)
                    .clickable {
                        val t = input.trim()
                        if (t.isEmpty()) return@clickable
                        scope.launch {
                            if (isGroup) {
                                db.messageDao().insert(
                                    Message(deviceMac = GROUP_ID, text = "Me: $t", isSent = true, status = MsgStatus.SENT.name)
                                )
                                bt.broadcast("Me: $t")
                            } else {
                                val connected = connectedSet.contains(mac)
                                val status = if (connected) MsgStatus.SENT else MsgStatus.SENDING
                                db.messageDao().insert(
                                    Message(deviceMac = mac, text = t, isSent = true,
                                        status = status.name, pendingSend = !connected)
                                )
                                if (connected) bt.send(mac, t)
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
fun MessageBubble(msg: Message, isGroup: Boolean) {
    val isSent = msg.isSent
    val bubbleColor = if (isSent) BubbleSent else BubbleReceived
    val shape = if (isSent)
        RoundedCornerShape(12.dp, 12.dp, 12.dp, 2.dp)
    else RoundedCornerShape(12.dp, 12.dp, 2.dp, 12.dp)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape).background(bubbleColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(msg.text, color = TextPrimary, fontSize = 15.sp)
            Spacer(Modifier.height(2.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(formatTime(msg.timestamp), color = TextSecondary, fontSize = 11.sp)
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
        MsgStatus.SENDING -> Icon(Icons.Default.Schedule, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
        MsgStatus.SENT -> Icon(Icons.Default.Check, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
        MsgStatus.DELIVERED -> Icon(Icons.Default.DoneAll, null, tint = TextSecondary, modifier = Modifier.size(14.dp))
        MsgStatus.READ -> Icon(Icons.Default.DoneAll, null, tint = TickRead, modifier = Modifier.size(14.dp))
    }
}

fun formatTime(ts: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
