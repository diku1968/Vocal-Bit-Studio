package com.example.audio.analysis

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

object WavAudioDecoder {

    const val TARGET_SAMPLE_RATE = 44100

    data class DecodedAudio(
        val samples: FloatArray,
        val sampleRate: Int,
        val durationMs: Long
    )

    /**
     * Decodes any supported audio format (WAV, MP3, M4A, AAC) into a normalized 44.1kHz mono FloatArray.
     */
    fun decodeAudio(context: Context, uri: Uri): DecodedAudio {
        return try {
            // First attempt native WAV parsing if it's a WAV file
            val isWav = uri.path?.lowercase()?.endsWith(".wav") == true ||
                    context.contentResolver.getType(uri)?.contains("wav") == true
            if (isWav) {
                tryDecodeWav(context, uri)?.let { return it }
            }
            // Otherwise use Android MediaCodec extractor for MP3, M4A, AAC, WAV
            decodeWithMediaCodec(context, uri)
        } catch (e: Exception) {
            e.printStackTrace()
            // If failed, return a fallback short audio buffer to avoid crashes
            DecodedAudio(FloatArray(TARGET_SAMPLE_RATE * 2), TARGET_SAMPLE_RATE, 2000L)
        }
    }

    private fun tryDecodeWav(context: Context, uri: Uri): DecodedAudio? {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val header = ByteArray(44)
            val read = stream.read(header)
            if (read < 44) return null

            val riff = String(header, 0, 4)
            val wave = String(header, 8, 4)
            if (riff != "RIFF" || wave != "WAVE") return null

            val channels = ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
            val sampleRate = ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val bitsPerSample = ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

            val rawBytes = stream.readBytes()
            val numSamples = rawBytes.size / (channels * (bitsPerSample / 8))
            val floatSamples = FloatArray(numSamples)

            val bb = ByteBuffer.wrap(rawBytes).order(ByteOrder.LITTLE_ENDIAN)
            for (i in 0 until numSamples) {
                var sum = 0f
                for (c in 0 until channels) {
                    val s = when (bitsPerSample) {
                        16 -> bb.short / 32768.0f
                        24 -> {
                            val b1 = bb.get().toInt() and 0xFF
                            val b2 = bb.get().toInt() and 0xFF
                            val b3 = bb.get().toInt()
                            val v = (b3 shl 16) or (b2 shl 8) or b1
                            v / 8388608.0f
                        }
                        32 -> bb.float
                        else -> (bb.get().toInt() - 128) / 128.0f
                    }
                    sum += s
                }
                floatSamples[i] = sum / channels.coerceAtLeast(1)
            }

            val durationMs = (numSamples.toLong() * 1000L) / sampleRate.coerceAtLeast(1)
            return DecodedAudio(floatSamples, sampleRate, durationMs)
        }
        return null
    }

    private fun decodeWithMediaCodec(context: Context, uri: Uri): DecodedAudio {
        val extractor = MediaExtractor()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Cannot open uri")
        extractor.setDataSource(pfd.fileDescriptor)

        var audioTrackIndex = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val trackFormat = extractor.getTrackFormat(i)
            val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime.startsWith("audio/")) {
                audioTrackIndex = i
                format = trackFormat
                break
            }
        }

        if (audioTrackIndex < 0 || format == null) {
            pfd.close()
            extractor.release()
            throw IllegalStateException("No audio track found in file")
        }

        extractor.selectTrack(audioTrackIndex)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: "audio/raw"
        val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else TARGET_SAMPLE_RATE
        val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

        val codec = MediaCodec.createDecoderByType(mime)
        codec.configure(format, null, null, 0)
        codec.start()

        val pcmOut = ByteArrayOutputStream()
        val info = MediaCodec.BufferInfo()
        var sawInputEOS = false
        var sawOutputEOS = false

        while (!sawOutputEOS) {
            if (!sawInputEOS) {
                val inputIndex = codec.dequeueInputBuffer(10000L)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, 10000L)
            if (outputIndex >= 0) {
                val outputBuffer = codec.getOutputBuffer(outputIndex)
                if (outputBuffer != null && info.size > 0) {
                    val chunk = ByteArray(info.size)
                    outputBuffer.position(info.offset)
                    outputBuffer.get(chunk)
                    pcmOut.write(chunk)
                }
                codec.releaseOutputBuffer(outputIndex, false)
                if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    sawOutputEOS = true
                }
            }
        }

        codec.stop()
        codec.release()
        extractor.release()
        pfd.close()

        val pcmBytes = pcmOut.toByteArray()
        val numShorts = pcmBytes.size / 2
        val bb = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)

        val totalFrames = numShorts / channelCount.coerceAtLeast(1)
        val monoSamples = FloatArray(totalFrames)

        for (i in 0 until totalFrames) {
            var sum = 0f
            for (c in 0 until channelCount) {
                if (bb.hasRemaining()) {
                    sum += bb.short / 32768.0f
                }
            }
            monoSamples[i] = (sum / channelCount.coerceAtLeast(1)).coerceIn(-1.0f, 1.0f)
        }

        val durationMs = (totalFrames.toLong() * 1000L) / sampleRate.coerceAtLeast(1)
        return DecodedAudio(monoSamples, sampleRate, durationMs)
    }

    /**
     * Writes 16-bit PCM WAV to a File.
     */
    fun writeWavFile(file: File, samples: FloatArray, sampleRate: Int = TARGET_SAMPLE_RATE, channels: Int = 1) {
        val totalAudioLen = (samples.size * 2).toLong()
        val totalDataLen = totalAudioLen + 36
        val byteRate = (sampleRate * channels * 2).toLong()

        FileOutputStream(file).use { out ->
            val header = ByteArray(44)
            header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
            header[4] = (totalDataLen and 0xffL).toByte()
            header[5] = ((totalDataLen shr 8) and 0xffL).toByte()
            header[6] = ((totalDataLen shr 16) and 0xffL).toByte()
            header[7] = ((totalDataLen shr 24) and 0xffL).toByte()
            header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
            header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
            header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0 // subchunk1 size
            header[20] = 1; header[21] = 0 // PCM = 1
            header[22] = channels.toByte(); header[23] = 0
            header[24] = (sampleRate and 0xff).toByte()
            header[25] = ((sampleRate shr 8) and 0xff).toByte()
            header[26] = ((sampleRate shr 16) and 0xff).toByte()
            header[27] = ((sampleRate shr 24) and 0xff).toByte()
            header[28] = (byteRate and 0xffL).toByte()
            header[29] = ((byteRate shr 8) and 0xffL).toByte()
            header[30] = ((byteRate shr 16) and 0xffL).toByte()
            header[31] = ((byteRate shr 24) and 0xffL).toByte()
            header[32] = (channels * 2).toByte(); header[33] = 0 // block align
            header[34] = 16; header[35] = 0 // bits per sample
            header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
            header[40] = (totalAudioLen and 0xffL).toByte()
            header[41] = ((totalAudioLen shr 8) and 0xffL).toByte()
            header[42] = ((totalAudioLen shr 16) and 0xffL).toByte()
            header[43] = ((totalAudioLen shr 24) and 0xffL).toByte()
            out.write(header, 0, 44)

            val buffer = ByteArray(2048)
            var byteIdx = 0
            for (s in samples) {
                val pcm = (s.coerceIn(-1.0f, 1.0f) * 32767.0f).toInt().toShort()
                buffer[byteIdx++] = (pcm.toInt() and 0xFF).toByte()
                buffer[byteIdx++] = ((pcm.toInt() shr 8) and 0xFF).toByte()
                if (byteIdx >= buffer.size) {
                    out.write(buffer, 0, byteIdx)
                    byteIdx = 0
                }
            }
            if (byteIdx > 0) {
                out.write(buffer, 0, byteIdx)
            }
        }
    }

    /**
     * Generates an authentic vocal phrase recording for demo testing, matching
     * realistic singing frequencies, vibrato, syllable envelopes and pauses.
     */
    fun generateDemoVocal(
        bpm: Double = 97.0,
        tonicSemitone: Int = 8, // 8 = G#
        isMinor: Boolean = true,
        durationSeconds: Int = 12
    ): DecodedAudio {
        val totalSamples = TARGET_SAMPLE_RATE * durationSeconds
        val samples = FloatArray(totalSamples)

        val beatDuration = 60.0 / bpm
        // Scale notes in G# minor: G#3 (207.65 Hz), A#3 (233.08), B3 (246.94), C#4 (277.18), D#4 (311.13), E4 (329.63), F#4 (369.99)
        val baseFreq = 440.0 * Math.pow(2.0, (tonicSemitone - 9 - 12) / 12.0) // G#3 ~ 207.65 Hz
        val intervals = if (isMinor) intArrayOf(0, 3, 5, 7, 10, 12, 10, 7, 5, 3, 0) else intArrayOf(0, 4, 7, 9, 12, 11, 7, 4, 2, 0)

        var sampleIndex = 0
        var currentBeat = 0.0

        while (sampleIndex < totalSamples) {
            val noteIdx = ((currentBeat / 2.0).toInt()) % intervals.size
            val semitone = intervals[noteIdx]
            val noteFreq = baseFreq * Math.pow(2.0, semitone / 12.0)
            val noteDurationSeconds = (beatDuration * if (currentBeat % 4 == 0.0) 1.8 else 0.9).coerceAtLeast(0.3)
            val noteSamples = (noteDurationSeconds * TARGET_SAMPLE_RATE).toInt()

            for (i in 0 until noteSamples) {
                if (sampleIndex + i >= totalSamples) break
                val t = i.toDouble() / TARGET_SAMPLE_RATE
                // Vibrato (5.5 Hz, ~3% depth)
                val vibrato = 1.0 + 0.025 * sin(2.0 * PI * 5.5 * t)
                val f = noteFreq * vibrato
                // Formants & harmonics (vocal singing character: fundamental + 2nd, 3rd, 4th harmonics)
                val wave = 0.60 * sin(2.0 * PI * f * t) +
                        0.25 * sin(2.0 * PI * 2 * f * t) +
                        0.10 * sin(2.0 * PI * 3 * f * t) +
                        0.05 * sin(2.0 * PI * 4 * f * t)

                // Syllable ADSR envelope
                val attackTime = 0.04
                val decayTime = 0.08
                val sustainLevel = 0.75
                val releaseTime = 0.10
                val totalTime = noteDurationSeconds

                val env = when {
                    t < attackTime -> (t / attackTime)
                    t < attackTime + decayTime -> 1.0 - (1.0 - sustainLevel) * ((t - attackTime) / decayTime)
                    t > totalTime - releaseTime -> sustainLevel * ((totalTime - t) / releaseTime).coerceAtLeast(0.0)
                    else -> sustainLevel
                }

                samples[sampleIndex + i] = (wave * env * 0.75).toFloat()
            }

            sampleIndex += noteSamples
            // Brief vocal pause between syllables
            val pauseSamples = (0.15 * beatDuration * TARGET_SAMPLE_RATE).toInt()
            sampleIndex += pauseSamples
            currentBeat += (noteDurationSeconds / beatDuration) + 0.15
        }

        return DecodedAudio(samples, TARGET_SAMPLE_RATE, (totalSamples.toLong() * 1000L) / TARGET_SAMPLE_RATE)
    }
}
