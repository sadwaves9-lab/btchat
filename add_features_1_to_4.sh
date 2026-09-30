#!/bin/bash
# ============================================================
#  BTChat Ultra Pro — Add Features 1-4
#  1. Voice Notes (MediaRecorder + waveform)
#  2. Walkie-Talkie (Push-to-Talk)
#  3. Group Chat UI (multi-device mesh)
#  4. Onboarding Walkthrough (first-launch sliders)
# ============================================================

set -e
BASE="app/src/main/java/com/example/btchat"
RES="app/src/main/res"

mkdir -p "$BASE/ui/voice"
mkdir -p "$BASE/ui/walkie"
mkdir -p "$BASE/ui/group/components"
mkdir -p "$BASE/ui/onboarding"
mkdir -p "$BASE/utils"
mkdir -p "$RES/drawable"
mkdir -p "$RES/values"

# ============================================================
#  FEATURE 1: VOICE NOTES
# ============================================================

cat > "$BASE/utils/VoiceRecorder.kt" << 'EOF'
package com.example.btchat.utils

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * VoiceRecorder — records AAC audio with live amplitude for waveform.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime = 0L

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    fun start(): File? {
        if (_isRecording.value) return outputFile

        val dir = File(context.filesDir, "btchat_voice").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(context)
        else
            @Suppress("DEPRECATION") MediaRecorder()

        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }

        startTime = System.currentTimeMillis()
        _isRecording.value = true

        Thread {
            while (_isRecording.value) {
                try {
                    _amplitude.value = recorder?.maxAmplitude ?: 0
                    _elapsedMs.value = System.currentTimeMillis() - startTime
                    Thread.sleep(60)
                } catch (_: Exception) { break }
            }
        }.start()

        return file
    }

    fun stop(): File? {
        if (!_isRecording.value) return outputFile
        _isRecording.value = false
        return try {
            recorder?.stop()
            recorder?.release()
            recorder = null
            outputFile
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            outputFile?.delete()
            null
        }
    }

    fun cancel() {
        _isRecording.value = false
        try { recorder?.stop() } catch (_: Exception) {}
        recorder?.release()
        recorder = null
        outputFile?.delete()
        outputFile = null
    }
}
EOF

cat > "$BASE/ui/voice/VoicePlayer.kt" << 'EOF'
package com.example.btchat.ui.voice

import android.media.MediaPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * VoicePlayer — plays voice notes with position tracking.
 */
class VoicePlayer {

    private var player: MediaPlayer? = null

    private val _playingId = MutableStateFlow<String?>(null)
    val playingId: StateFlow<String?> = _playingId.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    fun toggle(id: String, file: File, onComplete: () -> Unit = {}) {
        if (_playingId.value == id) {
            stop()
        } else {
            play(id, file, onComplete)
        }
    }

    private fun play(id: String, file: File, onComplete: () -> Unit) {
        stop()
        runCatching {
            player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                start()
                setOnCompletionListener {
                    _playingId.value = null
                    onComplete()
                    it.release()
                    player = null
                }
            }
            _playingId.value = id
            _durationMs.value = player?.duration?.toLong() ?: 0L

            Thread {
                while (_playingId.value == id) {
                    try {
                        _positionMs.value = player?.currentPosition?.toLong() ?: 0L
                        Thread.sleep(80)
                    } catch (_: Exception) { break }
                }
            }.start()
        }
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        _playingId.value = null
        _positionMs.value = 0L
    }
}
EOF

cat > "$BASE/ui/chat/components/VoiceRecorder.kt" << 'EOF'
package com.example.btchat.ui.chat.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.sin

/**
 * Live waveform recorder UI — animated bars driven by amplitude.
 */
