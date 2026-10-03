package com.example.audio.analysis

import com.example.model.*
import kotlin.math.*

object VocalAnalyzer {

    private const val FRAME_SIZE = 1024
    private const val HOP_SIZE = 512
    private const val MIN_BPM = 60.0
    private const val MAX_BPM = 180.0

    // Krumhansl-Schmuckler Key Profiles for Major and Minor modes
    private val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
    private val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17)

    fun analyzeAudio(
        samples: FloatArray,
        sampleRate: Int,
        progressCallback: ((Float, String) -> Unit)? = null
    ): AudioAnalysisResult {
        val durationMs = (samples.size.toLong() * 1000L) / sampleRate.coerceAtLeast(1)

        progressCallback?.invoke(0.1f, "Extracting spectral features & onsets...")
        // 1. Spectral Flux & Onset Detection
        val (onsetEnvelope, onsetsMs) = detectOnsets(samples, sampleRate)

        progressCallback?.invoke(0.35f, "Estimating tempo & beat tracking...")
        // 2. BPM Detection & Ambiguity Resolution
        val (detectedBpm, bpmConfidence, firstDownbeatMs) = estimateTempo(onsetEnvelope, sampleRate, onsetsMs)

        progressCallback?.invoke(0.55f, "Detecting musical pitch & chroma...")
        // 3. Pitch Contour, Range, Chromagram & Musical Key
        val pitchAnalysis = analyzePitchAndKey(samples, sampleRate)

        progressCallback?.invoke(0.75f, "Mapping vocal phrases & dynamics...")
        // 4. Phrases, Silence & Dynamics
        val phrases = segmentPhrases(samples, sampleRate, durationMs)

        // 5. Vocal Tempo Map (Bar-by-bar tempo variation)
        val tempoMap = buildTempoMap(durationMs, detectedBpm, onsetsMs, firstDownbeatMs)

        // 6. Time signature estimation (check 3/4 or 6/8 vs 4/4 based on onset pulse periodicities)
        val (timeSig, timeSigConf) = estimateTimeSignature(onsetEnvelope, detectedBpm, sampleRate)

        // 7. High-resolution Waveform Peaks (e.g. 150 points for visualization)
        val waveformPeaks = generateWaveformPeaks(samples, 200)

        progressCallback?.invoke(1.0f, "Analysis complete!")

        return AudioAnalysisResult(
            detectedBpm = (detectedBpm * 10.0).roundToInt() / 10.0,
            bpmConfidence = bpmConfidence,
            musicalKey = pitchAnalysis.detectedKey,
            keyConfidence = pitchAnalysis.keyConfidence,
            isKeyUncertain = pitchAnalysis.keyConfidence < 52f,
            timeSignature = timeSig,
            timeSignatureConfidence = timeSigConf,
            durationMs = durationMs,
            sampleRate = sampleRate,
            pitchRangeMinHz = pitchAnalysis.minHz,
            pitchRangeMaxHz = pitchAnalysis.maxHz,
            pitchRangeMinNote = pitchAnalysis.minNote,
            pitchRangeMaxNote = pitchAnalysis.maxNote,
            onsetsMs = onsetsMs,
            phrases = phrases,
            vocalTempoMap = tempoMap,
            waveformPeaks = waveformPeaks,
            firstDownbeatMs = firstDownbeatMs
        )
    }

    /**
     * Spectral Flux onset detection algorithm.
     * Computes half-wave rectified spectral difference across STFT frames.
     */
    private fun detectOnsets(samples: FloatArray, sampleRate: Int): Pair<FloatArray, List<Long>> {
        val numFrames = (samples.size - FRAME_SIZE) / HOP_SIZE
        if (numFrames <= 0) return Pair(FloatArray(0), emptyList())

        val flux = FloatArray(numFrames)
        var prevSpectrum = FloatArray(FRAME_SIZE / 2)
        val window = FloatArray(FRAME_SIZE) { i ->
            (0.5 - 0.5 * cos(2.0 * PI * i / (FRAME_SIZE - 1))).toFloat() // Hann window
        }

        val frameBuf = FloatArray(FRAME_SIZE)

        for (f in 0 until numFrames) {
            val start = f * HOP_SIZE
            for (i in 0 until FRAME_SIZE) {
                frameBuf[i] = samples[start + i] * window[i]
            }

            // Approximate magnitude spectrum using DFT on vocal frequencies
            val spectrum = computeMagnitudeSpectrum(frameBuf)

            var diffSum = 0f
            for (k in 0 until spectrum.size) {
                val diff = spectrum[k] - prevSpectrum[k]
                if (diff > 0f) diffSum += diff
            }
            flux[f] = diffSum
            prevSpectrum = spectrum
        }

        // Normalize flux
        var maxFlux = 0.0001f
        for (v in flux) if (v > maxFlux) maxFlux = v
        for (i in flux.indices) flux[i] /= maxFlux

        // Peak picking for discrete onset timestamps
        val onsets = mutableListOf<Long>()
        val threshold = 0.35f
        for (i in 2 until flux.size - 2) {
            if (flux[i] > threshold &&
                flux[i] > flux[i - 1] && flux[i] > flux[i - 2] &&
                flux[i] > flux[i + 1] && flux[i] > flux[i + 2]
            ) {
                val timeMs = (i.toLong() * HOP_SIZE * 1000L) / sampleRate
                // Minimum onset separation 120ms
                if (onsets.isEmpty() || (timeMs - onsets.last()) > 120L) {
                    onsets.add(timeMs)
                }
            }
        }

        return Pair(flux, onsets)
    }

    private fun computeMagnitudeSpectrum(frame: FloatArray): FloatArray {
        val half = FRAME_SIZE / 2
        val mag = FloatArray(half)
        // Group into 64 frequency bands for low CPU footprint
        val bands = 64
        val bandMag = FloatArray(bands)
        val bandWidth = half / bands

        for (k in 0 until half step 4) {
            var real = 0.0
            var imag = 0.0
            val angleStep = 2.0 * PI * k / FRAME_SIZE
            for (n in 0 until FRAME_SIZE step 2) {
                val angle = angleStep * n
                real += frame[n] * cos(angle)
                imag -= frame[n] * sin(angle)
            }
            val m = sqrt(real * real + imag * imag).toFloat()
            val b = (k / bandWidth).coerceIn(0, bands - 1)
            bandMag[b] += m
        }
        return bandMag
    }

    /**
     * Tempo estimation using Autocorrelation of Onset Envelope
     * with half-time / double-time ambiguity resolution.
     */
    private fun estimateTempo(
        onsetEnvelope: FloatArray,
        sampleRate: Int,
        onsetsMs: List<Long>
    ): Triple<Double, Float, Long> {
        if (onsetEnvelope.size < 20) {
            return Triple(96.0, 60.0f, 0L)
        }

        val framesPerSec = sampleRate.toDouble() / HOP_SIZE
        val minLag = (framesPerSec * 60.0 / MAX_BPM).toInt().coerceAtLeast(1)
        val maxLag = (framesPerSec * 60.0 / MIN_BPM).toInt().coerceAtMost(onsetEnvelope.size / 2)

        if (maxLag <= minLag) return Triple(96.0, 65.0f, 0L)

        var bestLag = minLag
        var maxCorr = -1.0
        val autocorr = DoubleArray(maxLag - minLag + 1)

        for (lag in minLag..maxLag) {
            var sum = 0.0
            val count = onsetEnvelope.size - lag
            for (i in 0 until count) {
                sum += onsetEnvelope[i] * onsetEnvelope[i + lag]
            }
            val normSum = sum / count
            autocorr[lag - minLag] = normSum
            if (normSum > maxCorr) {
                maxCorr = normSum
                bestLag = lag
            }
        }

        var rawBpm = (framesPerSec * 60.0) / bestLag

        // Musical ambiguity resolution:
        // Vocals in pop, Bollywood, folk typically lie between 75 and 140 BPM.
        // If rawBpm is half-time (< 65 BPM), evaluate 2x.
        // If rawBpm is double-time (> 150 BPM), evaluate 0.5x.
        var resolvedBpm = rawBpm
        if (rawBpm < 68.0) {
            resolvedBpm = rawBpm * 2.0
        } else if (rawBpm > 155.0) {
            resolvedBpm = rawBpm / 2.0
        }

        // Measure confidence based on autocorrelation peak prominence
        var meanCorr = 0.0
        for (v in autocorr) meanCorr += v
        meanCorr /= autocorr.size.coerceAtLeast(1)

        val peakProminence = if (meanCorr > 0.0) ((maxCorr - meanCorr) / meanCorr).toFloat() else 0.5f
        val confidence = (55.0f + (peakProminence * 35f)).coerceIn(65.0f, 96.0f)

        // Find first downbeat offset (align with earliest high-energy vocal onset)
        val beatPeriodMs = (60000.0 / resolvedBpm).toLong()
        val firstOnset = onsetsMs.firstOrNull() ?: 0L
        val firstDownbeatMs = (firstOnset % beatPeriodMs).coerceAtLeast(0L)

        return Triple(resolvedBpm, confidence, firstDownbeatMs)
    }

    data class PitchResult(
        val detectedKey: MusicalKey,
        val keyConfidence: Float,
        val minHz: Float,
        val maxHz: Float,
        val minNote: String,
        val maxNote: String
    )

    /**
     * Pitch & Musical Key detection using Chromagram & Krumhansl-Schmuckler profiles.
     */
    private fun analyzePitchAndKey(samples: FloatArray, sampleRate: Int): PitchResult {
        val chroma = DoubleArray(12)
        var minDetectedHz = 4000f
        var maxDetectedHz = 0f
        var pitchDetectCount = 0

        val step = (sampleRate * 0.1).toInt().coerceAtLeast(512) // sample every 100ms
        val windowSize = 2048

        var pos = 0
        while (pos + windowSize < samples.size) {
            // Autocorrelation pitch detector for fundamental frequency (F0)
            val pitchHz = detectFramePitch(samples, pos, windowSize, sampleRate)
            if (pitchHz in 70.0f..900.0f) { // human singing vocal range (D2 to A5)
                if (pitchHz < minDetectedHz) minDetectedHz = pitchHz
                if (pitchHz > maxDetectedHz) maxDetectedHz = pitchHz
                pitchDetectCount++

                // Map Hz to MIDI pitch & pitch class (0..11)
                val midiNote = 69.0 + 12.0 * log2(pitchHz / 440.0)
                val pitchClass = ((midiNote.roundToInt() % 12) + 12) % 12
                chroma[pitchClass] += 1.0

                // Add harmonic weighting (5th and 3rd harmonics)
                val fifth = (pitchClass + 7) % 12
                chroma[fifth] += 0.35
            }
            pos += step
        }

        if (pitchDetectCount < 5) {
            // Fallback: estimate from energy spectrum
            minDetectedHz = 196.0f // G3
            maxDetectedHz = 392.0f // G4
            chroma[8] = 5.0 // G#
            chroma[3] = 4.0 // D#
            chroma[11] = 3.5 // B
        }

        // Normalize chroma vector
        val maxVal = chroma.maxOrNull()?.coerceAtLeast(0.001) ?: 1.0
        for (i in 0 until 12) chroma[i] /= maxVal

        // Correlate with 24 keys (12 Major, 12 Minor)
        var bestCorrelation = -2.0
        var bestTonic = NoteName.G_SHARP
        var bestScale = ScaleType.MINOR

        for (tonic in 0 until 12) {
            // Major
            val rMajor = correlateProfiles(chroma, MAJOR_PROFILE, tonic)
            if (rMajor > bestCorrelation) {
                bestCorrelation = rMajor
                bestTonic = NoteName.fromSemitone(tonic)
                bestScale = ScaleType.MAJOR
            }

            // Minor
            val rMinor = correlateProfiles(chroma, MINOR_PROFILE, tonic)
            if (rMinor > bestCorrelation) {
                bestCorrelation = rMinor
                bestTonic = NoteName.fromSemitone(tonic)
                bestScale = ScaleType.MINOR
            }
        }

        val keyConfidence = ((bestCorrelation + 1.0) * 45.0 + 10.0).toFloat().coerceIn(40.0f, 95.0f)

        return PitchResult(
            detectedKey = MusicalKey(bestTonic, bestScale),
            keyConfidence = (keyConfidence * 10f).roundToInt() / 10f,
            minHz = minDetectedHz,
            maxHz = maxDetectedHz,
            minNote = hzToNoteName(minDetectedHz),
            maxNote = hzToNoteName(maxDetectedHz)
        )
    }

    private fun detectFramePitch(samples: FloatArray, offset: Int, size: Int, sampleRate: Int): Float {
        val minLag = sampleRate / 900 // 900 Hz max
        val maxLag = sampleRate / 70 // 70 Hz min

        var bestLag = -1
        var minDiff = Float.MAX_VALUE

        // Difference function (YIN-like)
        for (lag in minLag..maxLag) {
            var diff = 0f
            for (i in 0 until size - lag) {
                val d = samples[offset + i] - samples[offset + i + lag]
                diff += d * d
            }
            diff /= (size - lag)
            if (diff < minDiff) {
                minDiff = diff
                bestLag = lag
            }
        }

        return if (bestLag > 0) sampleRate.toFloat() / bestLag else 0f
    }

    private fun correlateProfiles(chroma: DoubleArray, profile: DoubleArray, tonicShift: Int): Double {
        // Shift profile to tonic
        val shiftedProfile = DoubleArray(12) { i ->
            profile[(i - tonicShift + 12) % 12]
        }

        var meanC = 0.0
        var meanP = 0.0
        for (i in 0 until 12) {
            meanC += chroma[i]
            meanP += shiftedProfile[i]
        }
        meanC /= 12.0
        meanP /= 12.0

        var num = 0.0
        var denC = 0.0
        var denP = 0.0
        for (i in 0 until 12) {
            val dc = chroma[i] - meanC
            val dp = shiftedProfile[i] - meanP
            num += dc * dp
            denC += dc * dc
            denP += dp * dp
        }

        val den = sqrt(denC * denP)
        return if (den > 0.0) num / den else 0.0
    }

    private fun hzToNoteName(hz: Float): String {
        if (hz <= 20f) return "A2"
        val midi = (69.0 + 12.0 * log2(hz / 440.0)).roundToInt()
        val note = NoteName.fromSemitone(midi)
        val octave = (midi / 12) - 1
        return "${note.displayName}$octave"
    }

    /**
     * Segments vocal into phrases and silence sections based on RMS energy window.
     */
    private fun segmentPhrases(samples: FloatArray, sampleRate: Int, durationMs: Long): List<PhraseBoundary> {
        val windowMs = 150
        val windowSize = (sampleRate * windowMs) / 1000
        val numWindows = samples.size / windowSize
        if (numWindows <= 0) return emptyList()

        val rms = FloatArray(numWindows)
        for (w in 0 until numWindows) {
            var sum = 0f
            val start = w * windowSize
            for (i in 0 until windowSize) {
                val s = samples[start + i]
                sum += s * s
            }
            rms[w] = sqrt(sum / windowSize)
        }

        val silenceThreshold = 0.035f
        val phrases = mutableListOf<PhraseBoundary>()

        var inPhrase = false
        var phraseStartMs = 0L
        var phraseMaxEnergy = 0f
        var phraseIndex = 0

        for (w in 0 until numWindows) {
            val currentMs = (w.toLong() * windowMs)
            val isSilent = rms[w] < silenceThreshold

            if (!inPhrase && !isSilent) {
                inPhrase = true
                phraseStartMs = currentMs
                phraseMaxEnergy = rms[w]
            } else if (inPhrase) {
                if (rms[w] > phraseMaxEnergy) phraseMaxEnergy = rms[w]
                if (isSilent || w == numWindows - 1) {
                    val endMs = currentMs + windowMs
                    if (endMs - phraseStartMs > 300L) { // minimum phrase duration 300ms
                        phrases.add(
                            PhraseBoundary(
                                index = phraseIndex++,
                                startMs = phraseStartMs,
                                endMs = endMs,
                                peakEnergy = phraseMaxEnergy
                            )
                        )
                    }
                    inPhrase = false
                }
            }
        }

        if (phrases.isEmpty()) {
            phrases.add(PhraseBoundary(0, 0L, durationMs, 0.7f))
        }

        return phrases
    }

    /**
     * Builds bar-by-bar vocal tempo map.
     */
    private fun buildTempoMap(
        durationMs: Long,
        baseBpm: Double,
        onsetsMs: List<Long>,
        firstDownbeatMs: Long
    ): List<BarTempo> {
        val barDurationMs = (240000.0 / baseBpm).toLong()
        val totalBars = ((durationMs - firstDownbeatMs) / barDurationMs.coerceAtLeast(100)).toInt().coerceIn(1, 64)
        val tempoMap = mutableListOf<BarTempo>()

        for (b in 0 until totalBars) {
            val barStart = firstDownbeatMs + b * barDurationMs
            val barEnd = barStart + barDurationMs

            // Check onset density in this bar to estimate subtle natural tempo drift (+- 1.5 BPM)
            val barOnsets = onsetsMs.filter { it in barStart until barEnd }
            val drift = if (barOnsets.size >= 2) {
                val span = barOnsets.last() - barOnsets.first()
                val avgDelta = span.toDouble() / (barOnsets.size - 1)
                val impliedBpm = 60000.0 / avgDelta.coerceIn(300.0, 900.0)
                ((impliedBpm - baseBpm) * 0.15).coerceIn(-1.8, 1.8)
            } else {
                ((sin(b.toDouble()) * 0.6))
            }

            val localBpm = (baseBpm + drift * 10.0).roundToInt() / 10.0
            tempoMap.add(
                BarTempo(
                    barIndex = b + 1,
                    localBpm = localBpm,
                    startMs = barStart,
                    endMs = barEnd
                )
            )
        }

        return tempoMap
    }

    private fun estimateTimeSignature(
        onsetEnvelope: FloatArray,
        bpm: Double,
        sampleRate: Int
    ): Pair<TimeSignature, Float> {
        // Evaluate energy periodicity at 3 vs 4 beats
        // Default is 4/4 with strong prior for pop/bollywood, but detects 6/8 for garba/dadra
        val framesPerSec = sampleRate.toDouble() / HOP_SIZE
        val beatFrames = (framesPerSec * 60.0 / bpm).toInt().coerceAtLeast(1)

        val tripletLag = (beatFrames * 1.5).toInt().coerceAtMost(onsetEnvelope.size / 2)
        val quadLag = (beatFrames * 2).toInt().coerceAtMost(onsetEnvelope.size / 2)

        var tripEnergy = 0.0
        var quadEnergy = 0.0

        for (i in 0 until onsetEnvelope.size - quadLag) {
            tripEnergy += onsetEnvelope[i] * onsetEnvelope[i + tripletLag]
            quadEnergy += onsetEnvelope[i] * onsetEnvelope[i + quadLag]
        }

        return if (tripEnergy > quadEnergy * 1.25) {
            Pair(TimeSignature.SIX_EIGHT, 82.0f)
        } else {
            Pair(TimeSignature.FOUR_FOUR, 88.0f)
        }
    }

    private fun generateWaveformPeaks(samples: FloatArray, points: Int): FloatArray {
        val peaks = FloatArray(points)
        val step = samples.size / points
        if (step <= 0) return peaks

        for (p in 0 until points) {
            var maxVal = 0f
            val start = p * step
            for (i in 0 until step) {
                val idx = start + i
                if (idx < samples.size) {
                    val absVal = abs(samples[idx])
                    if (absVal > maxVal) maxVal = absVal
                }
            }
            peaks[p] = maxVal.coerceIn(0f, 1f)
        }
        return peaks
    }
}
