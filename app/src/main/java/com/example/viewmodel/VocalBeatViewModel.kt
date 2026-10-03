package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.GeminiMusicalAdvisor
import com.example.audio.analysis.VocalAnalyzer
import com.example.audio.analysis.WavAudioDecoder
import com.example.audio.beat_engine.ArrangementGenerator
import com.example.audio.beat_engine.ChordProgressionGenerator
import com.example.audio.beat_engine.RhythmPatternGenerator
import com.example.audio.export.AudioExporter
import com.example.audio.mixer.AudioMixerEngine
import com.example.audio.recorder.VocalRecorder
import com.example.audio.sync.PitchShifter
import com.example.audio.sync.VocalSynchronizer
import com.example.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

enum class AppTab(val title: String, val iconName: String) {
    INPUT("Vocal Input", "Mic"),
    ANALYSIS("AI Analysis", "AutoAwesome"),
    GRID("Beat Grid", "GridOn"),
    RHYTHM("Rhythm Studio", "MusicNote"),
    CHORDS("Chords & Bass", "Piano"),
    SYNC("Sync & Pitch", "Tune"),
    MIXER("Mixer", "Equalizer"),
    EXPORT("Export", "IosShare")
}

class VocalBeatViewModel(application: Application) : AndroidViewModel(application) {

    val mixerEngine = AudioMixerEngine(sampleRate = 44100)
    val vocalRecorder = VocalRecorder(application.applicationContext)

    // Current Navigation Tab
    private val _currentTab = MutableStateFlow(AppTab.INPUT)
    val currentTab: StateFlow<AppTab> = _currentTab.asStateFlow()

    // Status / Snackbar Message
    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    // 1. Audio Input
    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName.asStateFlow()

    private val _rawVocalSamples = MutableStateFlow(FloatArray(0))
    val rawVocalSamples: StateFlow<FloatArray> = _rawVocalSamples.asStateFlow()

    private val _activeVocalBuffer = MutableStateFlow(FloatArray(0))
    val activeVocalBuffer: StateFlow<FloatArray> = _activeVocalBuffer.asStateFlow()

    private val _vocalDurationMs = MutableStateFlow(0L)
    val vocalDurationMs: StateFlow<Long> = _vocalDurationMs.asStateFlow()

    // 2. Vocal Analysis
    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisProgress = MutableStateFlow(0f)
    val analysisProgress: StateFlow<Float> = _analysisProgress.asStateFlow()

    private val _analysisStatusText = MutableStateFlow("Ready")
    val analysisStatusText: StateFlow<String> = _analysisStatusText.asStateFlow()

    private val _analysisResult = MutableStateFlow<AudioAnalysisResult?>(null)
    val analysisResult: StateFlow<AudioAnalysisResult?> = _analysisResult.asStateFlow()

    // Active Musical Parameters (detected or user-adjusted)
    private val _activeBpm = MutableStateFlow(96.0)
    val activeBpm: StateFlow<Double> = _activeBpm.asStateFlow()

    private val _activeKey = MutableStateFlow(MusicalKey(NoteName.G_SHARP, ScaleType.MINOR))
    val activeKey: StateFlow<MusicalKey> = _activeKey.asStateFlow()

    private val _activeTimeSignature = MutableStateFlow(TimeSignature.FOUR_FOUR)
    val activeTimeSignature: StateFlow<TimeSignature> = _activeTimeSignature.asStateFlow()

    private val _firstDownbeatMs = MutableStateFlow(0L)
    val firstDownbeatMs: StateFlow<Long> = _firstDownbeatMs.asStateFlow()

    // 3. Beat Grid & Phrasing
    private val _quantizeGrid = MutableStateFlow(GridQuantize.SIXTEENTH)
    val quantizeGrid: StateFlow<GridQuantize> = _quantizeGrid.asStateFlow()

    private val _swingPercent = MutableStateFlow(0)
    val swingPercent: StateFlow<Int> = _swingPercent.asStateFlow()

    private val _tempoMode = MutableStateFlow(TempoMode.FIXED_TEMPO)
    val tempoMode: StateFlow<TempoMode> = _tempoMode.asStateFlow()

    // 4. Rhythm Generation
    private val _selectedStyle = MutableStateFlow(RhythmStyle.BOLLYWOOD)
    val selectedStyle: StateFlow<RhythmStyle> = _selectedStyle.asStateFlow()

    private val _rhythmComplexity = MutableStateFlow(RhythmComplexity.MEDIUM)
    val rhythmComplexity: StateFlow<RhythmComplexity> = _rhythmComplexity.asStateFlow()

    private val _isGeneratingRhythm = MutableStateFlow(false)
    val isGeneratingRhythm: StateFlow<Boolean> = _isGeneratingRhythm.asStateFlow()

