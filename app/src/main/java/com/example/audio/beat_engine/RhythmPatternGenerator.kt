package com.example.audio.beat_engine

import com.example.model.*
import kotlin.math.min

object RhythmPatternGenerator {

    data class TimedPercussionHit(
        val sampleOffset: Int,
        val instrument: InstrumentType,
        val velocity: Float
    )

    fun generateRhythmTrack(
        style: RhythmStyle,
        complexity: RhythmComplexity,
        bpm: Double,
        durationMs: Long,
        firstDownbeatMs: Long,
        swingPercent: Int = 0,
        tempoMode: TempoMode = TempoMode.FIXED_TEMPO,
        tempoMap: List<BarTempo> = emptyList(),
        sampleRate: Int = 44100
    ): Pair<FloatArray, FloatArray> { // Returns Pair<DrumsFloatArray, PercussionFloatArray>
        val totalSamples = ((durationMs.toDouble() / 1000.0) * sampleRate).toInt().coerceAtLeast(sampleRate)
        val drumBuffer = FloatArray(totalSamples)
        val percBuffer = FloatArray(totalSamples)

        val hits = generateTimedHits(
            style = style,
            complexity = complexity,
            bpm = bpm,
            durationMs = durationMs,
            firstDownbeatMs = firstDownbeatMs,
            swingPercent = swingPercent,
            tempoMode = tempoMode,
            tempoMap = tempoMap,
            sampleRate = sampleRate
        )

        // Render hits into respective buffers
        for (hit in hits) {
            val sample = PercussionSynthesizer.getSample(hit.instrument)
            val target = if (hit.instrument.category == MixerChannel.DRUMS) drumBuffer else percBuffer

            val start = hit.sampleOffset
            val len = min(sample.size, totalSamples - start)
            for (i in 0 until len) {
                target[start + i] += sample[i] * hit.velocity
            }
        }

        // Soft clip to prevent digital clipping
        for (i in 0 until totalSamples) {
            drumBuffer[i] = drumBuffer[i].coerceIn(-1f, 1f)
            percBuffer[i] = percBuffer[i].coerceIn(-1f, 1f)
        }

        return Pair(drumBuffer, percBuffer)
    }

    private fun generateTimedHits(
        style: RhythmStyle,
        complexity: RhythmComplexity,
        bpm: Double,
        durationMs: Long,
        firstDownbeatMs: Long,
        swingPercent: Int,
        tempoMode: TempoMode,
        tempoMap: List<BarTempo>,
        sampleRate: Int
    ): List<TimedPercussionHit> {
        val hits = mutableListOf<TimedPercussionHit>()
        val defaultBarDurationMs = 240000.0 / bpm
        val numBars = ((durationMs - firstDownbeatMs).toDouble() / defaultBarDurationMs).toInt().coerceAtLeast(1)

        val stepsPerBar = if (style == RhythmStyle.GARBA) 12 else 16

        for (bar in 0 until numBars) {
            val barStartMs = if (tempoMode == TempoMode.VOCAL_FOLLOW && bar < tempoMap.size) {
                tempoMap[bar].startMs
            } else {
                firstDownbeatMs + (bar * defaultBarDurationMs).toLong()
            }

            val barDurMs = if (tempoMode == TempoMode.VOCAL_FOLLOW && bar < tempoMap.size) {
                tempoMap[bar].endMs - tempoMap[bar].startMs
            } else {
                defaultBarDurationMs.toLong()
            }

            val isFillBar = complexity == RhythmComplexity.ADVANCED && ((bar + 1) % 4 == 0)

            val patternHits = getPatternForStyle(style, complexity, isFillBar)

            for (hit in patternHits) {
                val stepFraction = hit.stepIndex.toDouble() / stepsPerBar
                // Calculate swing for offbeat 16th notes (odd step index)
                var swingOffsetMs = 0.0
                if (hit.stepIndex % 2 == 1 && swingPercent > 0) {
                    val stepDurationMs = barDurMs.toDouble() / stepsPerBar
                    swingOffsetMs = stepDurationMs * (swingPercent / 100.0) * 0.40
                }

                val hitTimeMs = barStartMs + (stepFraction * barDurMs) + swingOffsetMs
                val sampleOffset = ((hitTimeMs / 1000.0) * sampleRate).toInt()

                if (sampleOffset >= 0 && sampleOffset < (durationMs / 1000.0 * sampleRate)) {
                    hits.add(TimedPercussionHit(sampleOffset, hit.instrument, hit.velocity))
                }
            }
        }

        return hits
    }

