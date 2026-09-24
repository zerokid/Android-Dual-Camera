package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recorded_videos")
data class RecordedVideo(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val filePath: String,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val layoutMode: String, // "SPLIT_VERTICAL", "SPLIT_HORIZONTAL", "PIP", "FOCUS_70_30"
    val resolution: String = "1080p",
    val primaryLens: String = "BACK",
    val audioEnabled: Boolean = true
)