    private var drumAudioBuffer = FloatArray(0)
    private var percAudioBuffer = FloatArray(0)

    // 5. Chords & Bass
    private val _chordsEnabled = MutableStateFlow(true)
    val chordsEnabled: StateFlow<Boolean> = _chordsEnabled.asStateFlow()

    private val _bassEnabled = MutableStateFlow(true)
    val bassEnabled: StateFlow<Boolean> = _bassEnabled.asStateFlow()

    private val _chordStyle = MutableStateFlow(ChordProgressionStyle.BOLLYWOOD)
    val chordStyle: StateFlow<ChordProgressionStyle> = _chordStyle.asStateFlow()

    private val _bassPattern = MutableStateFlow(BassPatternType.GROOVE)
    val bassPattern: StateFlow<BassPatternType> = _bassPattern.asStateFlow()

    private val _detectedChords = MutableStateFlow<List<String>>(emptyList())
    val detectedChords: StateFlow<List<String>> = _detectedChords.asStateFlow()

    private var chordAudioBuffer = FloatArray(0)
    private var bassAudioBuffer = FloatArray(0)

    // 6. Vocal Sync & Pitch
    private val _vocalSyncMode = MutableStateFlow(VocalSyncMode.OFF)
    val vocalSyncMode: StateFlow<VocalSyncMode> = _vocalSyncMode.asStateFlow()

    private val _pitchShiftSemitones = MutableStateFlow(0)
    val pitchShiftSemitones: StateFlow<Int> = _pitchShiftSemitones.asStateFlow()

    private val _preserveTempo = MutableStateFlow(true)
    val preserveTempo: StateFlow<Boolean> = _preserveTempo.asStateFlow()

    // 7. Arrangement
    private val _sections = MutableStateFlow<List<ArrangementSection>>(emptyList())
    val sections: StateFlow<List<ArrangementSection>> = _sections.asStateFlow()

    // 8. AI Musical Advisor
    private val _aiAdvice = MutableStateFlow<GeminiMusicalAdvisor.AiMusicalAdvice?>(null)
    val aiAdvice: StateFlow<GeminiMusicalAdvisor.AiMusicalAdvice?> = _aiAdvice.asStateFlow()

    private val _isLoadingAi = MutableStateFlow(false)
    val isLoadingAi: StateFlow<Boolean> = _isLoadingAi.asStateFlow()

    // 9. Export
    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _exportProgress = MutableStateFlow(0f)
    val exportProgress: StateFlow<Float> = _exportProgress.asStateFlow()

    private val _exportedFile = MutableStateFlow<File?>(null)
    val exportedFile: StateFlow<File?> = _exportedFile.asStateFlow()

    // Tap Tempo state
    private val tapHistory = mutableListOf<Long>()

