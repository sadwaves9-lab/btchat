@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

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
import androidx.compose.ui.draw.clip
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
import com.example.btchat.bluetooth.BtService
import com.example.btchat.bluetooth.ScanDevice
import com.example.btchat.data.AppDatabase
import com.example.btchat.data.DeviceAlias
import com.example.btchat.data.Message
import com.example.btchat.data.MsgKind
import com.example.btchat.data.MsgStatus
import com.example.btchat.notif.Notifier
import com.example.btchat.ui.screens.AIChatScreen
import com.example.btchat.ui.screens.GroupManageScreen
import com.example.btchat.ui.screens.ImageViewerScreen
import com.example.btchat.ui.screens.ScanScreen
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

object Palette {
    val DeepPurple = Color(0xFF6B46C1)
    val RoyalBlue = Color(0xFF3B82F6)
    val Cyan = Color(0xFF06B6D4)
    val Pink = Color(0xFFEC4899)
    val Rose = Color(0xFFF43F5E)
    val Emerald = Color(0xFF10B981)
    val Violet = Color(0xFF8B5CF6)
    val Amber = Color(0xFFF59E0B)
    val BgDeep = Color(0xFF0A0A15)
    val Surface = Color(0xFF12121E)
    val Glass = Color(0x15FFFFFF)
    val TextPrimary = Color(0xFFF5F5FA)
    val TextSecondary = Color(0xFFA0A0B8)
    val TickRead = Color(0xFF53BDEB)
}

