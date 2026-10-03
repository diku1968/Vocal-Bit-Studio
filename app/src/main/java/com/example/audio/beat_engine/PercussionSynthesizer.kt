package com.example.audio.beat_engine

import com.example.model.InstrumentType
import java.util.Random
import kotlin.math.*

object PercussionSynthesizer {

    private const val SAMPLE_RATE = 44100
    private val random = Random(42)
    private val sampleCache = mutableMapOf<InstrumentType, FloatArray>()

    init {
        // Pre-warm cache for all percussion instruments
        InstrumentType.entries.forEach { instrument ->
            sampleCache[instrument] = synthesizeInstrument(instrument)
        }
    }

    fun getSample(instrument: InstrumentType): FloatArray {
        return sampleCache.getOrPut(instrument) { synthesizeInstrument(instrument) }
    }

    private fun synthesizeInstrument(instrument: InstrumentType): FloatArray {
        return when (instrument) {
            InstrumentType.KICK -> synthesizeKick()
            InstrumentType.SNARE -> synthesizeSnare()
            InstrumentType.CLAP -> synthesizeClap()
            InstrumentType.CLOSED_HAT -> synthesizeClosedHat()
            InstrumentType.OPEN_HAT -> synthesizeOpenHat()
            InstrumentType.TOM -> synthesizeTom()
            InstrumentType.SHAKER -> synthesizeShaker()
            InstrumentType.TAMBOURINE -> synthesizeTambourine()
            InstrumentType.TABLA_DHA -> synthesizeTablaDha()
            InstrumentType.TABLA_NA -> synthesizeTablaNa()
            InstrumentType.TABLA_GE -> synthesizeTablaGe()
            InstrumentType.DHOLAK_TA -> synthesizeDholakTa()
            InstrumentType.DHOLAK_DHA -> synthesizeDholakDha()
            InstrumentType.DHOL_BOOM -> synthesizeDholBoom()
            InstrumentType.DHOL_SLAP -> synthesizeDholSlap()
        }
    }