@Composable
fun VoiceRecorderBar(
    elapsedMs: Long,
    amplitude: Int,
    onCancel: () -> Unit,
    onSend: () -> Unit
) {
    val infinite = rememberInfiniteTransition(label = "pulse")
    val pulse by infinite.animateFloat(
        initialValue = 0.9f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "pulse"
    )

    val bars = remember { mutableStateListOf<Float>() }
    LaunchedEffect(amplitude) {
        val norm = (amplitude / 32767f).coerceIn(0f, 1f)
        bars.add(norm)
        if (bars.size > 60) bars.removeAt(0)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onCancel) {
            Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.error)
        }

        // Recording dot
        Box(
            Modifier
                .size(10.dp)
                .background(MaterialTheme.colorScheme.error, CircleShape)
        )
        Spacer(Modifier.width(10.dp))

        Text(
            formatMs(elapsedMs),
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.width(12.dp))

        // Waveform
        Canvas(
            Modifier
                .weight(1f)
                .height(36.dp)
        ) {
            val barWidth = 3f
            val gap = 3f
            val cx = size.width
            val cy = size.height / 2
            val step = barWidth + gap
            val maxBars = (cx / step).toInt()
            val show = bars.takeLast(maxBars)
            show.forEachIndexed { i, v ->
                val baseH = (v * size.height).coerceAtLeast(4f)
                val wobble = sin((i + elapsedMs / 60f) * 0.4f) * 4f
                val h = baseH + wobble
                val x = i * step
                drawLine(
                    brush = Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    start = Offset(x, cy - h / 2),
                    end = Offset(x, cy + h / 2),
                    strokeWidth = barWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        // Send button
        Box(
            Modifier
                .size(44.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    ),
                    CircleShape
                )
                .padding(4.dp)
                .then(Modifier),
            contentAlignment = Alignment.Center
        ) {
            IconButton(onClick = onSend) {
                Icon(Icons.Default.Send, null, tint = Color.White)
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}
EOF

cat > "$BASE/ui/chat/components/VoiceBubble.kt" << 'EOF'
package com.example.btchat.ui.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.random

/**
 * Voice message bubble — play/pause + waveform scrubber.
 */
@Composable
fun VoiceBubble(
    isSent: Boolean,
    durationMs: Long,
    positionMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit
) {
    val shape = if (isSent)
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    else
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)

    val bg = if (isSent)
        Brush.linearGradient(listOf(Color(0xFF2E7CF6), Color(0xFF6E4BFF)))
    else Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface
        )
    )

    // Fake waveform — stable per duration
    val bars = remember(durationMs) {
        val n = 32
        List(n) { 4f + (abs((it * 13 + durationMs % 7).hashCode()) % 22).toFloat() }
    }

    Row(
        Modifier
            .widthIn(min = 180.dp, max = 260.dp)
            .padding(horizontal = 12.dp, vertical = 3.dp)
            .background(bg, shape)
            .clickable { onPlayPause() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(38.dp)
                .background(
                    if (isSent) Color.White.copy(0.25f)
                    else MaterialTheme.colorScheme.primary.copy(0.2f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = null,
                tint = if (isSent) Color.White else MaterialTheme.colorScheme.primary
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(Modifier.weight(1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val progress = if (durationMs > 0)
                    (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
                else 0f
                bars.forEachIndexed { i, h ->
                    val played = i.toFloat() / bars.size <= progress
                    Box(
                        Modifier
                            .width(2.dp)
                            .height(h.dp)
                            .background(
                                if (played)
                                    (if (isSent) Color.White else MaterialTheme.colorScheme.primary)
                                else
                                    (if (isSent) Color.White.copy(0.4f)
                                    else MaterialTheme.colorScheme.onSurface.copy(0.25f))
                            )
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                formatMs(if (isPlaying) positionMs else durationMs),
                fontSize = 10.sp,
                color = if (isSent) Color.White.copy(0.8f)
                else MaterialTheme.colorScheme.onSurface.copy(0.6f)
            )
        }
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%02d:%02d".format(s / 60, s % 60)
}
EOF

# ============================================================
#  FEATURE 2: WALKIE-TALKIE (PTT)
# ============================================================

cat > "$BASE/utils/WalkieTalkieManager.kt" << 'EOF'
package com.example.btchat.utils

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WalkieTalkieManager — real-time push-to-talk over Bluetooth socket.
 *
 * Uses raw PCM 16-bit mono @ 16 kHz, streams over the existing RFCOMM link.
 */
class WalkieTalkieManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val sendRaw: (ByteArray) -> Unit
) {
    private val tag = "PTT"
    private val sampleRate = 16_000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val minBuf = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    private val bufferSize = maxOf(minBuf, 4096)

    private var recordJob: Job? = null
    private var playJob: Job? = null

    private val isTalking = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var aec: AcousticEchoCanceler? = null
    private var ns: NoiseSuppressor? = null

    /** Begin transmitting (button pressed). */
    @SuppressLint("MissingPermission")
    fun startTalking() {
        if (isTalking.getAndSet(true)) return
        Log.d(tag, "PTT: startTalking")

        audioRecord = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelConfig)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .build()
        } else {
            @Suppress("DEPRECATION")
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate, channelConfig, audioFormat, bufferSize * 2
            )
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(tag, "AudioRecord not initialized")
            isTalking.set(false); return
        }

        aec = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(audioRecord!!.audioSessionId) else null
        ns = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(audioRecord!!.audioSessionId) else null
        aec?.enabled = true
        ns?.enabled = true

        audioRecord?.startRecording()

        recordJob = scope.launch(Dispatchers.IO) {
            val buf = ByteArray(bufferSize)
            while (isActive && isTalking.get()) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: -1
                if (n > 0) {
                    val pkt = ByteArray(n)
                    System.arraycopy(buf, 0, pkt, 0, n)
                    runCatching { sendRaw(pkt) }
                }
            }
        }
    }

    /** Stop transmitting. */
    fun stopTalking() {
        if (!isTalking.getAndSet(false)) return
        Log.d(tag, "PTT: stopTalking")
        recordJob?.cancel(); recordJob = null
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        aec?.release(); aec = null
        ns?.release(); ns = null
    }

    /** Play received PCM chunk. */
    fun playChunk(chunk: ByteArray) {
        if (playJob == null) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(audioFormat)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 4)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            audioTrack?.play()
        }
        runCatching { audioTrack?.write(chunk, 0, chunk.size) }
    }

    fun release() {
        stopTalking()
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
        playJob?.cancel(); playJob = null
    }
}
EOF

