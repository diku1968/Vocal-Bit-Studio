package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GridQuantize
import com.example.model.TempoMode
import com.example.ui.components.WaveformBeatGridView
import com.example.ui.theme.*
import com.example.viewmodel.VocalBeatViewModel

@Composable
fun BeatGridScreen(
    viewModel: VocalBeatViewModel,
    modifier: Modifier = Modifier
) {
    val analysis by viewModel.analysisResult.collectAsState()
    val activeBpm by viewModel.activeBpm.collectAsState()
    val firstDownbeatMs by viewModel.firstDownbeatMs.collectAsState()
    val quantize by viewModel.quantizeGrid.collectAsState()
    val swing by viewModel.swingPercent.collectAsState()
    val tempoMode by viewModel.tempoMode.collectAsState()
    val sections by viewModel.sections.collectAsState()
    val durationMs by viewModel.vocalDurationMs.collectAsState()

    val playbackPos by viewModel.mixerEngine.playbackPositionMs.collectAsState()
    val isDownbeatActive by viewModel.mixerEngine.isDownbeatActive.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DAW BEAT GRID & VOCAL PHRASING",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = NeonCyan
                )
                Text(
                    text = "Synchronized to ${activeBpm.toInt()} BPM · Downbeat aligned",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            Surface(
                color = if (tempoMode == TempoMode.VOCAL_FOLLOW) AmberGold.copy(alpha = 0.2f) else NeonCyan.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (tempoMode == TempoMode.VOCAL_FOLLOW) AmberGold else NeonCyan
                )
            ) {
                Text(
                    text = tempoMode.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (tempoMode == TempoMode.VOCAL_FOLLOW) AmberGold else NeonCyan,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive Waveform & Grid View
        WaveformBeatGridView(
            waveformPeaks = analysis?.waveformPeaks ?: FloatArray(0),
            durationMs = durationMs,
            playbackPositionMs = playbackPos,
            bpm = activeBpm,
            firstDownbeatMs = firstDownbeatMs,
            phrases = analysis?.phrases ?: emptyList(),
            sections = sections,
            isDownbeatActive = isDownbeatActive,
            onSeek = { viewModel.mixerEngine.seekTo(it) },
            onSetFirstBeat = { viewModel.setFirstDownbeatMs(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Visual FL Studio Style Beat Step Matrix Preview (Bar 1 to 4)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkStudioCard
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BEAT SUBDIVISIONS",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Text(
                        text = "16 Steps / Bar",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Display 4 quarter beat blocks: | 1.1 1.2 1.3 1.4 | 2.1 2.2 2.3 2.4 |
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (beat in 1..4) {
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            color = if (beat == 1) NeonPink.copy(alpha = 0.25f) else DarkStudioCardElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (beat == 1) NeonPink else GridLine
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (beat == 1) "Beat 1 ★" else "Beat $beat",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (beat == 1) NeonPink else TextPrimary
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    repeat(4) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(if (beat == 1) NeonPink.copy(alpha = 0.7f) else NeonCyan.copy(alpha = 0.5f))
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Grid Controls: Quantize, Swing, Tempo Mode
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkStudioCardElevated
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "GRID & TIMING CONTROLS",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = AmberGold
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 1. Quantize Grid (1/4, 1/8, 1/16)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Grid Quantize:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GridQuantize.entries.forEach { q ->
                            FilterChip(
                                selected = quantize == q,
                                onClick = { viewModel.setQuantizeGrid(q) },
                                label = { Text(q.displayName) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Swing Control (0% - 100%)
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Groove Swing:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("$swing%", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = NeonCyan)
                    }
                    Slider(
                        value = swing.toFloat(),
                        onValueChange = { viewModel.setSwingPercent(it.toInt()) },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = DarkStudioCard
                        )
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Tempo Mode: Fixed Tempo vs Vocal Follow
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tempo Engine:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text(
                            text = if (tempoMode == TempoMode.FIXED_TEMPO) "Rock-solid constant BPM" else "Subtly adapts to natural vocal timing variations",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = tempoMode == TempoMode.FIXED_TEMPO,
                            onClick = { viewModel.setTempoMode(TempoMode.FIXED_TEMPO) },
                            label = { Text("Fixed") }
                        )
                        FilterChip(
                            selected = tempoMode == TempoMode.VOCAL_FOLLOW,
                            onClick = { viewModel.setTempoMode(TempoMode.VOCAL_FOLLOW) },
                            label = { Text("Vocal Follow") }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. First Downbeat Adjuster
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("First Downbeat Offset:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                        Text("${firstDownbeatMs} ms", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = AmberGold)
                    }
                    Slider(
                        value = firstDownbeatMs.toFloat(),
                        onValueChange = { viewModel.setFirstDownbeatMs(it.toLong()) },
                        valueRange = 0f..maxOf(2000f, (60000.0 / activeBpm).toFloat() * 2f),
                        colors = SliderDefaults.colors(
                            thumbColor = AmberGold,
                            activeTrackColor = AmberGold,
                            inactiveTrackColor = DarkStudioCard
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Vocal Tempo Map Table
        val tempoMap = analysis?.vocalTempoMap ?: emptyList()
        if (tempoMap.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DarkStudioCard
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "VOCAL TEMPO MAP (BAR-BY-BAR DYNAMICS)",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(tempoMap.take(16)) { bar ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = DarkStudioCardElevated,
                                border = androidx.compose.foundation.BorderStroke(1.dp, GridLine)
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Bar ${bar.barIndex}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(
                                        text = "${bar.localBpm}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = NeonCyan
                                    )
                                    Text("BPM", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = TextTertiary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