object Grad {
    val purplePink = Brush.linearGradient(listOf(Palette.DeepPurple, Palette.Pink))
    val blueCyan = Brush.linearGradient(listOf(Palette.RoyalBlue, Palette.Cyan))
    val aurora = Brush.linearGradient(listOf(Palette.Violet, Palette.Cyan, Palette.Emerald))
    val sunset = Brush.linearGradient(listOf(Palette.Amber, Palette.Rose, Palette.Pink))
    val bgMain = Brush.linearGradient(
        listOf(Color(0xFF0A0A15), Color(0xFF14142A), Color(0xFF0D0D1F))
    )
}

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
        perms.add(Manifest.permission.RECORD_AUDIO)
        permLauncher.launch(perms.toTypedArray())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(background = Palette.BgDeep, surface = Palette.Surface)) {
                Surface(color = Palette.BgDeep) { App(bt) }
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

    val groupPrefs = remember { ctx.getSharedPreferences("group_members", Context.MODE_PRIVATE) }
    var groupMembers by remember {
        mutableStateOf(groupPrefs.getStringSet("members", emptySet()) ?: emptySet())
    }

    var viewingImage by remember { mutableStateOf<String?>(null) }

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
                        db.messageDao().insert(Message(
                            deviceMac = packet.fromMac, text = packet.text,
                            isSent = false, status = MsgStatus.DELIVERED.name))
                        val senderName = DeviceAlias.displayName(ctx, packet.fromMac,
                            devices[packet.fromMac] ?: "Unknown")
                        db.messageDao().insert(Message(
                            deviceMac = GROUP_ID, text = "$senderName: ${packet.text}",
                            isSent = false, status = MsgStatus.DELIVERED.name))
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
                            db.messageDao().insert(Message(
                                deviceMac = packet.fromMac, text = fileName, isSent = false,
                                status = MsgStatus.DELIVERED.name, kind = packet.kind,
                                filePath = outFile.absolutePath, fileName = fileName, fileSize = size))
                            val senderName = DeviceAlias.displayName(ctx, packet.fromMac,
                                devices[packet.fromMac] ?: "Unknown")
                            db.messageDao().insert(Message(
                                deviceMac = GROUP_ID, text = "$senderName: $fileName",
                                isSent = false, status = MsgStatus.DELIVERED.name,
                                kind = packet.kind, filePath = outFile.absolutePath,
                                fileName = fileName, fileSize = size))
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

    viewingImage?.let { path ->
        ImageViewerScreen(path, onBack = { viewingImage = null })
        return
    }

    when (screen) {
        "home" -> HomeScreen(
            ctx = ctx,
            devices = devices,
            connectedSet = connectedSet,
            onDeviceClick = { mac, name -> selectedMac = mac; selectedName = name; screen = "chat" },
            onGroupClick = { screen = "group" },
            onAIClick = { screen = "ai" },
            onScanClick = { screen = "scan" },
            onGroupManage = { screen = "manage_group" },
            onRefresh = {
                scope.launch {
                    val info = UpdateChecker.check(getVersionCode(ctx))
                    if (info != null) updateInfo = info
                    else Toast.makeText(ctx, "Already latest ✓", Toast.LENGTH_SHORT).show()
                }
            }
        )
        "chat" -> ChatScreen(
            mac = selectedMac ?: "", name = selectedName ?: "Chat",
            bt = bt, db = db, scope = scope, ctx = ctx,
            onBack = { screen = "home" },
            isGroup = false,
            onImageClick = { viewingImage = it }
        )
        "group" -> ChatScreen(
            mac = GROUP_ID, name = "Group Chat",
            bt = bt, db = db, scope = scope, ctx = ctx,
            onBack = { screen = "home" },
            isGroup = true,
            onImageClick = { viewingImage = it },
            groupMembers = groupMembers
        )
        "ai" -> AIChatScreen(onBack = { screen = "home" })
        "scan" -> ScanScreen(
            onBack = { screen = "home" },
            onConnect = { device ->
                try {
                    val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
                    val btDevice = adapter?.getRemoteDevice(device.mac)
                    if (btDevice != null) {
                        bt.connect(btDevice)
                        devices[device.mac] = device.name
                        DeviceAlias.set(ctx, device.mac, device.name)
                        Toast.makeText(ctx, "Connecting to ${device.name}…", Toast.LENGTH_SHORT).show()
                        selectedMac = device.mac
                        selectedName = device.name
                        screen = "chat"
                    } else {
                        Toast.makeText(ctx, "Invalid device", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(ctx, "Connect failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        )
        "manage_group" -> GroupManageScreen(
            allDevices = devices.entries.map { it.key to DeviceAlias.displayName(ctx, it.key, it.value) },
            initialSelected = groupMembers,
            onBack = { screen = "home" },
            onSave = { sel ->
                groupMembers = sel
                groupPrefs.edit().putStringSet("members", sel).apply()
                Toast.makeText(ctx, "Saved ${sel.size} members", Toast.LENGTH_SHORT).show()
                screen = "home"
            }
        )
    }
}

// ============================================================
//  HOME SCREEN
// ============================================================
@Composable
fun HomeScreen(
    ctx: Context,
    devices: Map<String, String>,
    connectedSet: Set<String>,
    onDeviceClick: (String, String) -> Unit,
    onGroupClick: () -> Unit,
    onAIClick: () -> Unit,
    onScanClick: () -> Unit,
    onGroupManage: () -> Unit,
    onRefresh: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var showRename by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var manualMac by remember { mutableStateOf("") }
    var manualName by remember { mutableStateOf("") }

    val filtered = devices.entries
        .filter { (mac, name) ->
            val display = DeviceAlias.displayName(ctx, mac, name)
            searchQuery.isBlank() || display.contains(searchQuery, ignoreCase = true) || mac.contains(searchQuery)
        }
        .sortedBy { DeviceAlias.displayName(ctx, it.key, it.value) }

    Box(Modifier.fillMaxSize().background(Grad.bgMain)) {
        Canvas2()

        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BTChat", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Palette.TextPrimary)
                Spacer(Modifier.weight(1f))
                GlassIconBtn(Icons.Default.Refresh, Grad.aurora, onRefresh)
                Spacer(Modifier.width(10.dp))
                GlassIconBtn(Icons.Default.Add, Grad.blueCyan) { showAdd = true }
                Spacer(Modifier.width(10.dp))
                Box {
                    GlassIconBtn(Icons.Default.MoreVert, Grad.purplePink) { showMenu = true }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Palette.Surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Scan Devices", color = Palette.TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.BluetoothSearching, null, tint = Palette.Cyan) },
                            onClick = { showMenu = false; onScanClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Manage Group", color = Palette.TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.GroupAdd, null, tint = Palette.Violet) },
                            onClick = { showMenu = false; onGroupManage() }
                        )
                        DropdownMenuItem(
                            text = { Text("AI Chat", color = Palette.TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.AutoAwesome, null, tint = Palette.Cyan) },
                            onClick = { showMenu = false; onAIClick() }
                        )
                        DropdownMenuItem(
                            text = { Text("Refresh", color = Palette.TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Refresh, null, tint = Palette.Emerald) },
                            onClick = { showMenu = false; onRefresh() }
                        )
                        DropdownMenuItem(
                            text = { Text("About", color = Palette.TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Info, null, tint = Palette.Amber) },
                            onClick = {
                                showMenu = false
                                Toast.makeText(ctx, "BTChat Ultra 2.0 · Offline BT Messenger", Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(24.dp)).background(Color(0x15FFFFFF))
                    .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Search, null, tint = Palette.TextSecondary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                BasicTextField(
                    value = searchQuery, onValueChange = { searchQuery = it }, singleLine = true,
                    textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty())
                            Text("Search devices…", color = Palette.TextSecondary, fontSize = 15.sp)
                        inner()
                    }
                )
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickCard("Scan", Icons.Default.BluetoothSearching, Grad.blueCyan, Modifier.weight(1f)) { onScanClick() }
                QuickCard("Group", Icons.Default.Groups, Grad.aurora, Modifier.weight(1f)) { onGroupClick() }
                QuickCard("AI", Icons.Default.AutoAwesome, Grad.purplePink, Modifier.weight(1f)) { onAIClick() }
                QuickCard("Members", Icons.Default.GroupAdd, Grad.sunset, Modifier.weight(1f)) { onGroupManage() }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                "DEVICES (${filtered.size})",
                Modifier.padding(start = 20.dp, bottom = 8.dp),
                fontSize = 11.sp, color = Palette.TextSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
            )

            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.key }) { entry ->
                    val mac = entry.key
                    val origName = entry.value
                    val dispName = DeviceAlias.displayName(ctx, mac, origName)
                    val online = connectedSet.contains(mac)
                    DeviceCard(dispName, mac, online,
                        onClick = { onDeviceClick(mac, dispName) },
                        onLongClick = { renameTarget = mac; renameValue = dispName; showRename = true })
                }
                if (filtered.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.BluetoothSearching, null, tint = Palette.TextSecondary, modifier = Modifier.size(64.dp))
                                Spacer(Modifier.height(14.dp))
                                Text(if (searchQuery.isBlank()) "No paired devices" else "No results",
                                    color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(Modifier.height(6.dp))
                                Text("Tap Scan to find nearby devices",
                                    color = Palette.TextSecondary, fontSize = 12.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRename) {
        AppDialog(
            title = "Rename Device",
            onDismiss = { showRename = false },
            onSave = {
                renameTarget?.let { mac -> if (renameValue.isNotBlank()) DeviceAlias.set(ctx, mac, renameValue.trim()) }
                showRename = false
            }
        ) {
            DialogField(renameValue) { renameValue = it }
        }
    }

    if (showAdd) {
        AppDialog(
            title = "Add Device",
            onDismiss = { showAdd = false },
            onSave = {
                if (manualMac.isNotBlank() && manualName.isNotBlank()) {
                    DeviceAlias.set(ctx, manualMac.trim(), manualName.trim())
                }
                manualMac = ""; manualName = ""
                showAdd = false
            }
        ) {
            Text("MAC Address", color = Palette.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            DialogField(manualMac, "AA:BB:CC:DD:EE:FF") { manualMac = it }
            Spacer(Modifier.height(10.dp))
            Text("Name", color = Palette.TextSecondary, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            DialogField(manualName, "Friend") { manualName = it }
        }
    }
}

@Composable
fun GlassIconBtn(icon: androidx.compose.ui.graphics.vector.ImageVector, brush: Brush, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(brush).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
}

@Composable
fun QuickCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, brush: Brush, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Color(0x15FFFFFF))
            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp))
            .clickable { onClick() }.padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(brush), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Palette.TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AppDialog(title: String, onDismiss: () -> Unit, onSave: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = Palette.TextPrimary) },
        text = { Column { content() } },
        confirmButton = { TextButton(onClick = onSave) { Text("Save", color = Palette.Emerald) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Palette.TextSecondary) } },
        containerColor = Palette.Surface
    )
}

@Composable
fun DialogField(value: String, placeholder: String = "", onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true,
        placeholder = { if (placeholder.isNotEmpty()) Text(placeholder, color = Palette.TextSecondary) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color(0x20FFFFFF), unfocusedContainerColor = Color(0x20FFFFFF),
            focusedTextColor = Palette.TextPrimary, unfocusedTextColor = Palette.TextPrimary,
            cursorColor = Palette.Emerald
        )
    )
}