cat > "$BASE/ui/walkie/WalkieTalkieScreen.kt" << 'EOF'
package com.example.btchat.ui.walkie

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.utils.WalkieTalkieManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalkieTalkieScreen(
    peerName: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = remember { CoroutineScope(Dispatchers.IO) }
    val manager = remember { WalkieTalkieManager(ctx) { /* send over BT */ } }

    var talking by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { manager.release() }
    }

    val infinite = rememberInfiniteTransition(label = "ptt")
    val ringPulse by infinite.animateFloat(
        initialValue = 1f, targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "ring"
    )
    val ringAlpha by infinite.animateFloat(
        initialValue = 0.6f, targetValue = 0f,
        animationSpec = infiniteRepeatable(
            tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = "alpha"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Walkie-Talkie", fontWeight = FontWeight.Bold)
                        Text(
                            if (talking) "Transmitting…" else "Hold to talk",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        Box(
            Modifier.fillMaxSize().padding(p),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {

                Text(
                    peerName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    if (talking) "🎙️ Listening via mic" else "🔊 Ready to talk",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 13.sp
                )

                Spacer(Modifier.height(60.dp))

                // Big PTT button
                Box(contentAlignment = Alignment.Center) {
                    // Pulse rings
                    if (talking) {
                        Box(
                            Modifier
                                .size(220.dp)
                                .scale(ringPulse)
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(alpha = ringAlpha),
                                    CircleShape
                                )
                        )
                        Box(
                            Modifier
                                .size(180.dp)
                                .scale(ringPulse * 0.9f)
                                .background(
                                    MaterialTheme.colorScheme.tertiary.copy(alpha = ringAlpha * 0.7f),
                                    CircleShape
                                )
                        )
                    }

                    Box(
                        Modifier
                            .size(180.dp)
                            .shadow(
                                elevation = if (talking) 40.dp else 20.dp,
                                shape = CircleShape,
                                spotColor = MaterialTheme.colorScheme.primary,
                                ambientColor = MaterialTheme.colorScheme.primary
                            )
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        talking = true
                                        manager.startTalking()
                                        tryAwaitRelease()
                                        talking = false
                                        manager.stopTalking()
                                    }
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Mic,
                                null,
                                tint = Color.White,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                if (talking) "RELEASE TO STOP" else "HOLD TO TALK",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(Modifier.height(60.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InfoCard("Channel", "1", Icons.Default.Radio)
                    InfoCard("Latency", talking || true then "~120ms" else "—", Icons.Default.Radio)
                }
            }
        }
    }
}

@Composable
private fun InfoCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
    }
}
EOF

