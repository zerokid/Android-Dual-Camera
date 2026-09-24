package com.example.ui.components

import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Videocam
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import android.view.ViewGroup
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.camera.LensFacing
import com.example.camera.VideoFilter
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.RecordRed

@Composable
fun CameraPreviewSurface(
    previewView: PreviewView,
    modifier: Modifier = Modifier,
    zoomScale: Float = 1.0f
) {
    DisposableEffect(previewView) {
        onDispose {
            (previewView.parent as? ViewGroup)?.removeView(previewView)
        }
    }

    AndroidView(
        factory = {
            (previewView.parent as? ViewGroup)?.removeView(previewView)
            previewView
        },
        modifier = modifier
            .testTag("camera_preview_surface")
            .graphicsLayer {
                if (zoomScale > 1.0f) {
                    scaleX = zoomScale
                    scaleY = zoomScale
                }
            },
        update = { view ->
            view.requestLayout()
        }
    )
}

@Composable
fun SimulatedDirectorFeed(
    lensFacing: LensFacing,
    onTapToSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "simulated_lens")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )
    val scanLineY by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scanline_y"
    )

    Box(
        modifier = modifier
            .background(Color(0xFF0F172A))
            .clickable { onTapToSwap() }
            .testTag("simulated_director_feed")
    ) {
        // High-tech Director Grid & Radar Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Subtle vignette & dark gradient
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x3300E5FF), Color(0xDD0B0F19)),
                    center = Offset(w / 2f, h / 2f),
                    radius = (w.coerceAtLeast(h) * 0.7f)
                )
            )

            // Crosshair guidelines
            val linePaint = Color(0x3300E5FF)
            drawLine(linePaint, Offset(w * 0.5f, h * 0.2f), Offset(w * 0.5f, h * 0.8f), strokeWidth = 1f)
            drawLine(linePaint, Offset(w * 0.2f, h * 0.5f), Offset(w * 0.8f, h * 0.5f), strokeWidth = 1f)

            // Scanning line
            val curY = h * scanLineY
            drawLine(
                color = CyberCyan.copy(alpha = 0.25f),
                start = Offset(0f, curY),
                end = Offset(w, curY),
                strokeWidth = 2f
            )

            // Corner targeting brackets
            val bracketLen = 28f
            val bracketColor = CyberCyan.copy(alpha = pulseAlpha)
            val stroke = 3f

            // Top-Left
            drawLine(bracketColor, Offset(30f, 30f), Offset(30f + bracketLen, 30f), strokeWidth = stroke)
            drawLine(bracketColor, Offset(30f, 30f), Offset(30f, 30f + bracketLen), strokeWidth = stroke)

            // Top-Right
            drawLine(bracketColor, Offset(w - 30f, 30f), Offset(w - 30f - bracketLen, 30f), strokeWidth = stroke)
            drawLine(bracketColor, Offset(w - 30f, 30f), Offset(w - 30f, 30f + bracketLen), strokeWidth = stroke)

            // Bottom-Left
            drawLine(bracketColor, Offset(30f, h - 30f), Offset(30f + bracketLen, h - 30f), strokeWidth = stroke)
            drawLine(bracketColor, Offset(30f, h - 30f), Offset(30f, h - 30f - bracketLen), strokeWidth = stroke)

            // Bottom-Right
            drawLine(bracketColor, Offset(w - 30f, h - 30f), Offset(w - 30f - bracketLen, h - 30f), strokeWidth = stroke)
            drawLine(bracketColor, Offset(w - 30f, h - 30f), Offset(w - 30f, h - 30f - bracketLen), strokeWidth = stroke)
        }

        // Center Subject Tracker Icon & Lens Details
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0x3300E5FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = pulseAlpha)),
                modifier = Modifier.size(76.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (lensFacing == LensFacing.FRONT) Icons.Default.Person else Icons.Default.Videocam,
                        contentDescription = "Simulated Lens",
                        tint = CyberCyan,
                        modifier = Modifier.size(38.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xCC0B1220),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FlipCameraAndroid,
                        contentDescription = "Swap Active Lens",
                        tint = CyberCyan,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (lensFacing == LensFacing.FRONT) "FRONT FEED • TAP TO SWITCH" else "BACK FEED • TAP TO SWITCH",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Lens Specs Badge (Top Left)
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xBB000000),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(CyberCyan, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (lensFacing == LensFacing.FRONT) "FRONT 12MP (f/2.2)" else "BACK 50MP OIS (f/1.8)",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyberCyan,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun GridLinesOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val gridColor = Color(0x44FFFFFF)

        // Vertical lines (3x3 grid)
        drawLine(gridColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1f)
        drawLine(gridColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1f)

        // Horizontal lines
        drawLine(gridColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1f)
        drawLine(gridColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1f)
    }
}

@Composable
fun FilterColorOverlay(
    filter: VideoFilter,
    modifier: Modifier = Modifier
) {
    when (filter) {
        VideoFilter.NORMAL -> { /* no-op */ }
        VideoFilter.VIVID -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0x15FF6D00))
            )
        }
        VideoFilter.CINEMATIC -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0x22FFA000), Color(0x33002244))
                        )
                    )
            )
        }
        VideoFilter.NOIR -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(Color(0x40333333))
            )
        }
        VideoFilter.CYBER -> {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0x2000E5FF), Color(0x20FF007F))
                        )
                    )
            )
        }
    }
}

@Composable
fun LensSlotBadge(
    lens: LensFacing,
    isPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPrimary) Color(0xCC00363D) else Color(0xCC1E293B),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isPrimary) CyberCyan.copy(alpha = 0.8f) else Color(0x44FFFFFF)
        ),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(if (isPrimary) CyberCyan else Color.LightGray, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "${if (lens == LensFacing.BACK) "BACK LENS" else "FRONT SELFIE"}${if (isPrimary) " • REC" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = if (isPrimary) CyberCyan else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
fun AudioLevelVisualizer(
    isRecording: Boolean,
    liveLevel: Float = 0f,
    audioEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!isRecording) return

    if (!audioEnabled) {
        Row(
            modifier = modifier
                .background(Color(0x99000000), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "MIC OFF",
                color = Color.Gray,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "audio_meter")
    val ambient1 by infiniteTransition.animateFloat(
        initialValue = 0.1f, targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(300), RepeatMode.Reverse), label = "a1"
    )
    val ambient2 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.5f,
        animationSpec = infiniteRepeatable(tween(220), RepeatMode.Reverse), label = "a2"
    )

    // Blend live RMS sound level with ambient animation
    val level1 = (liveLevel * 1.2f + ambient1 * 0.3f).coerceIn(0.15f, 1.0f)
    val level2 = (liveLevel * 1.4f + ambient2 * 0.4f).coerceIn(0.2f, 1.0f)
    val level3 = (liveLevel * 1.6f + ambient1 * 0.25f).coerceIn(0.15f, 1.0f)
    val level4 = (liveLevel * 1.1f + ambient2 * 0.35f).coerceIn(0.2f, 1.0f)

    Row(
        modifier = modifier
            .background(Color(0x99000000), RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(level1, level2, level3, level4).forEach { lvl ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height((18 * lvl).coerceAtLeast(3f).dp)
                    .background(if (liveLevel > 0.3f) CyberCyan else CyberCyan.copy(alpha = 0.8f), RoundedCornerShape(2.dp))
            )
        }
    }
}
