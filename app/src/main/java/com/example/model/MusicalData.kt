package com.example.model

enum class NoteName(val displayName: String, val semitoneFromC: Int) {
    C("C", 0),
    C_SHARP("C#", 1),
    D("D", 2),
    D_SHARP("Eb", 3),
    E("E", 4),
    F("F", 5),
    F_SHARP("F#", 6),
    G("G", 7),
    G_SHARP("G#", 8),
    A("A", 9),
    A_SHARP("Bb", 10),
    B("B", 11);

    companion object {
        fun fromSemitone(semitone: Int): NoteName {
            val normalized = ((semitone % 12) + 12) % 12
            return entries.first { it.semitoneFromC == normalized }
        }
    }
}

enum class ScaleType(val displayName: String) {
    MAJOR("Major"),
    MINOR("Minor")
}

data class MusicalKey(
    val tonic: NoteName,
    val scale: ScaleType
) {
    val displayName: String get() = "${tonic.displayName} ${scale.displayName}"
}

enum class TimeSignature(val beatsPerBar: Int, val beatUnit: Int, val displayName: String) {
    FOUR_FOUR(4, 4, "4/4"),
    SIX_EIGHT(6, 8, "6/8"),
    THREE_FOUR(3, 4, "3/4")
}

enum class RhythmStyle(
    val id: String,
    val title: String,
    val category: String,
    val description: String,
    val defaultBpm: Double = 96.0
) {
    BOLLYWOOD("bollywood", "Bollywood", "Indian", "Signature filmi beat with punchy kick, dholak syncopation & claps", 96.0),
    ROMANTIC_BOLLYWOOD("romantic_bollywood", "Romantic Bollywood", "Indian", "Gentle acoustic groove with soft rimshot, shakers & warm tabla", 84.0),
    BOLLYWOOD_DANCE("bollywood_dance", "Bollywood Dance", "Indian", "Four-on-the-floor kick, sharp claps, high energy dhol accents", 124.0),
    INDIAN_POP("indian_pop", "Indian Pop", "Indian", "Modern fusion pop beat with syncopated hats and percussion", 105.0),
    ACOUSTIC("acoustic", "Acoustic", "Global", "Organic cajon kick, warm brush snare and soft tambourine", 88.0),
    ROCK("rock", "Rock", "Global", "Driving punchy rock groove with steady hats and powerful backbeat", 118.0),
    POP("pop", "Pop", "Global", "Radio-ready modern pop groove with bouncy snare and ghost claps", 112.0),
    HIP_HOP("hip_hop", "Hip-Hop", "Global", "Boom-bap rhythm with swung hats and deep saturated kicks", 90.0),
    LO_FI("lo_fi", "Lo-Fi", "Global", "Relaxed swung rhythm, warm dusty vinyl shakers and soft snare", 82.0),
    GARBA("garba", "Garba", "Folk", "Energetic Gujarati 3-Taali & Dodhiya clapping with dynamic dhol", 116.0),
    GUJARATI_FOLK("gujarati_folk", "Gujarati Folk", "Folk", "Traditional Keharwa/Dadra folk rhythm with dholak and claps", 108.0),
    DEVOTIONAL("devotional", "Devotional", "Spiritual", "Soulful Bhajan groove with kartal/manjira clinks and resonant dholak", 78.0),
    CUSTOM("custom", "Custom", "User", "Customizable step-sequencer rhythm grid", 100.0)
}

enum class RhythmComplexity(val displayName: String, val description: String) {
    SIMPLE("Simple", "Kick + Snare + Hi-Hat essentials"),
    MEDIUM("Medium", "Adds shakers, percussion & ghost notes"),
    ADVANCED("Advanced", "Fills, syncopation & dynamic phrase variations")
}

enum class TempoMode(val displayName: String) {
    FIXED_TEMPO("Fixed Tempo"),
    VOCAL_FOLLOW("Vocal Follow")
}

enum class VocalSyncMode(val displayName: String, val strength: Float) {
    OFF("Original", 0f),
    LIGHT("Light Sync", 0.35f),
    MEDIUM("Medium Sync", 0.70f),
    STRONG("Strong Sync", 1.0f)
}

enum class GridQuantize(val displayName: String, val subdivisionsPerBeat: Int) {
    QUARTER("1/4", 1),
    EIGHTH("1/8", 2),
    SIXTEENTH("1/16", 4)
}

enum class ChordProgressionStyle(val displayName: String, val description: String) {
    SIMPLE("Simple", "Classic I - V - vi - IV cadence"),
    EMOTIONAL("Emotional", "Soulful vi - IV - I - V progression"),
    ROMANTIC("Romantic", "Tender I - vi - IV - V movement"),
    BOLLYWOOD("Bollywood", "Filmi modal i - VII - VI - V tension"),
    MODERN_POP("Modern Pop", "Dynamic multi-chord uplifting flow")
}

enum class BassPatternType(val displayName: String) {
    ROOT("Root Only"),
    SIMPLE("Simple 8ths"),
    GROOVE("Syncopated Groove"),
    WALKING("Walking Bass")
}

enum class ArrangementSectionType(val displayName: String, val colorHex: Long) {
    INTRO("Intro", 0xFF6C757D),
    VERSE("Verse", 0xFF2A9D8F),
    PRE_CHORUS("Pre-Chorus", 0xFFE76F51),
    CHORUS("Chorus", 0xFFE63946),
    INTERLUDE("Interlude", 0xFF9D4EDD),
    OUTRO("Outro", 0xFF457B9D)
}

data class ArrangementSection(
    val type: ArrangementSectionType,
    val name: String,
    val startBar: Int,
    val barCount: Int,
    val energyLevel: Float // 0.0 to 1.0
)

data class BarTempo(
    val barIndex: Int,
    val localBpm: Double,
    val startMs: Long,
    val endMs: Long
)

data class PhraseBoundary(
    val index: Int,
    val startMs: Long,
    val endMs: Long,
    val peakEnergy: Float,
    val isSilence: Boolean = false
)

data class AudioAnalysisResult(
    val detectedBpm: Double,
    val bpmConfidence: Float,
    val musicalKey: MusicalKey,
    val keyConfidence: Float,
    val isKeyUncertain: Boolean,
    val timeSignature: TimeSignature,
    val timeSignatureConfidence: Float,
    val durationMs: Long,
    val sampleRate: Int,
    val pitchRangeMinHz: Float,
    val pitchRangeMaxHz: Float,
    val pitchRangeMinNote: String,
    val pitchRangeMaxNote: String,
    val onsetsMs: List<Long>,
    val phrases: List<PhraseBoundary>,
    val vocalTempoMap: List<BarTempo>,
    val waveformPeaks: FloatArray,
    val firstDownbeatMs: Long
)
