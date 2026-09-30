@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.example.btchat.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
import com.example.btchat.ui.theme.GlassCard
import com.example.btchat.ui.theme.Grad
import com.example.btchat.ui.theme.Palette
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

// ============ WhatsApp + Glassmorphism Colors ============
val BgDeep = Palette.BgDeep
val BubbleSent = Color(0xFF00695C)
val BubbleSent2 = Color(0xFF00A884)
val BubbleReceived = Color(0xFF1A1A2E)
val TextPrimary = Palette.TextPrimary
val TextSecondary = Palette.TextSecondary
val TickRead = Color(0xFF53BDEB)
val Accent = Palette.Emerald
val GroupColor = Palette.Violet

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
            MaterialTheme(colorScheme = darkColorScheme(background = BgDeep, surface = BubbleReceived)) {
                Surface(color = BgDeep) { App(bt) }
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
    val devices = remember { mutableStateMapOf<String, String>() }

    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        bt.startServer()
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
        adapter?.bondedDevices?.forEach { d -> devices[d.address] = (d.name ?: "Unknown") }
    }

    LaunchedEffect(Unit) {
        val info = UpdateChecker.check(getVersionCode(ctx))
        if (info != null) updateInfo = info
    }

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
                        if (!isCurrent) Notifier.showMessage(ctx, packet.fromMac, senderName, packet.text)
                    }
                    else -> {
                        val split = packet.text.split("|", limit = 3)
                        if (split.size >= 3) {
                            val fileName = split[0]
                            val size = split[1].toLongOrNull() ?: 0L
                            val bytes = Base64.decode(split[2], Base64.DEFAULT)
                            val dir = File(ctx.filesDir, "received").apply { mkdirs() }
                            val outFile = File(dir, "${System.currentTimeMillis()}_$fileName")
                            withContext(Dispatchers.IO) { outFile.writeBytes(bytes) }
                            db.messageDao().insert(
                                Message(deviceMac = packet.fromMac, text = fileName, isSent = false,
                                    status = MsgStatus.DELIVERED.name, kind = packet.kind,
                                    filePath = outFile.absolutePath, fileName = fileName, fileSize = size)
                            )
                            val senderName = DeviceAlias.displayName(ctx, packet.fromMac,
                                devices[packet.fromMac] ?: "Unknown")
                            db.messageDao().insert(
                                Message(deviceMac = GROUP_ID, text = "$senderName: $fileName",
                                    isSent = false, status = MsgStatus.DELIVERED.name,
                                    kind = packet.kind, filePath = outFile.absolutePath,
                                    fileName = fileName, fileSize = size)
                            )
                            bt.sendDeliveryReceipt("0")
                            val isCurrent = (screen == "chat" && selectedMac == packet.fromMac) || screen == "group"
                            if (!isCurrent) Notifier.showMessage(ctx, packet.fromMac, senderName, "📎 $fileName")
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
        "home" -> HomeScreen(ctx, devices, connectedSet, bt,
            onDeviceClick = { mac, name -> selectedMac = mac; selectedName = name; screen = "chat" },
            onGroupClick = { screen = "group" })
        "chat" -> ChatScreen(selectedMac ?: "", selectedName ?: "Chat",
            bt, db, scope, ctx, onBack = { screen = "home" }, isGroup = false)
        "group" -> ChatScreen(GROUP_ID, "Group Chat",
            bt, db, scope, ctx, onBack = { screen = "home" }, isGroup = true)
    }
}

