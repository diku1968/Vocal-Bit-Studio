package com.example.audio.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.audio.analysis.WavAudioDecoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

class VocalRecorder(private val context: Context) {

    private val sampleRate = 44100
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _liveAmplitude = MutableStateFlow(0f)
    val liveAmplitude: StateFlow<Float> = _liveAmplitude.asStateFlow()

    private val _liveWaveformHistory = MutableStateFlow<List<Float>>(emptyList())
    val liveWaveformHistory: StateFlow<List<Float>> = _liveWaveformHistory.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(onError: (String) -> Unit): Boolean {
        if (_isRecording.value) return false

        val minBufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        if (minBufferSize <= 0) {
            onError("AudioRecord initialization failed: unsupported buffer size.")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                onError("Microphone hardware failed to initialize.")
                return false
            }

            audioRecord?.startRecording()
            _isRecording.value = true
            _recordingDurationMs.value = 0L
            _liveWaveformHistory.value = emptyList()

            recordingJob = scope.launch(Dispatchers.IO) {
                val pcmOut = ByteArrayOutputStream()
                val shortBuffer = ShortArray(1024)
                val startTime = System.currentTimeMillis()
                val waveformPoints = mutableListOf<Float>()

                while (isActive && _isRecording.value) {
                    val readShorts = audioRecord?.read(shortBuffer, 0, shortBuffer.size) ?: 0
                    if (readShorts > 0) {
                        var sumSq = 0.0
                        for (i in 0 until readShorts) {
                            val s = shortBuffer[i]
                            // Write little-endian bytes
                            pcmOut.write(s.toInt() and 0xFF)
                            pcmOut.write((s.toInt() shr 8) and 0xFF)
                            sumSq += s * s
                        }

                        val rms = sqrt(sumSq / readShorts) / 32768.0
                        val normAmp = (rms * 2.5).toFloat().coerceIn(0f, 1f)
                        _liveAmplitude.value = normAmp

                        waveformPoints.add(normAmp)
                        if (waveformPoints.size > 80) waveformPoints.removeAt(0)
                        _liveWaveformHistory.value = waveformPoints.toList()

                        _recordingDurationMs.value = System.currentTimeMillis() - startTime
                    }
                }

                // Finish recording and write WAV
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (e: Exception) {
                    // ignore
                }
                audioRecord = null

                val pcmBytes = pcmOut.toByteArray()
                val totalShorts = pcmBytes.size / 2
                val floatSamples = FloatArray(totalShorts)
                val bb = java.nio.ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                for (i in 0 until totalShorts) {
                    floatSamples[i] = (bb.short / 32768.0f).coerceIn(-1.0f, 1.0f)
                }

                val timestamp = System.currentTimeMillis()
                val recordFile = File(context.filesDir, "recorded_vocal_$timestamp.wav")
                WavAudioDecoder.writeWavFile(recordFile, floatSamples, sampleRate, 1)

                // Post result to main thread or callback if needed
            }
            return true
        } catch (e: Exception) {
            onError("Recording error: ${e.message}")
            return false
        }
    }

    suspend fun stopRecording(): File? {
        if (!_isRecording.value) return null
        _isRecording.value = false
        recordingJob?.join()

        // Locate latest recorded file in filesDir
        val files = context.filesDir.listFiles { f -> f.name.startsWith("recorded_vocal_") && f.name.endsWith(".wav") }
        return files?.maxByOrNull { it.lastModified() }
    }

    fun cancelRecording() {
        _isRecording.value = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // ignore
        }
        audioRecord = null
    }
}
