package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.audio.analysis.VocalAnalyzer
import com.example.audio.analysis.WavAudioDecoder
import com.example.audio.beat_engine.ChordProgressionGenerator
import com.example.audio.beat_engine.PercussionSynthesizer
import com.example.audio.beat_engine.RhythmPatternGenerator
import com.example.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("VocalBeat AI", appName)
    }

    @Test
    fun `test dsp vocal analysis detects realistic bpm and key`() {
        // Generate a 10-second vocal melody with known 97.0 BPM and G# Minor tonic
        val demoAudio = WavAudioDecoder.generateDemoVocal(
            bpm = 97.0,
            tonicSemitone = 8, // G#
            isMinor = true,
            durationSeconds = 10
        )
        assertTrue("Audio samples should not be empty", demoAudio.samples.isNotEmpty())

        val analysis = VocalAnalyzer.analyzeAudio(demoAudio.samples, demoAudio.sampleRate)

        // Verify BPM detection within reasonable musical tempo range
        assertTrue("BPM should be detected in valid tempo range", analysis.detectedBpm in 80.0..120.0)
        assertTrue("Confidence should be above 50%", analysis.bpmConfidence >= 50f)
        assertNotNull("Musical key should be identified", analysis.musicalKey)
        assertTrue("Waveform peaks must be extracted", analysis.waveformPeaks.isNotEmpty())
    }

    @Test
    fun `test percussion synthesis and rhythm generation`() {
        val kick = PercussionSynthesizer.getSample(InstrumentType.KICK)
        assertTrue("Kick sample must have content", kick.isNotEmpty())

        val dholak = PercussionSynthesizer.getSample(InstrumentType.DHOLAK_DHA)
        assertTrue("Dholak sample must have content", dholak.isNotEmpty())

        val (drums, perc) = RhythmPatternGenerator.generateRhythmTrack(
            style = RhythmStyle.BOLLYWOOD,
            complexity = RhythmComplexity.MEDIUM,
            bpm = 96.0,
            durationMs = 4000L,
            firstDownbeatMs = 0L
        )

        assertEquals("Drums buffer size matches duration", (4.0 * 44100).toInt(), drums.size)
        assertEquals("Percussion buffer size matches duration", (4.0 * 44100).toInt(), perc.size)
    }

    @Test
    fun `test chord and bass progression generation`() {
        val key = MusicalKey(NoteName.G_SHARP, ScaleType.MINOR)
        val result = ChordProgressionGenerator.generateChordsAndBass(
            key = key,
            style = ChordProgressionStyle.BOLLYWOOD,
            bassPattern = BassPatternType.GROOVE,
            bpm = 96.0,
            durationMs = 6000L,
            firstDownbeatMs = 0L
        )

        assertTrue("Progression should contain chords", result.chordNames.isNotEmpty())
        assertTrue("Chord audio buffer should have non-zero duration", result.chordAudioBuffer.isNotEmpty())
        assertTrue("Bass audio buffer should have non-zero duration", result.bassAudioBuffer.isNotEmpty())
    }
}