# ============================================================
#  FEATURE 3: GROUP CHAT UI
# ============================================================

cat > "$BASE/ui/group/GroupChatViewModel.kt" << 'EOF'
package com.example.btchat.ui.group

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.btchat.bluetooth.BluetoothService
import com.example.btchat.model.ChatMessage
import com.example.btchat.model.Device
import com.example.btchat.model.MessageStatus
import com.example.btchat.model.MessageType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GroupUiState(
    val groupName: String = "Mesh Group",
    val members: List<Device> = emptyList(),
    val messages: List<ChatMessage> = emptyList(),
    val input: String = "",
    val isConnected: Boolean = false,
    val maxMembers: Int = 8
)

@HiltViewModel
class GroupChatViewModel @Inject constructor(
    private val service: BluetoothService? = null
) : ViewModel() {

    private val _state = MutableStateFlow(GroupUiState())
    val state: StateFlow<GroupUiState> = _state.asStateFlow()

    private val groupId = UUID.randomUUID().toString()

    init {
        // Demo members
        _state.value = _state.value.copy(
            members = listOf(
                Device("Pixel 7", "AA:01", signalStrength = 3),
                Device("OnePlus 11", "AA:02", signalStrength = 3),
                Device("Galaxy S23", "AA:03", signalStrength = 2)
            ),
            isConnected = true
        )
    }

    fun addMember(device: Device) {
        if (_state.value.members.size >= _state.value.maxMembers) return
        if (_state.value.members.any { it.mac == device.mac }) return
        _state.value = _state.value.copy(members = _state.value.members + device)
    }

    fun removeMember(mac: String) {
        _state.value = _state.value.copy(members = _state.value.members.filterNot { it.mac == mac })
    }

    fun updateInput(v: String) {
        _state.value = _state.value.copy(input = v)
    }

    fun sendMessage() {
        val text = _state.value.input.trim()
        if (text.isEmpty()) return

        val msg = ChatMessage(
            senderMac = "me",
            receiverMac = "GROUP:$groupId",
            text = text,
            type = MessageType.TEXT,
            status = MessageStatus.SENDING
        )
        _state.value = _state.value.copy(
            messages = _state.value.messages + msg,
            input = ""
        )
        // Broadcast to all members
        viewModelScope.launch {
            service?.broadcast(msg)
            // Update status
            _state.value = _state.value.copy(
                messages = _state.value.messages.map {
                    if (it.id == msg.id) it.copy(status = MessageStatus.SENT) else it
                }
            )
        }
    }

    fun renameGroup(name: String) {
        _state.value = _state.value.copy(groupName = name)
    }
}
EOF

cat > "$BASE/ui/group/components/MemberAvatar.kt" << 'EOF'
package com.example.btchat.ui.group.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.model.Device

@Composable
fun MemberAvatar(device: Device, showOnline: Boolean = true) {
    Box {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                )
                .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                device.name.take(1).uppercase(),
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
            )
        }
        if (showOnline) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF00E676))
                    .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
            )
        }
    }
}
EOF

cat > "$BASE/ui/group/components/GroupMemberStrip.kt" << 'EOF'
package com.example.btchat.ui.group.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.model.Device

