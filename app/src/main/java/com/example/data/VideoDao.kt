package com.example.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoDao {
    @Query("SELECT * FROM recorded_videos ORDER BY createdAt DESC")
    fun getAllVideos(): Flow<List<RecordedVideo>>

    @Query("SELECT * FROM recorded_videos WHERE id = :id")
    suspend fun getVideoById(id: Long): RecordedVideo?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVideo(video: RecordedVideo): Long

    @Delete
    suspend fun deleteVideo(video: RecordedVideo)

    @Query("DELETE FROM recorded_videos WHERE id = :id")
    suspend fun deleteVideoById(id: Long)
}