@Composable
fun Canvas2() {
    androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
        val w = size.width; val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Palette.DeepPurple.copy(alpha = 0.3f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.15f), radius = 500f),
            radius = 500f,
            center = androidx.compose.ui.geometry.Offset(w * 0.2f, h * 0.15f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Palette.Pink.copy(alpha = 0.25f), Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.75f), radius = 450f),
            radius = 450f,
            center = androidx.compose.ui.geometry.Offset(w * 0.8f, h * 0.75f)
        )
    }
}

@Composable
fun DeviceCard(name: String, mac: String, online: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(listOf(Color(0x15FFFFFF), Color(0x08FFFFFF))))
            .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(20.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                Modifier.size(52.dp).clip(CircleShape)
                    .background(if (online) Grad.aurora else Brush.linearGradient(listOf(Color(0xFF2A2A40), Color(0xFF1A1A30)))),
                contentAlignment = Alignment.Center
            ) { Text(name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 22.sp) }
            Box(
                Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape)
                    .background(if (online) Palette.Emerald else Color(0xFF666680))
                    .border(2.dp, Palette.BgDeep, CircleShape)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(name, color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.height(3.dp))
            Text(if (online) "online" else mac, color = if (online) Palette.Emerald else Palette.TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.ChevronRight, null, tint = Palette.TextSecondary.copy(0.6f))
    }
}