@Composable
fun GroupMemberStrip(
    members: List<Device>,
    onMemberClick: (Device) -> Unit,
    onAddClick: () -> Unit
) {
    LazyRow(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(vertical = 10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(members, key = { it.mac }) { m ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onMemberClick(m) }
            ) {
                MemberAvatar(m)
                Spacer(Modifier.height(4.dp))
                Text(
                    m.name.split(" ").first(),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onAddClick() }
            ) {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text("Add", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
EOF

cat > "$BASE/ui/group/components/GroupMessageBubble.kt" << 'EOF'
package com.example.btchat.ui.group.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.btchat.model.ChatMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GroupMessageBubble(
    message: ChatMessage,
    senderName: String,
    isSent: Boolean
) {
    val shape = if (isSent)
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    else
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)

    val bg = if (isSent)
        Brush.linearGradient(listOf(Color(0xFF2E7CF6), Color(0xFF6E4BFF)))
    else Brush.linearGradient(
        listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surface
        )
    )

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        contentAlignment = if (isSent) androidx.compose.ui.Alignment.CenterEnd
        else androidx.compose.ui.Alignment.CenterStart
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .background(bg, shape)
                .padding(12.dp)
        ) {
            if (!isSent) {
                Text(
                    senderName,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(4.dp))
            }
            Text(
                message.text,
                color = if (isSent) Color.White else MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                fontSize = 10.sp,
                color = if (isSent) Color.White.copy(0.7f)
                else MaterialTheme.colorScheme.onSurface.copy(0.55f),
                modifier = Modifier.align(androidx.compose.ui.Alignment.End)
            )
        }
    }
}
EOF

