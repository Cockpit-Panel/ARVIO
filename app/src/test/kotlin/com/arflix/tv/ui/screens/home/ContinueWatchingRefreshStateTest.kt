package com.arflix.tv.ui.screens.home

import androidx.datastore.preferences.core.*
import app.cash.turbine.test
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.Episode
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.ContinueWatchingItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ContinueWatchingRefreshStateTest {
    @Test fun forcedRefreshesDoNotCancelTheActiveFetchAndCoalesceOneFollowUp() {
        val requests = ContinueWatchingRefreshRequests()
        val startup = requests.claim(false)!!
        repeat(100) { assertNull(requests.claim(true)) }
        assertTrue(requests.finish(startup))
        val followUp = requests.claim(true)!!
        repeat(100) { assertNull(requests.claim(false)) }
        assertFalse(requests.finish(followUp))
        assertNotNull(requests.claim(false))
    }

    @Test fun oldProfileCompletionCannotReleaseTheNewProfilesFetch() {
        val requests = ContinueWatchingRefreshRequests()
        val old = requests.claim(false)!!
        requests.claim(true)
        requests.reset()
        val current = requests.claim(false)!!
        assertFalse(requests.finish(old))
        assertNull(requests.claim(false))
        assertFalse(requests.finish(current))
    }

    @Test fun startupPreservesPreloadedRealContinueWatchingAndPrefersNewerPublishedItems() {
        fun watching(id: Int) = Category("continue_watching", "Continue Watching", listOf(MediaItem(id, "Title $id")))
        val catalog = Category("trending_movies", "Movies", listOf(MediaItem(9, "Catalog")))
        assertEquals(1, preloadedHomeCategories(listOf(watching(1), catalog), emptyList()).first().items.single().id)
        assertEquals(2, preloadedHomeCategories(listOf(watching(1), catalog), listOf(watching(2))).first().items.single().id)
        val skeleton = watching(-1).copy(items = listOf(MediaItem(-1, "", isPlaceholder = true)))
        assertEquals(1, preloadedHomeCategories(listOf(watching(1), catalog), listOf(skeleton)).first().items.single().id)
        assertEquals(2, preloadedHomeCategories(listOf(catalog), emptyList()).size)
    }

    @Test fun restoredCredentialsAndRoutingRefreshButOrdinaryCacheWritesDoNot() = runTest {
        val profile = MutableStateFlow("one")
        val settings = MutableStateFlow<Preferences>(emptyPreferences())
        val credentials = MutableStateFlow<Preferences>(emptyPreferences())
        observeHomeTrackingState(profile, settings, credentials).test {
            assertFalse(awaitItem().connections.first())
            credentials.value = preferencesOf(stringPreferencesKey("profile_one_trakt_access_token") to "fixture")
            assertTrue(awaitItem().connections.first())
            credentials.value = preferencesOf(stringPreferencesKey("profile_one_trakt_access_token") to "rotated",
                stringPreferencesKey("profile_one_continue_watching_cache") to "changed")
            expectNoEvents()
            settings.value = preferencesOf(stringPreferencesKey("profile_one_tracking_continue_read_mode_v2") to "simkl",
                longPreferencesKey("profile_one_simkl_credential_updated_at_v3") to 1L)
            assertEquals("simkl", awaitItem().readMode)
            profile.value = "two"
            val next = awaitItem()
            assertEquals("two", next.profileId)
            assertFalse(next.connections.any { it })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun cachedStartupNeverFetchesSeasonMetadata() = runTest {
        var fetched = 0
        val rows = (1..50).map { watching(it) }
        val seasons = loadContinueWatchingSeasons(rows, true, { _, _ -> null }, { _, _ -> fetched++; emptyList() })
        assertEquals(0, fetched)
        assertEquals(50, seasons.size)
        assertEquals(rows, rows.mapNotNull { validatedContinueWatchingEpisode(it, seasons[it.id to it.season!!]) })
    }

    @Test fun freshValidationBatchesUniqueSeasonsWithBoundedConcurrency() = runTest {
        var active = 0
        var peak = 0
        var fetched = 0
        val items = (1..12).map { watching(it) }
        loadContinueWatchingSeasons(items + items, false, { _, _ -> null }, { _, _ ->
            fetched++; active++; peak = maxOf(peak, active)
            delay(100)
            active--
            emptyList()
        })
        assertEquals(12, fetched)
        assertEquals(4, peak)
    }

    @Test fun cacheOnlyValidationStillRejectsKnownFutureEpisodesAndKeepsNativeTitles() {
        val item = watching(1)
        assertNull(validatedContinueWatchingEpisode(item.copy(releaseDate = "2099-01-01"), null))
        assertNull(validatedContinueWatchingEpisode(item, listOf(Episode(1, 2, 1, "Future", airDate = "2099-01-01"))))
        assertEquals("Aired", validatedContinueWatchingEpisode(item, listOf(Episode(1, 2, 1, "Aired", airDate = "2020-01-01")))?.episodeTitle)
        assertEquals(item, validatedContinueWatchingEpisode(item, emptyList()))
        val native = item.copy(addonNativeId = "native", releaseDate = "2099-01-01")
        assertEquals(native, validatedContinueWatchingEpisode(native, null))
    }

    private fun watching(id: Int) = ContinueWatchingItem(id, "Show $id", MediaType.TV, 0,
        season = 1, episode = 2, isUpNext = true)
}
