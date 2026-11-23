package com.arflix.tv.ui.screens.watchlist

import com.arflix.tv.data.model.*
import org.junit.Assert.*
import org.junit.Test

class OledLibraryLayoutTest {
    @Test fun gridAdaptsToActualSpaceAndCardPreference() {
        assertEquals(2, libraryColumns(330, false))
        assertEquals(4, libraryColumns(780, false))
        assertEquals(6, libraryColumns(780, true))
        assertEquals(3, libraryColumns(900, false, true))
        assertEquals(1, libraryColumns(330, false, true))
    }
    @Test fun customCatalogsDoNotDisappearIntoTrackerWatchlists() {
        val custom = WatchlistSourceItem.Catalog(CatalogConfig("custom", "Friday night", CatalogSourceType.MDBLIST))
        val tracker = WatchlistSourceItem.TrackerList(TrackerLibraryProvider.SIMKL, "watching", "Watching")
        val personal = WatchlistSourceItem.TrackerList(TrackerLibraryProvider.TRAKT, "12345", "Personal list")
        val sources = listOf(WatchlistSourceItem.MyWatchlist, custom, tracker, personal)
        assertEquals(listOf(custom, personal), librarySources(sources, LibrarySection.LISTS))
        assertEquals(listOf(WatchlistSourceItem.MyWatchlist, tracker), librarySources(sources, LibrarySection.WATCHLISTS))
        assertTrue(librarySources(sources, LibrarySection.SERVERS).isEmpty())
    }
}
