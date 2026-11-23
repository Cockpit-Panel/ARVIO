package com.arflix.tv.data.repository.simkl

import android.content.Context
import com.arflix.tv.data.api.SimklActivitiesResponse
import com.arflix.tv.data.api.SimklActivityGroup
import com.arflix.tv.data.api.SimklApi
import com.arflix.tv.data.api.SimklIds
import com.arflix.tv.data.api.SimklMovieRef
import com.arflix.tv.data.api.SimklPlaybackItem
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.api.TmdbListResponse
import com.arflix.tv.data.api.TmdbMediaItem
import com.arflix.tv.data.repository.sync.SyncProviderStore
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class SimklSnapshotRegressionTest {
    @get:Rule val temporaryFolder = TemporaryFolder()
    private val api = mockk<SimklApi>(relaxed = true)
    private val store = mockk<SyncProviderStore>(relaxed = true)
    private val tmdb = mockk<TmdbApi>(relaxed = true)
    private val first = "2026-09-08T10:00:00Z"
    private val second = "2026-09-08T11:00:00Z"
    private var elapsedMs = 1_000_000L
    private fun json(value: String) = JsonParser.parseString(value)
    private fun service(context: Context? = null) = SimklSyncService(api, SimklAuthManager(api, store), tmdb, store, context)
        .apply { elapsedRealtimeMs = { elapsedMs } }
    private suspend fun SimklSyncService.refreshAfterCooldown(): Boolean {
        elapsedMs += SimklSyncService.MIN_ACTIVITY_CHECK_INTERVAL_MS
        return syncIfNeeded(force = true)
    }
    private fun diskContext(): Context = mockk<Context>().also { every { it.filesDir } returns temporaryFolder.root }

    @Before fun prepare() {
        coEvery { store.getSimklAccessToken() } returns "review-token"
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = first)
        coEvery { api.getAllItems(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns json("{}")
        coEvery { api.getAllItems(any(), any(), "movies", any(), any(), any(), any(), any(), any()) } returns json(
            """{"movies":[{"status":"plantowatch","movie":{"title":"Saved movie","ids":{"simkl":1,"tmdb":101}}}]}"""
        )
        coEvery { api.getAllItemsDelta(any(), any(), any(), any(), any(), any(), any()) } returns json("{}")
        coEvery { api.getPlayback(any(), any()) } returns emptyList()
        coEvery { api.getPlaybackDelta(any(), any(), any()) } returns emptyList()
        coEvery { api.getPlaybackBefore(any(), any(), any()) } returns emptyList()
    }

    @Test fun omittedSeasonsRetainHistoryButEmptySeasonsClearIt() = runBlocking {
        coEvery { api.getAllItems(any(), any(), "shows", any(), any(), any(), any(), any(), any()) } returns json(
            """{"shows":[{"status":"watching","show":{"ids":{"simkl":2,"tmdb":102}},"seasons":[{"number":1,"episodes":[{"number":1}]}]}]}"""
        )
        val sync = service()
        assertTrue(sync.refreshAfterCooldown())
        assertTrue(sync.getWatchedEpisodes().contains("show_tmdb:102:1:1"))
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = second)
        coEvery { api.getAllItemsDelta(any(), any(), any(), any(), any(), any(), any()) } returns json(
            """{"shows":[{"status":"watching","show":{"ids":{"simkl":2,"tmdb":102}}}]}"""
        )
        assertTrue(sync.refreshAfterCooldown())
        assertTrue(sync.getWatchedEpisodes().contains("show_tmdb:102:1:1"))
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = "2026-09-08T12:00:00Z")
        coEvery { api.getAllItemsDelta(any(), any(), any(), any(), any(), any(), any()) } returns json(
            """{"shows":[{"status":"plantowatch","show":{"ids":{"simkl":2,"tmdb":102}},"seasons":[]}]}"""
        )
        assertTrue(sync.refreshAfterCooldown())
        assertFalse(sync.getWatchedEpisodes().contains("show_tmdb:102:1:1"))
    }

    @Test fun deletionReconcilesOnlyChangedCategory() = runBlocking {
        val sync = service()
        assertTrue(sync.refreshAfterCooldown())
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(
            all = second, movies = SimklActivityGroup(removedFromList = second)
        )
        coEvery { api.getAllItemIds(any(), any(), "movies", any()) } returns json("[]")
        assertTrue(sync.refreshAfterCooldown())
        assertTrue(sync.getWatchlistItems().isEmpty())
        coVerify(exactly = 1) { api.getAllItemIds(any(), any(), "movies", "ids_only") }
        coVerify(exactly = 0) { api.getAllItemIds(any(), any(), "shows", any()) }
        coVerify(exactly = 0) { api.getAllItemIds(any(), any(), "anime", any()) }
        assertTrue(sync.refreshAfterCooldown())
        coVerify(exactly = 1) { api.getAllItemIds(any(), any(), any(), any()) }
    }

    @Test fun failedDeletionReconciliationKeepsCacheAndRetriesSameWatermark() = runBlocking {
        val sync = service()
        assertTrue(sync.refreshAfterCooldown())
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(
            all = second, movies = SimklActivityGroup(removedFromList = second)
        )
        coEvery { api.getAllItemIds(any(), any(), "movies", any()) } throws IOException("offline")
        assertFalse(sync.refreshAfterCooldown())
        assertEquals(listOf(101), sync.getWatchlistItems().map { it.id })
        coVerify(exactly = 0) { store.setSimklWatermark(second) }
        coEvery { api.getAllItemIds(any(), any(), "movies", any()) } returns json("{\"movies\":[]}")
        assertTrue(sync.refreshAfterCooldown())
        assertTrue(sync.getWatchlistItems().isEmpty())
        coVerify(exactly = 2) { api.getAllItemsDelta(any(), any(), first, any(), any(), any(), any()) }
    }

    @Test fun failedPlaybackDoesNotVetoWatermarkOrLibrarySync() = runBlocking {
        val sync = service()
        assertTrue(sync.refreshAfterCooldown())
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = second)
        coEvery { api.getPlaybackDelta(any(), any(), any()) } throws IOException("temporary playback outage")
        assertFalse(sync.refreshAfterCooldown())
        coVerify(exactly = 1) { store.setSimklWatermark(second) }
        coEvery { api.getPlaybackDelta(any(), any(), any()) } returns emptyList()
        assertTrue(sync.refreshAfterCooldown())
        coVerify(exactly = 1) { api.getAllItemsDelta(any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 1) { api.getPlayback(any(), any()) }
        coVerify(exactly = 2) { api.getPlaybackDelta(any(), any(), first) }
    }

    @Test fun failedBootstrapPlaybackDoesNotFailLibraryBootstrap() = runBlocking {
        val sync = service()
        coEvery { api.getPlayback(any(), any()) } throws IOException("offline")
        assertFalse(sync.refreshAfterCooldown())
        assertEquals(listOf(101), sync.getWatchlistItems().map { it.id })
        coEvery { api.getPlayback(any(), any()) } returns emptyList()
        assertTrue(sync.refreshAfterCooldown())
        coVerify(exactly = 3) { api.getAllItems(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 2) { api.getPlayback(any(), any()) }
    }

    @Test fun restartRestoresAccountSnapshotAndItsOwnWatermark() = runBlocking {
        val context = diskContext()
        assertTrue(service(context).refreshAfterCooldown())
        // A separately saved timestamp must never move this snapshot past data it actually contains.
        coEvery { store.getSimklWatermark() } returns "2026-09-08T12:00:00Z"
        val restarted = service(context)
        assertTrue(restarted.refreshAfterCooldown())
        assertEquals(listOf(101), restarted.getWatchlistItems().map { it.id })
        coVerify(exactly = 3) { api.getAllItems(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        coVerify(exactly = 0) { api.getAllItemsDelta(any(), any(), any(), any(), any(), any(), any()) }
        val file = File(temporaryFolder.root, "simkl_snapshot_cache.json")
        assertFalse(file.readText().contains("review-token"))
    }

    @Test fun differentAccountCannotRestorePreviousAccountSnapshot() = runBlocking {
        val context = diskContext()
        assertTrue(service(context).refreshAfterCooldown())
        coEvery { store.getSimklAccessToken() } returns "other-account"
        coEvery { api.getActivities(any(), any()) } throws IOException("offline")
        val otherAccount = service(context)
        assertFalse(otherAccount.refreshAfterCooldown())
        assertTrue(otherAccount.getWatchlistItems().isEmpty())
    }

    private fun pause(id: Int, at: String = first) = SimklPlaybackItem(
        id = id.toLong(), progress = 40f, pausedAt = at,
        movie = SimklMovieRef(title = "Movie $id", ids = SimklIds(tmdb = id))
    )

    @Test fun unchangedActivitiesSkipPlaybackEvenOnForcedOrExpiredRefresh() = runBlocking {
        val sync = service()
        assertTrue(sync.refreshAfterCooldown())
        assertTrue(sync.refreshAfterCooldown())
        elapsedMs += 15 * 60_000L
        assertTrue(sync.syncIfNeeded())
        coVerify(exactly = 3) { api.getActivities(any(), any()) }
        coVerify(exactly = 1) { api.getPlayback(any(), any()) }
        coVerify(exactly = 0) { api.getPlaybackDelta(any(), any(), any()) }
        coVerify(exactly = 0) { api.getPlaybackBefore(any(), any(), any()) }
    }

    @Test fun unrelatedLibraryChangesDoNotDownloadPlayback() = runBlocking {
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(
            all = first, movies = SimklActivityGroup(playback = first)
        )
        val sync = service()
        assertTrue(sync.refreshAfterCooldown())
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(
            all = second, movies = SimklActivityGroup(playback = first)
        )
        assertTrue(sync.refreshAfterCooldown())
        coVerify(exactly = 1) { api.getAllItemsDelta(any(), any(), first, any(), any(), any(), any()) }
        coVerify(exactly = 1) { api.getPlayback(any(), any()) }
        coVerify(exactly = 0) { api.getPlaybackDelta(any(), any(), any()) }
    }

    @Test fun playbackDeltaPreservesOlderSessionsButRemovesRemoteDeletions() = runBlocking {
        coEvery { api.getPlayback(any(), any()) } returns listOf(pause(201), pause(202))
        val sync = service()
        assertTrue(sync.refreshAfterCooldown())
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = second)
        coEvery { api.getPlaybackDelta(any(), any(), first) } returns listOf(pause(203, second))
        coEvery { api.getPlaybackBefore(any(), any(), first) } returns listOf(pause(201))
        assertTrue(sync.refreshAfterCooldown())
        assertEquals(setOf(201, 203), sync.getContinueWatching().map { it.id }.toSet())
        coVerify(exactly = 1) { api.getPlayback(any(), any()) }
        coVerify(exactly = 1) { api.getPlaybackDelta(any(), any(), first) }
        coVerify(exactly = 1) { api.getPlaybackBefore(any(), any(), first) }
    }

    @Test fun failedOlderReconciliationRetainsPlaybackAndItsCheckpointAcrossRestart() = runBlocking {
        coEvery { api.getPlayback(any(), any()) } returns listOf(pause(201))
        val context = diskContext()
        val sync = service(context)
        assertTrue(sync.refreshAfterCooldown())
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = second)
        coEvery { api.getPlaybackDelta(any(), any(), first) } returns listOf(pause(203, second))
        coEvery { api.getPlaybackBefore(any(), any(), first) } throws IOException("offline")
        assertFalse(sync.refreshAfterCooldown())
        assertEquals(listOf(201), sync.getContinueWatching().map { it.id })
        coEvery { api.getPlaybackBefore(any(), any(), first) } returns emptyList()
        val restarted = service(context)
        assertTrue(restarted.refreshAfterCooldown())
        assertEquals(listOf(203), restarted.getContinueWatching().map { it.id })
        coVerify(exactly = 2) { api.getPlaybackDelta(any(), any(), first) }
        coVerify(exactly = 1) { api.getPlayback(any(), any()) }
    }

    @Test fun emptyDeltaCanClearAllSavedSessions() = runBlocking {
        coEvery { api.getPlayback(any(), any()) } returns listOf(pause(201))
        val sync = service()
        assertTrue(sync.refreshAfterCooldown())
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = second)
        assertTrue(sync.refreshAfterCooldown())
        assertTrue(sync.getContinueWatching().isEmpty())
    }

    @Test fun restartDoesNotRedownloadUnchangedPlayback() = runBlocking {
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(
            all = first, movies = SimklActivityGroup(playback = first)
        )
        val context = diskContext()
        assertTrue(service(context).refreshAfterCooldown())
        assertTrue(service(context).refreshAfterCooldown())
        coVerify(exactly = 1) { api.getPlayback(any(), any()) }
        coVerify(exactly = 0) { api.getPlaybackDelta(any(), any(), any()) }
    }

    @Test fun legacySnapshotGetsOneFullPlaybackRefreshBeforeUsingDeltas() = runBlocking {
        val context = diskContext()
        assertTrue(service(context).refreshAfterCooldown())
        val file = File(temporaryFolder.root, "simkl_snapshot_cache.json")
        val old = json(file.readText()).asJsonObject
        old.remove("playbackWatermark")
        old.remove("playbackMarkers")
        file.writeText(old.toString())
        val restarted = service(context)
        assertTrue(restarted.refreshAfterCooldown())
        assertTrue(restarted.refreshAfterCooldown())
        coVerify(exactly = 2) { api.getPlayback(any(), any()) }
        coVerify(exactly = 0) { api.getPlaybackDelta(any(), any(), any()) }
    }

    @Test fun restartRetainsResolvedAnimeIdsWithoutRepeatingMetadataLookups() = runBlocking {
        val context = diskContext()
        coEvery { api.getAllItems(any(), any(), "anime", any(), any(), any(), any(), any(), any()) } returns json(
            """{"anime":[{"status":"plantowatch","show":{"title":"Test Anime","year":2025,"ids":{"simkl":900}}}]}"""
        )
        coEvery { tmdb.searchTv(any(), "Test Anime", any(), any(), 2025) } returns TmdbListResponse(
            results = listOf(TmdbMediaItem(id = 901, name = "Test Anime", firstAirDate = "2025-01-10", popularity = 10f))
        )
        assertTrue(service(context).refreshAfterCooldown())
        val restarted = service(context)
        assertTrue(restarted.refreshAfterCooldown())
        assertEquals(setOf(101, 901), restarted.getWatchlistItems().map { it.id }.toSet())
        coVerify(exactly = 1) { tmdb.searchTv(any(), "Test Anime", any(), any(), 2025) }
    }

    @Test fun failedRefreshNeverPersistsAnAdvancedWatermark() = runBlocking {
        val context = diskContext()
        assertTrue(service(context).refreshAfterCooldown())
        val sync = service(context)
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = second)
        coEvery { api.getAllItemsDelta(any(), any(), any(), any(), any(), any(), any()) } throws IOException("offline")
        assertFalse(sync.refreshAfterCooldown())
        coEvery { api.getAllItemsDelta(any(), any(), any(), any(), any(), any(), any()) } returns json("{}")
        assertTrue(service(context).refreshAfterCooldown())
        coVerify(exactly = 2) { api.getAllItemsDelta(any(), any(), first, any(), any(), any(), any()) }
    }

    @Test fun corruptCacheFallsBackToBootstrap() = runBlocking {
        val context = diskContext()
        File(temporaryFolder.root, "simkl_snapshot_cache.json").writeText("{broken")
        assertTrue(service(context).refreshAfterCooldown())
        coVerify(exactly = 3) { api.getAllItems(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }
}
