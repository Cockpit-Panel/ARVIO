package com.arflix.tv.data.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import androidx.tvprovider.media.tv.TvContractCompat
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.provider.LauncherArtworkProvider
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class LauncherArtworkDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun episodeAndNameArePublishedAsPrimaryArtworkInBothLauncherRows() = runBlocking {
        val item = fixture(context)
        val artwork = LauncherArtworkRepository(context).artworkFor(item, "launcher-test", "en", null)
        assertEquals("content", artwork.uri?.scheme)
        val image = context.contentResolver.openInputStream(artwork.uri!!)!!.use(BitmapFactory::decodeStream)!!
        assertEquals(960, image.width)
        assertEquals(540, image.height)
        // Episode is blue, series art is red: catch the original wrong-image bug.
        assertTrue(Color.blue(image.getPixel(480, 100)) > 180)
        assertTrue(Color.red(image.getPixel(480, 100)) < 30)
        assertTrue("Title fallback was not drawn", brightPixels(image, 330, 500) > 300)
        image.recycle()

        withContext(Dispatchers.IO) { publisher(context).syncPublishedRows(listOf(LauncherContinueWatchingProgram(item, artwork))) }
        assertPublished(context, TvContractCompat.PreviewPrograms.CONTENT_URI, artwork.uri!!, item)
        assertPublished(context, TvContractCompat.WatchNextPrograms.CONTENT_URI, artwork.uri!!, item)
    }

    @Test fun cachedArtworkSurvivesARepositoryRestartAndChangesWithTheEpisode() = runBlocking {
        val item = fixture(context, 2)
        val repository = LauncherArtworkRepository(context)
        val first = repository.artworkFor(item, "cache-test", "en", null)
        File(Uri.parse(item.episodeStillPath).path!!).delete()
        val cached = LauncherArtworkRepository(context).artworkFor(item, "cache-test", "en", null)
        assertEquals(first.uri, cached.uri)
        assertNotNull(context.contentResolver.openInputStream(cached.uri!!)?.use(BitmapFactory::decodeStream))
        val next = repository.artworkFor(fixture(context, 3), "cache-test", "en", null)
        assertNotEquals("Next episode reused the previous image URI", first.uri, next.uri)
        val anotherProfile = repository.artworkFor(fixture(context, 3), "other-profile", "en", null)
        assertNotEquals(next.uri, anotherProfile.uri)
    }

    @Test fun unavailableEpisodeAndLogoFallBackCleanlyAndTheProviderIsReadOnly() = runBlocking {
        val item = fixture(context, 4).copy(episodeStillPath = "file:///does-not-exist.jpg")
        val artwork = LauncherArtworkRepository(context).artworkFor(item, "fallback-test", "en", "file:///no-logo.png")
        val uri = requireNotNull(artwork.uri)
        val image = context.contentResolver.openInputStream(uri)!!.use(BitmapFactory::decodeStream)!!
        assertTrue("Backdrop fallback was not used", Color.red(image.getPixel(480, 100)) > 180)
        assertTrue("Missing logo did not fall back to a title", brightPixels(image, 330, 500) > 300)
        image.recycle()
        val privateFile = File(context.filesDir, "private-artwork-test.txt").apply { writeText("not launcher artwork") }
        try {
            val invalid = listOf("../${privateFile.name}", "%2e%2e%2f${privateFile.name}", privateFile.name, "${"a".repeat(64)}.jpg/extra")
            for (path in invalid) {
                try {
                    context.contentResolver.openFileDescriptor(Uri.parse("content://${LauncherArtworkProvider.authority(context)}/$path"), "r")?.close()
                    fail("Provider accepted an arbitrary file path")
                } catch (_: java.io.FileNotFoundException) { }
            }
        } finally {
            privateFile.delete()
        }
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.close()
            fail("Artwork provider permitted writes")
        } catch (_: java.io.FileNotFoundException) { }
    }

    @Test fun temporaryEpisodeFailureDoesNotLockLauncherToSeriesArtwork() = runBlocking {
        val item = fixture(context, 6)
        val original = File(Uri.parse(item.episodeStillPath).path!!)
        original.delete()
        val repository = LauncherArtworkRepository(context)
        val fallback = repository.artworkFor(item, "recovery-test", "en", null)
        fixture(context, 6) // The episode image becomes available at the same URL.
        val recovered = repository.artworkFor(item, "recovery-test", "en", null)
        assertNotEquals("Temporary failure permanently cached series art", fallback.uri, recovered.uri)
        val image = context.contentResolver.openInputStream(recovered.uri!!)!!.use(BitmapFactory::decodeStream)!!
        assertTrue("Recovered artwork is not the episode image", Color.blue(image.getPixel(480, 100)) > 180)
        image.recycle()
    }

    private fun brightPixels(image: Bitmap, top: Int, bottom: Int): Int {
        var count = 0
        for (y in top until bottom) for (x in 20 until 900) {
            val pixel = image.getPixel(x, y)
            if (Color.red(pixel) > 200 && Color.green(pixel) > 200 && Color.blue(pixel) > 200) count++
        }
        return count
    }

    companion object {
        private val fixtureRun = java.util.UUID.randomUUID().toString()

        internal fun publisher(context: Context) = LauncherContinueWatchingRepository(context,
            mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true),
            mockk(relaxed = true), mockk(relaxed = true), LauncherArtworkRepository(context))

        internal fun fixture(context: Context, episode: Int = 1): ContinueWatchingItem {
            val dir = File(context.cacheDir, "launcher-test-input-$fixtureRun").apply { mkdirs() }
            fun image(name: String, color: Int): String {
                val file = File(dir, "$name-$episode.png")
                val bitmap = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(color)
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                return Uri.fromFile(file).toString()
            }
            return ContinueWatchingItem(95396, "Severance", MediaType.TV, 35, season = 1, episode = episode,
                episodeTitle = "Test episode", resumePositionSeconds = 600L, durationSeconds = 2400L,
                episodeStillPath = image("episode", Color.BLUE), backdropPath = image("series", Color.RED), posterPath = image("poster", Color.GREEN))
        }

        internal fun assertPublished(context: Context, uri: Uri, expected: Uri, item: ContinueWatchingItem) {
            context.contentResolver.query(uri, null, null, null, null)!!.use { cursor ->
                assertTrue("Published row missing", cursor.moveToFirst())
                assertEquals(expected.toString(), cursor.getString(cursor.getColumnIndexOrThrow("poster_art_uri")))
                assertEquals(expected.toString(), cursor.getString(cursor.getColumnIndexOrThrow("thumbnail_uri")))
                assertEquals(TvContractCompat.PreviewPrograms.ASPECT_RATIO_16_9, cursor.getInt(cursor.getColumnIndexOrThrow("poster_art_aspect_ratio")))
                assertEquals(item.title, cursor.getString(cursor.getColumnIndexOrThrow("title")))
                assertEquals(item.episodeTitle, cursor.getString(cursor.getColumnIndexOrThrow("episode_title")))
                val intent = Intent.parseUri(cursor.getString(cursor.getColumnIndexOrThrow("intent_uri")), Intent.URI_INTENT_SCHEME)
                val request = requireNotNull(intent.data).toLauncherContinueWatchingRequest()!!
                assertEquals(item.id, request.mediaId)
                assertEquals(item.season, request.season)
                assertEquals(item.episode, request.episode)
                assertEquals(item.resumePositionSeconds * 1000L, request.startPositionMs)
            }
        }
    }
}