// ============================================================
//  CHAT SCREEN
// ============================================================
@SuppressLint("MissingPermission")
@Composable
fun ChatScreen(
    mac: String, name: String,
    bt: BtService, db: AppDatabase, scope: CoroutineScope, ctx: Context,
    onBack: () -> Unit, isGroup: Boolean,
    onImageClick: (String) -> Unit,
    groupMembers: Set<String> = emptySet()
) {
    var input by remember { mutableStateOf("") }
    val messages by db.messageDao().messagesFor(mac).collectAsState(initial = emptyList())
    val connectedSet by bt.connectedList.collectAsState()
    val listState = rememberLazyListState()

    val imagePicker = rememberPicker { uri -> handlePicked(ctx, uri, "IMAGE", mac, isGroup, bt, db, scope) }
    val videoPicker = rememberPicker { uri -> handlePicked(ctx, uri, "VIDEO", mac, isGroup, bt, db, scope) }
    val audioPicker = rememberPicker { uri -> handlePicked(ctx, uri, "AUDIO", mac, isGroup, bt, db, scope) }
    val filePicker = rememberPicker { uri -> handlePicked(ctx, uri, "FILE", mac, isGroup, bt, db, scope) }

    var showAttach by remember { mutableStateOf(false) }
    var menuTarget by remember { mutableStateOf<Message?>(null) }
    var replyTarget by remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1)
    }

    LaunchedEffect(isGroup) {
        if (isGroup) {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            adapter?.bondedDevices?.forEach { d ->
                if (groupMembers.isEmpty() || groupMembers.contains(d.address)) {
                    bt.connect(d)
                }
            }
        }
    }

    LaunchedEffect(mac) {
        if (!isGroup && !connectedSet.contains(mac)) {
            val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            try { adapter?.getRemoteDevice(mac)?.let { bt.connect(it) } } catch (_: Exception) { }
        }
    }

    Box(Modifier.fillMaxSize().background(Grad.bgMain)) {
        Canvas2()

        Column(Modifier.fillMaxSize().statusBarsPadding()) {

            Row(
                Modifier.fillMaxWidth().background(Color(0x30FFFFFF)).padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Palette.TextPrimary) }
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
                    Text(name, color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        if (isGroup) "${connectedSet.size} online" else if (connectedSet.contains(mac)) "online" else "offline",
                        color = if (isGroup || connectedSet.contains(mac)) Palette.Emerald else Palette.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                var showMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, null, tint = Palette.TextPrimary)
                    }
                    DropdownMenu(
                        expanded = showMenu, onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(Palette.Surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Clear chat", color = Palette.TextPrimary) },
                            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Palette.Rose) },
                            onClick = {
                                showMenu = false
                                scope.launch { db.messageDao().clearAll(mac) }
                            }
                        )
                    }
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    MessageBubble(msg, onLongPress = { menuTarget = msg }, onImageClick = onImageClick)
                }
            }

            AnimatedVisibility(
                visible = replyTarget != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                replyTarget?.let { r ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp)).background(Color(0x20FFFFFF)).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).height(30.dp).background(Palette.Emerald))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Replying to:", color = Palette.Emerald, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(r.text, color = Palette.TextPrimary, fontSize = 13.sp, maxLines = 1)
                        }
                        IconButton(onClick = { replyTarget = null }) {
                            Icon(Icons.Default.Close, null, tint = Palette.TextSecondary)
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().background(Color(0x30FFFFFF)).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable { showAttach = true },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.AttachFile, null, tint = Palette.TextPrimary, modifier = Modifier.size(26.dp)) }

                Spacer(Modifier.width(4.dp))

                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(Color(0x20FFFFFF))
                        .border(1.dp, Color(0x15FFFFFF), RoundedCornerShape(24.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    BasicTextField(
                        value = input, onValueChange = { input = it },
                        textStyle = TextStyle(color = Palette.TextPrimary, fontSize = 15.sp),
                        modifier = Modifier.fillMaxWidth(), maxLines = 4,
                        decorationBox = { inner ->
                            if (input.isEmpty())
                                Text(if (isGroup) "Message group" else "Message", color = Palette.TextSecondary, fontSize = 15.sp)
                            inner()
                        }
                    )
                }

                Spacer(Modifier.width(6.dp))

                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(Grad.blueCyan)
                        .clickable {
                            val t = input.trim()
                            if (t.isEmpty()) return@clickable
                            val replyId = replyTarget?.id?.toString()
                            scope.launch {
                                if (isGroup) {
                                    db.messageDao().insert(Message(deviceMac = GROUP_ID, text = "Me: $t",
                                        isSent = true, status = MsgStatus.SENT.name, replyTo = replyId))
                                    bt.broadcast("Me: $t")
                                } else {
                                    val connected = connectedSet.contains(mac)
                                    val status = if (connected) MsgStatus.SENT else MsgStatus.SENDING
                                    db.messageDao().insert(Message(deviceMac = mac, text = t, isSent = true,
                                        status = status.name, pendingSend = !connected, replyTo = replyId))
                                    if (connected) bt.send(mac, t)
                                }
                            }
                            input = ""
                            replyTarget = null
                        },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Default.Send, null, tint = Color.White) }
            }
        }
    }

    if (showAttach) {
        ModalBottomSheet(onDismissRequest = { showAttach = false }, containerColor = Palette.Surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Send", color = Palette.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    AttachOpt(Icons.Default.Image, "Image", Color(0xFF9C27B0)) { showAttach = false; imagePicker.launch("image/*") }
                    AttachOpt(Icons.Default.Videocam, "Video", Palette.Rose) { showAttach = false; videoPicker.launch("video/*") }
                    AttachOpt(Icons.Default.Mic, "Audio", Palette.Amber) { showAttach = false; audioPicker.launch("audio/*") }
                    AttachOpt(Icons.Default.InsertDriveFile, "File", Palette.RoyalBlue) { showAttach = false; filePicker.launch("*/*") }
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }

    menuTarget?.let { msg ->
        AlertDialog(
            onDismissRequest = { menuTarget = null },
            title = { Text("Options", color = Palette.TextPrimary) },
            text = {
                Column {
                    MsgMenu(Icons.Default.Reply, "Reply") { replyTarget = msg; menuTarget = null }
                    MsgMenu(Icons.Default.ContentCopy, "Copy") {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("msg", msg.text))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show(); menuTarget = null
                    }
                    MsgMenu(Icons.Default.Star, if (msg.isStarred) "Unstar" else "Star") {
                        scope.launch { db.messageDao().star(msg.id, !msg.isStarred) }; menuTarget = null
                    }
                    MsgMenu(Icons.Default.Share, "Forward") {
                        val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, msg.text)
                        }
                        ctx.startActivity(android.content.Intent.createChooser(i, "Forward")); menuTarget = null
                    }
                    MsgMenu(Icons.Default.Delete, "Delete") {
                        scope.launch { db.messageDao().softDelete(msg.id) }; menuTarget = null
                    }
                }
            },
            confirmButton = {},
            containerColor = Palette.Surface
        )
    }
}