    fun setTab(tab: AppTab) {
        _currentTab.value = tab
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    /**
     * Loads a built-in vocal sample (e.g. Bollywood vocal melody)
     * so user can test the entire workflow instantly.
     */
    fun loadSampleVocal(sampleType: String = "bollywood") {
        viewModelScope.launch(Dispatchers.Default) {
            mixerEngine.stop()
            _fileName.value = "Demo_${sampleType.replaceFirstChar { it.uppercase() }}_Vocal.wav"
            val decoded = when (sampleType) {
                "romantic" -> WavAudioDecoder.generateDemoVocal(bpm = 84.0, tonicSemitone = 4, isMinor = true, durationSeconds = 14)
                "pop" -> WavAudioDecoder.generateDemoVocal(bpm = 112.0, tonicSemitone = 0, isMinor = false, durationSeconds = 12)
                else -> WavAudioDecoder.generateDemoVocal(bpm = 97.0, tonicSemitone = 8, isMinor = true, durationSeconds = 15) // G# Minor 97 BPM
            }

            processDecodedVocal(decoded.samples, decoded.sampleRate, decoded.durationMs)
        }
    }

    fun importVocalFromUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.Default) {
            mixerEngine.stop()
            _fileName.value = uri.lastPathSegment ?: "Imported_Vocal.wav"
            _isAnalyzing.value = true
            _analysisStatusText.value = "Decoding audio format..."

            val decoded = WavAudioDecoder.decodeAudio(getApplication(), uri)
            if (decoded.samples.isEmpty() || decoded.durationMs < 800) {
                _isAnalyzing.value = false
                _userMessage.value = "Audio file is too short or could not be decoded. Please try another file."
                return@launch
            }

            processDecodedVocal(decoded.samples, decoded.sampleRate, decoded.durationMs)
        }
    }

    fun handleRecordedFile(file: File) {
        viewModelScope.launch(Dispatchers.Default) {
            mixerEngine.stop()
            _fileName.value = file.name
            _isAnalyzing.value = true
            _analysisStatusText.value = "Reading microphone recording..."

            val decoded = WavAudioDecoder.decodeAudio(getApplication(), Uri.fromFile(file))
            if (decoded.samples.isEmpty() || decoded.durationMs < 800) {
                _isAnalyzing.value = false
                _userMessage.value = "Recording was too short. Please record for at least 1-2 seconds."
                return@launch
            }

            processDecodedVocal(decoded.samples, decoded.sampleRate, decoded.durationMs)
        }
    }

    private suspend fun processDecodedVocal(samples: FloatArray, sampleRate: Int, durationMs: Long) {
        _rawVocalSamples.value = samples
        _activeVocalBuffer.value = samples
        _vocalDurationMs.value = durationMs

        _isAnalyzing.value = true
        _analysisProgress.value = 0.05f

        val result = VocalAnalyzer.analyzeAudio(samples, sampleRate) { progress, status ->
            _analysisProgress.value = progress
            _analysisStatusText.value = status
        }

        _analysisResult.value = result
        _activeBpm.value = result.detectedBpm
        _activeKey.value = result.musicalKey
        _activeTimeSignature.value = result.timeSignature
        _firstDownbeatMs.value = result.firstDownbeatMs
        _isAnalyzing.value = false

        mixerEngine.currentBpm = result.detectedBpm
        mixerEngine.firstDownbeatMs = result.firstDownbeatMs

        // Generate arrangement sections based on detected phrases
        val arrangement = ArrangementGenerator.generateArrangement(durationMs, result.detectedBpm, result.phrases)
        _sections.value = arrangement

        // Automatically trigger AI suggestion and rhythm generation
        generateRhythm(autoPlay = false)
        fetchAiAdvice()

        // Move to Analysis tab to show results
        _currentTab.value = AppTab.ANALYSIS
    }

    fun generateRhythm(autoPlay: Boolean = false) {
        viewModelScope.launch(Dispatchers.Default) {
            _isGeneratingRhythm.value = true

            val durationMs = _vocalDurationMs.value.coerceAtLeast(4000L)
            val bpm = _activeBpm.value
            val style = _selectedStyle.value
            val complexity = _rhythmComplexity.value
            val swing = _swingPercent.value
            val tempoMode = _tempoMode.value
            val tempoMap = _analysisResult.value?.vocalTempoMap ?: emptyList()
            val downbeatMs = _firstDownbeatMs.value

            // 1. Generate Drums and Percussion
            val (drums, perc) = RhythmPatternGenerator.generateRhythmTrack(
                style = style,
                complexity = complexity,
                bpm = bpm,
                durationMs = durationMs,
                firstDownbeatMs = downbeatMs,
                swingPercent = swing,
                tempoMode = tempoMode,
                tempoMap = tempoMap
            )
            drumAudioBuffer = drums
            percAudioBuffer = perc

            // 2. Generate Chords and Bass
            val harmonicResult = ChordProgressionGenerator.generateChordsAndBass(
                key = _activeKey.value,
                style = _chordStyle.value,
                bassPattern = _bassPattern.value,
                bpm = bpm,
                durationMs = durationMs,
                firstDownbeatMs = downbeatMs
            )
            _detectedChords.value = harmonicResult.chordNames
            chordAudioBuffer = if (_chordsEnabled.value) harmonicResult.chordAudioBuffer else FloatArray(drums.size)
            bassAudioBuffer = if (_bassEnabled.value) harmonicResult.bassAudioBuffer else FloatArray(drums.size)

            // 3. Update Audio Mixer
            mixerEngine.setBuffers(
                vocal = _activeVocalBuffer.value,
                drums = drumAudioBuffer,
                percussion = percAudioBuffer,
                bass = bassAudioBuffer,
                chords = chordAudioBuffer
            )

            _isGeneratingRhythm.value = false
            _userMessage.value = "Rhythm generated for ${style.title} at ${bpm.toInt()} BPM!"

            if (autoPlay) {
                mixerEngine.play()
            }
        }
    }

    fun setBpm(newBpm: Double) {
        val clamped = (newBpm * 10.0).roundToInt() / 10.0
        val bounded = clamped.coerceIn(45.0, 220.0)
        _activeBpm.value = bounded
        mixerEngine.currentBpm = bounded
        // Automatically regenerate rhythm to match the new BPM in real-time
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun adjustBpm(delta: Double) {
        setBpm(_activeBpm.value + delta)
    }

    fun resetToDetectedBpm() {
        val detected = _analysisResult.value?.detectedBpm ?: 96.0
        setBpm(detected)
    }

    fun tapTempo() {
        val now = System.currentTimeMillis()
        tapHistory.add(now)
        // Keep last 5 taps within 2.5 seconds
        tapHistory.removeAll { now - it > 2500 }
        if (tapHistory.size >= 2) {
            val intervals = mutableListOf<Long>()
            for (i in 1 until tapHistory.size) {
                intervals.add(tapHistory[i] - tapHistory[i - 1])
            }
            val avgInterval = intervals.average()
            if (avgInterval > 150) {
                val tappedBpm = (60000.0 / avgInterval).coerceIn(50.0, 200.0)
                setBpm(tappedBpm)
            }
        }
    }

    fun setKey(key: MusicalKey) {
        _activeKey.value = key
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setStyle(style: RhythmStyle) {
        _selectedStyle.value = style
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setComplexity(complexity: RhythmComplexity) {
        _rhythmComplexity.value = complexity
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setTempoMode(mode: TempoMode) {
        _tempoMode.value = mode
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setSwingPercent(swing: Int) {
        _swingPercent.value = swing.coerceIn(0, 100)
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setQuantizeGrid(grid: GridQuantize) {
        _quantizeGrid.value = grid
    }

    fun setFirstDownbeatMs(offsetMs: Long) {
        _firstDownbeatMs.value = offsetMs.coerceAtLeast(0L)
        mixerEngine.firstDownbeatMs = _firstDownbeatMs.value
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setChordsEnabled(enabled: Boolean) {
        _chordsEnabled.value = enabled
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setBassEnabled(enabled: Boolean) {
        _bassEnabled.value = enabled
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setChordStyle(style: ChordProgressionStyle) {
        _chordStyle.value = style
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setBassPattern(pattern: BassPatternType) {
        _bassPattern.value = pattern
        generateRhythm(autoPlay = mixerEngine.isPlaying.value)
    }

    fun setVocalSyncMode(mode: VocalSyncMode) {
        _vocalSyncMode.value = mode
        applyVocalProcessing()
    }

    fun setPitchShiftSemitones(semitones: Int) {
        _pitchShiftSemitones.value = semitones.coerceIn(-12, 12)
        applyVocalProcessing()
    }

    private fun applyVocalProcessing() {
        viewModelScope.launch(Dispatchers.Default) {
            val raw = _rawVocalSamples.value
            if (raw.isEmpty()) return@launch

            // 1. Pitch shift if != 0
            val shifted = if (_pitchShiftSemitones.value != 0) {
                PitchShifter.shiftPitch(raw, _pitchShiftSemitones.value, 44100)
            } else {
                raw.copyOf()
            }

            // 2. Synchronize if mode != OFF
            val syncedResult = if (_vocalSyncMode.value != VocalSyncMode.OFF) {
                val analysis = _analysisResult.value
                VocalSynchronizer.synchronizeVocal(
                    context = getApplication(),
                    originalSamples = shifted,
                    sampleRate = 44100,
                    bpm = _activeBpm.value,
                    firstDownbeatMs = _firstDownbeatMs.value,
                    onsetsMs = analysis?.onsetsMs ?: emptyList(),
                    phrases = analysis?.phrases ?: emptyList(),
                    syncMode = _vocalSyncMode.value
                )
            } else {
                VocalSynchronizer.SyncResult(shifted, null)
            }

            _activeVocalBuffer.value = syncedResult.syncedSamples
            mixerEngine.updateVocalBuffer(syncedResult.syncedSamples)
        }
    }

    fun fetchAiAdvice() {
        val analysis = _analysisResult.value ?: return
        viewModelScope.launch(Dispatchers.Default) {
            _isLoadingAi.value = true
            val advice = GeminiMusicalAdvisor.getMusicalAdvice(analysis)
            _aiAdvice.value = advice
            _isLoadingAi.value = false
        }
    }

    fun exportTrack(option: ExportOption, onComplete: (File) -> Unit) {
        viewModelScope.launch(Dispatchers.Default) {
            _isExporting.value = true
            _exportProgress.value = 0.05f

            val channelMap = MixerChannel.entries.associateWith { ch ->
                mixerEngine.channelStates[ch]?.value ?: ChannelState()
            }

            val file = AudioExporter.exportAudio(
                context = getApplication(),
                option = option,
                vocalBuffer = _activeVocalBuffer.value,
                drumsBuffer = drumAudioBuffer,
                percBuffer = percAudioBuffer,
                bassBuffer = bassAudioBuffer,
                chordsBuffer = chordAudioBuffer,
                channelStates = channelMap,
                masterVolume = mixerEngine.masterVolume.value,
                useLimiter = mixerEngine.isLimiterEnabled.value,
                bpm = _activeBpm.value,
                style = _selectedStyle.value
            ) { progress ->
                _exportProgress.value = progress
            }

            _exportedFile.value = file
            _isExporting.value = false
            _userMessage.value = "Exported successfully: ${file.name}"
            withContext(Dispatchers.Main) {
                onComplete(file)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mixerEngine.release()
    }
}