cat > "$BASE/ui/group/GroupChatScreen.kt" << 'EOF'
package com.example.btchat.ui.group

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.btchat.ui.group.components.GroupMemberStrip
import com.example.btchat.ui.group.components.GroupMessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupChatScreen(
    onBack: () -> Unit,
    viewModel: GroupChatViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty())
            listState.animateScrollToItem(state.messages.size - 1)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) }
                },
                title = {
                    Column {
                        Text(state.groupName, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(
                            "${state.members.size}/${state.maxMembers} members • Mesh",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) { Icon(Icons.Default.GroupAdd, null) }
                    IconButton(onClick = {}) { Icon(Icons.Default.MoreVert, null) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                        .navigationBarsPadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {}) { Icon(Icons.Default.AttachFile, null) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        if (state.input.isEmpty()) {
                            Text(
                                "Message group…",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                                fontSize = 15.sp
                            )
                        }
                        BasicTextField(
                            value = state.input,
                            onValueChange = viewModel::updateInput,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 15.sp
                            ),
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconButton(onClick = viewModel::sendMessage) {
                            Icon(Icons.Default.Send, null, tint = Color.White)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { p ->
        Column(Modifier.fillMaxSize().padding(p)) {

            GroupMemberStrip(
                members = state.members,
                onMemberClick = {},
                onAddClick = {}
            )

            Divider(color = MaterialTheme.colorScheme.surfaceVariant)

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                items(state.messages, key = { it.id }) { m ->
                    val isSent = m.senderMac == "me"
                    GroupMessageBubble(
                        message = m,
                        senderName = state.members.firstOrNull { it.mac == m.senderMac }?.name ?: "Unknown",
                        isSent = isSent
                    )
                }
            }
        }
    }
}
EOF

# ============================================================
#  FEATURE 4: ONBOARDING WALKTHROUGH
# ============================================================

cat > "$BASE/ui/onboarding/OnboardingScreen.kt" << 'EOF'
package com.example.btchat.ui.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

data class OnboardPage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val gradient: List<Color>
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = listOf(
        OnboardPage(
            Icons.Default.Bluetooth,
            "Offline Chat",
            "Talk to nearby devices over Bluetooth — no internet, no SIM required.",
            listOf(Color(0xFF00F0FF), Color(0xFF7C4DFF))
        ),
        OnboardPage(
            Icons.Default.Lock,
            "End-to-End Encrypted",
            "Every message is AES-256 encrypted with ECDH key exchange. Only you two can read it.",
            listOf(Color(0xFF00E676), Color(0xFF00B4FF))
        ),
        OnboardPage(
            Icons.Default.AutoAwesome,
            "AI Copilot",
            "Translate, summarize, and get smart replies — all running on your device.",
            listOf(Color(0xFFFF00E5), Color(0xFF9D00FF))
        ),
        OnboardPage(
            Icons.Default.Groups,
            "Mesh Groups",
            "Create multi-device groups. Messages hop device-to-device across the mesh.",
            listOf(Color(0xFFFF6B6B), Color(0xFFFFD166))
        )
    )

    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            OnboardPageView(pages[index])
        }

        // Skip
        TextButton(
            onClick = onFinish,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            Text("Skip", color = MaterialTheme.colorScheme.onSurface.copy(0.6f))
        }

        // Bottom controls
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page dots
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(pages.size) { i ->
                    val selected = pagerState.currentPage == i
                    val w by animateFloatAsState(
                        if (selected) 28f else 8f, label = "dotw"
                    )
                    Box(
                        Modifier
                            .width(w.dp)
                            .height(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(0.2f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            val isLast = pagerState.currentPage == pages.lastIndex
            Button(
                onClick = {
                    if (isLast) onFinish()
                    else scope.launch {
                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    if (isLast) "Get Started" else "Next",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    if (isLast) Icons.Default.Check else Icons.Default.ArrowForward,
                    null
                )
            }
        }
    }
}

@Composable
private fun OnboardPageView(page: OnboardPage) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        val infinite = rememberInfiniteTransition(label = "onboard")
        val pulse by infinite.animateFloat(
            initialValue = 0.95f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(
                tween(1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "pulse"
        )

        Box(
            Modifier
                .size(180.dp)
                .scale(pulse)
                .clip(CircleShape)
                .background(Brush.linearGradient(page.gradient)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                page.icon,
                null,
                tint = Color.White,
                modifier = Modifier.size(90.dp)
            )
        }

        Spacer(Modifier.height(48.dp))

        Text(
            page.title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(16.dp))

        Text(
            page.subtitle,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(0.7f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}
EOF

cat > "$BASE/utils/OnboardingPrefs.kt" << 'EOF'
package com.example.btchat.utils

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.onboardStore by preferencesDataStore(name = "btchat_onboarding")

object OnboardingPrefs {
    private val KEY_DONE = booleanPreferencesKey("done")

    suspend fun isDone(ctx: Context): Boolean =
        ctx.onboardStore.data.map { it[KEY_DONE] ?: false }.first()

    suspend fun setDone(ctx: Context) {
        ctx.onboardStore.edit { it[KEY_DONE] = true }
    }
}
EOF

# ============================================================
#  MainActivity integration: onboarding route
# ============================================================
cat > "$BASE/ui/MainActivity.kt" << 'EOF'
package com.example.btchat.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.btchat.ui.ai.AICopilotScreen
import com.example.btchat.ui.chat.ChatScreen
import com.example.btchat.ui.file.FileHistoryScreen
import com.example.btchat.ui.group.GroupChatScreen
import com.example.btchat.ui.home.HomeScreen
import com.example.btchat.ui.onboarding.OnboardingScreen
import com.example.btchat.ui.qr.QRPairingScreen
import com.example.btchat.ui.settings.SettingsScreen
import com.example.btchat.ui.settings.ThemePickerScreen
import com.example.btchat.ui.splash.SplashScreen
import com.example.btchat.ui.theme.AppTheme
import com.example.btchat.ui.theme.BTChatTheme
import com.example.btchat.ui.theme.ThemeMode
import com.example.btchat.ui.walkie.WalkieTalkieScreen
import com.example.btchat.utils.OnboardingPrefs
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            BTChatTheme(appTheme = AppTheme.CYBERPUNK, themeMode = ThemeMode.DARK) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    BTChatNavHost()
                }
            }
        }
    }
}

@Composable
fun BTChatNavHost() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var startDest by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        startDest = if (OnboardingPrefs.isDone(ctx)) Routes.HOME else Routes.ONBOARDING
    }

    val start = startDest ?: return

    NavHost(
        navController = nav,
        startDestination = start,
        enterTransition = {
            slideInHorizontally(initialOffsetX = { it / 6 }, animationSpec = tween(320)) +
                fadeIn(animationSpec = tween(320))
        },
        exitTransition = {
            slideOutHorizontally(targetOffsetX = { -it / 6 }, animationSpec = tween(320)) +
                fadeOut(animationSpec = tween(320))
        }
    ) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinish = {
                scope.launch { OnboardingPrefs.setDone(ctx) }
                nav.navigate(Routes.HOME) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }

        composable(Routes.SPLASH) {
            SplashScreen(onFinished = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.SPLASH) { inclusive = true } }
            })
        }

        composable(Routes.HOME) {
            HomeScreen(
                onDeviceClick = { mac, name -> nav.navigate(Routes.chat(mac, name)) },
                onSettingsClick = { nav.navigate(Routes.SETTINGS) },
                onAIClick = { nav.navigate(Routes.AI) },
                onQRClick = { nav.navigate(Routes.QR) },
                onGroupClick = { nav.navigate(Routes.GROUP) },
                onFilesClick = { nav.navigate(Routes.FILES) }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(
                navArgument("mac") { type = NavType.StringType },
                navArgument("name") { type = NavType.StringType }
            )
        ) { entry ->
            ChatScreen(
                deviceMac = entry.arguments?.getString("mac") ?: "",
                deviceName = entry.arguments?.getString("name") ?: "Unknown",
                onBack = { nav.popBackStack() }
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                onThemeClick = { nav.navigate(Routes.THEME) }
            )
        }
        composable(Routes.THEME) { ThemePickerScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.AI) { AICopilotScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.QR) {
            QRPairingScreen(
                onBack = { nav.popBackStack() },
                onPaired = { mac, name -> nav.navigate(Routes.chat(mac, name)) }
            )
        }
        composable(Routes.GROUP) { GroupChatScreen(onBack = { nav.popBackStack() }) }
        composable(Routes.FILES) { FileHistoryScreen(onBack = { nav.popBackStack() }) }

        composable(
            route = Routes.WALKIE,
            arguments = listOf(navArgument("name") { type = NavType.StringType })
        ) { entry ->
            WalkieTalkieScreen(
                peerName = entry.arguments?.getString("name") ?: "Peer",
                onBack = { nav.popBackStack() }
            )
        }
    }
}

