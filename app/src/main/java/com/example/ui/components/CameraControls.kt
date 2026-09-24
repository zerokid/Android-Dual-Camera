package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.GridOff
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.camera.RecordingStatus
import com.example.camera.SplitLayoutMode
import com.example.camera.VideoFilter
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RecordRed

@Composable
fun ShutterButton(
    recordingStatus: RecordingStatus,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRecording = recordingStatus == RecordingStatus.RECORDING || recordingStatus == RecordingStatus.PAUSED

    val buttonSize by animateDpAsState(
        targetValue = if (isRecording) 34.dp else 58.dp,
        animationSpec = tween(250),
        label = "shutter_size"
    )
    val buttonCorner by animateDpAsState(
        targetValue = if (isRecording) 8.dp else 30.dp,
        animationSpec = tween(250),
        label = "shutter_corner"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse_ring")
    val ringAlpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ring_alpha"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(80.dp)
            .clip(CircleShape)
            .clickable {
                if (isRecording) {
                    onStopRecording()
                } else {
                    onStartRecording()
                }
            }
            .testTag("shutter_button")
    ) {
        // Outer ring
        Box(
            modifier = Modifier
                .size(76.dp)
                .border(
                    width = 4.dp,
                    color = if (isRecording) RecordRed.copy(alpha = ringAlpha) else Color.White,
                    shape = CircleShape
                )
        )

        // Inner trigger shape
        Box(
            modifier = Modifier
                .size(buttonSize)
                .background(
                    color = if (isRecording) RecordRed else RecordRed,
                    shape = RoundedCornerShape(buttonCorner)
                )
        )
    }
}

@Composable
fun RecordingTimerBadge(
    recordingStatus: RecordingStatus,
    durationSec: Int,
    audioLevel: Float = 0f,
    audioEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (recordingStatus == RecordingStatus.IDLE) return

    val hours = durationSec / 3600
    val minutes = (durationSec % 3600) / 60
    val seconds = durationSec % 60
    val timeFormatted = String.format("%02d:%02d:%02d", hours, minutes, seconds)

    val infiniteTransition = rememberInfiniteTransition(label = "recording_dot")
    val dotAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xCC000000),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
        modifier = modifier.testTag("recording_timer_badge")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(
                        color = if (recordingStatus == RecordingStatus.PAUSED) AccentAmber else RecordRed.copy(alpha = dotAlpha),
                        shape = CircleShape
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = timeFormatted,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                ),
                color = Color.White
            )
            if (recordingStatus == RecordingStatus.PAUSED) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PAUSED",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentAmber,
                    fontWeight = FontWeight.Bold
                )
            } else {
                Spacer(modifier = Modifier.width(8.dp))
                AudioLevelVisualizer(
                    isRecording = true,
                    liveLevel = audioLevel,
                    audioEnabled = audioEnabled
                )
            }
        }
    }
}

