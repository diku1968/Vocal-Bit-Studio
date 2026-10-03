package com.example.audio.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.audio.analysis.WavAudioDecoder
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.tanh

object AudioExporter {

    suspend fun exportAudio(
        context: Context,
        option: ExportOption,
        vocalBuffer: FloatArray,
        drumsBuffer: FloatArray,
        percBuffer: FloatArray,
        bassBuffer: FloatArray,
        chordsBuffer: FloatArray,
        channelStates: Map<MixerChannel, ChannelState>,
        masterVolume: Float,
        useLimiter: Boolean,
        bpm: Double,
        style: RhythmStyle,
        progressCallback: ((Float) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = System.currentTimeMillis()

        if (option == ExportOption.MIDI_RHYTHM) {
            progressCallback?.invoke(0.2f)
            val midiFile = File(exportDir, "VocalBeat_${style.id}_${bpm.toInt()}bpm_$timestamp.mid")
            exportMidiPattern(midiFile, bpm, style)
            progressCallback?.invoke(1.0f)
            return@withContext midiFile
        }

        // WAV mix export
        val maxSamples = maxOf(
            vocalBuffer.size,
            drumsBuffer.size,
            percBuffer.size,
            bassBuffer.size,
            chordsBuffer.size
        ).coerceAtLeast(44100)

        val mixed = FloatArray(maxSamples)
        val includeVocal = option == ExportOption.VOCAL_ONLY || option == ExportOption.VOCAL_BEAT || option == ExportOption.FULL_MIX
        val includeDrums = option == ExportOption.BEAT_ONLY || option == ExportOption.VOCAL_BEAT || option == ExportOption.FULL_MIX
        val includePerc = option == ExportOption.BEAT_ONLY || option == ExportOption.VOCAL_BEAT || option == ExportOption.FULL_MIX
        val includeBass = option == ExportOption.FULL_MIX
        val includeChords = option == ExportOption.FULL_MIX

        val vocalVol = if (includeVocal && !(channelStates[MixerChannel.VOCAL]?.isMuted ?: false)) (channelStates[MixerChannel.VOCAL]?.volume ?: 0.9f) else 0f
        val drumsVol = if (includeDrums && !(channelStates[MixerChannel.DRUMS]?.isMuted ?: false)) (channelStates[MixerChannel.DRUMS]?.volume ?: 0.85f) else 0f
        val percVol = if (includePerc && !(channelStates[MixerChannel.PERCUSSION]?.isMuted ?: false)) (channelStates[MixerChannel.PERCUSSION]?.volume ?: 0.85f) else 0f
        val bassVol = if (includeBass && !(channelStates[MixerChannel.BASS]?.isMuted ?: false)) (channelStates[MixerChannel.BASS]?.volume ?: 0.85f) else 0f
        val chordsVol = if (includeChords && !(channelStates[MixerChannel.CHORDS]?.isMuted ?: false)) (channelStates[MixerChannel.CHORDS]?.volume ?: 0.80f) else 0f

        val step = maxSamples / 20
        for (i in 0 until maxSamples) {
            var sum = 0f
            if (vocalVol > 0f && i < vocalBuffer.size) sum += vocalBuffer[i] * vocalVol
            if (drumsVol > 0f && i < drumsBuffer.size) sum += drumsBuffer[i] * drumsVol
            if (percVol > 0f && i < percBuffer.size) sum += percBuffer[i] * percVol
            if (bassVol > 0f && i < bassBuffer.size) sum += bassBuffer[i] * bassVol
            if (chordsVol > 0f && i < chordsBuffer.size) sum += chordsBuffer[i] * chordsVol

            var finalSample = sum * masterVolume
            if (useLimiter) {
                finalSample = tanh(finalSample * 1.05f).toFloat()
            }
            mixed[i] = finalSample.coerceIn(-1f, 1f)

            if (step > 0 && i % step == 0) {
                progressCallback?.invoke(0.1f + (i.toFloat() / maxSamples) * 0.7f)
            }
        }

        val wavFile = File(exportDir, "VocalBeat_${option.id}_${bpm.toInt()}bpm_$timestamp.wav")
        WavAudioDecoder.writeWavFile(wavFile, mixed, 44100, 1)

        progressCallback?.invoke(1.0f)
        wavFile
    }

    fun shareExportedFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mimeType = if (file.extension == "mid") "audio/midi" else "audio/wav"

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "VocalBeat AI Production: ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Export Track").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    /**
     * Writes standard Type 0 MIDI file for the rhythm tracks.
     */
    private fun exportMidiPattern(file: File, bpm: Double, style: RhythmStyle) {
        val trackData = ByteArrayOutputStream()
        val ticksPerBeat = 480
        val ticksPer16th = ticksPerBeat / 4

        // 1. Set Tempo Event (FF 51 03 tt tt tt)
        val usPerQuarter = (60000000.0 / bpm).toInt()
        writeVarLen(trackData, 0) // delta time
        trackData.write(0xFF)
        trackData.write(0x51)
        trackData.write(0x03)
        trackData.write((usPerQuarter shr 16) and 0xFF)
        trackData.write((usPerQuarter shr 8) and 0xFF)
        trackData.write(usPerQuarter and 0xFF)

        // Generate 4 bars of MIDI pattern on Channel 10 (percussion: 0x99 note on, 0x89 note off)
        val patternHits = listOf(
            Pair(0, 36), // Kick (C1)
            Pair(4, 38), // Snare (D1)
            Pair(8, 36),
            Pair(10, 36),
            Pair(12, 38),
            Pair(0, 42), // Closed hat (F#1)
            Pair(2, 42),
            Pair(4, 42),
            Pair(6, 42),
            Pair(8, 42),
            Pair(10, 42),
            Pair(12, 42),
            Pair(14, 42),
            Pair(3, 65), // Indian percussion Dholak Ta
            Pair(7, 67), // Dholak Dha
            Pair(11, 65),
            Pair(15, 67)
        )

        var lastTick = 0
        for (bar in 0 until 4) {
            val barStartTick = bar * 16 * ticksPer16th
            for ((step, pitch) in patternHits) {
                val hitTick = barStartTick + step * ticksPer16th
                val delta = hitTick - lastTick
                writeVarLen(trackData, delta.coerceAtLeast(0))
                trackData.write(0x99) // Note On channel 10
                trackData.write(pitch and 0x7F)
                trackData.write(100) // velocity

                // Note Off 60 ticks later
                writeVarLen(trackData, 60)
                trackData.write(0x89)
                trackData.write(pitch and 0x7F)
                trackData.write(0)

                lastTick = hitTick + 60
            }
        }

        // End of track event (FF 2F 00)
        writeVarLen(trackData, 0)
        trackData.write(0xFF)
        trackData.write(0x2F)
        trackData.write(0x00)

        val trackBytes = trackData.toByteArray()

        FileOutputStream(file).use { fos ->
            // Header chunk "MThd"
            fos.write("MThd".toByteArray())
            fos.write(byteArrayOf(0, 0, 0, 6)) // length 6
            fos.write(byteArrayOf(0, 0)) // format 0
            fos.write(byteArrayOf(0, 1)) // 1 track
            fos.write(byteArrayOf((ticksPerBeat shr 8).toByte(), (ticksPerBeat and 0xFF).toByte()))

            // Track chunk "MTrk"
            fos.write("MTrk".toByteArray())
            val len = trackBytes.size
            fos.write(byteArrayOf((len shr 24).toByte(), (len shr 16).toByte(), (len shr 8).toByte(), (len and 0xFF).toByte()))
            fos.write(trackBytes)
        }
    }

    private fun writeVarLen(out: ByteArrayOutputStream, value: Int) {
        var v = value
        val buffer = IntArray(4)
        var i = 0
        do {
            buffer[i++] = v and 0x7F
            v = v shr 7
        } while (v > 0)

        while (i > 0) {
            i--
            val b = if (i > 0) buffer[i] or 0x80 else buffer[i]
            out.write(b)
        }
    }
}
