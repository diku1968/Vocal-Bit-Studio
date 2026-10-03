package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.MusicalKey
import com.example.model.NoteName
import com.example.model.ScaleType
import com.example.ui.theme.*
import com.example.viewmodel.AppTab
import com.example.viewmodel.VocalBeatViewModel

@Composable
fun AnalysisScreen(
    viewModel: VocalBeatViewModel,
    modifier: Modifier = Modifier
) {
    val analysis by viewModel.analysisResult.collectAsState()
    val activeBpm by viewModel.activeBpm.collectAsState()
    val activeKey by viewModel.activeKey.collectAsState()
    val activeTimeSig by viewModel.activeTimeSignature.collectAsState()
    val aiAdvice by viewModel.aiAdvice.collectAsState()
    val isLoadingAi by viewModel.isLoadingAi.collectAsState()

    var showManualKeyDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "AI VOCAL ANALYSIS ENGINE",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = NeonCyan
        )
        Text(
            text = "Real DSP audio analysis extracted from vocal waveform",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (analysis == null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = DarkStudioCard
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No vocal analyzed yet", color = TextSecondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { viewModel.setTab(AppTab.INPUT) },
                        colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = Color.Black)
                    ) {
                        Text("Import or Record Vocal")
                    }
                }
            }
            return
        }

        val res = analysis!!

        // Main 3 Metric Cards (BPM, KEY, TIME SIGNATURE)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // BPM Card
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = DarkStudioCardElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("BPM", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${activeBpm.toInt()}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Confidence ${res.bpmConfidence.toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextSecondary
                    )
                }
            }

            // Key Card
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = DarkStudioCardElevated,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (res.isKeyUncertain) AmberGold else NeonPink.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("KEY", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeKey.displayName,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Black),
                        color = if (res.isKeyUncertain) AmberGold else NeonPink
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Confidence ${res.keyConfidence.toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextSecondary
                    )
                }
            }

            // Time Signature Card
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = DarkStudioCardElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("TIME SIG", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = activeTimeSig.displayName,
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                        color = ElectricViolet
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Confidence ${res.timeSignatureConfidence.toInt()}%",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = TextSecondary
                    )
                }
            }
        }

        // Key Uncertainty Warning (as required by prompt)
        if (res.isKeyUncertain) {
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = AmberGold.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = AmberGold, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Key detection uncertain — please verify or select key manually.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AmberGold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Manual Key Override Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Musical Key Selection:", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
            OutlinedButton(
                onClick = { showManualKeyDialog = true },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonPink)
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Change Key (${activeKey.displayName})", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Vocal Pitch & Range Details Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkStudioCard
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "VOCAL ACOUSTIC PROPERTIES",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Vocal Range", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            text = "${res.pitchRangeMinNote} → ${res.pitchRangeMaxNote}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = NeonCyan
                        )
                        Text(
                            text = "${res.pitchRangeMinHz.toInt()} Hz - ${res.pitchRangeMaxHz.toInt()} Hz",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }

                    Column {
                        Text("Phrases Detected", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            text = "${res.phrases.size} vocal phrases",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "${res.onsetsMs.size} syllable onsets",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }

                    Column {
                        Text("Tempo Map", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                        Text(
                            text = "${res.vocalTempoMap.size} bars mapped",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = AmberGold
                        )
                        Text(
                            text = "Fixed / Follow ready",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextTertiary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AI Musical Producer Advice Card
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = DarkStudioCardElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, ElectricViolet.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AmberGold, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI PRODUCER RECOMMENDATION",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = AmberGold
                        )
                    }
                    if (isLoadingAi) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = AmberGold, strokeWidth = 2.dp)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val advice = aiAdvice
                if (advice != null) {
                    Text(
                        text = "• Recommended Style: ${advice.recommendedStyle.title}",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = NeonCyan
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "• Suggested Chords: ${advice.suggestedProgression}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "• Arrangement: ${advice.arrangementTip}",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                } else {
                    Text(
                        text = "Analyzing musical harmony and arrangement...",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Next Step CTA
        Button(
            onClick = {
                viewModel.generateRhythm(autoPlay = true)
                viewModel.setTab(AppTab.GRID)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = Color.Black
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("GENERATE RHYTHM & OPEN BEAT GRID", fontWeight = FontWeight.Bold)
        }
    }

    // Manual Key Selector Dialog
    if (showManualKeyDialog) {
        AlertDialog(
            onDismissRequest = { showManualKeyDialog = false },
            title = { Text("Select Musical Key", color = TextPrimary) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Tonic Note:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    // 12 chromatic notes
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        NoteName.entries.take(6).forEach { note ->
                            FilterChip(
                                selected = activeKey.tonic == note,
                                onClick = { viewModel.setKey(activeKey.copy(tonic = note)) },
                                label = { Text(note.displayName, fontSize = 11.sp) }
                            )
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        NoteName.entries.drop(6).forEach { note ->
                            FilterChip(
                                selected = activeKey.tonic == note,
                                onClick = { viewModel.setKey(activeKey.copy(tonic = note)) },
                                label = { Text(note.displayName, fontSize = 11.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text("Scale Mode:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ScaleType.entries.forEach { scale ->
                            FilterChip(
                                selected = activeKey.scale == scale,
                                onClick = { viewModel.setKey(activeKey.copy(scale = scale)) },
                                label = { Text(scale.displayName) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showManualKeyDialog = false }) {
                    Text("Done")
                }
            },
            containerColor = DarkStudioCard
        )
    }
}