    private fun getPatternForStyle(
        style: RhythmStyle,
        complexity: RhythmComplexity,
        isFillBar: Boolean
    ): List<RhythmHit> {
        if (isFillBar) {
            return getFillPattern(style)
        }

        return when (style) {
            RhythmStyle.BOLLYWOOD -> getBollywoodPattern(complexity)
            RhythmStyle.ROMANTIC_BOLLYWOOD -> getRomanticBollywoodPattern(complexity)
            RhythmStyle.BOLLYWOOD_DANCE -> getBollywoodDancePattern(complexity)
            RhythmStyle.INDIAN_POP -> getIndianPopPattern(complexity)
            RhythmStyle.ACOUSTIC -> getAcousticPattern(complexity)
            RhythmStyle.ROCK -> getRockPattern(complexity)
            RhythmStyle.POP -> getPopPattern(complexity)
            RhythmStyle.HIP_HOP -> getHipHopPattern(complexity)
            RhythmStyle.LO_FI -> getLoFiPattern(complexity)
            RhythmStyle.GARBA -> getGarbaPattern(complexity)
            RhythmStyle.GUJARATI_FOLK -> getGujaratiFolkPattern(complexity)
            RhythmStyle.DEVOTIONAL -> getDevotionalPattern(complexity)
            RhythmStyle.CUSTOM -> getCustomPattern(complexity)
        }
    }

