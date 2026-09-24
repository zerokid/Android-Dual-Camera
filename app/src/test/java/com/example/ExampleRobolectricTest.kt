package com.example

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.camera.LensFacing
import com.example.camera.SplitLayoutMode
import com.example.data.AppDatabase
import com.example.data.RecordedVideo
import com.example.data.VideoDao
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase
    private lateinit var videoDao: VideoDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        videoDao = database.videoDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read string from context matches DualCam`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("DualCam", appName)
    }

    @Test
    fun `insert and retrieve recorded dual video in Room`() = runBlocking {
        val video = RecordedVideo(
            title = "Duo Video Test",
            filePath = "/data/user/0/com.example/files/videos/test.mp4",
            durationMs = 12500L,
            fileSizeBytes = 10485760L,
            createdAt = System.currentTimeMillis(),
            layoutMode = SplitLayoutMode.VERTICAL_SPLIT.name,
            primaryLens = LensFacing.BACK.name,
            audioEnabled = true
        )

        val id = videoDao.insertVideo(video)
        assertTrue(id > 0)

        val list = videoDao.getAllVideos().first()
        assertEquals(1, list.size)
        assertEquals("Duo Video Test", list[0].title)
        assertEquals(SplitLayoutMode.VERTICAL_SPLIT.name, list[0].layoutMode)
    }

    @Test
    fun `viewModel initial state and mode toggles`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val viewModel = DualCamViewModel(app)

        assertEquals(SplitLayoutMode.VERTICAL_SPLIT, viewModel.uiState.value.splitMode)
        assertEquals(LensFacing.BACK, viewModel.uiState.value.primaryLens)
        assertEquals(LensFacing.FRONT, viewModel.uiState.value.secondaryLens)

        // Swap lenses
        viewModel.swapLenses()
        assertEquals(LensFacing.FRONT, viewModel.uiState.value.primaryLens)
        assertEquals(LensFacing.BACK, viewModel.uiState.value.secondaryLens)

        // Switch to PIP mode
        viewModel.setSplitMode(SplitLayoutMode.PIP)
        assertEquals(SplitLayoutMode.PIP, viewModel.uiState.value.splitMode)

        // Toggle audio
        val initialAudio = viewModel.uiState.value.audioEnabled
        viewModel.toggleAudio()
        assertEquals(!initialAudio, viewModel.uiState.value.audioEnabled)
    }
}
