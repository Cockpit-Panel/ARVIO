package com.arflix.tv.ui.screens.watchlist

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryArtworkPrefetchTest {
    @Test fun prefetchIsBoundedToTwoOffscreenRows() {
        assertEquals((8..15).toList(), libraryArtworkPrefetchRange(7, 1755, 4).toList())
        assertEquals((12..19).toList(), libraryArtworkPrefetchRange(11, 1755, 4).toList())
        assertEquals((16..31).toList(), libraryArtworkPrefetchRange(15, 1755, 8).toList())
    }

    @Test fun shortAndEmptyLibrariesNeverReadOutsideTheirItems() {
        assertEquals(emptyList<Int>(), libraryArtworkPrefetchRange(0, 0, 4).toList())
        assertEquals(listOf(8, 9), libraryArtworkPrefetchRange(7, 10, 4).toList())
        assertEquals(emptyList<Int>(), libraryArtworkPrefetchRange(9, 10, 4).toList())
        assertEquals(emptyList<Int>(), libraryArtworkPrefetchRange(50, 10, 4).toList())
    }
}