// ============ HOME SCREEN (Glassmorphism) ============
@SuppressLint("MissingPermission")
@Composable
fun HomeScreen(
    ctx: Context, devices: Map<String, String>, connectedSet: Set<String>,
    bt: BtService, onDeviceClick: (String, String) -> Unit, onGroupClick: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var manualMac by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    val filtered = devices.entries
        .filter { (mac, name) ->
            val display = DeviceAlias.displayName(ctx, mac, name)
            searchQuery.isBlank() || display.contains(searchQuery, ignoreCase = true) || mac.contains(searchQuery)
        }
        .sortedBy { DeviceAlias.displayName(ctx, it.key, it.value) }

    Box(Modifier.fillMaxSize().background(Grad.bgMain)) {

        // Animated gradient blobs
        AnimatedBlobs()

        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            // Glass Top Bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "BTChat",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (connectedSet.isNotEmpty()) Grad.purplePink
                            else Grad.glass
                        )
                        .clickable {},
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Bluetooth, null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Grad.blueCyan)
                        .clickable { showAddDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
            }

            // Glass Search Bar
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0x15FFFFFF))
                    .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Search, null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) {
                                Text("Search devices…", color = TextSecondary, fontSize = 15.sp)
                            }
                            inner()
                        }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Glass Group Chat Card
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0x15FFFFFF))
                    .border(1.dp, GroupColor.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
                    .clickable { onGroupClick() }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Grad.aurora),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Group Chat", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${connectedSet.size} connected", color = TextSecondary, fontSize = 12.sp)
                }
                Icon(Icons.Default.ChevronRight, null, tint = TextSecondary)
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "DEVICES (${filtered.size})",
                Modifier.padding(start = 20.dp, bottom = 8.dp),
                fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
            )

            // Device List
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.key }) { entry ->
                    val mac = entry.key
                    val originalName = entry.value
                    val displayName = DeviceAlias.displayName(ctx, mac, originalName)
                    val online = connectedSet.contains(mac)

                    DeviceCard(
                        name = displayName,
                        mac = mac,
                        online = online,
                        onClick = { onDeviceClick(mac, displayName) },
                        onLongClick = {
                            renameTarget = mac; renameValue = displayName; showRenameDialog = true
                        }
                    )
                }
                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.BluetoothSearching, null, tint = TextSecondary, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    if (searchQuery.isBlank()) "No paired devices" else "No results",
                                    color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp
                                )
                                Spacer(Modifier.height(6.dp))
                                Text(
                                    "Pair from Settings > Bluetooth,\nor tap + to add manually",
                                    color = TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Rename Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Device", color = TextPrimary) },
            text = {
                Column {
                    Text("New name:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = renameValue, onValueChange = { renameValue = it }, singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0x20FFFFFF),
                            unfocusedContainerColor = Color(0x20FFFFFF),
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary,
                            cursorColor = Accent
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    renameTarget?.let { mac -> if (renameValue.isNotBlank()) DeviceAlias.set(ctx, mac, renameValue.trim()) }
                    showRenameDialog = false
                }) { Text("Save", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = TextSecondary) }
            },
            containerColor = BubbleReceived
        )
    }

    // Add Device Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Device", color = TextPrimary) },
            text = {
                Column {
                    Text("MAC address:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualMac, onValueChange = { manualMac = it }, singleLine = true,
                        placeholder = { Text("AA:BB:CC:DD:EE:FF", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0x20FFFFFF), unfocusedContainerColor = Color(0x20FFFFFF),
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
                        )
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Name:", color = TextSecondary, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = manualName, onValueChange = { manualName = it }, singleLine = true,
                        placeholder = { Text("Friend", color = TextSecondary) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color(0x20FFFFFF), unfocusedContainerColor = Color(0x20FFFFFF),
                            focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary, cursorColor = Accent
                        )
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
            containerColor = BubbleReceived
        )
    }
}

// ============ Animated Background Blobs ============
@Composable
fun AnimatedBlobs() {
    val infinite = rememberInfiniteTransition(label = "blobs")
    val shift by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(15000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "shift"
    )
    Canvas2(shift)
}

@Composable
fun Canvas2(shift: Float) {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Palette.DeepPurple.copy(alpha = 0.3f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(w * (0.2f + 0.3f * shift), h * 0.15f),
                radius = 500f
            ),
            radius = 500f,
            center = androidx.compose.ui.geometry.Offset(w * (0.2f + 0.3f * shift), h * 0.15f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Palette.Pink.copy(alpha = 0.25f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(w * (0.8f - 0.3f * shift), h * 0.75f),
                radius = 450f
            ),
            radius = 450f,
            center = androidx.compose.ui.geometry.Offset(w * (0.8f - 0.3f * shift), h * 0.75f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Palette.Cyan.copy(alpha = 0.2f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * (0.4f + 0.2f * shift)),
                radius = 400f
            ),
            radius = 400f,
            center = androidx.compose.ui.geometry.Offset(w * 0.5f, h * (0.4f + 0.2f * shift))
        )
    }
}

// ============ Device Card (Glass) ============
@Composable
fun DeviceCard(
    name: String, mac: String, online: Boolean,
    onClick: () -> Unit, onLongClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            )
            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (online) Grad.aurora
                        else Brush.linearGradient(listOf(Color(0xFF2A2A40), Color(0xFF1A1A30)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp)
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(if (online) Palette.Emerald else Color(0xFF666680))
                    .border(2.dp, BgDeep, CircleShape)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(3.dp))
            Text(if (online) "online" else mac, color = if (online) Accent else TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = TextSecondary.copy(0.6f))
    }
}