    // 1. Bollywood: Kick, Snare/Clap, Dholak, Shaker
    private fun getBollywoodPattern(complexity: RhythmComplexity): List<RhythmHit> {
        val list = mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 0.95f),
            RhythmHit(4, InstrumentType.CLAP, 0.90f),
            RhythmHit(8, InstrumentType.KICK, 0.85f),
            RhythmHit(10, InstrumentType.KICK, 0.70f),
            RhythmHit(12, InstrumentType.CLAP, 0.90f),
            // Hi-hats
            RhythmHit(0, InstrumentType.CLOSED_HAT, 0.65f),
            RhythmHit(2, InstrumentType.CLOSED_HAT, 0.50f),
            RhythmHit(4, InstrumentType.CLOSED_HAT, 0.65f),
            RhythmHit(6, InstrumentType.CLOSED_HAT, 0.50f),
            RhythmHit(8, InstrumentType.CLOSED_HAT, 0.65f),
            RhythmHit(10, InstrumentType.CLOSED_HAT, 0.50f),
            RhythmHit(12, InstrumentType.CLOSED_HAT, 0.65f),
            RhythmHit(14, InstrumentType.CLOSED_HAT, 0.50f)
        )
        if (complexity != RhythmComplexity.SIMPLE) {
            // Add Dholak backbeat & shakers
            list.add(RhythmHit(0, InstrumentType.DHOLAK_DHA, 0.85f))
            list.add(RhythmHit(3, InstrumentType.DHOLAK_TA, 0.60f))
            list.add(RhythmHit(6, InstrumentType.DHOLAK_DHA, 0.70f))
            list.add(RhythmHit(7, InstrumentType.DHOLAK_TA, 0.75f))
            list.add(RhythmHit(11, InstrumentType.DHOLAK_TA, 0.65f))
            list.add(RhythmHit(14, InstrumentType.DHOLAK_DHA, 0.75f))
            list.add(RhythmHit(15, InstrumentType.DHOLAK_TA, 0.80f))
        }
        if (complexity == RhythmComplexity.ADVANCED) {
            list.add(RhythmHit(2, InstrumentType.SHAKER, 0.55f))
            list.add(RhythmHit(6, InstrumentType.SHAKER, 0.55f))
            list.add(RhythmHit(10, InstrumentType.SHAKER, 0.55f))
            list.add(RhythmHit(14, InstrumentType.SHAKER, 0.55f))
            list.add(RhythmHit(15, InstrumentType.OPEN_HAT, 0.65f))
        }
        return list
    }

    // 2. Romantic Bollywood: Soft kick, rim/snare, gentle tabla & shaker
    private fun getRomanticBollywoodPattern(complexity: RhythmComplexity): List<RhythmHit> {
        val list = mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 0.70f),
            RhythmHit(4, InstrumentType.SNARE, 0.65f),
            RhythmHit(8, InstrumentType.KICK, 0.60f),
            RhythmHit(12, InstrumentType.SNARE, 0.70f),
            RhythmHit(2, InstrumentType.SHAKER, 0.60f),
            RhythmHit(6, InstrumentType.SHAKER, 0.60f),
            RhythmHit(10, InstrumentType.SHAKER, 0.60f),
            RhythmHit(14, InstrumentType.SHAKER, 0.60f)
        )
        if (complexity != RhythmComplexity.SIMPLE) {
            list.add(RhythmHit(0, InstrumentType.TABLA_DHA, 0.75f))
            list.add(RhythmHit(4, InstrumentType.TABLA_NA, 0.60f))
            list.add(RhythmHit(6, InstrumentType.TABLA_GE, 0.65f))
            list.add(RhythmHit(10, InstrumentType.TABLA_DHA, 0.70f))
            list.add(RhythmHit(12, InstrumentType.TABLA_NA, 0.65f))
        }
        if (complexity == RhythmComplexity.ADVANCED) {
            list.add(RhythmHit(7, InstrumentType.TABLA_NA, 0.50f))
            list.add(RhythmHit(14, InstrumentType.TABLA_GE, 0.55f))
            list.add(RhythmHit(15, InstrumentType.SHAKER, 0.65f))
        }
        return list
    }

    // 3. Bollywood Dance: 4-on-the-floor kick, sharp claps, open hats, dhol
    private fun getBollywoodDancePattern(complexity: RhythmComplexity): List<RhythmHit> {
        val list = mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 1.0f),
            RhythmHit(4, InstrumentType.KICK, 0.95f),
            RhythmHit(8, InstrumentType.KICK, 1.0f),
            RhythmHit(12, InstrumentType.KICK, 0.95f),
            RhythmHit(4, InstrumentType.CLAP, 0.90f),
            RhythmHit(12, InstrumentType.CLAP, 0.90f),
            // Offbeat open hats
            RhythmHit(2, InstrumentType.OPEN_HAT, 0.75f),
            RhythmHit(6, InstrumentType.OPEN_HAT, 0.75f),
            RhythmHit(10, InstrumentType.OPEN_HAT, 0.75f),
            RhythmHit(14, InstrumentType.OPEN_HAT, 0.75f)
        )
        if (complexity != RhythmComplexity.SIMPLE) {
            list.add(RhythmHit(0, InstrumentType.DHOL_BOOM, 0.90f))
            list.add(RhythmHit(6, InstrumentType.DHOL_SLAP, 0.80f))
            list.add(RhythmHit(8, InstrumentType.DHOL_BOOM, 0.85f))
            list.add(RhythmHit(14, InstrumentType.DHOL_SLAP, 0.85f))
        }
        if (complexity == RhythmComplexity.ADVANCED) {
            list.add(RhythmHit(7, InstrumentType.DHOL_SLAP, 0.70f))
            list.add(RhythmHit(10, InstrumentType.TAMBOURINE, 0.65f))
            list.add(RhythmHit(15, InstrumentType.SNARE, 0.80f))
        }
        return list
    }

    // 4. Indian Pop
    private fun getIndianPopPattern(complexity: RhythmComplexity): List<RhythmHit> {
        val list = mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 0.90f),
            RhythmHit(4, InstrumentType.SNARE, 0.85f),
            RhythmHit(7, InstrumentType.KICK, 0.70f),
            RhythmHit(10, InstrumentType.KICK, 0.80f),
            RhythmHit(12, InstrumentType.SNARE, 0.90f),
            RhythmHit(0, InstrumentType.CLOSED_HAT, 0.70f),
            RhythmHit(2, InstrumentType.CLOSED_HAT, 0.60f),
            RhythmHit(4, InstrumentType.CLOSED_HAT, 0.70f),
            RhythmHit(6, InstrumentType.CLOSED_HAT, 0.60f),
            RhythmHit(8, InstrumentType.CLOSED_HAT, 0.70f),
            RhythmHit(10, InstrumentType.CLOSED_HAT, 0.60f),
            RhythmHit(12, InstrumentType.CLOSED_HAT, 0.70f),
            RhythmHit(14, InstrumentType.CLOSED_HAT, 0.60f)
        )
        if (complexity != RhythmComplexity.SIMPLE) {
            list.add(RhythmHit(0, InstrumentType.DHOLAK_DHA, 0.75f))
            list.add(RhythmHit(6, InstrumentType.DHOLAK_TA, 0.65f))
            list.add(RhythmHit(14, InstrumentType.DHOLAK_TA, 0.70f))
        }
        return list
    }

    // 5. Acoustic
    private fun getAcousticPattern(complexity: RhythmComplexity): List<RhythmHit> {
        val list = mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 0.75f),
            RhythmHit(4, InstrumentType.SNARE, 0.70f),
            RhythmHit(8, InstrumentType.KICK, 0.70f),
            RhythmHit(10, InstrumentType.KICK, 0.55f),
            RhythmHit(12, InstrumentType.SNARE, 0.75f),
            RhythmHit(2, InstrumentType.SHAKER, 0.60f),
            RhythmHit(6, InstrumentType.SHAKER, 0.60f),
            RhythmHit(10, InstrumentType.SHAKER, 0.60f),
            RhythmHit(14, InstrumentType.SHAKER, 0.60f)
        )
        if (complexity != RhythmComplexity.SIMPLE) {
            list.add(RhythmHit(4, InstrumentType.TAMBOURINE, 0.65f))
            list.add(RhythmHit(12, InstrumentType.TAMBOURINE, 0.70f))
        }
        return list
    }

    // 6. Rock
    private fun getRockPattern(complexity: RhythmComplexity): List<RhythmHit> {
        val list = mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 0.95f),
            RhythmHit(4, InstrumentType.SNARE, 0.90f),
            RhythmHit(8, InstrumentType.KICK, 0.85f),
            RhythmHit(10, InstrumentType.KICK, 0.80f),
            RhythmHit(12, InstrumentType.SNARE, 0.95f)
        )
        for (step in 0 until 16 step 2) {
            list.add(RhythmHit(step, InstrumentType.CLOSED_HAT, 0.75f))
        }
        if (complexity == RhythmComplexity.ADVANCED) {
            list.add(RhythmHit(15, InstrumentType.TOM, 0.70f))
        }
        return list
    }

    // 7. Pop
    private fun getPopPattern(complexity: RhythmComplexity): List<RhythmHit> {
        return mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 0.95f),
            RhythmHit(4, InstrumentType.CLAP, 0.85f),
            RhythmHit(8, InstrumentType.KICK, 0.85f),
            RhythmHit(11, InstrumentType.KICK, 0.75f),
            RhythmHit(12, InstrumentType.CLAP, 0.90f),
            RhythmHit(2, InstrumentType.CLOSED_HAT, 0.60f),
            RhythmHit(6, InstrumentType.CLOSED_HAT, 0.60f),
            RhythmHit(10, InstrumentType.CLOSED_HAT, 0.60f),
            RhythmHit(14, InstrumentType.CLOSED_HAT, 0.60f),
            RhythmHit(15, InstrumentType.OPEN_HAT, 0.70f)
        )
    }

    // 8. Hip-Hop
    private fun getHipHopPattern(complexity: RhythmComplexity): List<RhythmHit> {
        return mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 1.0f),
            RhythmHit(4, InstrumentType.SNARE, 0.90f),
            RhythmHit(7, InstrumentType.KICK, 0.85f),
            RhythmHit(10, InstrumentType.KICK, 0.90f),
            RhythmHit(12, InstrumentType.SNARE, 0.95f),
            RhythmHit(0, InstrumentType.CLOSED_HAT, 0.75f),
            RhythmHit(2, InstrumentType.CLOSED_HAT, 0.50f),
            RhythmHit(4, InstrumentType.CLOSED_HAT, 0.70f),
            RhythmHit(6, InstrumentType.CLOSED_HAT, 0.55f),
            RhythmHit(8, InstrumentType.CLOSED_HAT, 0.75f),
            RhythmHit(10, InstrumentType.CLOSED_HAT, 0.50f),
            RhythmHit(12, InstrumentType.CLOSED_HAT, 0.70f),
            RhythmHit(14, InstrumentType.CLOSED_HAT, 0.60f)
        )
    }

    // 9. Lo-Fi
    private fun getLoFiPattern(complexity: RhythmComplexity): List<RhythmHit> {
        return mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 0.80f),
            RhythmHit(4, InstrumentType.SNARE, 0.75f),
            RhythmHit(7, InstrumentType.KICK, 0.65f),
            RhythmHit(12, InstrumentType.SNARE, 0.80f),
            RhythmHit(2, InstrumentType.SHAKER, 0.55f),
            RhythmHit(6, InstrumentType.SHAKER, 0.55f),
            RhythmHit(10, InstrumentType.SHAKER, 0.55f),
            RhythmHit(14, InstrumentType.SHAKER, 0.55f)
        )
    }

    // 10. Garba (12 steps / 6-8 style with 3-taali clap & Dhol)
    private fun getGarbaPattern(complexity: RhythmComplexity): List<RhythmHit> {
        val list = mutableListOf(
            RhythmHit(0, InstrumentType.DHOL_BOOM, 0.95f),
            RhythmHit(2, InstrumentType.CLAP, 0.85f),
            RhythmHit(4, InstrumentType.CLAP, 0.85f),
            RhythmHit(6, InstrumentType.DHOL_BOOM, 0.90f),
            RhythmHit(8, InstrumentType.CLAP, 0.95f),
            RhythmHit(10, InstrumentType.DHOL_SLAP, 0.80f)
        )
        if (complexity != RhythmComplexity.SIMPLE) {
            list.add(RhythmHit(1, InstrumentType.DHOLAK_TA, 0.65f))
            list.add(RhythmHit(5, InstrumentType.DHOLAK_TA, 0.65f))
            list.add(RhythmHit(7, InstrumentType.DHOLAK_DHA, 0.75f))
            list.add(RhythmHit(11, InstrumentType.DHOLAK_TA, 0.70f))
        }
        return list
    }

    // 11. Gujarati Folk (Keharwa 8-beat style with dholak & claps)
    private fun getGujaratiFolkPattern(complexity: RhythmComplexity): List<RhythmHit> {
        return mutableListOf(
            RhythmHit(0, InstrumentType.DHOLAK_DHA, 0.90f),
            RhythmHit(2, InstrumentType.DHOLAK_TA, 0.75f),
            RhythmHit(4, InstrumentType.CLAP, 0.85f),
            RhythmHit(6, InstrumentType.DHOLAK_DHA, 0.80f),
            RhythmHit(8, InstrumentType.DHOLAK_DHA, 0.85f),
            RhythmHit(10, InstrumentType.DHOLAK_TA, 0.70f),
            RhythmHit(12, InstrumentType.CLAP, 0.90f),
            RhythmHit(14, InstrumentType.DHOLAK_TA, 0.80f)
        )
    }

    // 12. Devotional (Bhajan / Kirtan groove: peaceful Dholak theka + Manjira bells)
    private fun getDevotionalPattern(complexity: RhythmComplexity): List<RhythmHit> {
        val list = mutableListOf(
            RhythmHit(0, InstrumentType.DHOLAK_DHA, 0.80f),
            RhythmHit(4, InstrumentType.DHOLAK_TA, 0.65f),
            RhythmHit(6, InstrumentType.DHOLAK_DHA, 0.75f),
            RhythmHit(8, InstrumentType.DHOLAK_DHA, 0.70f),
            RhythmHit(12, InstrumentType.DHOLAK_TA, 0.70f),
            // Manjira/Tambourine clink
            RhythmHit(0, InstrumentType.TAMBOURINE, 0.70f),
            RhythmHit(4, InstrumentType.TAMBOURINE, 0.60f),
            RhythmHit(8, InstrumentType.TAMBOURINE, 0.70f),
            RhythmHit(12, InstrumentType.TAMBOURINE, 0.65f)
        )
        if (complexity != RhythmComplexity.SIMPLE) {
            list.add(RhythmHit(2, InstrumentType.TABLA_NA, 0.55f))
            list.add(RhythmHit(10, InstrumentType.TABLA_NA, 0.55f))
            list.add(RhythmHit(14, InstrumentType.DHOLAK_TA, 0.60f))
        }
        return list
    }

    // 13. Custom
    private fun getCustomPattern(complexity: RhythmComplexity): List<RhythmHit> {
        return mutableListOf(
            RhythmHit(0, InstrumentType.KICK, 0.90f),
            RhythmHit(4, InstrumentType.SNARE, 0.85f),
            RhythmHit(8, InstrumentType.KICK, 0.85f),
            RhythmHit(12, InstrumentType.SNARE, 0.85f),
            RhythmHit(2, InstrumentType.CLOSED_HAT, 0.65f),
            RhythmHit(6, InstrumentType.CLOSED_HAT, 0.65f),
            RhythmHit(10, InstrumentType.CLOSED_HAT, 0.65f),
            RhythmHit(14, InstrumentType.CLOSED_HAT, 0.65f)
        )
    }

    // Dynamic 4th / 8th bar fill pattern
    private fun getFillPattern(style: RhythmStyle): List<RhythmHit> {
        return if (style.category == "Indian" || style.category == "Folk") {
            mutableListOf(
                RhythmHit(0, InstrumentType.DHOLAK_DHA, 0.85f),
                RhythmHit(2, InstrumentType.DHOLAK_TA, 0.75f),
                RhythmHit(4, InstrumentType.DHOLAK_TA, 0.80f),
                RhythmHit(6, InstrumentType.DHOL_SLAP, 0.85f),
                RhythmHit(8, InstrumentType.DHOL_BOOM, 0.90f),
                RhythmHit(10, InstrumentType.DHOL_SLAP, 0.85f),
                RhythmHit(12, InstrumentType.DHOL_SLAP, 0.90f),
                RhythmHit(14, InstrumentType.DHOLAK_DHA, 0.95f),
                RhythmHit(15, InstrumentType.CLAP, 1.0f)
            )
        } else {
            mutableListOf(
                RhythmHit(0, InstrumentType.KICK, 0.90f),
                RhythmHit(4, InstrumentType.SNARE, 0.85f),
                RhythmHit(8, InstrumentType.TOM, 0.80f),
                RhythmHit(10, InstrumentType.TOM, 0.85f),
                RhythmHit(12, InstrumentType.SNARE, 0.90f),
                RhythmHit(14, InstrumentType.SNARE, 0.95f),
                RhythmHit(15, InstrumentType.OPEN_HAT, 1.0f)
            )
        }
    }
}
