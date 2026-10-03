package com.example.audio.beat_engine

import com.example.model.*
import kotlin.math.min

object ChordProgressionGenerator {

    data class ProgressionResult(
        val chordNames: List<String>,
        val chordVoicings: List<ChordVoicing>,
        val chordAudioBuffer: FloatArray,
        val bassAudioBuffer: FloatArray
    )

    fun generateChordsAndBass(
        key: MusicalKey,
        style: ChordProgressionStyle,
        bassPattern: BassPatternType,
        bpm: Double,
        durationMs: Long,
        firstDownbeatMs: Long,
        sampleRate: Int = 44100
    ): ProgressionResult {
        val totalSamples = ((durationMs.toDouble() / 1000.0) * sampleRate).toInt().coerceAtLeast(sampleRate)
        val chordBuffer = FloatArray(totalSamples)
        val bassBuffer = FloatArray(totalSamples)

        val barDurationSec = 240.0 / bpm
        val barDurationSamples = (barDurationSec * sampleRate).toInt()
        val numBars = ((durationMs - firstDownbeatMs) / (barDurationSec * 1000.0)).toInt().coerceAtLeast(1)

        val romanNumerals = getRomanProgression(style, key.scale)
        val chordVoicings = mutableListOf<ChordVoicing>()
        val chordNames = mutableListOf<String>()

        for (bar in 0 until numBars) {
            val numeral = romanNumerals[bar % romanNumerals.size]
            val chord = buildChord(numeral, key)
            chordVoicings.add(chord)
            if (bar < romanNumerals.size) {
                chordNames.add(chord.chordName)
            }

            val barStartSample = (((firstDownbeatMs + (bar * barDurationSec * 1000.0)) / 1000.0) * sampleRate).toInt()

            // 1. Synthesize chord pad
            val chordAudio = HarmonicSynthesizer.synthesizeChordVoicing(
                midiPitches = chord.midiPitches,
                durationSeconds = barDurationSec,
                velocity = 0.75f
            )

            val chordCopyLen = min(chordAudio.size, totalSamples - barStartSample)
            if (barStartSample in 0 until totalSamples) {
                for (i in 0 until chordCopyLen) {
                    chordBuffer[barStartSample + i] += chordAudio[i]
                }
            }

            // 2. Synthesize Bass notes for this bar based on pattern
            val bassEvents = getBassNotesForPattern(bassPattern, chord.rootPitch, barDurationSec)
            for (be in bassEvents) {
                val bassNoteAudio = HarmonicSynthesizer.synthesizeBassNote(
                    midiPitch = be.midiPitch,
                    durationSeconds = be.durationBeats * (60.0 / bpm),
                    velocity = be.velocity
                )
                val bassStartSample = barStartSample + ((be.startBeat * (60.0 / bpm)) * sampleRate).toInt()
                val bassCopyLen = min(bassNoteAudio.size, totalSamples - bassStartSample)
                if (bassStartSample in 0 until totalSamples) {
                    for (i in 0 until bassCopyLen) {
                        bassBuffer[bassStartSample + i] += bassNoteAudio[i]
                    }
                }
            }
        }

        // Limit buffers
        for (i in 0 until totalSamples) {
            chordBuffer[i] = chordBuffer[i].coerceIn(-1f, 1f)
            bassBuffer[i] = bassBuffer[i].coerceIn(-1f, 1f)
        }

        return ProgressionResult(
            chordNames = chordNames,
            chordVoicings = chordVoicings,
            chordAudioBuffer = chordBuffer,
            bassAudioBuffer = bassBuffer
        )
    }

