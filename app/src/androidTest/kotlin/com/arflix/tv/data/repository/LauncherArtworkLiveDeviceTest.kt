package com.arflix.tv.data.repository

import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.di.RepositoryAccessEntryPoint
import com.arflix.tv.util.Constants
import dagger.hilt.android.EntryPointAccessors
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in public-metadata check: no account, provider credentials or playback is used. */
class LauncherArtworkLiveDeviceTest {
    @Test fun publicEpisodeArtworkAndClearlogosArePublishedForTheNativeLauncher() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("liveArtwork") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val media = EntryPointAccessors.fromApplication(context, RepositoryAccessEntryPoint::class.java).mediaRepository()
        val art = LauncherArtworkRepository(context)
        val output = File(context.getExternalFilesDir(null), "launcher-artwork-test").apply { mkdirs() }
        val programs = listOf(95396, 136315, 100088).map { id ->
            val episode = requireNotNull(media.getLightweightEpisode(id, 1, 1, "en-US"))
            val title = requireNotNull(media.getLightweightTvTitle(id, "en-US"))
            val logo = requireNotNull(media.getLogoUrl(MediaType.TV, id))
            val item = ContinueWatchingItem(id, title, MediaType.TV, 35, season = 1, episode = 1,
                episodeTitle = episode.name, resumePositionSeconds = 600L, durationSeconds = 2400L,
                episodeStillPath = "${Constants.IMAGE_BASE_LARGE}${requireNotNull(episode.stillPath)}")
            val artwork = art.artworkFor(item, "live-launcher-test", "en-US", logo)
            assertEquals("Generated logo artwork was unavailable", "content", artwork.uri?.scheme)
            assertEquals("Live check used fallback art instead of the episode and clearlogo",
                "${launcherArtworkKey(item, "live-launcher-test", "en-US", logo)}.jpg", artwork.uri?.lastPathSegment)
            val file = File(output, "$id-episode.jpg")
            context.contentResolver.openInputStream(artwork.uri!!)!!.use { source -> file.outputStream().use { source.copyTo(it) } }
            val decoded = BitmapFactory.decodeFile(file.absolutePath)
            assertEquals(960, decoded.width)
            assertEquals(540, decoded.height)
            decoded.recycle()
            instrumentation.sendStatus(0, Bundle().apply { putString("artwork", "$title: ${artwork.uri}") })
            LauncherContinueWatchingProgram(item, artwork)
        }
        withContext(Dispatchers.IO) { LauncherArtworkDeviceTest.publisher(context).syncPublishedRows(programs) }
    }
}
