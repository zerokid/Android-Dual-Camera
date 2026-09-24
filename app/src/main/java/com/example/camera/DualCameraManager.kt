package com.example.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ConcurrentCamera
import androidx.camera.core.Preview
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DualCameraManager(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner
) {
    private val tag = "DualCameraManager"

    private var cameraProvider: ProcessCameraProvider? = null
    private var activeCamera: Camera? = null
    private var concurrentCamera: ConcurrentCamera? = null

    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null

    var isHardwareConcurrentSupported: Boolean = false
        private set

    var hardwareInfo: HardwareDualCameraInfo = HardwareDualCameraInfo()
        private set

    private var primaryPreviewView: PreviewView? = null
    private var secondaryPreviewView: PreviewView? = null

    private var currentPrimaryLens: LensFacing = LensFacing.BACK
    private var isTorchOn: Boolean = false

    fun init(onReady: (HardwareDualCameraInfo) -> Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                inspectHardware()
                setupVideoCapture()
                onReady(hardwareInfo)
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize CameraProvider", e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun inspectHardware() {
        val provider = cameraProvider ?: return
        var concurrentPair = false
        try {
            val availableConcurrent = provider.availableConcurrentCameraInfos
            if (availableConcurrent.isNotEmpty()) {
                concurrentPair = true
            }
        } catch (e: Throwable) {
            Log.w(tag, "Concurrent camera check threw: ${e.message}")
        }

        var backId: String? = null
        var frontId: String? = null
        var totalCams = 0
        try {
            val cm = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
            if (cm != null) {
                val ids = cm.cameraIdList
                totalCams = ids.size
                for (id in ids) {
                    val chars = cm.getCameraCharacteristics(id)
                    val facing = chars.get(CameraCharacteristics.LENS_FACING)
                    if (facing == CameraCharacteristics.LENS_FACING_BACK && backId == null) {
                        backId = id
                    } else if (facing == CameraCharacteristics.LENS_FACING_FRONT && frontId == null) {
                        frontId = id
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Error inspecting CameraManager", e)
        }

        isHardwareConcurrentSupported = concurrentPair
        hardwareInfo = HardwareDualCameraInfo(
            isConcurrentSupported = concurrentPair,
            backCameraId = backId,
            frontCameraId = frontId,
            concurrentPairFound = concurrentPair,
            cameraCount = totalCams,
            hardwareLevel = if (concurrentPair) "Dual-Hardware ISP Supported" else "Single ISP (Director Mode Active)"
        )
    }

    private fun setupVideoCapture() {
        val qualitySelector = QualitySelector.from(
            Quality.FHD,
            FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)
        )
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        videoCapture = VideoCapture.withOutput(recorder)
    }

    fun bindViewfinders(
        primaryView: PreviewView,
        secondaryView: PreviewView?,
        primaryLens: LensFacing,
        onBound: (Boolean) -> Unit
    ) {
        val provider = cameraProvider ?: return
        this.primaryPreviewView = primaryView
        this.secondaryPreviewView = secondaryView
        this.currentPrimaryLens = primaryLens

        try {
            provider.unbindAll()
            concurrentCamera = null
            activeCamera = null

            val primarySelector = if (primaryLens == LensFacing.BACK) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            val secondarySelector = if (primaryLens == LensFacing.BACK) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }

            // Attempt hardware concurrent binding if supported and secondary preview view is provided
            if (isHardwareConcurrentSupported && secondaryView != null) {
                try {
                    val primaryPreview = Preview.Builder().build().also {
                        it.setSurfaceProvider(primaryView.surfaceProvider)
                    }
                    val secondaryPreview = Preview.Builder().build().also {
                        it.setSurfaceProvider(secondaryView.surfaceProvider)
                    }

                    val groupBuilder = UseCaseGroup.Builder().addUseCase(primaryPreview)
                    videoCapture?.let { groupBuilder.addUseCase(it) }

                    val primaryConfig = ConcurrentCamera.SingleCameraConfig(
                        primarySelector,
                        groupBuilder.build(),
                        lifecycleOwner
                    )
                    val secondaryConfig = ConcurrentCamera.SingleCameraConfig(
                        secondarySelector,
                        UseCaseGroup.Builder().addUseCase(secondaryPreview).build(),
                        lifecycleOwner
                    )

                    concurrentCamera = provider.bindToLifecycle(listOf(primaryConfig, secondaryConfig))
                    Log.i(tag, "Successfully bound hardware concurrent cameras!")
                    onBound(true)
                    return
                } catch (e: Exception) {
                    Log.w(tag, "Hardware concurrent binding failed, falling back to active lens: ${e.message}")
                    provider.unbindAll()
                }
            }

            // Fallback: Bind primary active camera with preview and recording
            val primaryPreview = Preview.Builder().build().also {
                it.setSurfaceProvider(primaryView.surfaceProvider)
            }

            val useCases = mutableListOf<androidx.camera.core.UseCase>(primaryPreview)
            videoCapture?.let { useCases.add(it) }

            activeCamera = provider.bindToLifecycle(
                lifecycleOwner,
                primarySelector,
                *useCases.toTypedArray()
            )

            // Restore torch state if on back lens
            if (primaryLens == LensFacing.BACK && isTorchOn) {
                activeCamera?.cameraControl?.enableTorch(true)
            }

            Log.i(tag, "Bound active camera lens: $primaryLens")
            onBound(false)
        } catch (e: Exception) {
            Log.e(tag, "Error binding camera viewfinders", e)
            onBound(false)
        }
    }

    fun startRecording(
        audioEnabled: Boolean,
        onDurationUpdate: (Int) -> Unit,
        onFinalize: (File?, Long, String?) -> Unit
    ) {
        val capture = videoCapture ?: run {
            onFinalize(null, 0L, "Video recorder not ready")
            return
        }

        val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val videoFile = File(videosDir, "DUOCAM_$timeStamp.mp4")

        val outputOptions = FileOutputOptions.Builder(videoFile).build()
        val pendingRecording = capture.output.prepareRecording(context, outputOptions)

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (audioEnabled && hasAudioPermission) {
            try {
                pendingRecording.withAudioEnabled()
            } catch (e: SecurityException) {
                Log.w(tag, "Audio record permission error: ${e.message}")
            }
        }

        var durationSec = 0
        currentRecording = pendingRecording.start(ContextCompat.getMainExecutor(context)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    Log.i(tag, "Recording started: ${videoFile.name}")
                }
                is VideoRecordEvent.Status -> {
                    val nanos = event.recordingStats.recordedDurationNanos
                    durationSec = (nanos / 1_000_000_000L).toInt()
                    onDurationUpdate(durationSec)
                }
                is VideoRecordEvent.Pause -> {
                    Log.i(tag, "Recording paused")
                }
                is VideoRecordEvent.Resume -> {
                    Log.i(tag, "Recording resumed")
                }
                is VideoRecordEvent.Finalize -> {
                    if (event.hasError()) {
                        Log.e(tag, "Recording finalize error: ${event.error}")
                        currentRecording = null
                        onFinalize(null, 0L, "Recording error: ${event.error}")
                    } else {
                        Log.i(tag, "Recording finalize success: ${videoFile.length()} bytes")
                        currentRecording = null
                        val durationMs = durationSec * 1000L
                        onFinalize(videoFile, durationMs, null)
                    }
                }
            }
        }
    }

    fun pauseRecording() {
        currentRecording?.pause()
    }

    fun resumeRecording() {
        currentRecording?.resume()
    }

    fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null
    }

    fun toggleTorch(enabled: Boolean) {
        isTorchOn = enabled
        if (currentPrimaryLens == LensFacing.BACK) {
            activeCamera?.cameraControl?.enableTorch(enabled)
        }
    }

    fun setZoom(ratio: Float) {
        activeCamera?.cameraControl?.setZoomRatio(ratio)
    }

    fun getZoomRange(): Pair<Float, Float> {
        val zoomState = activeCamera?.cameraInfo?.zoomState?.value
        val min = zoomState?.minZoomRatio ?: 1.0f
        val max = (zoomState?.maxZoomRatio ?: 5.0f).coerceAtMost(8.0f)
        return Pair(min, max)
    }
}