    private fun getRomanProgression(style: ChordProgressionStyle, scale: ScaleType): List<String> {
        return if (scale == ScaleType.MAJOR) {
            when (style) {
                ChordProgressionStyle.SIMPLE -> listOf("I", "V", "vi", "IV")
                ChordProgressionStyle.EMOTIONAL -> listOf("vi", "IV", "I", "V")
                ChordProgressionStyle.ROMANTIC -> listOf("I", "vi", "IV", "V")
                ChordProgressionStyle.BOLLYWOOD -> listOf("I", "bVII", "IV", "I")
                ChordProgressionStyle.MODERN_POP -> listOf("I", "V", "vi", "IV", "ii", "V", "I", "I")
            }
        } else {
            // Minor scale progressions
            when (style) {
                ChordProgressionStyle.SIMPLE -> listOf("i", "VI", "III", "VII")
                ChordProgressionStyle.EMOTIONAL -> listOf("i", "iv", "VI", "V")
                ChordProgressionStyle.ROMANTIC -> listOf("i", "VI", "iv", "v")
                ChordProgressionStyle.BOLLYWOOD -> listOf("i", "VII", "VI", "V") // classic Bollywood minor cadence
                ChordProgressionStyle.MODERN_POP -> listOf("i", "VI", "iv", "VII")
            }
        }
    }

    private fun buildChord(numeral: String, key: MusicalKey): ChordVoicing {
        val tonicSemitone = key.tonic.semitoneFromC // 0 = C, 8 = G#, etc.
        val baseOctave = 48 // C3 for chords

        val (semitoneOffset, isMinor, chordSuffix) = when (numeral) {
            "I" -> Triple(0, false, "")
            "i" -> Triple(0, true, "m")
            "ii" -> Triple(2, true, "m")
            "iii" -> Triple(4, true, "m")
            "III" -> Triple(if (key.scale == ScaleType.MINOR) 3 else 4, false, "")
            "IV", "iv" -> Triple(5, numeral == "iv", if (numeral == "iv") "m" else "")
            "V", "v" -> Triple(7, numeral == "v", if (numeral == "v") "m" else "")
            "vi" -> Triple(9, true, "m")
            "VI" -> Triple(if (key.scale == ScaleType.MINOR) 8 else 9, false, "")
            "VII" -> Triple(if (key.scale == ScaleType.MINOR) 10 else 11, false, "")
            "bVII" -> Triple(10, false, "")
            else -> Triple(0, key.scale == ScaleType.MINOR, if (key.scale == ScaleType.MINOR) "m" else "")
        }

        val rootMidi = baseOctave + ((tonicSemitone + semitoneOffset) % 12)
        val thirdOffset = if (isMinor) 3 else 4
        val fifthOffset = 7

        val pitches = listOf(
            rootMidi,
            rootMidi + thirdOffset,
            rootMidi + fifthOffset,
            rootMidi + 12 // octave shine
        )

        val rootNoteName = NoteName.fromSemitone((tonicSemitone + semitoneOffset) % 12)
        val displayName = "${rootNoteName.displayName}$chordSuffix"

        return ChordVoicing(
            chordName = displayName,
            rootPitch = rootMidi - 12, // Bass octave (C2)
            midiPitches = pitches,
            durationBeats = 4.0
        )
    }

    private fun getBassNotesForPattern(
        pattern: BassPatternType,
        rootPitch: Int,
        barDurationSec: Double
    ): List<BassNote> {
        return when (pattern) {
            BassPatternType.ROOT -> listOf(
                BassNote(rootPitch, 0.0, 3.8, 0.90f)
            )
            BassPatternType.SIMPLE -> listOf(
                BassNote(rootPitch, 0.0, 0.9, 0.90f),
                BassNote(rootPitch, 1.0, 0.9, 0.75f),
                BassNote(rootPitch, 2.0, 0.9, 0.85f),
                BassNote(rootPitch, 3.0, 0.9, 0.75f)
            )
            BassPatternType.GROOVE -> listOf(
                BassNote(rootPitch, 0.0, 1.4, 0.95f),
                BassNote(rootPitch, 1.5, 0.4, 0.70f),
                BassNote(rootPitch + 7, 2.0, 0.8, 0.85f), // 5th accent
                BassNote(rootPitch, 3.0, 0.9, 0.80f)
            )
            BassPatternType.WALKING -> listOf(
                BassNote(rootPitch, 0.0, 0.9, 0.90f),
                BassNote(rootPitch + 4, 1.0, 0.9, 0.80f),
                BassNote(rootPitch + 7, 2.0, 0.9, 0.85f),
                BassNote(rootPitch + 5, 3.0, 0.9, 0.75f)
            )
        }
    }
}
