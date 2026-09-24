package com.example.camera

enum class SplitLayoutMode(val title: String, val description: String) {
    VERTICAL_SPLIT("Split Vertical", "Top / Bottom 50:50 View"),
    HORIZONTAL_SPLIT("Split Horizontal", "Left / Right 50:50 View"),
    PIP("Picture-in-Picture", "Floating movable Reaction Cam"),
    FOCUS_70_30("Director 70:30", "Main subject 70%, Reaction 30%")
}

enum class LensFacing {
    BACK,
    FRONT
}

enum class RecordingStatus {
    IDLE,
    RECORDING,
    PAUSED,
    FINALIZING
}

enum class PipPosition {
    TOP_RIGHT,
    TOP_LEFT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT
}

enum class VideoFilter(val displayName: String) {
    NORMAL("Normal"),
    VIVID("Vivid"),
    CINEMATIC("Cinematic"),
    NOIR("B&W Noir"),
    CYBER("Cyber Cyan")
}

data class HardwareDualCameraInfo(
    val isConcurrentSupported: Boolean = false,
    val backCameraId: String? = null,
    val frontCameraId: String? = null,
    val concurrentPairFound: Boolean = false,
    val cameraCount: Int = 0,
    val hardwareLevel: String = "Normal"
)
