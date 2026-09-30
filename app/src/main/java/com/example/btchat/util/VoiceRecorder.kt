package com.example.btchat.util

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startTime = 0L

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _amplitude = MutableStateFlow(0)
    val amplitude: StateFlow<Int> = _amplitude.asStateFlow()

    private val _elapsed = MutableStateFlow(0L)
    val elapsed: StateFlow<Long> = _elapsed.asStateFlow()

    @Suppress("DEPRECATION")
    fun start(): File? {
        if (_isRecording.value) return outputFile
        val dir = File(context.filesDir, "voice").apply { mkdirs() }
        val file = File(dir, "voice_${System.currentTimeMillis()}.m4a")
        outputFile = file

        recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(context)
        else MediaRecorder()

        recorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(96_000)
            setAudioSamplingRate(44_100)
            setOutputFile(file.absolutePath)
            try {
                prepare()
                start()
                startTime = System.currentTimeMillis()
                _isRecording.value = true
                Thread {
                    while (_isRecording.value) {
                        try {
                            _amplitude.value = maxAmplitude
                            _elapsed.value = System.currentTimeMillis() - startTime
                            Thread.sleep(80)
                        } catch (_: Exception) { break }
                    }
                }.start()
            } catch (e: Exception) {
                release()
                return null
            }
        }
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
            release()
            outputFile?.delete()
            null
        }
    }

    fun cancel() {
        _isRecording.value = false
        try { recorder?.stop() } catch (_: Exception) { }
        release()
        outputFile?.delete()
        outputFile = null
    }

    private fun release() {
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
    }
}
