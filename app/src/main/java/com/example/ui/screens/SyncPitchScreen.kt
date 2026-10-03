package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import com.example.model.VocalSyncMode
import com.example.ui.theme.*
import com.example.viewmodel.VocalBeatViewModel

@Composable
fun SyncPitchScreen(
    viewModel: VocalBeatViewModel,
    modifier: Modifier = Modifier
) {
    val syncMode by viewModel.vocalSyncMode.collectAsState()
    val semitones by viewModel.pitchShiftSemitones.collectAsState()
    val preserveTempo by viewModel.preserveTempo.collectAsState()
    val activeBpm by viewModel.activeBpm.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "VOCAL SYNCHRONIZATION & PITCH SHIFT",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = NeonCyan
        )
        Text(
            text = "Non-destructive processing · Original vocal remains untouched",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 1. Vocal + Beat Synchronization Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = DarkStudioCardElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SYNC VOCAL TO BEAT",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = NeonCyan
                        )
                        Text(
                            text = "Aligns singing syllables to 16th grid using SOLA time-stretch",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Icon(Icons.Default.Sync, contentDescription = null, tint = NeonCyan)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    VocalSyncMode.entries.forEach { mode ->
                        val isSel = syncMode == mode
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setVocalSyncMode(mode) },
                            color = if (isSel) NeonCyan else DarkStudioCard,
                            border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) NeonCyan else GridLine)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = mode.displayName,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSel) Color.Black else TextPrimary,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (mode == VocalSyncMode.OFF) "Natural" else "${(mode.strength * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = if (isSel) Color.Black.copy(alpha = 0.7f) else TextSecondary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Generates: synced_vocal.wav (Original audio preserved)",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextTertiary
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Key Shift / Transpose Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            color = DarkStudioCard
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "VOCAL KEY SHIFT / TRANSPOSE",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = NeonPink
                        )
                        Text(
                            text = "-12 to +12 semitones with pitch-preserving algorithm",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Surface(
                        color = NeonPink.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Preserve Tempo: ON",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                            color = NeonPink,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Semitone Shift:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                    Text(
                        text = if (semitones > 0) "+$semitones st" else if (semitones < 0) "$semitones st" else "0 (Original Pitch)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                        color = if (semitones == 0) TextPrimary else NeonPink
                    )
                }

                Slider(
                    value = semitones.toFloat(),
                    onValueChange = { viewModel.setPitchShiftSemitones(it.toInt()) },
                    valueRange = -12f..12f,
                    steps = 23,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonPink,
                        activeTrackColor = NeonPink,
                        inactiveTrackColor = DarkStudioCardElevated
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("-12 (Octave Down)", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                    TextButton(onClick = { viewModel.setPitchShiftSemitones(0) }) {
                        Text("Reset Pitch", fontSize = 11.sp, color = NeonCyan)
                    }
                    Text("+12 (Octave Up)", style = MaterialTheme.typography.labelSmall, color = TextTertiary)
                }
            }
        }
    }
}
