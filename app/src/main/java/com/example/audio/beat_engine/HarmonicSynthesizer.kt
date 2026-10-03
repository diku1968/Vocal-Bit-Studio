package com.example.audio.beat_engine

import kotlin.math.*

object HarmonicSynthesizer {

    private const val SAMPLE_RATE = 44100

    /**
     * Synthesizes a warm analog-style bass note at given MIDI pitch and duration.
     */
    fun synthesizeBassNote(midiPitch: Int, durationSeconds: Double, velocity: Float = 0.85f): FloatArray {
        val totalSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val buffer = FloatArray(totalSamples)
        val freq = 440.0 * 2.0.pow((midiPitch - 69) / 12.0)

        val attackSamples = (SAMPLE_RATE * 0.015).toInt()
        val releaseSamples = (SAMPLE_RATE * 0.06).toInt()
        val sustainLevel = 0.85

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Fundamental + sub-oscillator + 2nd harmonic
            val wave = 0.65 * sin(2.0 * PI * freq * t) +
                    0.25 * sin(PI * freq * t) + // sub-octave
                    0.15 * sin(4.0 * PI * freq * t)

            // Warm saturation
            val saturated = tanh(wave * 1.4)

            // ADSR Envelope
            val env = when {
                i < attackSamples -> (i.toDouble() / attackSamples)
                i > totalSamples - releaseSamples -> sustainLevel * ((totalSamples - i).toDouble() / releaseSamples)
                else -> sustainLevel
            }

            buffer[i] = (saturated * env * velocity * 0.85).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    /**
     * Synthesizes a polyphonic chord voicing (lush electric piano / warm pad) at given pitches.
     */
    fun synthesizeChordVoicing(
        midiPitches: List<Int>,
        durationSeconds: Double,
        velocity: Float = 0.8f
    ): FloatArray {
        val totalSamples = (SAMPLE_RATE * durationSeconds).toInt()
        val buffer = FloatArray(totalSamples)

        val attackSamples = (SAMPLE_RATE * 0.04).toInt()
        val releaseSamples = (SAMPLE_RATE * 0.15).toInt()
        val sustainLevel = 0.70

        for (pitch in midiPitches) {
            val freq = 440.0 * 2.0.pow((pitch - 69) / 12.0)

            for (i in 0 until totalSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                // E-piano / warm synth pad harmonic structure:
                // Gentle chorus detune
                val detune = 0.6 * sin(2.0 * PI * 1.2 * t)
                val voice = 0.50 * sin(2.0 * PI * (freq + detune) * t) +
                        0.25 * sin(2.0 * PI * 2.0 * freq * t) +
                        0.15 * sin(2.0 * PI * 3.0 * freq * t) +
                        0.08 * sin(2.0 * PI * 4.0 * freq * t)

                val env = when {
                    i < attackSamples -> (i.toDouble() / attackSamples)
                    i > totalSamples - releaseSamples -> sustainLevel * ((totalSamples - i).toDouble() / releaseSamples).coerceAtLeast(0.0)
                    else -> 1.0 - (1.0 - sustainLevel) * (i.toDouble() / totalSamples)
                }

                buffer[i] += (voice * env * (velocity / midiPitches.size.coerceAtLeast(1))).toFloat()
            }
        }

        // Soft limit
        for (i in 0 until totalSamples) {
            buffer[i] = tanh(buffer[i] * 1.1).toFloat().coerceIn(-1f, 1f)
        }

        return buffer
    }
}
