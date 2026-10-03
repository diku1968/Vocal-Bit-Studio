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
import com.example.model.MixerChannel
import com.example.ui.theme.*
import com.example.viewmodel.VocalBeatViewModel

@Composable
fun MixerScreen(
    viewModel: VocalBeatViewModel,
    modifier: Modifier = Modifier
) {
    val mixer = viewModel.mixerEngine
    val masterVol by mixer.masterVolume.collectAsState()
    val isLimiterOn by mixer.isLimiterEnabled.collectAsState()
    val isPlaying by mixer.isPlaying.collectAsState()

    val channels = listOf(
        MixerChannel.VOCAL,
        MixerChannel.DRUMS,
        MixerChannel.PERCUSSION,
        MixerChannel.BASS,
        MixerChannel.CHORDS
    )

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
                    text = "STUDIO MULTI-TRACK MIXER",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp
                    ),
                    color = NeonCyan
                )
                Text(
                    text = "Balance vocal stem with generated rhythm & harmony",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary
                )
            }

            // Quick Play/Pause in mixer
            FilledIconButton(
                onClick = { if (isPlaying) mixer.pause() else mixer.play() },
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (isPlaying) NeonPink else NeonCyan,
                    contentColor = Color.Black
                ),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = null
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Horizontal channel strips row
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(channels) { channel ->
                val stateFlow = mixer.channelStates[channel]
                val state by stateFlow?.collectAsState() ?: remember { mutableStateOf(com.example.model.ChannelState()) }

                val accentColor = when (channel) {
                    MixerChannel.VOCAL -> NeonCyan
                    MixerChannel.DRUMS -> NeonPink
                    MixerChannel.PERCUSSION -> AmberGold
                    MixerChannel.BASS -> ElectricViolet
                    MixerChannel.CHORDS -> StudioPurple
                    MixerChannel.MASTER -> Color.White
                }

                Surface(
                    modifier = Modifier
                        .width(105.dp)
                        .height(290.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = DarkStudioCard,
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (state.isSolo) accentColor else GridLine)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Channel Label
                        Text(
                            text = channel.displayName.uppercase(),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Black, fontSize = 10.sp),
                            color = accentColor,
                            maxLines = 1
                        )

                        // Solo / Mute Toggles
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Mute
                            Button(
                                onClick = { mixer.toggleMute(channel) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isMuted) Color(0xFFD90429) else DarkStudioCardElevated,
                                    contentColor = Color.White
                                )
                            ) {
                                Text("M", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }

                            // Solo
                            Button(
                                onClick = { mixer.toggleSolo(channel) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(26.dp),
                                contentPadding = PaddingValues(0.dp),
                                shape = RoundedCornerShape(4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (state.isSolo) AmberGold else DarkStudioCardElevated,
                                    contentColor = if (state.isSolo) Color.Black else Color.White
                                )
                            ) {
                                Text("S", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Simulated VU meter / level slider
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Slider(
                                value = state.volume,
                                onValueChange = { mixer.setChannelVolume(channel, it) },
                                valueRange = 0f..1f,
                                modifier = Modifier
                                    .fillMaxHeight(),
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor,
                                    inactiveTrackColor = DarkStudioCardElevated
                                )
                            )
                        }

                        // Volume Level percentage
                        Text(
                            text = "${(state.volume * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = if (state.isMuted) TextTertiary else TextPrimary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Master Output & Limiter Strip
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
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
                            text = "MASTER OUTPUT BUS",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = TextPrimary
                        )
                        Text(
                            text = "Peak Limiter & Dynamic Range Ceiling",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Limiter", style = MaterialTheme.typography.bodySmall, color = TextPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = isLimiterOn,
                            onCheckedChange = { mixer.setLimiterEnabled(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = NeonCyan)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Master Gain", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                    Text("${(masterVol * 100).toInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold), color = NeonCyan)
                }

                Slider(
                    value = masterVol,
                    onValueChange = { mixer.setMasterVolume(it) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = NeonCyan,
                        activeTrackColor = NeonCyan,
                        inactiveTrackColor = DarkStudioCard
                    )
                )
            }
        }
    }
}
