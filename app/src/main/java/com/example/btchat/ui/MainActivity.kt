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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.bluetooth.BtService
import com.example.btchat.data.AppDatabase
import com.example.btchat.data.Message
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bt: BtService

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bt = BtService(this)

        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        permLauncher.launch(perms)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = MaterialTheme.colorScheme.background) {
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

    LaunchedEffect(Unit) {
        bt.startServer()
        val adapter = (ctx.getSystemService(Context.BLUETOOTH_SERVICE)
            as? BluetoothManager)?.adapter
        adapter?.bondedDevices?.forEach { d ->
            devices.add(d.address to (d.name ?: "Unknown"))
        }
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
            .padding(16.dp)
    ) {
        Text("BTChat", fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            if (connectedMac == null) "Not connected" else "Connected: $connectedMac",
            fontSize = 14.sp,
            color = if (connectedMac != null) Color(0xFF00E676) else Color.Gray
        )
        Spacer(Modifier.height(24.dp))
        Text("Paired Devices", fontSize = 14.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices) { pair ->
                val mac = pair.first
                val name = pair.second
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onDeviceClick(mac, name) }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(50)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            name.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(name, fontWeight = FontWeight.Bold)
                        Text(mac, fontSize = 12.sp, color = Color.Gray)
                    }
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

    LaunchedEffect(Unit) {
        bt.incoming.collect { pair ->
            val fromMac = pair.first
            val text = pair.second
            scope.launch {
                db.messageDao().insert(
                    Message(deviceMac = fromMac, text = text, isSent = false)
                )
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(name) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, null)
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        LazyColumn(
            Modifier
                .weight(1f)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (msg.isSent) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        Modifier
                            .widthIn(max = 280.dp)
                            .background(
                                if (msg.isSent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(16.dp)
                            )
                            .padding(10.dp)
                    ) {
                        Text(
                            msg.text,
                            color = if (msg.isSent) Color.White
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message…") },
                shape = RoundedCornerShape(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val t = input.trim()
                    if (t.isEmpty()) return@IconButton
                    bt.send(t)
                    scope.launch {
                        db.messageDao().insert(
                            Message(deviceMac = mac, text = t, isSent = true)
                        )
                    }
                    input = ""
                }
            ) {
                Icon(Icons.Default.Send, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
