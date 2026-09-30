package com.example.btchat.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.bluetooth.BtPacket
import com.example.btchat.bluetooth.BtService
import com.example.btchat.data.AppDatabase
import com.example.btchat.data.DeviceAlias
import com.example.btchat.data.Message
import com.example.btchat.data.MsgKind
import com.example.btchat.data.MsgStatus
import com.example.btchat.notif.Notifier
import com.example.btchat.update.UpdateChecker
import com.example.btchat.update.UpdateDialog
import com.example.btchat.update.UpdateInfo
import com.example.btchat.update.UpdateManager
import com.example.btchat.util.FileUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ===== WhatsApp Colors =====
val BgDark = Color(0xFF0B141A)
val BgTopBar = Color(0xFF1F2C34)
val BubbleSent = Color(0xFF005C4B)
val BubbleReceived = Color(0xFF1F2C34)
val TextPrimary = Color(0xFFE9EDEF)
val TextSecondary = Color(0xFF8696A0)
val TickRead = Color(0xFF53BDEB)
val Accent = Color(0xFF00A884)
val GroupColor = Color(0xFF6E4BFF)

const val GROUP_ID = "GROUP_CHAT"

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

    // pairedDevices: mac -> originalName
    val devices = remember { mutableStateMapOf<String, String>() }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        bt.startServer()
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        adapter?.bondedDevices?.forEach { d ->
            devices[d.address] = (d.name ?: "Unknown")
        }
    }

    LaunchedEffect(Unit) {
        val info = UpdateChecker.check(getVersionCode(ctx))
        if (info != null) updateInfo = info
    }

    // Handle incoming
    LaunchedEffect(Unit) {
        bt.incoming.collect { packet ->
            scope.launch {
                when (packet.kind) {
                    "TEXT" -> {
                        db.messageDao().insert(
                            Message(deviceMac = packet.fromMac, text = packet.text,
                                isSent = false, status = MsgStatus.DELIVERED.name)
                        )
                        val senderName = DeviceAlias.displayName(ctx, packet.fromMac,
                            devices[packet.fromMac] ?: "Unknown")
                        db.messageDao().insert(
                            Message(deviceMac = GROUP_ID, text = "$senderName: ${packet.text}",
                                isSent = false, status = MsgStatus.DELIVERED.name)
                        )
                        bt.sendDeliveryReceipt("0")
                        val isCurrent = (screen == "chat" && selectedMac == packet.fromMac) || screen == "group"
                        if (!isCurrent) {
                            Notifier.showMessage(ctx, packet.fromMac, senderName, packet.text)
                        }
                    }
                    else -> {
                        // FILE|kind|name|size|b64
                        val split = packet.text.split("|", limit = 3)
                        if (split.size >= 3) {
                            val fileName = split[0]
                            val size = split[1].toLongOrNull() ?: 0L
                            val b64 = split[2]
                            val bytes = Base64.decode(b64, Base64.DEFAULT)
                            val dir = File(ctx.filesDir, "received").apply { mkdirs() }
                            val outFile = File(dir, "${System.currentTimeMillis()}_$fileName")
                            withContext(Dispatchers.IO) {
                                outFile.writeBytes(bytes)
                            }
                            db.messageDao().insert(
                                Message(
                                    deviceMac = packet.fromMac,
                                    text = fileName,
                                    isSent = false,
                                    status = MsgStatus.DELIVERED.name,
                                    kind = packet.kind,
                                    filePath = outFile.absolutePath,
                                    fileName = fileName,
                                    fileSize = size,
                                    mimeType = null
                                )
                            )
                            val senderName = DeviceAlias.displayName(ctx, packet.fromMac,
                                devices[packet.fromMac] ?: "Unknown")
                            db.messageDao().insert(
                                Message(
                                    deviceMac = GROUP_ID,
                                    text = "$senderName: $fileName",
                                    isSent = false,
                                    status = MsgStatus.DELIVERED.name,
                                    kind = packet.kind,
                                    filePath = outFile.absolutePath,
                                    fileName = fileName,
                                    fileSize = size
                                )
                            )
                            bt.sendDeliveryReceipt("0")
                            val isCurrent = (screen == "chat" && selectedMac == packet.fromMac) || screen == "group"
                            if (!isCurrent) {
                                Notifier.showMessage(ctx, packet.fromMac, senderName, "📎 $fileName")
                            }
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        bt.delivered.collect {
            scope.launch {
                db.messageDao().markAllSent(GROUP_ID, MsgStatus.DELIVERED.name)
                selectedMac?.let { db.messageDao().markAllSent(it, MsgStatus.DELIVERED.name) }
            }
        }
    }

    LaunchedEffect(Unit) {
        bt.read.collect {
            scope.launch {
                db.messageDao().markAllRead(GROUP_ID, MsgStatus.READ.name, MsgStatus.READ.name)
                selectedMac?.let { db.messageDao().markAllRead(it, MsgStatus.READ.name, MsgStatus.READ.name) }
            }
        }
    }

    updateInfo?.let { info ->
        UpdateDialog(
            info = info, isDownloading = isDownloading, progress = downloadProgress,
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
            ctx = ctx,
            devices = devices,
            connectedSet = connectedSet,
            bt = bt,
            onDeviceClick = { mac, name ->
                selectedMac = mac; selectedName = name; screen = "chat"
            },
            onGroupClick = { screen = "group" }
        )
        "chat" -> ChatScreen(
            mac = selectedMac ?: "", name = selectedName ?: "Chat",
            bt = bt, db = db, scope = scope, ctx = ctx,
            onBack = { screen = "home" }, isGroup = false
        )
        "group" -> ChatScreen(
            mac = GROUP_ID, name = "Group Chat",
            bt = bt, db = db, scope = scope, ctx = ctx,
            onBack = { screen = "home" }, isGroup = true
        )
    }
}

// ============================================================
//  HOME SCREEN
// ============================================================
@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
    ctx: Context,
    devices: Map<String, String>,
    connectedSet: Set<String>,
    bt: BtService,
    onDeviceClick: (String, String) -> Unit,
    onGroupClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var manualMac by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    // Auto-connect to all paired when group clicked
    val filtered = devices.entries
        .filter { (mac, name) ->
            val display = DeviceAlias.displayName(ctx, mac, name)
            searchQuery.isBlank() || display.contains(searchQuery, ignoreCase = true) || mac.contains(searchQuery)
        }
        .sortedBy { DeviceAlias.displayName(ctx, it.key, it.value) }

    Column(Modifier.fillMaxSize().background(BgDark)) {

        // TOP BAR with WhatsApp-like design
        Row(
            Modifier
                .fillMaxWidth()
                .background(BgTopBar)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("BTChat", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Bluetooth,
                contentDescription = "Bluetooth",
                tint = if (connectedSet.isNotEmpty()) Accent else TextSecondary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(14.dp))
            Icon(
                Icons.Default.Add,
                contentDescription = "Add Device",
                tint = TextPrimary,
                modifier = Modifier
                    .size(26.dp)
                    .clickable { showAddDialog = true }
            )
            Spacer(Modifier.width(14.dp))
            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = TextPrimary)
        }

        // SEARCH BAR
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF2A3942))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            BasicSearchField(
                value = searchQuery,
                onValueChange = { searchQuery = it }
            )
        }

        // GROUP CHAT BUTTON
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(GroupColor.copy(alpha = 0.18f))
                .clickable { onGroupClick() }
                .padding(14.dp),
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

        Spacer(Modifier.height(6.dp))

        Text(
            "PAIRED DEVICES (${filtered.size})",
            Modifier.padding(start = 16.dp, top = 6.dp, bottom = 8.dp),
            fontSize = 12.sp, color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
        )

        // DEVICE LIST
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 4.dp)) {
            items(filtered, key = { it.key }) { entry ->
                val mac = entry.key
                val originalName = entry.value
                val displayName = DeviceAlias.displayName(ctx, mac, originalName)
                val online = connectedSet.contains(mac)

                Row(
                    Modifier
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = { onDeviceClick(mac, displayName) },
                            onLongClick = {
                                renameTarget = mac
                                renameValue = displayName
                                showRenameDialog = true
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        Box(
                            Modifier.size(50.dp).clip(CircleShape).background(Accent.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                displayName.take(1).uppercase(),
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp
                            )
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
                        Text(displayName, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            if (online) "online" else mac,
                            color = if (online) Accent else TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Icon(
                        Icons.Default.ChevronRight,
                        null,
                        tint = TextSecondary
                    )
                }
            }
            if (filtered.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.BluetoothSearching,
                                null, tint = TextSecondary,
                                modifier = Modifier.size(60.dp)
                            )
                            Spacer(Modifier.height(14.dp))
                            Text(
                                if (searchQuery.isBlank()) "No paired devices" else "No results",
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Pair from Settings > Bluetooth,\nor tap + to add manually",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // RENAME DIALOG
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Device", color = TextPrimary) },
            text = {
                Column {
                    Text("New name:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameValue,
                        onValueChange = { renameValue = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF2A3942),
                            unfocusedContainerColor = Color(0xFF2A3942),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Accent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget?.let { mac ->
                        if (renameValue.isNotBlank()) {
                            DeviceAlias.set(ctx, mac, renameValue.trim())
                        }
                    }
                    showRenameDialog = false
                }) { Text("Save", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = BgTopBar
        )
    }

    // ADD DEVICE DIALOG
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Device", color = TextPrimary) },
            text = {
                Column {
                    Text("Enter MAC address manually:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualMac,
                        onValueChange = { manualMac = it },
                        singleLine = true,
                        placeholder = { Text("AA:BB:CC:DD:EE:FF", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF2A3942),
                            unfocusedContainerColor = Color(0xFF2A3942),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Accent
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Name:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = manualName,
                        onValueChange = { manualName = it },
                        singleLine = true,
                        placeholder = { Text("Friend's phone", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0xFF2A3942),
                            unfocusedContainerColor = Color(0xFF2A3942),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = Accent
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Tip: Pair from Settings > Bluetooth first, then it auto-appears here.",
                        color = TextSecondary, fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (manualMac.isNotBlank() && manualName.isNotBlank()) {
                        DeviceAlias.set(ctx, manualMac.trim(), manualName.trim())
                    }
                    manualMac = ""; manualName = ""
                    showAddDialog = false
                }) { Text("Add", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = BgTopBar
        )
    }
}

@Composable
fun BasicSearchField(value: String, onValueChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = TextPrimary,
            fontSize = 15.sp
        ),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            if (value.isEmpty()) {
                Text("Search devices", color = TextSecondary, fontSize = 15.sp)
            }
            inner()
        }
    )
}

// ============================================================
//  CHAT SCREEN
// ============================================================
@SuppressLint("MissingPermission")
@Composable
fun ChatScreen(
    mac: String, name: String,
    bt: BtService, db: AppDatabase, scope: CoroutineScope, ctx: Context,
    onBack: () -> Unit, isGroup: Boolean
) {
    var input by remember { mutableStateOf("") }
    val messages by db.messageDao().messagesFor(mac).collectAsState(initial = emptyList())
    val connectedSet by bt.connectedList.collectAsState()
    val listState = rememberLazyListState()

    // media pickers
    val imagePicker = rememberLauncherForPicker("image/*") { uri ->
        handlePickedFile(ctx, uri, "IMAGE", mac, isGroup, bt, db, scope)
    }
    val videoPicker = rememberLauncherForPicker("video/*") { uri ->
        handlePickedFile(ctx, uri, "VIDEO", mac, isGroup, bt, db, scope)
    }
    val audioPicker = rememberLauncherForPicker("audio/*") { uri ->
        handlePickedFile(ctx, uri, "AUDIO", mac, isGroup, bt, db, scope)
    }
    val filePicker = rememberLauncherForPicker("*/*") { uri ->
        handlePickedFile(ctx, uri, "FILE", mac, isGroup, bt, db, scope)
    }

    var showAttachSheet by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(isGroup) {
        if (isGroup) {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.bondedDevices?.forEach { d -> bt.connect(d) }
        }
    }

    LaunchedEffect(mac) {
        if (!isGroup && !connectedSet.contains(mac)) {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            try { adapter?.getRemoteDevice(mac)?.let { bt.connect(it) } } catch (_: Exception) { }
        }
    }

    Column(Modifier.fillMaxSize().background(BgDark)) {

        // TOP BAR
        Row(
            Modifier.fillMaxWidth().background(BgTopBar).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, null, tint = TextPrimary)
            }
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(if (isGroup) GroupColor else Accent.copy(alpha = 0.3f)),
                contentAlignment = Alignment.Center
            ) {
                if (isGroup) Icon(Icons.Default.Groups, null, tint = Color.White)
                else Text(name.take(1).uppercase(), color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(
                    if (isGroup) "${connectedSet.size} participants"
                    else if (connectedSet.contains(mac)) "online" else "offline",
                    color = if (isGroup || connectedSet.contains(mac)) Accent else TextSecondary,
                    fontSize = 12.sp
                )
            }
            Icon(Icons.Default.MoreVert, null, tint = TextPrimary)
        }

        // MESSAGES
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
        }

        // INPUT BAR with WhatsApp-style attach
        Row(
            Modifier.fillMaxWidth().background(BgTopBar).padding(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Attach button
            Box(
                Modifier.size(44.dp).clip(CircleShape).clickable { showAttachSheet = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AttachFile,
                    contentDescription = "Attach",
                    tint = TextSecondary,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(4.dp))

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

    // ATTACH BOTTOM SHEET
    if (showAttachSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachSheet = false },
            containerColor = BgTopBar
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Send Attachment", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    AttachOption(Icons.Default.Image, "Image", Color(0xFF9C27B0)) {
                        showAttachSheet = false; imagePicker.launch("image/*")
                    }
                    AttachOption(Icons.Default.Videocam, "Video", Color(0xFFE91E63)) {
                        showAttachSheet = false; videoPicker.launch("video/*")
                    }
                    AttachOption(Icons.Default.Mic, "Audio", Color(0xFFFF9800)) {
                        showAttachSheet = false; audioPicker.launch("audio/*")
                    }
                    AttachOption(Icons.Default.InsertDriveFile, "File", Color(0xFF2196F3)) {
                        showAttachSheet = false; filePicker.launch("*/*")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun AttachOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Box(
            Modifier.size(56.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
fun rememberLauncherForPicker(mime: String, onPicked: (Uri) -> Unit): ManagedActivityResultLauncher<String, Uri?> {
    return androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { onPicked(it) } }
}

typealias ManagedActivityResultLauncher<I, O> = androidx.activity.result.ActivityResultLauncher<I>

fun handlePickedFile(
    ctx: Context, uri: Uri, kind: String,
    mac: String, isGroup: Boolean,
    bt: BtService, db: AppDatabase, scope: CoroutineScope
) {
    scope.launch {
        withContext(Dispatchers.IO) {
            val name = FileUtil.queryName(ctx, uri)
            val size = FileUtil.querySize(ctx, uri)
            val f = FileUtil.copyToInternal(ctx, uri, "sent") ?: return@withContext
            val bytes = f.readBytes()
            val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

            if (isGroup) {
                db.messageDao().insert(
                    Message(
                        deviceMac = GROUP_ID, text = "Me: $name", isSent = true,
                        status = MsgStatus.SENT.name, kind = kind,
                        filePath = f.absolutePath, fileName = name, fileSize = size
                    )
                )
                bt.broadcastFile(kind, name, size, b64)
            } else {
                val connected = true // assumed; simpler
                db.messageDao().insert(
                    Message(
                        deviceMac = mac, text = name, isSent = true,
                        status = MsgStatus.SENT.name, kind = kind,
                        filePath = f.absolutePath, fileName = name, fileSize = size
                    )
                )
                bt.sendFile(mac, kind, name, size, b64)
            }
        }
    }
}

@Composable
fun MessageBubble(msg: Message) {
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
            Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bubbleColor)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            when (msg.kind) {
                MsgKind.IMAGE.name -> {
                    val path = msg.filePath
                    if (path != null) {
                        val bmp = remember(path) {
                            try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier
                                    .width(240.dp)
                                    .heightIn(max = 300.dp)
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Fit
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(msg.text, color = TextPrimary, fontSize = 13.sp)
                    } else {
                        Text("📷 ${msg.text}", color = TextPrimary, fontSize = 15.sp)
                    }
                }
                MsgKind.VIDEO.name -> Text("🎥 ${msg.text}", color = TextPrimary, fontSize = 15.sp)
                MsgKind.AUDIO.name -> Text("🎵 ${msg.text}", color = TextPrimary, fontSize = 15.sp)
                MsgKind.FILE.name -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = Accent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(msg.text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(FileUtil.humanSize(msg.fileSize), color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                else -> Text(msg.text, color = TextPrimary, fontSize = 15.sp)
            }
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