@Composable
fun MsgMenu(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Palette.TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, color = Palette.TextPrimary, fontSize = 15.sp)
    }
}

@Composable
fun AttachOpt(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = Palette.TextPrimary, fontSize = 12.sp)
    }
}

@Composable
fun rememberPicker(cb: (Uri) -> Unit) = androidx.activity.compose.rememberLauncherForActivityResult(
    ActivityResultContracts.GetContent()
) { uri -> uri?.let { cb(it) } }

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

@Composable
fun MessageBubble(msg: Message, onLongPress: () -> Unit, onImageClick: (String) -> Unit) {
    val isSent = msg.isSent
    val shape = if (isSent) RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
    else RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 300.dp).clip(shape)
                .background(
                    if (isSent) Grad.blueCyan
                    else Brush.linearGradient(listOf(Color(0x25FFFFFF), Color(0x15FFFFFF)))
                )
                .border(1.dp, if (isSent) Color.Transparent else Color(0x20FFFFFF), shape)
                .combinedClickable(onClick = {}, onLongClick = onLongPress)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (msg.replyTo != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(3.dp).height(20.dp).background(Color(0x80FFFFFF)))
                    Spacer(Modifier.width(6.dp))
                    Text("↩ Replied", color = Color(0xCCFFFFFF), fontSize = 11.sp)
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
                            Box(
                                Modifier.width(220.dp).heightIn(max = 280.dp).clip(RoundedCornerShape(12.dp))
                                    .clickable { onImageClick(path) }
                            ) {
                                Image(
                                    bitmap = bmp.asImageBitmap(), contentDescription = null,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                                    contentScale = ContentScale.Fit
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(msg.text, color = Color.White, fontSize = 13.sp)
                        }
                    } else {
                        Text("📷 ${msg.text}", color = Palette.TextPrimary, fontSize = 15.sp)
                    }
                }
                MsgKind.VIDEO.name -> Text("🎥 ${msg.text}", color = Palette.TextPrimary, fontSize = 15.sp)
                MsgKind.AUDIO.name -> Text("🎵 ${msg.text}", color = Palette.TextPrimary, fontSize = 15.sp)
                MsgKind.FILE.name -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.InsertDriveFile, null, tint = if (isSent) Color.White else Palette.Emerald, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(msg.text, color = Palette.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text(FileUtil.humanSize(msg.fileSize), color = Palette.TextSecondary, fontSize = 11.sp)
                        }
                    }
                }
                else -> Text(msg.text, color = if (isSent) Color.White else Palette.TextPrimary, fontSize = 15.sp)
            }
            Spacer(Modifier.height(3.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                if (msg.isStarred) {
                    Icon(Icons.Default.Star, null, tint = Color(0xFFFFD700), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(formatTime(msg.timestamp), color = if (isSent) Color(0xCCFFFFFF) else Palette.TextSecondary, fontSize = 11.sp)
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
        MsgStatus.READ -> Icon(Icons.Default.DoneAll, null, tint = Palette.TickRead, modifier = Modifier.size(14.dp))
    }
}

fun formatTime(ts: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
