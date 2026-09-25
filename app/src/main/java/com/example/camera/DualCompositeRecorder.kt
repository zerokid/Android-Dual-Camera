package com.example.camera

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * High-performance, jitter-free composite MP4 video encoder.
 * Combines BOTH front and back camera feeds (PIP, Side-by-Side, Top-Down, or 70:30)
 * along with synchronized AAC audio from the microphone at a rock-solid 30.00 FPS.
 */
class DualCompositeRecorder(
    private val context: Context,
    private val width: Int = 720,
    private val height: Int = 1280
) {
    private val tag = "DualCompositeRecorder"

    private val isRecording = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var outputFile: File? = null
    private var mediaMuxer: MediaMuxer? = null
    private var videoTrackIndex = -1
    private var audioTrackIndex = -1
    private var muxerStarted = false
    private val muxerLock = Object()
    private var recordStartTimeMs = 0L

    // Pending buffers to preserve initial keyframes before MediaMuxer starts
    private class PendingBuffer(
        val isVideo: Boolean,
        val buffer: ByteBuffer,
        val info: MediaCodec.BufferInfo
    )
    private val pendingBuffers = mutableListOf<PendingBuffer>()

    // Main thread handler for fallback frame snapshots
    private val mainHandler = Handler(Looper.getMainLooper())

    // Threads
    private var videoThread: Thread? = null
    private var audioThread: Thread? = null

    // EGL / GLES references
    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE
    private var videoEncoder: MediaCodec? = null

    // Audio references
    private var audioRecord: AudioRecord? = null
    private var audioEncoder: MediaCodec? = null

    // Thread-safe non-blocking asynchronous snapshot state
    private val frameLock = Object()
    @Volatile private var latestPrimaryBitmap: Bitmap? = null
    @Volatile private var pendingRecyclePrimary: Bitmap? = null
    @Volatile private var isCapturingPrimary = false

    @Volatile private var latestSecondaryBitmap: Bitmap? = null
    @Volatile private var pendingRecycleSecondary: Bitmap? = null
    @Volatile private var isCapturingSecondary = false

    // Cached frames to ensure zero black/blank frames during capture jitter
    private var lastValidPrimary: Bitmap? = null
    private var lastValidSecondary: Bitmap? = null

    // Texture tracking for fast texSubImage2D updates (zero VRAM re-allocations)
    private var primaryTexAllocated = false
    private var primaryTexWidth = 0
    private var primaryTexHeight = 0

    private var secondaryTexAllocated = false
    private var secondaryTexWidth = 0
    private var secondaryTexHeight = 0

    // Constant colors for simulated feed
    private val colorBg = 0xFF0F172A.toInt()
    private val colorCyan = 0xFF00E5FF.toInt()
    private val colorCyanDim = 0x3300E5FF.toInt()

    // Callbacks
    var onDurationUpdate: ((Int) -> Unit)? = null
    var onAudioLevelUpdate: ((Float) -> Unit)? = null

    fun start(
        splitMode: SplitLayoutMode,
        primaryLens: LensFacing,
        secondaryLens: LensFacing,
        isHardwareConcurrent: Boolean,
        primaryPreviewView: PreviewView,
        secondaryPreviewView: PreviewView?,
        pipPosition: PipPosition,
        audioEnabled: Boolean,
        targetFps: Int = 30,
        onFinalize: (File?, Long, String?) -> Unit
    ) {
        if (isRecording.get()) return

        val fps = if (targetFps >= 60) 60 else 30
        val videosDir = File(context.filesDir, "videos").apply { mkdirs() }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val destFile = File(videosDir, "DUO_${splitMode.name}_${fps}FPS_$timeStamp.mp4")
        outputFile = destFile

        try {
            mediaMuxer = MediaMuxer(destFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        } catch (e: Exception) {
            Log.e(tag, "Failed to create MediaMuxer", e)
            onFinalize(null, 0L, "Could not initialize video file: ${e.message}")
            return
        }

        videoTrackIndex = -1
        audioTrackIndex = -1
        muxerStarted = false
        recordStartTimeMs = SystemClock.uptimeMillis()
        synchronized(muxerLock) {
            pendingBuffers.clear()
        }
        isRecording.set(true)
        isPaused.set(false)

        val hasAudioPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val recordAudio = audioEnabled && hasAudioPermission

        // Start Audio Thread if audio is enabled
        if (recordAudio) {
            audioThread = Thread {
                runAudioRecording()
            }.apply {
                name = "DualCam-AudioRecord"
                start()
            }
        }

        // Start Video Encoding Thread
        videoThread = Thread {
            runVideoEncoding(
                splitMode = splitMode,
                primaryLens = primaryLens,
                secondaryLens = secondaryLens,
                isHardwareConcurrent = isHardwareConcurrent,
                primaryPreviewView = primaryPreviewView,
                secondaryPreviewView = secondaryPreviewView,
                pipPosition = pipPosition,
                recordAudio = recordAudio,
                targetFps = fps,
                onComplete = { durationMs, error ->
                    onFinalize(destFile, durationMs, error)
                }
            )
        }.apply {
            name = "DualCam-VideoEncoder"
            start()
        }
    }

    fun pause() {
        isPaused.set(true)
    }

    fun resume() {
        isPaused.set(false)
    }

    fun stop() {
        isRecording.set(false)
    }

    private fun runAudioRecording() {
        val sampleRate = 44100
        val channelConfig = AudioFormat.CHANNEL_IN_MONO
        val audioFormatEncoding = AudioFormat.ENCODING_PCM_16BIT
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormatEncoding)
            .coerceAtLeast(4096)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormatEncoding,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord failed to initialize")
                return
            }

            val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, 1).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, 128000)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bufferSize)
            }

            audioEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            audioRecord?.startRecording()

            val pcmBuffer = ByteArray(bufferSize)
            val bufferInfo = MediaCodec.BufferInfo()
            var totalAudioFrames = 0L

            while (isRecording.get()) {
                if (isPaused.get()) {
                    SystemClock.sleep(20)
                    continue
                }

                val readBytes = audioRecord?.read(pcmBuffer, 0, pcmBuffer.size) ?: 0
                if (readBytes > 0) {
                    // Compute sound level for visualizer
                    var sum = 0.0
                    for (i in 0 until readBytes step 2) {
                        val sample = (pcmBuffer[i].toInt() and 0xFF) or (pcmBuffer[i + 1].toInt() shl 8)
                        sum += sample * sample
                    }
                    val rms = sqrt(sum / (readBytes / 2.0))
                    val db = if (rms > 0) (20 * log10(rms)).toFloat().coerceIn(0f, 90f) / 90f else 0f
                    onAudioLevelUpdate?.invoke(db)

                    // Feed PCM into audioEncoder with exact sample-based timestamp
                    val inputIndex = audioEncoder?.dequeueInputBuffer(10000L) ?: -1
                    if (inputIndex >= 0) {
                        val inputBuf = audioEncoder?.getInputBuffer(inputIndex)
                        inputBuf?.clear()
                        inputBuf?.put(pcmBuffer, 0, readBytes)

                        val samples = readBytes / 2 // 16-bit mono = 2 bytes per sample
                        val presentationTimeUs = (totalAudioFrames * 1_000_000L) / sampleRate
                        totalAudioFrames += samples

                        audioEncoder?.queueInputBuffer(inputIndex, 0, readBytes, presentationTimeUs, 0)
                    }
                }

                // Drain audioEncoder outputs
                while (true) {
                    val outIndex = audioEncoder?.dequeueOutputBuffer(bufferInfo, 0L) ?: -1
                    if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        break
                    } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        synchronized(muxerLock) {
                            val newFormat = audioEncoder?.outputFormat ?: return
                            audioTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                            Log.i(tag, "Audio track added with index $audioTrackIndex")
                            checkStartMuxerLocked(recordAudio = true)
                        }
                    } else if (outIndex >= 0) {
                        val encodedData = audioEncoder?.getOutputBuffer(outIndex)
                        if (encodedData != null) {
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                                bufferInfo.size = 0
                            }
                            if (bufferInfo.size > 0) {
                                synchronized(muxerLock) {
                                    if (muxerStarted && audioTrackIndex >= 0) {
                                        encodedData.position(bufferInfo.offset)
                                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                        mediaMuxer?.writeSampleData(audioTrackIndex, encodedData, bufferInfo)
                                    } else {
                                        // Buffer audio until muxer starts
                                        val copy = ByteBuffer.allocateDirect(bufferInfo.size)
                                        encodedData.position(bufferInfo.offset)
                                        encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                        copy.put(encodedData)
                                        copy.flip()

                                        val infoCopy = MediaCodec.BufferInfo().apply {
                                            set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                                        }
                                        pendingBuffers.add(PendingBuffer(isVideo = false, buffer = copy, info = infoCopy))
                                    }
                                }
                            }
                        }
                        audioEncoder?.releaseOutputBuffer(outIndex, false)
                    }
                }
            }

            // Flush audio encoder
            val inIndex = audioEncoder?.dequeueInputBuffer(10000L) ?: -1
            if (inIndex >= 0) {
                audioEncoder?.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            }
        } catch (e: Exception) {
            Log.e(tag, "Audio recording exception", e)
        } finally {
            try {
                audioRecord?.stop()
                audioRecord?.release()
                audioRecord = null
                audioEncoder?.stop()
                audioEncoder?.release()
                audioEncoder = null
            } catch (ignored: Exception) {}
        }
    }

    private fun capturePreviewFrame(previewView: PreviewView?, isPrimary: Boolean): Bitmap? {
        if (previewView == null) return null

        if (isPrimary) {
            if (!isCapturingPrimary && isRecording.get()) {
                isCapturingPrimary = true
                mainHandler.post {
                    try {
                        if (!isRecording.get()) return@post
                        val bmp = previewView.bitmap ?: return@post
                        val toRecycle: Bitmap?
                        synchronized(frameLock) {
                            toRecycle = pendingRecyclePrimary
                            pendingRecyclePrimary = latestPrimaryBitmap
                            latestPrimaryBitmap = bmp
                        }
                        toRecycle?.recycle()
                    } catch (e: Throwable) {
                        Log.w(tag, "Async primary previewView.bitmap error: ${e.message}")
                    } finally {
                        isCapturingPrimary = false
                    }
                }
            }
            return synchronized(frameLock) { latestPrimaryBitmap }
        } else {
            if (!isCapturingSecondary && isRecording.get()) {
                isCapturingSecondary = true
                mainHandler.post {
                    try {
                        if (!isRecording.get()) return@post
                        val bmp = previewView.bitmap ?: return@post
                        val toRecycle: Bitmap?
                        synchronized(frameLock) {
                            toRecycle = pendingRecycleSecondary
                            pendingRecycleSecondary = latestSecondaryBitmap
                            latestSecondaryBitmap = bmp
                        }
                        toRecycle?.recycle()
                    } catch (e: Throwable) {
                        Log.w(tag, "Async secondary previewView.bitmap error: ${e.message}")
                    } finally {
                        isCapturingSecondary = false
                    }
                }
            }
            return synchronized(frameLock) { latestSecondaryBitmap }
        }
    }

    private fun runVideoEncoding(
        splitMode: SplitLayoutMode,
        primaryLens: LensFacing,
        secondaryLens: LensFacing,
        isHardwareConcurrent: Boolean,
        primaryPreviewView: PreviewView,
        secondaryPreviewView: PreviewView?,
        pipPosition: PipPosition,
        recordAudio: Boolean,
        targetFps: Int = 30,
        onComplete: (Long, String?) -> Unit
    ) {
        var durationMs = 0L
        try {
            val fps = if (targetFps >= 60) 60 else 30
            val bitrate = if (fps >= 60) 8_000_000 else 4_000_000
            val videoFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR)
            }

            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            videoEncoder = encoder
            encoder.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val inputSurface = encoder.createInputSurface()
            encoder.start()

            initEgl(inputSurface)

            val bufferInfo = MediaCodec.BufferInfo()
            val frameIntervalNs = 1_000_000_000L / fps.toLong() // 16,666,666 ns for 60fps, 33,333,333 ns for 30fps
            var frameCount = 0L
            val loopStartNs = System.nanoTime()

            // Reset texture allocation trackers
            primaryTexAllocated = false
            secondaryTexAllocated = false

            // Fallback placeholder bitmap
            val placeholderPrimaryBitmap = Bitmap.createBitmap(width, height / 2, Bitmap.Config.ARGB_8888).apply {
                val c = Canvas(this)
                c.drawColor(colorBg)
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = colorCyan
                    textSize = 28f
                    textAlign = Paint.Align.CENTER
                }
                c.drawText("PRIMARY CAMERA", width * 0.5f, (height / 4).toFloat(), p)
            }

            // Pre-allocate secondary simulated bitmap canvas for single-camera devices
            val secondaryBitmap = Bitmap.createBitmap(width, height / 2, Bitmap.Config.ARGB_8888)
            val secondaryCanvas = Canvas(secondaryBitmap)
            val secPaint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Initial warm-up snapshot so frame 0 has a valid, properly oriented image immediately
            val initLatch = CountDownLatch(1)
            mainHandler.post {
                try {
                    val pBmp = primaryPreviewView.bitmap
                    val sBmp = if (isHardwareConcurrent && secondaryPreviewView != null) secondaryPreviewView.bitmap else null
                    synchronized(frameLock) {
                        latestPrimaryBitmap = pBmp
                        latestSecondaryBitmap = sBmp
                    }
                } catch (e: Throwable) {
                    Log.w(tag, "Initial snapshot capture failed: ${e.message}")
                } finally {
                    initLatch.countDown()
                }
            }
            try {
                initLatch.await(500, TimeUnit.MILLISECONDS)
            } catch (ignored: InterruptedException) {}

            while (isRecording.get()) {
                if (isPaused.get()) {
                    SystemClock.sleep(30)
                    continue
                }

                // Strictly uniform presentation timestamps for jitter-free playback
                val presentationTimeNs = frameCount * frameIntervalNs

                // 1. Fetch live primary camera frame with non-blocking snapshot
                val primaryRaw = capturePreviewFrame(primaryPreviewView, isPrimary = true)
                if (primaryRaw != null && !primaryRaw.isRecycled) {
                    lastValidPrimary = primaryRaw
                }
                val primaryBitmap = if (primaryRaw != null && !primaryRaw.isRecycled) primaryRaw else (if (lastValidPrimary != null && !lastValidPrimary!!.isRecycled) lastValidPrimary else placeholderPrimaryBitmap)

                // 2. Fetch or render secondary camera frame
                val secBmp = if (isHardwareConcurrent && secondaryPreviewView != null) {
                    val secRaw = capturePreviewFrame(secondaryPreviewView, isPrimary = false)
                    if (secRaw != null && !secRaw.isRecycled) {
                        lastValidSecondary = secRaw
                        secRaw
                    } else if (lastValidSecondary != null && !lastValidSecondary!!.isRecycled) {
                        lastValidSecondary
                    } else {
                        renderSimulatedFrame(secondaryCanvas, secondaryBitmap, secPaint, secondaryLens, frameCount)
                        secondaryBitmap
                    }
                } else {
                    renderSimulatedFrame(secondaryCanvas, secondaryBitmap, secPaint, secondaryLens, frameCount)
                    secondaryBitmap
                }

                // 3. Render both frames into composite OpenGL surface with sub-image updates
                renderCompositeFrame(
                    primaryBitmap = primaryBitmap,
                    secondaryBitmap = secBmp,
                    splitMode = splitMode,
                    pipPosition = pipPosition
                )

                // 4. Submit exact frame timestamp to EGL
                EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, presentationTimeNs)
                EGL14.eglSwapBuffers(eglDisplay, eglSurface)

                frameCount++
                val durationSec = (presentationTimeNs / 1_000_000_000L).toInt()
                durationMs = durationSec * 1000L
                onDurationUpdate?.invoke(durationSec)

                // 5. Drain encoded video buffers and write to muxer
                drainVideoEncoder(bufferInfo, recordAudio)

                // Regulate frame rate based on absolute target clock to prevent drift
                val targetNextNs = loopStartNs + frameCount * frameIntervalNs
                val nowNs = System.nanoTime()
                val sleepNs = targetNextNs - nowNs
                if (sleepNs > 1_500_000L) {
                    SystemClock.sleep(sleepNs / 1_000_000L)
                }
            }

            // Finalize video stream
            try {
                encoder.signalEndOfInputStream()
                drainVideoEncoder(bufferInfo, recordAudio, isEndOfStream = true)
            } catch (e: Exception) {
                Log.w(tag, "Error signaling end of stream", e)
            }

            // Wait for audio thread to complete its flushing before stopping muxer
            try {
                audioThread?.join(1000)
            } catch (ignored: Exception) {}

            onComplete(durationMs, null)
        } catch (e: Exception) {
            Log.e(tag, "Video encoding error", e)
            onComplete(durationMs, "Recording error: ${e.message}")
        } finally {
            cleanup()
        }
    }

    private fun drainVideoEncoder(bufferInfo: MediaCodec.BufferInfo, recordAudio: Boolean, isEndOfStream: Boolean = false) {
        val encoder = videoEncoder ?: return
        while (true) {
            val outIndex = encoder.dequeueOutputBuffer(bufferInfo, if (isEndOfStream) 20000L else 0L)
            if (outIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            } else if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                synchronized(muxerLock) {
                    val newFormat = encoder.outputFormat
                    videoTrackIndex = mediaMuxer?.addTrack(newFormat) ?: -1
                    Log.i(tag, "Video track added with index $videoTrackIndex")
                    checkStartMuxerLocked(recordAudio)
                }
            } else if (outIndex >= 0) {
                val encodedData = encoder.getOutputBuffer(outIndex)
                if (encodedData != null) {
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                        bufferInfo.size = 0
                    }
                    if (bufferInfo.size > 0) {
                        synchronized(muxerLock) {
                            if (muxerStarted && videoTrackIndex >= 0) {
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                mediaMuxer?.writeSampleData(videoTrackIndex, encodedData, bufferInfo)
                            } else {
                                // Buffer initial keyframe and early video frames so they are NOT dropped!
                                val copy = ByteBuffer.allocateDirect(bufferInfo.size)
                                encodedData.position(bufferInfo.offset)
                                encodedData.limit(bufferInfo.offset + bufferInfo.size)
                                copy.put(encodedData)
                                copy.flip()

                                val infoCopy = MediaCodec.BufferInfo().apply {
                                    set(0, bufferInfo.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                                }
                                pendingBuffers.add(PendingBuffer(isVideo = true, buffer = copy, info = infoCopy))
                            }
                        }
                    }
                }
                encoder.releaseOutputBuffer(outIndex, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    break
                }
            }
        }
    }

    private fun checkStartMuxerLocked(recordAudio: Boolean) {
        if (muxerStarted) return
        val videoReady = videoTrackIndex >= 0
        val audioTimeout = (SystemClock.uptimeMillis() - recordStartTimeMs) > 1500L
        val audioReady = !recordAudio || audioTrackIndex >= 0 || audioTimeout

        if (videoReady && audioReady) {
            try {
                mediaMuxer?.start()
                muxerStarted = true
                Log.i(tag, "MediaMuxer started! videoTrack=$videoTrackIndex, audioTrack=$audioTrackIndex. Flushing ${pendingBuffers.size} pending buffers.")

                // Flush all pending samples in order
                for (item in pendingBuffers) {
                    val track = if (item.isVideo) videoTrackIndex else audioTrackIndex
                    if (track >= 0 && item.info.size > 0) {
                        mediaMuxer?.writeSampleData(track, item.buffer, item.info)
                    }
                }
                pendingBuffers.clear()
            } catch (e: Exception) {
                Log.e(tag, "Failed to start MediaMuxer", e)
            }
        }
    }

    // --- OpenGL Rendering Functions ---

    private var program = 0
    private var aPositionHandle = 0
    private var aTexCoordHandle = 0
    private var uTextureHandle = 0
    private var primaryTexId = 0
    private var secondaryTexId = 0

    private val vertexBuffer: FloatBuffer
    private val texCoordBuffer: FloatBuffer

    init {
        // Standard full-viewport quad
        val vertices = floatArrayOf(
            -1.0f, -1.0f,
             1.0f, -1.0f,
            -1.0f,  1.0f,
             1.0f,  1.0f
        )
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(vertices); position(0) }

        // Standard texture coordinates (inverted Y for OpenGL surface orientation)
        val texCoords = floatArrayOf(
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
        )
        texCoordBuffer = ByteBuffer.allocateDirect(texCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(texCoords); position(0) }
    }

    private fun initEgl(surface: Any) {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val version = IntArray(2)
        EGL14.eglInitialize(eglDisplay, version, 0, version, 1)

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGLExt.EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)
        val config = configs[0] ?: throw RuntimeException("Unable to find suitable EGLConfig")

        val contextAttribs = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )
        eglContext = EGL14.eglCreateContext(eglDisplay, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, config, surface, surfaceAttribs, 0)
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)

        initGlShaders()
    }

    private fun initGlShaders() {
        val vertexShaderCode = """
            attribute vec4 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                gl_Position = aPosition;
                vTexCoord = aTexCoord;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            varying vec2 vTexCoord;
            uniform sampler2D uTexture;
            void main() {
                gl_FragColor = texture2D(uTexture, vTexCoord);
            }
        """.trimIndent()

        val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vShader)
            GLES20.glAttachShader(it, fShader)
            GLES20.glLinkProgram(it)
        }

        aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
        aTexCoordHandle = GLES20.glGetAttribLocation(program, "aTexCoord")
        uTextureHandle = GLES20.glGetUniformLocation(program, "uTexture")

        val textures = IntArray(2)
        GLES20.glGenTextures(2, textures, 0)
        primaryTexId = textures[0]
        secondaryTexId = textures[1]

        GLES20.glPixelStorei(GLES20.GL_UNPACK_ALIGNMENT, 1)

        for (tex in textures) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }

    private fun renderCompositeFrame(
        primaryBitmap: Bitmap?,
        secondaryBitmap: Bitmap?,
        splitMode: SplitLayoutMode,
        pipPosition: PipPosition
    ) {
        GLES20.glClearColor(0.05f, 0.07f, 0.1f, 1.0f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glEnableVertexAttribArray(aPositionHandle)
        GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 8, vertexBuffer)

        GLES20.glEnableVertexAttribArray(aTexCoordHandle)
        GLES20.glVertexAttribPointer(aTexCoordHandle, 2, GLES20.GL_FLOAT, false, 8, texCoordBuffer)

        // Upload primary bitmap with sub-image update to avoid GPU re-allocation
        if (primaryBitmap != null && !primaryBitmap.isRecycled) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, primaryTexId)
            if (!primaryTexAllocated || primaryTexWidth != primaryBitmap.width || primaryTexHeight != primaryBitmap.height) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, primaryBitmap, 0)
                primaryTexAllocated = true
                primaryTexWidth = primaryBitmap.width
                primaryTexHeight = primaryBitmap.height
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, primaryBitmap)
            }
        }

        // Upload secondary bitmap with sub-image update
        if (secondaryBitmap != null && !secondaryBitmap.isRecycled) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, secondaryTexId)
            if (!secondaryTexAllocated || secondaryTexWidth != secondaryBitmap.width || secondaryTexHeight != secondaryBitmap.height) {
                GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, secondaryBitmap, 0)
                secondaryTexAllocated = true
                secondaryTexWidth = secondaryBitmap.width
                secondaryTexHeight = secondaryBitmap.height
            } else {
                GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, secondaryBitmap)
            }
        }

        when (splitMode) {
            SplitLayoutMode.VERTICAL_SPLIT -> {
                // Top half: Primary Camera
                GLES20.glViewport(0, height / 2 + 2, width, height / 2 - 2)
                GLES20.glUniform1i(uTextureHandle, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Bottom half: Secondary Camera
                GLES20.glViewport(0, 0, width, height / 2 - 2)
                GLES20.glUniform1i(uTextureHandle, 1)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
            SplitLayoutMode.HORIZONTAL_SPLIT -> {
                // Left half: Primary
                GLES20.glViewport(0, 0, width / 2 - 2, height)
                GLES20.glUniform1i(uTextureHandle, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Right half: Secondary
                GLES20.glViewport(width / 2 + 2, 0, width / 2 - 2, height)
                GLES20.glUniform1i(uTextureHandle, 1)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
            SplitLayoutMode.PIP -> {
                // Fullscreen background: Primary Camera
                GLES20.glViewport(0, 0, width, height)
                GLES20.glUniform1i(uTextureHandle, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Floating PIP window: Secondary Camera
                val pipW = (width * 0.35f).toInt()
                val pipH = (height * 0.25f).toInt()
                val margin = 32

                val (pipX, pipY) = when (pipPosition) {
                    PipPosition.BOTTOM_RIGHT -> Pair(width - pipW - margin, margin + 40)
                    PipPosition.BOTTOM_LEFT -> Pair(margin, margin + 40)
                    PipPosition.TOP_RIGHT -> Pair(width - pipW - margin, height - pipH - margin - 40)
                    PipPosition.TOP_LEFT -> Pair(margin, height - pipH - margin - 40)
                }

                GLES20.glViewport(pipX, pipY, pipW, pipH)
                GLES20.glUniform1i(uTextureHandle, 1)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
            SplitLayoutMode.FOCUS_70_30 -> {
                // Main subject: 70% height
                val topH = (height * 0.70f).toInt()
                val botH = height - topH - 4
                GLES20.glViewport(0, botH + 4, width, topH)
                GLES20.glUniform1i(uTextureHandle, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

                // Reaction: 30% height
                GLES20.glViewport(0, 0, width, botH)
                GLES20.glUniform1i(uTextureHandle, 1)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            }
        }
    }

    private fun renderSimulatedFrame(
        canvas: Canvas,
        bitmap: Bitmap,
        paint: Paint,
        lens: LensFacing,
        frameCount: Long
    ) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()

        // Background
        canvas.drawColor(colorBg)

        // Crosshair lines
        paint.color = colorCyanDim
        paint.strokeWidth = 2f
        canvas.drawLine(w * 0.5f, h * 0.2f, w * 0.5f, h * 0.8f, paint)
        canvas.drawLine(w * 0.2f, h * 0.5f, w * 0.8f, h * 0.5f, paint)

        // Pulsing reticle circle
        val pulse = (kotlin.math.sin(frameCount * 0.1) * 8.0).toFloat()
        paint.style = Paint.Style.STROKE
        paint.color = colorCyan
        paint.strokeWidth = 3f
        canvas.drawCircle(w * 0.5f, h * 0.5f, 60f + pulse, paint)

        // Corner brackets
        val bLen = 30f
        canvas.drawLine(20f, 20f, 20f + bLen, 20f, paint)
        canvas.drawLine(20f, 20f, 20f, 20f + bLen, paint)

        canvas.drawLine(w - 20f, 20f, w - 20f - bLen, 20f, paint)
        canvas.drawLine(w - 20f, 20f, w - 20f, 20f + bLen, paint)

        canvas.drawLine(20f, h - 20f, 20f + bLen, h - 20f, paint)
        canvas.drawLine(20f, h - 20f, 20f, h - 20f - bLen, paint)

        canvas.drawLine(w - 20f, h - 20f, w - 20f - bLen, h - 20f, paint)
        canvas.drawLine(w - 20f, h - 20f, w - 20f, h - 20f - bLen, paint)

        // Text Badge
        paint.style = Paint.Style.FILL
        paint.textSize = 28f
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        val label = if (lens == LensFacing.FRONT) "FRONT REACTION CAM" else "BACK PRO CAM"
        canvas.drawText(label, w * 0.5f, h * 0.5f + 110f, paint)

        paint.textSize = 20f
        paint.color = colorCyan
        canvas.drawText("SYNCED AUDIO & COMPOSITE RECORDER", w * 0.5f, h * 0.5f + 140f, paint)
    }

    private fun cleanup() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                if (eglSurface != EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroySurface(eglDisplay, eglSurface)
                }
                if (eglContext != EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglDestroyContext(eglDisplay, eglContext)
                }
                EGL14.eglTerminate(eglDisplay)
            }
            eglDisplay = EGL14.EGL_NO_DISPLAY
            eglContext = EGL14.EGL_NO_CONTEXT
            eglSurface = EGL14.EGL_NO_SURFACE

            videoEncoder?.stop()
            videoEncoder?.release()
            videoEncoder = null

            synchronized(muxerLock) {
                if (muxerStarted) {
                    try {
                        mediaMuxer?.stop()
                    } catch (e: Exception) {
                        Log.w(tag, "MediaMuxer stop warning: ${e.message}")
                    }
                    mediaMuxer?.release()
                    mediaMuxer = null
                    muxerStarted = false
                }
                pendingBuffers.clear()
            }

            synchronized(frameLock) {
                latestPrimaryBitmap?.recycle()
                latestPrimaryBitmap = null
                pendingRecyclePrimary?.recycle()
                pendingRecyclePrimary = null

                latestSecondaryBitmap?.recycle()
                latestSecondaryBitmap = null
                pendingRecycleSecondary?.recycle()
                pendingRecycleSecondary = null
            }
        } catch (e: Exception) {
            Log.e(tag, "Cleanup error", e)
        }
    }
}
