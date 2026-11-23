package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram
import org.junit.Assert.*
import org.junit.Test

class FavoriteTvGuideRefreshTest {
    private val retry = 30 * 60_000L
    private val airing = IptvNowNext(now = IptvProgram(title = "On air", startUtcMillis = 0, endUtcMillis = 1))

    @Test fun savedGuideCoveringNowNeverAsksTheNetwork() {
        val saved = mapOf("a" to airing, "b" to airing)
        assertFalse(favoriteTvGuideNeedsNetwork(setOf("a", "b"), saved, lastNetworkMs = 0, nowMs = 10, retryMs = retry))
    }

    @Test fun aChannelWithoutACurrentProgrammeAsksOnceThenWaits() {
        val saved = mapOf("a" to airing, "b" to IptvNowNext())
        assertTrue(favoriteTvGuideNeedsNetwork(setOf("a", "b"), saved, lastNetworkMs = 0, nowMs = 10, retryMs = retry))
        assertFalse(favoriteTvGuideNeedsNetwork(setOf("a", "b"), saved, lastNetworkMs = 10, nowMs = 10 + retry - 1, retryMs = retry))
        assertTrue(favoriteTvGuideNeedsNetwork(setOf("a", "b"), saved, lastNetworkMs = 10, nowMs = 10 + retry, retryMs = retry))
    }

    @Test fun noSavedGuideAtAllCountsAsMissing() {
        assertTrue(favoriteTvGuideNeedsNetwork(setOf("a"), null, lastNetworkMs = 0, nowMs = 10, retryMs = retry))
    }

    @Test fun focusRefreshIsThrottled() {
        assertTrue(favoriteTvFocusRefreshDue(lastRefreshMs = 0, nowMs = 5, intervalMs = 60_000))
        assertFalse(favoriteTvFocusRefreshDue(lastRefreshMs = 5, nowMs = 59_000, intervalMs = 60_000))
        assertTrue(favoriteTvFocusRefreshDue(lastRefreshMs = 5, nowMs = 60_005, intervalMs = 60_000))
    }

    private val mine = com.arflix.tv.data.repository.IptvPlaylistEntry(id = "list_1", name = "Mine", m3uUrl = "https://a.test/list.m3u")
    private val other = com.arflix.tv.data.repository.IptvPlaylistEntry(id = "list_2", name = "Other", m3uUrl = "https://b.test/list.m3u")

    @Test fun switchingWhichPlaylistIsOnChangesTheFavoriteSourceKey() {
        val before = com.arflix.tv.data.repository.IptvConfig(playlists = listOf(mine, other.copy(enabled = false)))
        val after = com.arflix.tv.data.repository.IptvConfig(playlists = listOf(mine.copy(enabled = false), other))

        assertNotEquals(iptvFavoriteSourceKey(before), iptvFavoriteSourceKey(after))
    }

    @Test fun unrelatedSettingsLeaveTheFavoriteSourceKeyAlone() {
        val config = com.arflix.tv.data.repository.IptvConfig(playlists = listOf(mine, other.copy(enabled = false)))

        assertEquals(iptvFavoriteSourceKey(config), iptvFavoriteSourceKey(config.copy(sortOrder = "name")))
        assertEquals(
            iptvFavoriteSourceKey(config),
            iptvFavoriteSourceKey(config.copy(playlists = listOf(mine, other.copy(enabled = false, name = "Renamed")))),
        )
    }
}
