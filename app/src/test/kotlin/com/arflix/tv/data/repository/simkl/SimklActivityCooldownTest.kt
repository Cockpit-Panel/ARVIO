package com.arflix.tv.data.repository.simkl

import com.arflix.tv.data.api.SimklActivitiesResponse
import com.arflix.tv.data.api.SimklApi
import com.arflix.tv.data.api.TmdbApi
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.sync.SyncProviderStore
import com.google.gson.JsonParser
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class SimklActivityCooldownTest {
    private val api = mockk<SimklApi>(relaxed = true)
    private val store = mockk<SyncProviderStore>(relaxed = true)
    private val tmdb = mockk<TmdbApi>(relaxed = true)
    private var now = 1_000_000L
    private val sync = SimklSyncService(api, SimklAuthManager(api, store), tmdb, store)
        .apply { elapsedRealtimeMs = { now } }
    private val first = "2026-10-16T10:00:00Z"

    @Before fun prepare() {
        coEvery { store.getSimklAccessToken() } returns "cooldown-test-token"
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = first)
        coEvery { api.getAllItems(any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns JsonParser.parseString("{}")
        coEvery { api.getPlayback(any(), any()) } returns emptyList()
    }

    @Test fun forcedNavigationRefreshesShareOneActivityCheckPerMinute() = runBlocking {
        assertTrue(sync.syncIfNeeded(true))
        repeat(59) {
            now += 1_000L
            assertTrue(sync.syncIfNeeded(true))
            sync.getWatchlistItems()
            sync.getWatchedMovies()
            sync.getLibraryItems("watching", forceRefresh = true)
            sync.getContinueWatching(true)
        }
        coVerify(exactly = 1) { api.getActivities(any(), any()) }
        now += 1_000L
        assertTrue(sync.syncIfNeeded(true))
        coVerify(exactly = 2) { api.getActivities(any(), any()) }
        coVerify(exactly = 1) { api.getPlayback(any(), any()) }
    }

    @Test fun concurrentForcedCallersReuseTheCompletedRequest() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        coEvery { api.getActivities(any(), any()) } coAnswers {
            entered.complete(Unit)
            release.await()
            SimklActivitiesResponse(all = first)
        }
        val calls = List(30) { async { sync.syncIfNeeded(true) } }
        entered.await()
        now += 90_000L // Even a slow first request must not queue 29 fresh checks.
        release.complete(Unit)
        assertTrue(calls.awaitAll().all { it })
        coVerify(exactly = 1) { api.getActivities(any(), any()) }
    }

    @Test fun forcedFailureRetriesCannotBypassCooldown() = runBlocking {
        coEvery { api.getActivities(any(), any()) } throws IOException("offline")
        assertFalse(sync.syncIfNeeded(true))
        repeat(20) { now += 1_000L; assertFalse(sync.syncIfNeeded(true)) }
        coVerify(exactly = 1) { api.getActivities(any(), any()) }
        now += 40_000L
        coEvery { api.getActivities(any(), any()) } returns SimklActivitiesResponse(all = first)
        assertTrue(sync.syncIfNeeded(true))
        coVerify(exactly = 2) { api.getActivities(any(), any()) }
    }

    @Test fun cancellationDoesNotRestartActivityPollingImmediately() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        coEvery { api.getActivities(any(), any()) } coAnswers {
            entered.complete(Unit)
            awaitCancellation()
        }
        val request = async { sync.syncIfNeeded(true) }
        entered.await()
        request.cancelAndJoin()
        assertFalse(sync.syncIfNeeded(true))
        coVerify(exactly = 1) { api.getActivities(any(), any()) }
    }

    @Test fun localWatchlistChangesStayImmediateWithoutResettingRequestCooldown() = runBlocking {
        assertTrue(sync.syncIfNeeded(true))
        coEvery { api.addToList(any(), any(), any()) } returns retrofit2.Response.success(mockk<okhttp3.ResponseBody>())
        coEvery { api.removeFromHistory(any(), any(), any()) } returns retrofit2.Response.success(mockk<okhttp3.ResponseBody>())
        assertTrue(sync.addToWatchlist(MediaType.MOVIE, 101))
        assertEquals(listOf(101), sync.getWatchlistItems().map { it.id })
        assertTrue(sync.syncIfNeeded(true))
        assertTrue(sync.removeFromWatchlist(MediaType.MOVIE, 101))
        assertTrue(sync.getWatchlistItems().isEmpty())
        coVerify(exactly = 1) { api.getActivities(any(), any()) }
    }

    @Test fun incompleteSnapshotKeepsItsRetryCooldownEvenForForcedReaders() = runBlocking {
        coEvery { api.getAllItems(any(), any(), "shows", any(), any(), any(), any(), any(), any()) } throws IOException("offline")
        assertFalse(sync.syncIfNeeded(true))
        repeat(10) { assertFalse(sync.syncIfNeeded(true)) }
        coVerify(exactly = 1) { api.getActivities(any(), any()) }
        coVerify(exactly = 1) { api.getAllItems(any(), any(), "shows", any(), any(), any(), any(), any(), any()) }
    }

    @Test fun backgroundReadsKeepTheFifteenMinuteCache() = runBlocking {
        assertTrue(sync.syncIfNeeded())
        now += 15 * 60_000L - 1L
        assertTrue(sync.syncIfNeeded())
        coVerify(exactly = 1) { api.getActivities(any(), any()) }
        now++
        assertTrue(sync.syncIfNeeded())
        coVerify(exactly = 2) { api.getActivities(any(), any()) }
    }

    @Test fun differentAccountDoesNotReuseAnotherAccountsCacheOrCooldown() = runBlocking {
        assertTrue(sync.syncIfNeeded(true))
        coEvery { store.getSimklAccessToken() } returns "other-cooldown-test-token"
        assertTrue(sync.syncIfNeeded(true))
        coVerify(exactly = 1) { api.getActivities("Bearer other-cooldown-test-token", any()) }
        coVerify(exactly = 6) { api.getAllItems(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test fun cooldownAlsoWorksAtZeroDeviceUptime() = runBlocking {
        now = 0L
        assertTrue(sync.syncIfNeeded(true))
        assertTrue(sync.syncIfNeeded(true))
        coVerify(exactly = 1) { api.getActivities(any(), any()) }
    }
}
