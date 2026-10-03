package com.example.audio.sync

import android.content.Context
import com.example.audio.analysis.WavAudioDecoder
import com.example.model.PhraseBoundary
import com.example.model.VocalSyncMode
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

object VocalSynchronizer {

    data class SyncResult(
        val syncedSamples: FloatArray,
        val syncedWavFile: File?
    )

    fun synchronizeVocal(
        context: Context,
        originalSamples: FloatArray,
        sampleRate: Int,
        bpm: Double,
        firstDownbeatMs: Long,
        onsetsMs: List<Long>,
        phrases: List<PhraseBoundary>,
        syncMode: VocalSyncMode
    ): SyncResult {
        if (syncMode == VocalSyncMode.OFF || originalSamples.isEmpty()) {
            return SyncResult(originalSamples.copyOf(), null)
        }

        val beatDurationMs = 60000.0 / bpm
        val sixteenthMs = beatDurationMs / 4.0
        val strength = syncMode.strength

        val output = originalSamples.copyOf()

        // Synchronize onsets by micro time-stretching each inter-phrase window
        for (onset in onsetsMs) {
            val relativeMs = onset - firstDownbeatMs
            if (relativeMs < 0) continue

            val nearestGridMs = (relativeMs / sixteenthMs).roundToInt() * sixteenthMs
            val deltaMs = (nearestGridMs - relativeMs) * strength // pull towards grid

            if (abs(deltaMs) > 10.0 && abs(deltaMs) < 120.0) {
                val startSample = ((onset / 1000.0) * sampleRate).toInt().coerceIn(0, output.size - 1)
                val windowSamples = ((0.180 * sampleRate).toInt()).coerceAtMost(output.size - startSample)

                val stretchRatio = (1.0 + (deltaMs / 180.0)).coerceIn(0.75, 1.30)
                if (windowSamples > 512) {
                    val slice = FloatArray(windowSamples) { i -> output[startSample + i] }
                    val stretched = PitchShifter.timeStretchSola(slice, stretchRatio, sampleRate, windowSamples)
                    for (i in 0 until windowSamples) {
                        output[startSample + i] = stretched[i]
                    }
                }
            }
        }

        // Save to synced_vocal.wav
        val syncedFile = try {
            val file = File(context.filesDir, "synced_vocal.wav")
            WavAudioDecoder.writeWavFile(file, output, sampleRate, 1)
            file
        } catch (e: Exception) {
            null
        }

        return SyncResult(output, syncedFile)
    }
}
