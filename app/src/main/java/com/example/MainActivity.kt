package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.components.AdMobBannerBar
import com.example.ui.components.TopPlaybackBar
import com.example.ui.screens.*
import com.example.ui.theme.DarkStudioBg
import com.example.ui.theme.DarkStudioCard
import com.example.ui.theme.DarkStudioSurface
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPink
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VocalBeatAITheme
import com.example.viewmodel.AppTab
import com.example.viewmodel.VocalBeatViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VocalBeatAITheme {
                val viewModel: VocalBeatViewModel = viewModel()
                VocalBeatApp(viewModel)
            }
        }
    }
}

@Composable
fun VocalBeatApp(viewModel: VocalBeatViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    val mixer = viewModel.mixerEngine
    val isPlaying by mixer.isPlaying.collectAsState()
    val positionMs by mixer.playbackPositionMs.collectAsState()
    val durationMs by mixer.totalDurationMs.collectAsState()
    val activeBpm by viewModel.activeBpm.collectAsState()
    val isDownbeatActive by mixer.isDownbeatActive.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Display user messages via snackbar
    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    // Back handler: return to input if in sub-screen
    BackHandler(enabled = currentTab != AppTab.INPUT) {
        if (currentTab != AppTab.GRID) {
            viewModel.setTab(AppTab.GRID)
        } else {
            viewModel.setTab(AppTab.INPUT)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = DarkStudioBg,
        topBar = {
            TopPlaybackBar(
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                bpm = activeBpm,
                isDownbeatActive = isDownbeatActive,
                onPlayPause = {
                    if (isPlaying) mixer.pause() else mixer.play()
                },
                onStop = { mixer.stop() },
                onSeek = { mixer.seekTo(it) },
                onAdjustBpm = { viewModel.adjustBpm(it) },
                onTapTempo = { viewModel.tapTempo() },
                onResetBpm = { viewModel.resetToDetectedBpm() }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
                // Scrollable Studio Tabs Bar
                Surface(
                    color = DarkStudioSurface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppTab.entries.forEach { tab ->
                            val isSelected = currentTab == tab
                            val icon = getTabIcon(tab)
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setTab(tab) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (isSelected) Color.Black else NeonCyan
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = NeonCyan,
                                    selectedLabelColor = Color.Black,
                                    containerColor = DarkStudioCard,
                                    labelColor = TextPrimary
                                )
                            )
                        }
                    }
                }

                // AdMob Banner
                AdMobBannerBar()
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                AppTab.INPUT -> VocalInputScreen(viewModel)
                AppTab.ANALYSIS -> AnalysisScreen(viewModel)
                AppTab.GRID -> BeatGridScreen(viewModel)
                AppTab.RHYTHM -> RhythmStudioScreen(viewModel)
                AppTab.CHORDS -> ChordsBassScreen(viewModel)
                AppTab.SYNC -> SyncPitchScreen(viewModel)
                AppTab.MIXER -> MixerScreen(viewModel)
                AppTab.EXPORT -> ExportScreen(viewModel)
            }
        }
    }
}

private fun getTabIcon(tab: AppTab): ImageVector {
    return when (tab) {
        AppTab.INPUT -> Icons.Default.Mic
        AppTab.ANALYSIS -> Icons.Default.AutoAwesome
        AppTab.GRID -> Icons.Default.GridOn
        AppTab.RHYTHM -> Icons.Default.MusicNote
        AppTab.CHORDS -> Icons.Default.Piano
        AppTab.SYNC -> Icons.Default.Tune
        AppTab.MIXER -> Icons.Default.Equalizer
        AppTab.EXPORT -> Icons.Default.Share
    }
}
