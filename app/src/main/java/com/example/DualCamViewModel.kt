package com.example

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.camera.DualCameraManager
import com.example.camera.HardwareDualCameraInfo
import com.example.camera.LensFacing
import com.example.camera.PipPosition
import com.example.camera.RecordingStatus
import com.example.camera.SplitLayoutMode
import com.example.camera.VideoFilter
import com.example.data.AppDatabase
import com.example.data.RecordedVideo
import com.example.data.VideoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DualCamUiState(
    val splitMode: SplitLayoutMode = SplitLayoutMode.VERTICAL_SPLIT,
    val primaryLens: LensFacing = LensFacing.BACK,
    val secondaryLens: LensFacing = LensFacing.FRONT,
    val isHardwareConcurrent: Boolean = false,
    val hardwareInfo: HardwareDualCameraInfo = HardwareDualCameraInfo(),
    val recordingStatus: RecordingStatus = RecordingStatus.IDLE,
    val recordingDurationSec: Int = 0,
    val audioEnabled: Boolean = true,
    val audioLevel: Float = 0f,
    val torchEnabled: Boolean = false,
    val gridLinesEnabled: Boolean = false,
    val selectedFilter: VideoFilter = VideoFilter.NORMAL,
    val zoomRatio: Float = 1.0f,
    val splitRatio: Float = 0.5f,
    val pipPosition: PipPosition = PipPosition.BOTTOM_RIGHT,
    val showSpecsDialog: Boolean = false,
    val toastMessage: String? = null
)

class DualCamViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getInstance(application)
    private val repository = VideoRepository(database.videoDao())

    private val _uiState = MutableStateFlow(DualCamUiState())
    val uiState: StateFlow<DualCamUiState> = _uiState.asStateFlow()

    val recordedVideos: StateFlow<List<RecordedVideo>> = repository.allVideos
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    var cameraManager: DualCameraManager? = null

    fun setHardwareInfo(info: HardwareDualCameraInfo) {
        _uiState.update {
            it.copy(
                isHardwareConcurrent = info.isConcurrentSupported,
                hardwareInfo = info
            )
        }
    }

    fun setSplitMode(mode: SplitLayoutMode) {
        _uiState.update { it.copy(splitMode = mode) }
    }

    fun swapLenses(onSwapDone: () -> Unit = {}) {
        _uiState.update { current ->
            val newPrimary = if (current.primaryLens == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
            val newSecondary = if (current.secondaryLens == LensFacing.BACK) LensFacing.FRONT else LensFacing.BACK
            current.copy(
                primaryLens = newPrimary,
                secondaryLens = newSecondary,
                torchEnabled = if (newPrimary == LensFacing.FRONT) false else current.torchEnabled
            )
        }
        onSwapDone()
    }

    fun toggleAudio() {
        _uiState.update { it.copy(audioEnabled = !it.audioEnabled) }
    }

    fun toggleTorch() {
        val currentTorch = _uiState.value.torchEnabled
        val newTorch = !currentTorch
        _uiState.update { it.copy(torchEnabled = newTorch) }
        cameraManager?.toggleTorch(newTorch)
    }

    fun toggleGridLines() {
        _uiState.update { it.copy(gridLinesEnabled = !it.gridLinesEnabled) }
    }

    fun setFilter(filter: VideoFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun setZoom(ratio: Float) {
        _uiState.update { it.copy(zoomRatio = ratio) }
        cameraManager?.setZoom(ratio)
    }

    fun setSplitRatio(ratio: Float) {
        _uiState.update { it.copy(splitRatio = ratio.coerceIn(0.25f, 0.75f)) }
    }

    fun cyclePipPosition() {
        _uiState.update { current ->
            val nextPosition = when (current.pipPosition) {
                PipPosition.BOTTOM_RIGHT -> PipPosition.BOTTOM_LEFT
                PipPosition.BOTTOM_LEFT -> PipPosition.TOP_LEFT
                PipPosition.TOP_LEFT -> PipPosition.TOP_RIGHT
                PipPosition.TOP_RIGHT -> PipPosition.BOTTOM_RIGHT
            }
            current.copy(pipPosition = nextPosition)
        }
    }

    fun showSpecsDialog(show: Boolean) {
        _uiState.update { it.copy(showSpecsDialog = show) }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    fun startRecording() {
        val manager = cameraManager ?: return
        if (_uiState.value.recordingStatus != RecordingStatus.IDLE) return

        val state = _uiState.value

        _uiState.update {
            it.copy(
                recordingStatus = RecordingStatus.RECORDING,
                recordingDurationSec = 0,
                audioLevel = 0f
            )
        }

        manager.startRecording(
            splitMode = state.splitMode,
            primaryLens = state.primaryLens,
            secondaryLens = state.secondaryLens,
            pipPosition = state.pipPosition,
            audioEnabled = state.audioEnabled,
            onDurationUpdate = { durationSec ->
                _uiState.update { it.copy(recordingDurationSec = durationSec) }
            },
            onAudioLevelUpdate = { level ->
                _uiState.update { it.copy(audioLevel = level) }
            },
            onFinalize = { file, durationMs, errorMsg ->
                if (file != null && file.exists()) {
                    onRecordingCompleted(file, durationMs)
                } else {
                    _uiState.update {
                        it.copy(
                            recordingStatus = RecordingStatus.IDLE,
                            recordingDurationSec = 0,
                            audioLevel = 0f,
                            toastMessage = errorMsg ?: "Recording stopped"
                        )
                    }
                }
            }
        )
    }

    fun pauseRecording() {
        if (_uiState.value.recordingStatus == RecordingStatus.RECORDING) {
            cameraManager?.pauseRecording()
            _uiState.update { it.copy(recordingStatus = RecordingStatus.PAUSED) }
        }
    }

    fun resumeRecording() {
        if (_uiState.value.recordingStatus == RecordingStatus.PAUSED) {
            cameraManager?.resumeRecording()
            _uiState.update { it.copy(recordingStatus = RecordingStatus.RECORDING) }
        }
    }

    fun stopRecording() {
        if (_uiState.value.recordingStatus == RecordingStatus.RECORDING ||
            _uiState.value.recordingStatus == RecordingStatus.PAUSED
        ) {
            _uiState.update { it.copy(recordingStatus = RecordingStatus.FINALIZING) }
            cameraManager?.stopRecording()
        }
    }

    private fun onRecordingCompleted(file: File, durationMs: Long) {
        viewModelScope.launch {
            val state = _uiState.value
            val modeName = when (state.splitMode) {
                SplitLayoutMode.VERTICAL_SPLIT -> "Top-Down"
                SplitLayoutMode.HORIZONTAL_SPLIT -> "Side-by-Side"
                SplitLayoutMode.PIP -> "PiP"
                SplitLayoutMode.FOCUS_70_30 -> "70:30 Focus"
            }
            val timeFormatted = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            val title = "DuoCam $modeName ($timeFormatted)"

            val video = RecordedVideo(
                title = title,
                filePath = file.absolutePath,
                durationMs = durationMs.coerceAtLeast(1000L),
                fileSizeBytes = file.length(),
                createdAt = System.currentTimeMillis(),
                layoutMode = state.splitMode.name,
                primaryLens = state.primaryLens.name,
                audioEnabled = state.audioEnabled
            )
            repository.insertVideo(video)
            _uiState.update {
                it.copy(
                    recordingStatus = RecordingStatus.IDLE,
                    recordingDurationSec = 0,
                    audioLevel = 0f,
                    toastMessage = "Dual $modeName video saved with audio!"
                )
            }
        }
    }

    fun deleteVideo(video: RecordedVideo) {
        viewModelScope.launch {
            try {
                val file = File(video.filePath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                // Ignore file deletion error
            }
            repository.deleteVideo(video)
            _uiState.update { it.copy(toastMessage = "Video deleted") }
        }
    }
}
