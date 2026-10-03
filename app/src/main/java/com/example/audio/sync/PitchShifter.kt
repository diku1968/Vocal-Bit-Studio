package com.example.audio.sync

import kotlin.math.*

object PitchShifter {

    /**
     * Shifts pitch by given semitones (-12 to +12) while preserving tempo using SOLA.
     */
    fun shiftPitch(
        input: FloatArray,
        semitones: Int,
        sampleRate: Int = 44100
    ): FloatArray {
        if (semitones == 0 || input.isEmpty()) return input.copyOf()

        val pitchRatio = 2.0.pow(semitones / 12.0)
        // 1. Resample by pitchRatio
        val resampled = resample(input, pitchRatio)

        // 2. Time-stretch by pitchRatio to restore original duration
        return timeStretchSola(resampled, pitchRatio, sampleRate, input.size)
    }

    private fun resample(input: FloatArray, ratio: Double): FloatArray {
        val outLen = (input.size / ratio).toInt().coerceAtLeast(1)
        val output = FloatArray(outLen)

        for (i in 0 until outLen) {
            val srcPos = i * ratio
            val srcIdx = srcPos.toInt()
            val frac = srcPos - srcIdx

            if (srcIdx + 1 < input.size) {
                // Linear interpolation
                output[i] = ((1.0 - frac) * input[srcIdx] + frac * input[srcIdx + 1]).toFloat()
            } else if (srcIdx < input.size) {
                output[i] = input[srcIdx]
            }
        }
        return output
    }

    /**
     * Synchronized Overlap-Add (SOLA) time stretching.
     */
    fun timeStretchSola(
        input: FloatArray,
        stretchFactor: Double,
        sampleRate: Int = 44100,
        targetLength: Int = (input.size * stretchFactor).toInt()
    ): FloatArray {
        if (abs(stretchFactor - 1.0) < 0.001) return input.copyOf()

        val windowSize = (sampleRate * 0.040).toInt().coerceAtLeast(512) // 40ms window
        val hopAnalysis = (windowSize / 2)
        val hopSynthesis = (hopAnalysis * stretchFactor).toInt().coerceAtLeast(128)
        val searchRange = windowSize / 4

        val output = FloatArray(targetLength)
        val window = FloatArray(windowSize) { i ->
            (0.5 - 0.5 * cos(2.0 * PI * i / (windowSize - 1))).toFloat()
        }

        var inPos = 0
        var outPos = 0

        while (inPos + windowSize < input.size && outPos + windowSize < targetLength) {
            // Find best cross-correlation overlap offset in search range
            var bestOffset = 0
            var bestCorr = -Float.MAX_VALUE

            if (outPos > 0) {
                for (offset in -searchRange..searchRange) {
                    val testIn = inPos + offset
                    if (testIn >= 0 && testIn + windowSize < input.size) {
                        var corr = 0f
                        for (i in 0 until windowSize step 4) {
                            corr += input[testIn + i] * output[outPos + i]
                        }
                        if (corr > bestCorr) {
                            bestCorr = corr
                            bestOffset = offset
                        }
                    }
                }
            }

            val actualIn = (inPos + bestOffset).coerceIn(0, input.size - windowSize)

            // Overlap add
            for (i in 0 until windowSize) {
                val outIdx = outPos + i
                if (outIdx < targetLength) {
                    val s = input[actualIn + i] * window[i]
                    output[outIdx] += s
                }
            }

            inPos += hopAnalysis
            outPos += hopSynthesis
        }

        // Normalize / soft limit
        var maxAmp = 0.001f
        for (v in output) if (abs(v) > maxAmp) maxAmp = abs(v)
        val gain = if (maxAmp > 0.95f) 0.95f / maxAmp else 1.0f
        for (i in output.indices) output[i] *= gain

        return output
    }
}
