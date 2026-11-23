package com.arflix.tv.data.repository

import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class LauncherArtworkTest {
    private val episode = ContinueWatchingItem(10, "Example show", MediaType.TV, 30,
        season = 2, episode = 3, episodeStillPath = "episode.jpg", backdropPath = "series.jpg", posterPath = "poster.jpg")

    @Test fun episodeImageTakesPriorityOverSeriesArtwork() {
        assertEquals(listOf("episode.jpg", "series.jpg", "poster.jpg"), launcherArtworkCandidates(episode))
    }

    @Test fun blankEpisodeImageFallsBackToBackdropThenPoster() {
        assertEquals(listOf("series.jpg", "poster.jpg"), launcherArtworkCandidates(episode.copy(episodeStillPath = " ")))
        assertEquals(listOf("poster.jpg"), launcherArtworkCandidates(episode.copy(episodeStillPath = null, backdropPath = "")))
    }

    @Test fun movieDoesNotAccidentallyUseAnEpisodeImage() {
        assertEquals(listOf("series.jpg", "poster.jpg"), launcherArtworkCandidates(episode.copy(mediaType = MediaType.MOVIE)))
    }

    @Test fun duplicateAndMissingArtworkDoNotGenerateUselessDownloads() {
        assertEquals(listOf("same.jpg"), launcherArtworkCandidates(episode.copy(episodeStillPath = "same.jpg", backdropPath = "same.jpg", posterPath = "same.jpg")))
        assertTrue(launcherArtworkCandidates(episode.copy(episodeStillPath = null, backdropPath = "", posterPath = null)).isEmpty())
    }

    @Test fun playbackProgressDoesNotInvalidateTheArtworkCache() {
        assertEquals(key(episode), key(episode.copy(progress = 80, resumePositionSeconds = 1200, updatedAtMs = 999)))
    }

    @Test fun nextEpisodeOrDifferentArtworkCannotReuseStaleThumbnail() {
        for (changed in listOf(episode.copy(episode = 4), episode.copy(season = 3), episode.copy(title = "Localized show"),
            episode.copy(episodeStillPath = "new.jpg"), episode.copy(mediaType = MediaType.MOVIE))) {
            assertNotEquals(key(episode), key(changed))
        }
    }

    @Test fun profileLanguageAndLogoHaveIndependentCacheEntries() {
        val base = key(episode)
        assertNotEquals(base, launcherArtworkKey(episode, "another-profile", "en", "logo.png"))
        assertNotEquals(base, launcherArtworkKey(episode, "profile", "es", "logo.png"))
        assertNotEquals(base, launcherArtworkKey(episode, "profile", "en", null))
    }

    @Test fun cacheNamesCannotContainPrivateUrlsOrBeConfusedBySeparators() {
        val item = episode.copy(episodeStillPath = "https://private.invalid/image?token=secret")
        val first = launcherArtworkKey(item, "a:b", "c", null)
        val second = launcherArtworkKey(item, "a", "b:c", null)
        assertTrue(first.matches(Regex("[a-f0-9]{64}")))
        assertNotEquals(first, second)
    }

    private fun key(item: ContinueWatchingItem) = launcherArtworkKey(item, "profile", "en", "logo.png")
}