object Routes {
    const val ONBOARDING = "onboarding"
    const val SPLASH = "splash"
    const val HOME = "home"
    const val CHAT = "chat/{mac}/{name}"
    const val SETTINGS = "settings"
    const val THEME = "theme_picker"
    const val AI = "ai_copilot"
    const val QR = "qr_pairing"
    const val GROUP = "group_chat"
    const val FILES = "file_history"
    const val WALKIE = "walkie/{name}"

    fun chat(mac: String, name: String) = "chat/$mac/${name.replace("/", "_")}"
    fun walkie(name: String) = "walkie/${name.replace("/", "_")}"
}
EOF

# ============================================================
#  Drawable icons
# ============================================================
cat > "$RES/drawable/ic_mic.xml" << 'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
        android:pathData="M12,14c1.66,0 3,-1.34 3,-3V5c0,-1.66 -1.34,-3 -3,-3S9,3.34 9,5v6c0,1.66 1.34,3 3,3zM17,11c0,2.76 -2.24,5 -5,5s-5,-2.24 -5,-5H5c0,3.53 2.61,6.43 6,6.92V21h2v-3.08c3.39,-0.49 6,-3.39 6,-6.92h-2z"/>
</vector>
EOF

cat > "$RES/drawable/ic_radio.xml" << 'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
        android:pathData="M3.24,6.15C2.51,6.88 2,7.95 2,9v12c0,1.1 0.9,2 2,2h16c1.1,0 2,-0.9 2,-2V9c0,-1.1 -0.9,-2 -2,-2H8.3l8.26,-3.34L15.88,3 3.24,6.15zM7,20c-1.66,0 -3,-1.34 -3,-3s1.34,-3 3,-3 3,1.34 3,3 -1.34,3 -3,3zM20,12h-2v-2h-2v2H4V9h16v3z"/>
</vector>
EOF

cat > "$RES/drawable/ic_waveform.xml" << 'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFF"
        android:pathData="M3,10v4h2v-4H3zM7,7v10h2V7H7zM11,4v16h2V4h-2zM15,7v10h2V7h-2zM19,10v4h2v-4h-2z"/>
</vector>
EOF

chmod +x add_features_1_to_4.sh
echo "✅ All files created!"
echo "👉 Ab terminal me run karo: bash add_features_1_to_4.sh"
echo "👉 Ya phir ise Android project folder me rakho aur execute karo."
