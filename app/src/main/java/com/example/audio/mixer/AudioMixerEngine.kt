package com.example.audio.mixer

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.model.ChannelState
import com.example.model.MixerChannel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

class AudioMixerEngine(
    private val sampleRate: Int = 44100
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null

    // Channel buffers (mono float arrays, normalized -1..1)
    private var vocalBuffer = FloatArray(0)
    private var drumsBuffer = FloatArray(0)
    private var percBuffer = FloatArray(0)
    private var bassBuffer = FloatArray(0)
    private var chordsBuffer = FloatArray(0)

    // Mixer states
    val channelStates = mutableMapOf<MixerChannel, MutableStateFlow<ChannelState>>()
    private val _masterVolume = MutableStateFlow(0.90f)
    val masterVolume: StateFlow<Float> = _masterVolume.asStateFlow()

    private val _isLimiterEnabled = MutableStateFlow(true)
    val isLimiterEnabled: StateFlow<Boolean> = _isLimiterEnabled.asStateFlow()

    // Playback state
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackPositionMs = MutableStateFlow(0L)
    val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()

    private val _totalDurationMs = MutableStateFlow(0L)
    val totalDurationMs: StateFlow<Long> = _totalDurationMs.asStateFlow()

    private val _isDownbeatActive = MutableStateFlow(false)
    val isDownbeatActive: StateFlow<Boolean> = _isDownbeatActive.asStateFlow()

    var onDownbeatTriggered: (() -> Unit)? = null

    // Beat grid sync
    var currentBpm: Double = 96.0
    var firstDownbeatMs: Long = 0L

    init {
        MixerChannel.entries.forEach { ch ->
            channelStates[ch] = MutableStateFlow(ChannelState())
        }
    }

    fun setBuffers(
        vocal: FloatArray,
        drums: FloatArray,
        percussion: FloatArray,
        bass: FloatArray,
        chords: FloatArray
    ) {
        vocalBuffer = vocal
        drumsBuffer = drums
        percBuffer = percussion
        bassBuffer = bass
        chordsBuffer = chords

        val maxLen = maxOf(
            vocal.size,
            drums.size,
            percBuffer.size,
            bassBuffer.size,
            chordsBuffer.size
        )
        val durMs = (maxLen.toLong() * 1000L) / sampleRate.coerceAtLeast(1)
        _totalDurationMs.value = durMs
    }

    fun updateVocalBuffer(vocal: FloatArray) {
        vocalBuffer = vocal
    }

    fun updateRhythmBuffers(drums: FloatArray, percussion: FloatArray) {
        drumsBuffer = drums
        percBuffer = percussion
    }

    fun updateHarmonicBuffers(bass: FloatArray, chords: FloatArray) {
        bassBuffer = bass
        chordsBuffer = chords
    }

    fun setChannelVolume(channel: MixerChannel, volume: Float) {
        channelStates[channel]?.let { flow ->
            flow.value = flow.value.copy(volume = volume.coerceIn(0f, 1f))
        }
    }

    fun toggleMute(channel: MixerChannel) {
        channelStates[channel]?.let { flow ->
            flow.value = flow.value.copy(isMuted = !flow.value.isMuted)
        }
    }

    fun toggleSolo(channel: MixerChannel) {
        channelStates[channel]?.let { flow ->
            val newSolo = !flow.value.isSolo
            flow.value = flow.value.copy(isSolo = newSolo)
        }
    }

    fun setMasterVolume(volume: Float) {
        _masterVolume.value = volume.coerceIn(0f, 1f)
    }

    fun setLimiterEnabled(enabled: Boolean) {
        _isLimiterEnabled.value = enabled
    }

    fun play() {
        if (_isPlaying.value) return
        startAudioTrack()
    }

    fun pause() {
        _isPlaying.value = false
        playbackJob?.cancel()
        audioTrack?.pause()
    }

    fun stop() {
        _isPlaying.value = false
        playbackJob?.cancel()
        try {
            audioTrack?.stop()
            audioTrack?.flush()
        } catch (e: Exception) {
            // ignore
        }
        _playbackPositionMs.value = 0L
    }

    fun seekTo(positionMs: Long) {
        val bounded = positionMs.coerceIn(0L, _totalDurationMs.value)
        _playbackPositionMs.value = bounded
    }

    private fun startAudioTrack() {
        val bufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_STEREO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(4096)

        if (audioTrack == null || audioTrack?.state != AudioTrack.STATE_INITIALIZED) {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize * 2)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }

        audioTrack?.play()
        _isPlaying.value = true

        playbackJob = scope.launch(Dispatchers.Default) {
            val chunkFrames = 1024
            val shortBuffer = ShortArray(chunkFrames * 2) // Stereo (L, R)
            var currentFrame = ((_playbackPositionMs.value.toDouble() / 1000.0) * sampleRate).toInt()

            val maxFrames = maxOf(
                vocalBuffer.size,
                drumsBuffer.size,
                percBuffer.size,
                bassBuffer.size,
                chordsBuffer.size
            )

            val beatIntervalFrames = ((60.0 / currentBpm.coerceAtLeast(40.0)) * sampleRate).toInt()
            val barIntervalFrames = beatIntervalFrames * 4
            val downbeatOffsetFrames = ((firstDownbeatMs.toDouble() / 1000.0) * sampleRate).toInt()

            while (isActive && _isPlaying.value) {
                if (maxFrames == 0) {
                    delay(50)
                    continue
                }

                if (currentFrame >= maxFrames) {
                    // Loop or stop
                    currentFrame = 0
                }

                // Check for solo state across any channel
                val anySolo = MixerChannel.entries.any {
                    it != MixerChannel.MASTER && (channelStates[it]?.value?.isSolo == true)
                }

                val masterGain = _masterVolume.value
                val useLimiter = _isLimiterEnabled.value

                for (i in 0 until chunkFrames) {
                    val frameIdx = currentFrame + i
                    var mixSample = 0f

                    if (frameIdx < maxFrames) {
                        // Check if hitting downbeat for visual pulse
                        val relFrame = frameIdx - downbeatOffsetFrames
                        if (relFrame >= 0 && relFrame % barIntervalFrames in 0..128) {
                            _isDownbeatActive.value = true
                            onDownbeatTriggered?.invoke()
                        } else if (_isDownbeatActive.value && relFrame % barIntervalFrames > 2000) {
                            _isDownbeatActive.value = false
                        }

                        // Mix each channel according to Mute/Solo/Volume
                        // 1. Vocal
                        mixSample += getChannelSample(MixerChannel.VOCAL, vocalBuffer, frameIdx, anySolo)
                        // 2. Drums
                        mixSample += getChannelSample(MixerChannel.DRUMS, drumsBuffer, frameIdx, anySolo)
                        // 3. Percussion
                        mixSample += getChannelSample(MixerChannel.PERCUSSION, percBuffer, frameIdx, anySolo)
                        // 4. Bass
                        mixSample += getChannelSample(MixerChannel.BASS, bassBuffer, frameIdx, anySolo)
                        // 5. Chords
                        mixSample += getChannelSample(MixerChannel.CHORDS, chordsBuffer, frameIdx, anySolo)
                    }

                    // Apply Master Volume
                    var finalSample = mixSample * masterGain

                    // Limiter (tanh saturation / soft knee compression)
                    if (useLimiter) {
                        finalSample = tanh(finalSample * 1.05f).toFloat()
                    }
                    val clamped = finalSample.coerceIn(-1.0f, 1.0f)
                    val pcmShort = (clamped * 32767.0f).toInt().toShort()

                    // Stereo interleaved (L, R)
                    shortBuffer[i * 2] = pcmShort
                    shortBuffer[i * 2 + 1] = pcmShort
                }

                audioTrack?.write(shortBuffer, 0, shortBuffer.size)
                currentFrame += chunkFrames

                val currentMs = (currentFrame.toLong() * 1000L) / sampleRate
                _playbackPositionMs.value = currentMs
            }
        }
    }

    private fun getChannelSample(
        channel: MixerChannel,
        buffer: FloatArray,
        index: Int,
        anySolo: Boolean
    ): Float {
        if (index >= buffer.size) return 0f
        val state = channelStates[channel]?.value ?: return buffer[index]
        if (state.isMuted) return 0f
        if (anySolo && !state.isSolo) return 0f
        return buffer[index] * state.volume
    }

    fun release() {
        stop()
        audioTrack?.release()
        audioTrack = null
        scope.cancel()
    }
}
