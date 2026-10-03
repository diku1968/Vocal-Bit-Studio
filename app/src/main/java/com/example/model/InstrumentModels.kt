package com.example.model

enum class InstrumentType(val displayName: String, val category: MixerChannel, val defaultPitch: Int = 36) {
    KICK("Kick Drum", MixerChannel.DRUMS, 36),
    SNARE("Snare", MixerChannel.DRUMS, 38),
    CLAP("Clap", MixerChannel.DRUMS, 39),
    CLOSED_HAT("Closed Hi-Hat", MixerChannel.DRUMS, 42),
    OPEN_HAT("Open Hi-Hat", MixerChannel.DRUMS, 46),
    TOM("Tom Drum", MixerChannel.DRUMS, 45),
    SHAKER("Shaker", MixerChannel.PERCUSSION, 69),
    TAMBOURINE("Tambourine", MixerChannel.PERCUSSION, 54),
    TABLA_DHA("Tabla Dha (Bass)", MixerChannel.PERCUSSION, 60),
    TABLA_NA("Tabla Na (Treble)", MixerChannel.PERCUSSION, 62),
    TABLA_GE("Tabla Ge (Mod)", MixerChannel.PERCUSSION, 64),
    DHOLAK_TA("Dholak Ta", MixerChannel.PERCUSSION, 65),
    DHOLAK_DHA("Dholak Dha", MixerChannel.PERCUSSION, 67),
    DHOL_BOOM("Dhol Boom", MixerChannel.PERCUSSION, 35),
    DHOL_SLAP("Dhol Slap", MixerChannel.PERCUSSION, 40)
}

enum class MixerChannel(val displayName: String) {
    VOCAL("Vocal"),
    DRUMS("Drums"),
    PERCUSSION("Percussion"),
    BASS("Bass"),
    CHORDS("Chords"),
    MASTER("Master")
}

data class ChannelState(
    val volume: Float = 0.85f,
    val isMuted: Boolean = false,
    val isSolo: Boolean = false
)

data class RhythmHit(
    val stepIndex: Int, // 16th note step index in bar (0..15)
    val instrument: InstrumentType,
    val velocity: Float = 0.8f
)

data class ChordVoicing(
    val chordName: String,
    val rootPitch: Int, // MIDI note (e.g. 60 = C4)
    val midiPitches: List<Int>,
    val durationBeats: Double = 4.0
)

data class BassNote(
    val midiPitch: Int,
    val startBeat: Double,
    val durationBeats: Double,
    val velocity: Float = 0.85f
)

enum class ExportOption(val id: String, val title: String, val description: String, val extension: String) {
    VOCAL_ONLY("vocal_only", "Vocal Only (Dry/Synced)", "Original or time-synced vocal isolated as 44.1kHz WAV", "wav"),
    BEAT_ONLY("beat_only", "Beat & Percussion Only", "Drum kit + Indian percussion backing track as WAV", "wav"),
    VOCAL_BEAT("vocal_beat", "Vocal + Beat", "Vocals balanced with rhythmic accompaniment", "wav"),
    FULL_MIX("full_mix", "Full Master Mix", "Vocal + Beat + Bass + Chords with master limiter", "wav"),
    MIDI_RHYTHM("midi_rhythm", "MIDI Rhythm Pattern", "Standard MIDI file (.mid) with all percussion tracks", "mid")
}
