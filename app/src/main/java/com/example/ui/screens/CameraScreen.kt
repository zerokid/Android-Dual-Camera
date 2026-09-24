package com.example.ui.screens

import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.DualCamUiState
import com.example.DualCamViewModel
import com.example.camera.DualCameraManager
import com.example.camera.LensFacing
import com.example.camera.PipPosition
import com.example.camera.RecordingStatus
import com.example.camera.SplitLayoutMode
import com.example.ui.components.CameraPreviewSurface
import com.example.ui.components.FilterColorOverlay
import com.example.ui.components.GridLinesOverlay
import com.example.ui.components.LayoutModeBar
import com.example.ui.components.LensSlotBadge
import com.example.ui.components.RecordingTimerBadge
import com.example.ui.components.ShutterButton
import com.example.ui.components.SimulatedDirectorFeed
import com.example.ui.components.SpecsDialog
import com.example.ui.components.TopControlBar
import com.example.ui.components.ZoomHudBadge
import com.example.ui.components.ZoomQuickBar
import com.example.ui.theme.AccentAmber
import com.example.ui.theme.BorderGlass
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.RecordRed

@Composable
fun CameraScreen(
    viewModel: DualCamViewModel,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recordedVideos by viewModel.recordedVideos.collectAsStateWithLifecycle()

    val primaryPreviewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }
    val secondaryPreviewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Initialize CameraManager once with lifecycleOwner
    LaunchedEffect(lifecycleOwner) {
        val manager = DualCameraManager(context, lifecycleOwner)
        viewModel.cameraManager = manager
        manager.init { info ->
            viewModel.setHardwareInfo(info)
            manager.bindViewfinders(
                primaryView = primaryPreviewView,
                secondaryView = if (info.isConcurrentSupported) secondaryPreviewView else null,
                primaryLens = uiState.primaryLens,
                onBound = { isConcurrent ->
                    // bound callback
                }
            )
        }
    }

    // Toast message handler
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Rebind when lens changes
    fun performSwap() {
        viewModel.swapLenses {
            val manager = viewModel.cameraManager ?: return@swapLenses
            val currentPrimary = viewModel.uiState.value.primaryLens
            manager.bindViewfinders(
                primaryView = primaryPreviewView,
                secondaryView = if (uiState.isHardwareConcurrent) secondaryPreviewView else null,
                primaryLens = currentPrimary,
                onBound = {}
            )
        }
    }

    // Rebind viewfinders seamlessly whenever layout split mode changes
    LaunchedEffect(uiState.splitMode) {
        val manager = viewModel.cameraManager ?: return@LaunchedEffect
        // Short frame delay to allow Compose layout hierarchy to complete attachment
        kotlinx.coroutines.delay(60)
        manager.bindViewfinders(
            primaryView = primaryPreviewView,
            secondaryView = if (uiState.isHardwareConcurrent) secondaryPreviewView else null,
            primaryLens = uiState.primaryLens,
            onBound = {}
        )
    }

    LaunchedEffect(uiState.zoomRatio, uiState.isZooming) {
        if (uiState.isZooming) {
            kotlinx.coroutines.delay(1800)
            viewModel.dismissZoomHud()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .testTag("camera_screen")
    ) {
        // --- Split Screen Viewfinders Area with Pinch-to-Zoom Gesture ---
        ViewfinderContainer(
            uiState = uiState,
            primaryPreviewView = primaryPreviewView,
            secondaryPreviewView = secondaryPreviewView,
            onSwap = { performSwap() },
            onCyclePip = { viewModel.cyclePipPosition() },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoomChange, _ ->
                        if (zoomChange != 1.0f) {
                            viewModel.applyZoomDelta(zoomChange)
                        }
                    }
                }
        )

        // --- Grid Lines Overlay ---
        if (uiState.gridLinesEnabled) {
            GridLinesOverlay(modifier = Modifier.fillMaxSize())
        }

        // --- Filter Color Tint ---
        FilterColorOverlay(filter = uiState.selectedFilter, modifier = Modifier.fillMaxSize())

        // --- Floating Zoom Level HUD (Pinch or Button Feedback) ---
        ZoomHudBadge(
            zoomRatio = uiState.zoomRatio,
            visible = uiState.isZooming,
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 40.dp)
        )

        // --- Top Bar Controls ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(top = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            TopControlBar(
                isHardwareConcurrent = uiState.isHardwareConcurrent,
                torchEnabled = uiState.torchEnabled,
                audioEnabled = uiState.audioEnabled,
                gridEnabled = uiState.gridLinesEnabled,
                videoCount = recordedVideos.size,
                onToggleTorch = { viewModel.toggleTorch() },
                onToggleAudio = { viewModel.toggleAudio() },
                onToggleGrid = { viewModel.toggleGridLines() },
                onOpenGallery = onNavigateToGallery,
                onOpenSpecs = { viewModel.showSpecsDialog(true) }
            )

            // Recording Timer Badge (Centered below top bar when active)
            RecordingTimerBadge(
                recordingStatus = uiState.recordingStatus,
                durationSec = uiState.recordingDurationSec,
                audioLevel = uiState.audioLevel,
                audioEnabled = uiState.audioEnabled,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        // --- Bottom Controls (Shutter, Pause, Modes, Zoom) ---
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Zoom Selector with +/- Step and Dynamic Presets
            ZoomQuickBar(
                currentZoom = uiState.zoomRatio,
                minZoom = uiState.minZoomRatio,
                maxZoom = uiState.maxZoomRatio,
                onSelectZoom = { zoom -> viewModel.setZoom(zoom) },
                onStepZoom = { delta -> viewModel.stepZoom(delta) },
                modifier = Modifier.padding(bottom = 10.dp)
            )

            // Layout Mode Switcher (Hide during recording to keep clean)
            AnimatedVisibility(
                visible = uiState.recordingStatus == RecordingStatus.IDLE,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                LayoutModeBar(
                    currentMode = uiState.splitMode,
                    onSelectMode = { mode -> viewModel.setSplitMode(mode) },
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Shutter & Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Action: Quick Swap Lenses
                Surface(
                    shape = CircleShape,
                    color = Color(0x990D131D),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .clickable { performSwap() }
                        .testTag("swap_lenses_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FlipCameraAndroid,
                            contentDescription = "Swap Cameras",
                            tint = CyberCyan,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Center Action: Large Shutter Button
                ShutterButton(
                    recordingStatus = uiState.recordingStatus,
                    onStartRecording = { viewModel.startRecording() },
                    onStopRecording = { viewModel.stopRecording() }
                )

                // Right Action: Pause/Resume (when recording) or PiP cycle
                if (uiState.recordingStatus == RecordingStatus.RECORDING || uiState.recordingStatus == RecordingStatus.PAUSED) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0x990D131D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .clickable {
                                if (uiState.recordingStatus == RecordingStatus.RECORDING) {
                                    viewModel.pauseRecording()
                                } else {
                                    viewModel.resumeRecording()
                                }
                            }
                            .testTag("pause_resume_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (uiState.recordingStatus == RecordingStatus.RECORDING) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (uiState.recordingStatus == RecordingStatus.RECORDING) "Pause" else "Resume",
                                tint = if (uiState.recordingStatus == RecordingStatus.RECORDING) AccentAmber else CyberCyan,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                } else {
                    // Quick layout mode info or spacer
                    Surface(
                        shape = CircleShape,
                        color = Color(0x990D131D),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .clickable {
                                val modes = SplitLayoutMode.values()
                                val nextIndex = (uiState.splitMode.ordinal + 1) % modes.size
                                viewModel.setSplitMode(modes[nextIndex])
                            }
                            .testTag("cycle_mode_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Cameraswitch,
                                contentDescription = "Cycle Layout",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }

        // --- Hardware Specs Dialog ---
        if (uiState.showSpecsDialog) {
            SpecsDialog(
                hardwareInfo = uiState.hardwareInfo,
                onDismiss = { viewModel.showSpecsDialog(false) }
            )
        }
    }
}

@Composable
fun ViewfinderContainer(
    uiState: DualCamUiState,
    primaryPreviewView: PreviewView,
    secondaryPreviewView: PreviewView,
    onSwap: () -> Unit,
    onCyclePip: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (uiState.splitMode) {
        SplitLayoutMode.VERTICAL_SPLIT -> {
            VerticalSplitLayout(
                uiState = uiState,
                primaryPreviewView = primaryPreviewView,
                secondaryPreviewView = secondaryPreviewView,
                onSwap = onSwap,
                modifier = modifier
            )
        }
        SplitLayoutMode.HORIZONTAL_SPLIT -> {
            HorizontalSplitLayout(
                uiState = uiState,
                primaryPreviewView = primaryPreviewView,
                secondaryPreviewView = secondaryPreviewView,
                onSwap = onSwap,
                modifier = modifier
            )
        }
        SplitLayoutMode.PIP -> {
            PipLayout(
                uiState = uiState,
                primaryPreviewView = primaryPreviewView,
                secondaryPreviewView = secondaryPreviewView,
                onSwap = onSwap,
                onCyclePip = onCyclePip,
                modifier = modifier
            )
        }
        SplitLayoutMode.FOCUS_70_30 -> {
            Focus7030Layout(
                uiState = uiState,
                primaryPreviewView = primaryPreviewView,
                secondaryPreviewView = secondaryPreviewView,
                onSwap = onSwap,
                modifier = modifier
            )
        }
    }
}

@Composable
fun VerticalSplitLayout(
    uiState: DualCamUiState,
    primaryPreviewView: PreviewView,
    secondaryPreviewView: PreviewView,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val digitalZoomFallback = if (uiState.maxZoomRatio > 1.05f) 1.0f else uiState.zoomRatio

    Column(modifier = modifier.testTag("vertical_split_layout")) {
        // Top slot (Primary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            CameraPreviewSurface(
                previewView = primaryPreviewView,
                zoomScale = digitalZoomFallback,
                modifier = Modifier.fillMaxSize()
            )
            LensSlotBadge(
                lens = uiState.primaryLens,
                isPrimary = true,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }

        // Sleek Split Divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(CyberCyan)
        )

        // Bottom slot (Secondary)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (uiState.isHardwareConcurrent) {
                CameraPreviewSurface(
                    previewView = secondaryPreviewView,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SimulatedDirectorFeed(
                    lensFacing = uiState.secondaryLens,
                    onTapToSwap = onSwap,
                    modifier = Modifier.fillMaxSize()
                )
            }
            LensSlotBadge(
                lens = uiState.secondaryLens,
                isPrimary = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun HorizontalSplitLayout(
    uiState: DualCamUiState,
    primaryPreviewView: PreviewView,
    secondaryPreviewView: PreviewView,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val digitalZoomFallback = if (uiState.maxZoomRatio > 1.05f) 1.0f else uiState.zoomRatio

    Row(modifier = modifier.testTag("horizontal_split_layout")) {
        // Left Slot
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            CameraPreviewSurface(
                previewView = primaryPreviewView,
                zoomScale = digitalZoomFallback,
                modifier = Modifier.fillMaxSize()
            )
            LensSlotBadge(
                lens = uiState.primaryLens,
                isPrimary = true,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }

        // Center Divider
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(CyberCyan)
        )

        // Right Slot
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .weight(1f)
        ) {
            if (uiState.isHardwareConcurrent) {
                CameraPreviewSurface(
                    previewView = secondaryPreviewView,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SimulatedDirectorFeed(
                    lensFacing = uiState.secondaryLens,
                    onTapToSwap = onSwap,
                    modifier = Modifier.fillMaxSize()
                )
            }
            LensSlotBadge(
                lens = uiState.secondaryLens,
                isPrimary = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}

@Composable
fun PipLayout(
    uiState: DualCamUiState,
    primaryPreviewView: PreviewView,
    secondaryPreviewView: PreviewView,
    onSwap: () -> Unit,
    onCyclePip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val digitalZoomFallback = if (uiState.maxZoomRatio > 1.05f) 1.0f else uiState.zoomRatio

    Box(modifier = modifier.testTag("pip_layout")) {
        // Fullscreen main camera
        CameraPreviewSurface(
            previewView = primaryPreviewView,
            zoomScale = digitalZoomFallback,
            modifier = Modifier.fillMaxSize()
        )
        LensSlotBadge(
            lens = uiState.primaryLens,
            isPrimary = true,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 120.dp)
        )

        // Floating PiP Window
        val pipAlignment = when (uiState.pipPosition) {
            PipPosition.TOP_RIGHT -> Alignment.TopEnd
            PipPosition.TOP_LEFT -> Alignment.TopStart
            PipPosition.BOTTOM_RIGHT -> Alignment.BottomEnd
            PipPosition.BOTTOM_LEFT -> Alignment.BottomStart
        }

        Box(
            modifier = Modifier
                .align(pipAlignment)
                .padding(
                    top = if (pipAlignment == Alignment.TopEnd || pipAlignment == Alignment.TopStart) 110.dp else 0.dp,
                    bottom = if (pipAlignment == Alignment.BottomEnd || pipAlignment == Alignment.BottomStart) 150.dp else 0.dp,
                    start = 16.dp,
                    end = 16.dp
                )
                .size(width = 120.dp, height = 170.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(2.dp, CyberCyan, RoundedCornerShape(16.dp))
                .clickable { onCyclePip() }
                .testTag("pip_window")
        ) {
            if (uiState.isHardwareConcurrent) {
                CameraPreviewSurface(
                    previewView = secondaryPreviewView,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SimulatedDirectorFeed(
                    lensFacing = uiState.secondaryLens,
                    onTapToSwap = onSwap,
                    modifier = Modifier.fillMaxSize()
                )
            }
            LensSlotBadge(
                lens = uiState.secondaryLens,
                isPrimary = false,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }
    }
}

@Composable
fun Focus7030Layout(
    uiState: DualCamUiState,
    primaryPreviewView: PreviewView,
    secondaryPreviewView: PreviewView,
    onSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val digitalZoomFallback = if (uiState.maxZoomRatio > 1.05f) 1.0f else uiState.zoomRatio

    Column(modifier = modifier.testTag("focus_70_30_layout")) {
        // Main focus slot (70% height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.7f)
        ) {
            CameraPreviewSurface(
                previewView = primaryPreviewView,
                zoomScale = digitalZoomFallback,
                modifier = Modifier.fillMaxSize()
            )
            LensSlotBadge(
                lens = uiState.primaryLens,
                isPrimary = true,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }

        // Accent divider
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(CyberCyan)
        )

        // Reaction slot (30% height)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.3f)
        ) {
            if (uiState.isHardwareConcurrent) {
                CameraPreviewSurface(
                    previewView = secondaryPreviewView,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                SimulatedDirectorFeed(
                    lensFacing = uiState.secondaryLens,
                    onTapToSwap = onSwap,
                    modifier = Modifier.fillMaxSize()
                )
            }
            LensSlotBadge(
                lens = uiState.secondaryLens,
                isPrimary = false,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            )
        }
    }
}
