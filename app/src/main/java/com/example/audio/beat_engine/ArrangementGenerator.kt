package com.example.audio.beat_engine

import com.example.model.ArrangementSection
import com.example.model.ArrangementSectionType
import com.example.model.PhraseBoundary

object ArrangementGenerator {

    fun generateArrangement(
        durationMs: Long,
        bpm: Double,
        phrases: List<PhraseBoundary>
    ): List<ArrangementSection> {
        val barDurationMs = 240000.0 / bpm
        val totalBars = (durationMs / barDurationMs).toInt().coerceAtLeast(4)

        val sections = mutableListOf<ArrangementSection>()

        if (totalBars <= 8) {
            // Short vocal: Intro -> Verse -> Outro
            sections.add(ArrangementSection(ArrangementSectionType.INTRO, "Intro", 1, 2, 0.4f))
            sections.add(ArrangementSection(ArrangementSectionType.VERSE, "Main Vocal", 3, (totalBars - 3).coerceAtLeast(1), 0.8f))
            sections.add(ArrangementSection(ArrangementSectionType.OUTRO, "Outro", totalBars, 1, 0.5f))
            return sections
        }

        if (totalBars <= 16) {
            // 16 bar structure: Intro (2) -> Verse (6) -> Chorus (6) -> Outro (2)
            sections.add(ArrangementSection(ArrangementSectionType.INTRO, "Intro", 1, 2, 0.4f))
            sections.add(ArrangementSection(ArrangementSectionType.VERSE, "Verse", 3, 6, 0.7f))
            sections.add(ArrangementSection(ArrangementSectionType.CHORUS, "Chorus", 9, 6, 0.95f))
            sections.add(ArrangementSection(ArrangementSectionType.OUTRO, "Outro", 15, 2, 0.5f))
            return sections
        }

        // Full structure: Intro -> Verse 1 -> Pre-Chorus -> Chorus -> Interlude -> Verse 2 -> Chorus -> Outro
        var currentBar = 1
        // 1. Intro (2-4 bars)
        val introBars = 4.coerceAtMost(totalBars / 8)
        sections.add(ArrangementSection(ArrangementSectionType.INTRO, "Intro", currentBar, introBars, 0.4f))
        currentBar += introBars

        // 2. Verse (4-8 bars)
        val verseBars = 8.coerceAtMost((totalBars - currentBar) / 3)
        sections.add(ArrangementSection(ArrangementSectionType.VERSE, "Verse 1", currentBar, verseBars, 0.65f))
        currentBar += verseBars

        // 3. Pre-Chorus (4 bars)
        if (totalBars - currentBar >= 12) {
            sections.add(ArrangementSection(ArrangementSectionType.PRE_CHORUS, "Pre-Chorus", currentBar, 4, 0.80f))
            currentBar += 4
        }

        // 4. Chorus (8 bars)
        val chorusBars = 8.coerceAtMost((totalBars - currentBar) / 2)
        sections.add(ArrangementSection(ArrangementSectionType.CHORUS, "Chorus", currentBar, chorusBars, 1.0f))
        currentBar += chorusBars

        // 5. Interlude / Verse 2 / Outro
        if (totalBars - currentBar >= 6) {
            val remain = totalBars - currentBar
            val interludeBars = 2.coerceAtMost(remain / 2)
            sections.add(ArrangementSection(ArrangementSectionType.INTERLUDE, "Interlude", currentBar, interludeBars, 0.7f))
            currentBar += interludeBars

            val outroBars = totalBars - currentBar + 1
            if (outroBars > 0) {
                sections.add(ArrangementSection(ArrangementSectionType.OUTRO, "Outro", currentBar, outroBars, 0.45f))
            }
        } else if (currentBar <= totalBars) {
            sections.add(ArrangementSection(ArrangementSectionType.OUTRO, "Outro", currentBar, totalBars - currentBar + 1, 0.45f))
        }

        return sections
    }
}