@Composable
fun TopControlBar(
    isHardwareConcurrent: Boolean,
    torchEnabled: Boolean,
    audioEnabled: Boolean,
    gridEnabled: Boolean,
    videoCount: Int,
    onToggleTorch: () -> Unit,
    onToggleAudio: () -> Unit,
    onToggleGrid: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSpecs: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color(0xCC0D131D),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("top_control_bar")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Hardware Status Pill
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isHardwareConcurrent) Color(0x3300E5FF) else Color(0x33FFB703),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isHardwareConcurrent) CyberCyan.copy(alpha = 0.5f) else AccentAmber.copy(alpha = 0.5f)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onOpenSpecs() }
                    .testTag("hardware_status_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(
                                if (isHardwareConcurrent) CyberCyan else AccentAmber,
                                CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isHardwareConcurrent) "Dual ISP" else "Director Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Specs",
                        tint = if (isHardwareConcurrent) CyberCyan else AccentAmber,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            // Quick Toggle Icons
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Torch
                IconButton(
                    onClick = onToggleTorch,
                    modifier = Modifier.testTag("torch_toggle_button")
                ) {
                    Icon(
                        imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = "Torch",
                        tint = if (torchEnabled) AccentAmber else Color.White
                    )
                }

                // Audio
                IconButton(
                    onClick = onToggleAudio,
                    modifier = Modifier.testTag("audio_toggle_button")
                ) {
                    Icon(
                        imageVector = if (audioEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = "Microphone",
                        tint = if (audioEnabled) CyberCyan else Color.Gray
                    )
                }

                // Grid
                IconButton(
                    onClick = onToggleGrid,
                    modifier = Modifier.testTag("grid_toggle_button")
                ) {
                    Icon(
                        imageVector = if (gridEnabled) Icons.Default.GridOn else Icons.Default.GridOff,
                        contentDescription = "Grid",
                        tint = if (gridEnabled) CyberCyan else Color.White
                    )
                }

                // Gallery Button with Count Badge
                IconButton(
                    onClick = onOpenGallery,
                    modifier = Modifier.testTag("gallery_button")
                ) {
                    BadgedBox(
                        badge = {
                            if (videoCount > 0) {
                                Badge(
                                    containerColor = CyberCyan,
                                    contentColor = Color.Black
                                ) {
                                    Text(videoCount.toString())
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LayoutModeBar(
    currentMode: SplitLayoutMode,
    onSelectMode: (SplitLayoutMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color(0xDD0D131D),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
        modifier = modifier
            .padding(horizontal = 16.dp)
            .testTag("layout_mode_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SplitLayoutMode.values().forEach { mode ->
                val isSelected = currentMode == mode
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) CyberCyan else Color.Transparent,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectMode(mode) }
                ) {
                    Text(
                        text = when (mode) {
                            SplitLayoutMode.VERTICAL_SPLIT -> "Top/Bot"
                            SplitLayoutMode.HORIZONTAL_SPLIT -> "Side/Side"
                            SplitLayoutMode.PIP -> "PiP"
                            SplitLayoutMode.FOCUS_70_30 -> "70:30"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) Color(0xFF00252C) else Color.White,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ZoomHudBadge(
    zoomRatio: Float,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.scaleOut(),
        modifier = modifier
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xDD001A22),
            border = androidx.compose.foundation.BorderStroke(1.5.dp, CyberCyan),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CyberCyan, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = String.format("%.1fx ZOOM", zoomRatio),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    ),
                    color = CyberCyan
                )
            }
        }
    }
}

@Composable
fun ZoomQuickBar(
    currentZoom: Float,
    minZoom: Float = 1.0f,
    maxZoom: Float = 8.0f,
    onSelectZoom: (Float) -> Unit,
    onStepZoom: (Float) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Generate valid presets based on hardware range
    val allPossible = listOf(0.5f, 0.6f, 1.0f, 2.0f, 3.0f, 5.0f, 8.0f)
    val presets = allPossible.filter { it in minZoom..maxZoom }.toMutableList()
    if (!presets.contains(1.0f) && 1.0f in minZoom..maxZoom) {
        presets.add(1.0f)
        presets.sort()
    }
    if (presets.isEmpty()) {
        presets.addAll(listOf(1.0f, 2.0f, 4.0f))
    }

    Surface(
        shape = CircleShape,
        color = Color(0xCC0D131D),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
        modifier = modifier.testTag("zoom_quick_bar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Zoom Out Step Button (-)
            Surface(
                shape = CircleShape,
                color = Color(0x33FFFFFF),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onStepZoom(-0.5f) }
                    .testTag("zoom_minus_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "−",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            // Quick Preset Buttons
            presets.take(4).forEach { zoom ->
                val isSelected = (currentZoom - zoom).let { kotlin.math.abs(it) < 0.2f }
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) CyberCyan else Color.Transparent,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .clickable { onSelectZoom(zoom) }
                        .testTag("zoom_preset_${zoom}x")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (zoom == 0.5f || zoom == 0.6f) ".5" else "${zoom.toInt()}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color(0xFF00252C) else Color.White
                        )
                    }
                }
            }

            // Zoom In Step Button (+)
            Surface(
                shape = CircleShape,
                color = Color(0x33FFFFFF),
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable { onStepZoom(+0.5f) }
                    .testTag("zoom_plus_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
