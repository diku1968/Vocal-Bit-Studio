package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ArrangementSection
import com.example.model.PhraseBoundary
import com.example.ui.theme.*
import kotlin.math.max

@Composable
fun WaveformBeatGridView(
    waveformPeaks: FloatArray,
    durationMs: Long,
    playbackPositionMs: Long,
    bpm: Double,
    firstDownbeatMs: Long,
    phrases: List<PhraseBoundary>,
    sections: List<ArrangementSection>,
    isDownbeatActive: Boolean,
    onSeek: (Long) -> Unit,
    onSetFirstBeat: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Smooth pulse animation when downbeat occurs
    val pulseAlpha by animateFloatAsState(
        targetValue = if (isDownbeatActive) 0.85f else 0.15f,
        animationSpec = tween(durationMillis = if (isDownbeatActive) 60 else 180),
        label = "downbeat_pulse"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkStudioCard)
            .padding(10.dp)
    ) {
        // Timeline Header: Section Chips
        if (sections.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sections.take(6).forEach { sec ->
                    Surface(
                        color = Color(sec.type.colorHex).copy(alpha = 0.25f),
                        shape = RoundedCornerShape(4.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(sec.type.colorHex).copy(alpha = 0.7f))
                    ) {
                        Text(
                            text = "${sec.name} (B${sec.startBar})",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                            color = Color(sec.type.colorHex),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // Interactive Waveform + Beat Grid Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F121C))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(durationMs) {
                        detectTapGestures(
                            onTap = { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                val seekMs = (fraction * durationMs).toLong()
                                onSeek(seekMs)
                            },
                            onLongPress = { offset ->
                                val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                                val beatMs = (fraction * durationMs).toLong()
                                onSetFirstBeat(beatMs)
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height
                val midY = h / 2f
                val safeDur = max(1000L, durationMs).toFloat()

                // 1. Draw Downbeat Glow Background
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            NeonPink.copy(alpha = pulseAlpha * 0.35f),
                            Color.Transparent
                        )
                    )
                )

                // 2. Draw Beat & Bar Grid Lines
                val beatIntervalMs = (60000.0 / max(40.0, bpm)).toFloat()
                val barIntervalMs = beatIntervalMs * 4f
                val downbeatOffsetMs = firstDownbeatMs.toFloat()

                var currentBar = 1
                var barTime = downbeatOffsetMs
                while (barTime < safeDur) {
                    val x = (barTime / safeDur) * w

                    // Bar line (Thick Electric Violet / Pink)
                    drawLine(
                        color = NeonPink.copy(alpha = 0.65f),
                        start = Offset(x, 0f),
                        end = Offset(x, h),
                        strokeWidth = 2.5f
                    )

                    // Bar Number Text
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "Bar $currentBar",
                        style = TextStyle(color = NeonPink, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        topLeft = Offset(x + 4f, 4f)
                    )

                    // Intermediate beat lines (1.2, 1.3, 1.4)
                    for (beat in 1..3) {
                        val beatTime = barTime + beat * beatIntervalMs
                        if (beatTime < safeDur) {
                            val beatX = (beatTime / safeDur) * w
                            drawLine(
                                color = GridLine.copy(alpha = 0.85f),
                                start = Offset(beatX, 16f),
                                end = Offset(beatX, h - 8f),
                                strokeWidth = 1f
                            )
                        }
                    }

                    barTime += barIntervalMs
                    currentBar++
                }

                // 3. Draw Vocal Phrases and Silence Highlights
                for (phrase in phrases) {
                    val startX = (phrase.startMs.toFloat() / safeDur) * w
                    val endX = (phrase.endMs.toFloat() / safeDur) * w
                    val phraseW = (endX - startX).coerceAtLeast(2f)

                    drawRect(
                        color = NeonCyan.copy(alpha = 0.08f),
                        topLeft = Offset(startX, 18f),
                        size = androidx.compose.ui.geometry.Size(phraseW, h - 26f)
                    )
                }

                // 4. Draw Waveform Peaks
                if (waveformPeaks.isNotEmpty()) {
                    val barWidth = (w / waveformPeaks.size)
                    val playedFraction = (playbackPositionMs.toFloat() / safeDur).coerceIn(0f, 1f)
                    val playheadX = playedFraction * w

                    for (i in waveformPeaks.indices) {
                        val x = i * barWidth
                        val peakAmp = waveformPeaks[i].coerceIn(0.04f, 1.0f)
                        val barHeight = peakAmp * (midY - 14f)

                        val isPlayed = x <= playheadX
                        val barColor = if (isPlayed) {
                            NeonCyan
                        } else {
                            WaveformInactive
                        }

                        drawLine(
                            color = barColor,
                            start = Offset(x, midY - barHeight),
                            end = Offset(x, midY + barHeight),
                            strokeWidth = (barWidth * 0.75f).coerceAtLeast(1.5f)
                        )
                    }
                }

                // 5. First Downbeat Marker Flag (draggable / editable)
                val firstBeatX = (firstDownbeatMs.toFloat() / safeDur) * w
                drawLine(
                    color = AmberGold,
                    start = Offset(firstBeatX, 0f),
                    end = Offset(firstBeatX, h),
                    strokeWidth = 3f
                )
                // Downbeat inverted triangle marker
                val flagPath = Path().apply {
                    moveTo(firstBeatX - 6f, 0f)
                    lineTo(firstBeatX + 6f, 0f)
                    lineTo(firstBeatX, 10f)
                    close()
                }
                drawPath(flagPath, color = AmberGold)

                // 6. Playhead Cursor (Bright Red/Pink line with smooth movement)
                val playheadFraction = (playbackPositionMs.toFloat() / safeDur).coerceIn(0f, 1f)
                val currentPlayheadX = playheadFraction * w

                drawLine(
                    color = Color.White,
                    start = Offset(currentPlayheadX, 0f),
                    end = Offset(currentPlayheadX, h),
                    strokeWidth = 2.5f
                )
                // Playhead top handle
                drawCircle(
                    color = if (isDownbeatActive) NeonPink else NeonCyan,
                    radius = 5.5f,
                    center = Offset(currentPlayheadX, 7f)
                )
            }
        }

        // Footer Legend & Action hint
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).background(NeonPink, RoundedCornerShape(2.dp)))
                Text("Bar / Downbeat", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)

                Box(modifier = Modifier.size(8.dp).background(AmberGold, RoundedCornerShape(2.dp)))
                Text("First Beat (Long-press to set)", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = TextSecondary)
            }

            Text(
                text = "Tap waveform to seek",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextTertiary
            )
        }
    }
}
