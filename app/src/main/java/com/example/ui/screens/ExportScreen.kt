package com.example.ui.screens

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.export.AudioExporter
import com.example.model.ExportOption
import com.example.ui.components.InterstitialAdDialog
import com.example.ui.theme.*
import com.example.viewmodel.VocalBeatViewModel
import java.io.File

@Composable
fun ExportScreen(
    viewModel: VocalBeatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedOption by remember { mutableStateOf(ExportOption.FULL_MIX) }

    val isExporting by viewModel.isExporting.collectAsState()
    val exportProgress by viewModel.exportProgress.collectAsState()
    val exportedFile by viewModel.exportedFile.collectAsState()

    var showInterstitialAd by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = "AUDIO EXPORT & STEMS",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            ),
            color = NeonCyan
        )
        Text(
            text = "Broadcast quality 44.1 kHz 16-bit PCM WAV / Standard MIDI",
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Export Options List
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportOption.entries.forEach { option ->
                val isSel = selectedOption == option
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { selectedOption = option },
                    color = if (isSel) DarkStudioCardElevated else DarkStudioCard,
                    border = androidx.compose.foundation.BorderStroke(
                        if (isSel) 1.5.dp else 1.dp,
                        if (isSel) NeonCyan else GridLine
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = option.title,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (isSel) NeonCyan else TextPrimary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    color = if (option.extension == "mid") AmberGold.copy(alpha = 0.2f) else ElectricViolet.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = ".${option.extension.uppercase()}",
                                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                        color = if (option.extension == "mid") AmberGold else ElectricViolet,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }

                        RadioButton(
                            selected = isSel,
                            onClick = { selectedOption = option },
                            colors = RadioButtonDefaults.colors(selectedColor = NeonCyan)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Export Button
        Button(
            onClick = {
                viewModel.exportTrack(selectedOption) { file ->
                    // Show Interstitial Ad before file access
                    showInterstitialAd = true
                }
            },
            enabled = !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = NeonCyan,
                contentColor = Color.Black
            )
        ) {
            if (isExporting) {
                CircularProgressIndicator(
                    progress = { exportProgress },
                    modifier = Modifier.size(20.dp),
                    color = Color.Black,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("RENDERING AUDIO (${(exportProgress * 100).toInt()}%)...", fontWeight = FontWeight.Bold)
            } else {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("EXPORT ${selectedOption.title.uppercase()}", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Exported File Ready Card
        val file = exportedFile
        if (file != null && file.exists()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = DarkStudioCardElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, AmberGold.copy(alpha = 0.7f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "FILE READY FOR USE",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = AmberGold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = TextPrimary
                            )
                            Text(
                                text = "${file.length() / 1024} KB · Saved to Cache",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }

                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = AmberGold, modifier = Modifier.size(28.dp))
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { AudioExporter.shareExportedFile(context, file) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPink,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("SHARE / SAVE AUDIO FILE", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showInterstitialAd) {
        InterstitialAdDialog(
            onDismiss = {
                showInterstitialAd = false
            }
        )
    }
}