// ============ CHAT SCREEN ============
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

    val imagePicker = rememberImagePicker { uri -> handlePicked(ctx, uri, "IMAGE", mac, isGroup, bt, db, scope) }
    val videoPicker = rememberVideoPicker { uri -> handlePicked(ctx, uri, "VIDEO", mac, isGroup, bt, db, scope) }
    val audioPicker = rememberAudioPicker { uri -> handlePicked(ctx, uri, "AUDIO", mac, isGroup, bt, db, scope) }
    val filePicker = rememberFilePicker { uri -> handlePicked(ctx, uri, "FILE", mac, isGroup, bt, db, scope) }

    var showAttach by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<Message?>(null) }
    var replyTarget by remember { mutableStateOf<Message?>(null) }

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

    Box(Modifier.fillMaxSize().background(Grad.bgMain)) {
        Canvas2(0.5f)

        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            // Top Bar Glass
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x30FFFFFF))
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = TextPrimary) }
                Box(
                    Modifier.size(42.dp).clip(CircleShape)
                        .background(if (isGroup) Grad.aurora else Grad.purplePink),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGroup) Icon(Icons.Default.Groups, null, tint = Color.White)
                    else Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
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

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg) { menuTarget = msg }
                }
            }

            // Reply preview
            AnimatedVisibility(
                visible = replyTarget != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                replyTarget?.let { r ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x20FFFFFF))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).height(30.dp).background(Accent))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Replying to:", color = Accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(r.text, color = TextPrimary, fontSize = 13.sp, maxLines = 1)
                        }
                        IconButton(onClick = { replyTarget = null }) {
                            Icon(Icons.Default.Close, null, tint = TextSecondary)
                        }
                    }
                }
            }

            // Input Bar
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0x30FFFFFF))
                    .padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { showAttach = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.AttachFile, null, tint = TextPrimary, modifier = Modifier.size(26.dp))
                }
                Spacer(Modifier.width(4.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color(0x20FFFFFF))
                        .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        decorationBox = { inner ->
                            if (input.isEmpty()) {
                                Text(if (isGroup) "Message group" else "Message", color = TextSecondary, fontSize = 15.sp)
                            }
                            inner()
                        }
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Grad.blueCyan)
                        .clickable {
                            val t = input.trim()
                            if (t.isEmpty()) return@clickable
                            val replyId = replyTarget?.id?.toString()
                            scope.launch {
                                if (isGroup) {
                                    db.messageDao().insert(
                                        Message(deviceMac = GROUP_ID, text = "Me: $t", isSent = true,
                                            status = MsgStatus.SENT.name, replyTo = replyId)
                                    )
                                    bt.broadcast("Me: $t")
                                } else {
                                    val connected = connectedSet.contains(mac)
                                    val status = if (connected) MsgStatus.SENT else MsgStatus.SENDING
                                    db.messageDao().insert(
                                        Message(deviceMac = mac, text = t, isSent = true,
                                            status = status.name, pendingSend = !connected, replyTo = replyId)
                                    )
                                    if (connected) bt.send(mac, t)
                                }
                            }
                            input = ""
                            replyTarget = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Send, null, tint = Color.White)
                }
            }
        }
    }

    if (showAttach) {
        ModalBottomSheet(
            onDismissRequest = { showAttach = false },
            containerColor = BubbleReceived
        ) {
            Column(Modifier.padding(20.dp)) {
                Text("Send", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AttachOpt(Icons.Default.Image, "Image", Palette.Purple2()) { showAttach = false; imagePicker.launch("image/*") }
                    AttachOpt(Icons.Default.Videocam, "Video", Palette.Rose) { showAttach = false; videoPicker.launch("video/*") }
                    AttachOpt(Icons.Default.Mic, "Audio", Palette.Amber) { showAttach = false; audioPicker.launch("audio/*") }
                    AttachOpt(Icons.Default.InsertDriveFile, "File", Palette.RoyalBlue) { showAttach = false; filePicker.launch("*/*") }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    // Context Menu
    menuTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            title = { Text("Options", color = TextPrimary) },
            text = {
                Column {
                    MsgMenuItem(Icons.Default.Reply, "Reply") { replyTarget = msg; menuTarget = null }
                    MsgMenuItem(Icons.Default.ContentCopy, "Copy") {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("msg", msg.text))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        menuTarget = null
                    }
                    MsgMenuItem(Icons.Default.Star, if (msg.isStarred) "Unstar" else "Star") {
                        scope.launch { db.messageDao().star(msg.id, !msg.isStarred) }
                        menuTarget = null
                    }
                    MsgMenuItem(Icons.Default.Share, "Forward") {
                        val sendIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(android.content.Intent.EXTRA_TEXT, msg.text)
                        }
                        ctx.startActivity(android.content.Intent.createChooser(sendIntent, "Forward"))
                        menuTarget = null
                    }
                    MsgMenuItem(Icons.Default.Delete, "Delete") {
                        scope.launch { db.messageDao().softDelete(msg.id) }
                        menuTarget = null
                    }
                }
            },
            confirmButton = {},
            containerColor = BubbleReceived
        )
    }
}

@Composable
fun MsgMenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = TextPrimary, fontSize = 15.sp)
    }
}