    private fun synthesizeKick(): FloatArray {
        val duration = 0.38
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        val startFreq = 145.0
        val endFreq = 42.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val freq = endFreq + (startFreq - endFreq) * exp(-14.0 * progress)
            val phase = 2.0 * PI * (endFreq * t + (startFreq - endFreq) / 14.0 * (1.0 - exp(-14.0 * progress)))
            val envelope = exp(-7.5 * progress)
            // Initial beater click
            val click = if (i < 200) (1.0 - i / 200.0) * (random.nextDouble() * 2.0 - 1.0) * 0.4 else 0.0

            buffer[i] = ((sin(phase) * envelope * 0.95) + click).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun synthesizeSnare(): FloatArray {
        val duration = 0.28
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            // Tone body (185 Hz decaying to 130 Hz)
            val toneFreq = 130.0 + 55.0 * exp(-20.0 * progress)
            val tone = sin(2.0 * PI * toneFreq * t) * exp(-15.0 * progress) * 0.45
            // Snappy noise wire
            val noise = (random.nextDouble() * 2.0 - 1.0) * exp(-10.0 * progress) * 0.70

            buffer[i] = (tone + noise).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun synthesizeClap(): FloatArray {
        val duration = 0.32
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        val flamOffsets = intArrayOf(0, (SAMPLE_RATE * 0.012).toInt(), (SAMPLE_RATE * 0.024).toInt())

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            var sample = 0.0
            for (offset in flamOffsets) {
                if (i >= offset) {
                    val dt = (i - offset).toDouble() / SAMPLE_RATE
                    val env = exp(-28.0 * dt)
                    sample += (random.nextDouble() * 2.0 - 1.0) * env * 0.40
                }
            }
            // Tail reverb
            if (i > flamOffsets.last()) {
                val dt = (i - flamOffsets.last()).toDouble() / SAMPLE_RATE
                sample += (random.nextDouble() * 2.0 - 1.0) * exp(-11.0 * dt) * 0.35
            }
            buffer[i] = sample.toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun synthesizeClosedHat(): FloatArray {
        val duration = 0.065
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        // Metallic ring mod frequencies
        val f1 = 2800.0; val f2 = 3650.0; val f3 = 4780.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val metallic = sin(2.0 * PI * f1 * t) * sin(2.0 * PI * f2 * t) * 0.3 + sin(2.0 * PI * f3 * t) * 0.2
            val noise = (random.nextDouble() * 2.0 - 1.0) * 0.5
            val env = exp(-32.0 * progress)
            buffer[i] = ((metallic + noise) * env).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun synthesizeOpenHat(): FloatArray {
        val duration = 0.38
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        val f1 = 3100.0; val f2 = 4200.0; val f3 = 5400.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val metallic = sin(2.0 * PI * f1 * t) * 0.25 + sin(2.0 * PI * f2 * t) * 0.25 + sin(2.0 * PI * f3 * t) * 0.2
            val noise = (random.nextDouble() * 2.0 - 1.0) * 0.45
            val env = exp(-7.5 * progress)
            buffer[i] = ((metallic + noise) * env).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun synthesizeTom(): FloatArray {
        val duration = 0.30
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val freq = 75.0 + 80.0 * exp(-15.0 * progress)
            val tone = sin(2.0 * PI * freq * t) * exp(-9.0 * progress) * 0.85
            buffer[i] = tone.toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun synthesizeShaker(): FloatArray {
        val duration = 0.11
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            // Soft attack, quick decay
            val attack = min(1.0, progress * 4.0)
            val decay = exp(-18.0 * progress)
            val noise = (random.nextDouble() * 2.0 - 1.0) * 0.65
            buffer[i] = (noise * attack * decay).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    private fun synthesizeTambourine(): FloatArray {
        val duration = 0.18
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        val jingleFreqs = doubleArrayOf(4500.0, 5800.0, 7200.0, 8900.0)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            var jingle = 0.0
            for (f in jingleFreqs) {
                jingle += sin(2.0 * PI * f * t) * 0.15
            }
            val noise = (random.nextDouble() * 2.0 - 1.0) * 0.40
            val env = exp(-12.0 * progress)
            buffer[i] = ((jingle + noise) * env).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    /**
     * Tabla Bayan (Bass "Dha"): Resonant sliding pitch bend with warm skin resonance.
     */
    private fun synthesizeTablaDha(): FloatArray {
        val duration = 0.45
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        val fStart = 88.0
        val fEnd = 72.0

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            // Characteristic subtle pitch bend on Indian Bayan
            val freq = fEnd + (fStart - fEnd) * exp(-6.0 * progress)
            val tone = sin(2.0 * PI * freq * t) * 0.70 + sin(2.0 * PI * freq * 2.0 * t) * 0.25
            val env = exp(-6.5 * progress)
            // Rim harmonic slap
            val rim = if (i < 300) sin(2.0 * PI * 340.0 * t) * exp(-35.0 * progress) * 0.35 else 0.0

            buffer[i] = ((tone + rim) * env).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    /**
     * Tabla Dayan (Treble "Na"): High metallic bell-like ringing harmonic (~420 Hz).
     */
    private fun synthesizeTablaNa(): FloatArray {
        val duration = 0.35
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        val ringFreq = 425.0
        val harmonicFreq = ringFreq * 2.2

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val fundamental = sin(2.0 * PI * ringFreq * t) * exp(-7.0 * progress) * 0.65
            val harmonic = sin(2.0 * PI * harmonicFreq * t) * exp(-12.0 * progress) * 0.30
            val click = if (i < 150) (random.nextDouble() * 2.0 - 1.0) * (1.0 - i / 150.0) * 0.35 else 0.0

            buffer[i] = (fundamental + harmonic + click).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    /**
     * Tabla Bayan (Sliding Modulation "Ge")
     */
    private fun synthesizeTablaTablaGe(): FloatArray {
        return synthesizeTablaGe()
    }

    private fun synthesizeTablaGe(): FloatArray {
        val duration = 0.40
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            // Upward then downward slide
            val freq = 65.0 + 25.0 * sin(PI * progress)
            val tone = sin(2.0 * PI * freq * t) * exp(-6.0 * progress) * 0.85
            buffer[i] = tone.toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    /**
     * Dholak Ta: Sharp treble slap
     */
    private fun synthesizeDholakTa(): FloatArray {
        val duration = 0.22
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val tone = sin(2.0 * PI * 380.0 * t) * exp(-16.0 * progress) * 0.50
            val slap = (random.nextDouble() * 2.0 - 1.0) * exp(-24.0 * progress) * 0.55
            buffer[i] = (tone + slap).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    /**
     * Dholak Dha: Warm punchy folk bass
     */
    private fun synthesizeDholakDha(): FloatArray {
        val duration = 0.36
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val freq = 62.0 + 35.0 * exp(-10.0 * progress)
            val bass = sin(2.0 * PI * freq * t) * exp(-7.0 * progress) * 0.80
            val rim = if (i < 250) sin(2.0 * PI * 290.0 * t) * 0.3 else 0.0
            buffer[i] = (bass + rim).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    /**
     * Punjabi Dhol Boom: Heavy low punch
     */
    private fun synthesizeDholBoom(): FloatArray {
        val duration = 0.48
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val freq = 50.0 + 55.0 * exp(-8.0 * progress)
            val boom = sin(2.0 * PI * freq * t) * exp(-5.0 * progress) * 0.95
            buffer[i] = boom.toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }

    /**
     * Punjabi Dhol Slap: High tension stick strike
     */
    private fun synthesizeDholSlap(): FloatArray {
        val duration = 0.20
        val totalSamples = (SAMPLE_RATE * duration).toInt()
        val buffer = FloatArray(totalSamples)

        for (i in 0 until totalSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            val progress = t / duration
            val stick = sin(2.0 * PI * 680.0 * t) * exp(-18.0 * progress) * 0.45
            val crack = (random.nextDouble() * 2.0 - 1.0) * exp(-22.0 * progress) * 0.60
            buffer[i] = (stick + crack).toFloat().coerceIn(-1f, 1f)
        }
        return buffer
    }
}
