package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.example.model.BassPatternType
import com.example.model.ChordProgressionStyle
import com.example.ui.theme.*
import com.example.viewmodel.VocalBeatViewModel

@Composable
fun ChordsBassScreen(
    viewModel: VocalBeatViewModel,
    modifier: Modifier = Modifier
) {
    val activeKey by viewModel.activeKey.collectAsState()
    val chordsEnabled by viewModel.chordsEnabled.collectAsState()
    val chordStyle by viewModel.chordStyle.collectAsState()
    val detectedChords by viewModel.detectedChords.collectAsState()

    val bassEnabled by viewModel.bassEnabled.collectAsState()
    val bassPattern by viewModel.bassPattern.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "CHORD & BASS ACCOMPANIMENT",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = NeonCyan
        )
        Text(
            text = "Harmonically tuned to detected key: ${activeKey.displayName}",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Active Chord Progression Badges
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkStudioCardElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.6f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "ACTIVE CHORD CYCLE",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ElectricViolet
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chords = if (detectedChords.isNotEmpty()) detectedChords else listOf("G#m", "E", "B", "F#")
                    chords.forEach { chord ->
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF231E3D),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NeonPink)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = chord,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Black),
                                    color = NeonPink
                                )
                                Text(
                                    text = "4 beats",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = TextTertiary
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 1: CHORD GENERATOR CONTROLS
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
                    Column {
                        Text(
                            text = "CHORDS GENERATION",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = NeonCyan
                        )
                        Text(
                            text = "Polyphonic piano & electric pad chords",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = chordsEnabled,
                        onCheckedChange = { viewModel.setChordsEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = ElectricViolet
                        )
                    )
                }

                if (chordsEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Chord Progression Style:", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChordProgressionStyle.entries.forEach { style ->
                            val isSel = chordStyle == style
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { viewModel.setChordStyle(style) },
                                color = if (isSel) DarkStudioCardElevated else Color.Transparent,
                                border = androidx.compose.foundation.BorderStroke(1.dp, if (isSel) NeonCyan else GridLine)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = style.displayName,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = if (isSel) NeonCyan else TextPrimary
                                        )
                                        Text(text = style.description, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    }
                                    RadioButton(
                                        selected = isSel,
                                        onClick = { viewModel.setChordStyle(style) },
                                        colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // SECTION 2: BASS GENERATOR CONTROLS
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
                    Column {
                        Text(
                            text = "BASS GENERATION",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = AmberGold
                        )
                        Text(
                            text = "Warm analog synth bass following chord roots",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = bassEnabled,
                        onCheckedChange = { viewModel.setBassEnabled(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AmberGold,
                            checkedTrackColor = ElectricViolet
                        )
                    )
                }

                if (bassEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Bass Groove Pattern:", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BassPatternType.entries.forEach { pattern ->
                            val isSel = bassPattern == pattern
                            FilterChip(
                                selected = isSel,
                                onClick = { viewModel.setBassPattern(pattern) },
                                label = { Text(pattern.displayName, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = AmberGold,
                                    selectedLabelColor = Color.Black
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}