@Composable
fun AttachOpt(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = TextPrimary, fontSize = 12.sp)
    }
}

@Composable
fun rememberImagePicker(cb: (Uri) -> Unit) = androidx.activity.compose.rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()) { uri -> uri?.let { cb(it) } }

@Composable
fun rememberVideoPicker(cb: (Uri) -> Unit) = androidx.activity.compose.rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()) { uri -> uri?.let { cb(it) } }

@Composable
fun rememberAudioPicker(cb: (Uri) -> Unit) = androidx.activity.compose.rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()) { uri -> uri?.let { cb(it) } }

@Composable
fun rememberFilePicker(cb: (Uri) -> Unit) = androidx.activity.compose.rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()) { uri -> uri?.let { cb(it) } }

fun handlePicked(ctx: Context, uri: Uri, kind: String, mac: String, isGroup: Boolean,
                 bt: BtService, db: AppDatabase, scope: CoroutineScope) {
    scope.launch {
        withContext(Dispatchers.IO) {
            val name = FileUtil.queryName(ctx, uri)
            val size = FileUtil.querySize(ctx, uri)
            val f = FileUtil.copyToInternal(ctx, uri, "sent") ?: return@withContext
            val b64 = Base64.encodeToString(f.readBytes(), Base64.NO_WRAP)
            if (isGroup) {
                db.messageDao().insert(Message(deviceMac = GROUP_ID, text = "Me: $name", isSent = true,
                    status = MsgStatus.SENT.name, kind = kind, filePath = f.absolutePath,
                    fileName = name, fileSize = size))
                bt.broadcastFile(kind, name, size, b64)
            } else {
                db.messageDao().insert(Message(deviceMac = mac, text = name, isSent = true,
                    status = MsgStatus.SENT.name, kind = kind, filePath = f.absolutePath,
                    fileName = name, fileSize = size))
                bt.sendFile(mac, kind, name, size, b64)
            }
        }
    }
}

// ============ Message Bubble (Glass) ============
@Composable
fun MessageBubble(msg: Message, onLongPress: () -> Unit) {
    val isSent = msg.isSent
    val bubbleShape = if (isSent)
        RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(bubbleShape)
                .background(
                    if (isSent) Grad.blueCyan
                    else Brush.linearGradient(listOf(Color(0x25FFFFFF), Color(0x15FFFFFF)))
                )
                .border(
                    1.dp,
                    if (isSent) Color.Transparent else Color(0x20FFFFFF),
                    bubbleShape
                )
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (msg.replyTo != null) {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.width(3.dp).height(20.dp).background(Color(0x80FFFFFF)))
                    Spacer(Modifier.width(6.dp))
                    Text("↩ Replied message", color = Color(0xCCFFFFFF), fontSize = 11.sp)
                }
            }
            when (msg.kind) {
                MsgKind.IMAGE.name -> {
                    val path = msg.filePath
                    if (path != null) {
                        val bmp = remember(path) {
                            try { BitmapFactory.decodeFile(path) } catch (_: Exception) { null }
                        }
                        if (bmp != null) {
                            Image(
                                bitmap = bmp.asImageBitmap(), contentDescription = null,
                                modifier = Modifier.width(220.dp).heightIn(max = 280.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text, color = Color.White, fontSize = 13.sp)
                        }
                    } else {
                        Text("📷 ${msg.text}", color = TextPrimary, fontSize = 15.sp)
                    }
                }
                MsgKind.VIDEO.name -> Text("🎥 ${msg.text}", color = TextPrimary, fontSize = 15.sp)
                MsgKind.AUDIO.name -> Text("🎵 ${msg.text}", color = TextPrimary, fontSize = 15.sp)
                MsgKind.FILE.name -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = if (isSent) Color.White else Accent, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(msg.text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(FileUtil.humanSize(msg.fileSize), color = TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                else -> Text(msg.text, color = if (isSent) Color.White else TextPrimary, fontSize = 15.sp)
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (msg.isStarred) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(formatTime(msg.timestamp), color = if (isSent) Color(0xCCFFFFFF) else TextSecondary, fontSize = 11.sp)
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
        MsgStatus.SENDING -> Icon(Icons.Default.Schedule, null, tint = Color(0xAAFFFFFF), modifier = Modifier.size(14.dp))
        MsgStatus.SENT -> Icon(Icons.Default.Check, null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(14.dp))
        MsgStatus.DELIVERED -> Icon(Icons.Default.DoneAll, null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(14.dp))
        MsgStatus.READ -> Icon(Icons.Default.DoneAll, null, tint = TickRead, modifier = Modifier.size(14.dp))
    }
}

fun formatTime(ts: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))

// Helper for Purple color (to avoid name clash)
private fun Palette.Purple2() = Color(0xFF9C27B0)
