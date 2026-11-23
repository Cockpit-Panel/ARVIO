package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class HomeWatchlistTest {
    private val watching = Category("continue_watching", "Continue Watching", listOf(MediaItem(1, "Watching")))
    private val catalog = Category("trending_movies", "Trending", listOf(MediaItem(2, "Trending")))
    private val saved = listOf(MediaItem(3, "Saved movie"), MediaItem(4, "Saved series", mediaType = MediaType.TV))

    @Test fun `watchlist follows continue watching without moving other rows`() {
        val rows = withHomeWatchlistRow(listOf(watching, catalog), saved, true)
        assertEquals(listOf("continue_watching", HOME_WATCHLIST_ROW_ID, "trending_movies"), rows.map { it.id })
        assertSame(watching, rows[0])
        assertSame(catalog, rows[2])
        assertEquals(saved, rows[1].items)
    }

    @Test fun `without continue watching the watchlist is first`() {
        assertEquals(HOME_WATCHLIST_ROW_ID, withHomeWatchlistRow(listOf(catalog), saved, true).first().id)
    }

    @Test fun `disabled and empty watchlists do not leave an empty row`() {
        val base = listOf(watching, catalog)
        assertSame(base, withHomeWatchlistRow(base, saved, false))
        assertSame(base, withHomeWatchlistRow(base, emptyList(), true))
    }

    @Test fun `refreshes cannot duplicate or restore a disabled watchlist row`() {
        val first = withHomeWatchlistRow(listOf(watching, catalog), saved, true)
        val refreshed = withHomeWatchlistRow(first, saved.take(1), true)
        assertEquals(1, refreshed.count { it.id == HOME_WATCHLIST_ROW_ID })
        assertEquals(1, refreshed[1].items.size)
        assertEquals(listOf(watching, catalog), withHomeWatchlistRow(refreshed, saved, false))
    }

    @Test fun `display order matches library and keeps movie and series ids distinct`() {
        val movie = MediaItem(3, "Movie", addedAt = 20L)
        val series = MediaItem(3, "Series", mediaType = MediaType.TV, addedAt = 30L)
        val ranked = MediaItem(5, "Ranked", sourceOrder = 0, addedAt = 10L)
        assertEquals(listOf(ranked, series, movie), homeWatchlistDisplayItems(listOf(movie, series, movie, ranked)))
    }

    @Test fun `skeletons and blank items are not shown`() {
        assertEquals(saved, homeWatchlistDisplayItems(saved + MediaItem(-1, "", isPlaceholder = true)))
    }
}
