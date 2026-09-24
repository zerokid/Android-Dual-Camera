package com.example.data

import kotlinx.coroutines.flow.Flow

class VideoRepository(private val videoDao: VideoDao) {
    val allVideos: Flow<List<RecordedVideo>> = videoDao.getAllVideos()

    suspend fun insertVideo(video: RecordedVideo): Long = videoDao.insertVideo(video)

    suspend fun deleteVideo(video: RecordedVideo) = videoDao.deleteVideo(video)

    suspend fun deleteVideoById(id: Long) = videoDao.deleteVideoById(id)

    suspend fun getVideoById(id: Long): RecordedVideo? = videoDao.getVideoById(id)
}
