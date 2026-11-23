package com.arflix.tv.ui.screens.home

import com.arflix.tv.data.model.KidsAgeLevel
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import com.arflix.tv.data.repository.KidsContentFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KidsPagingTest {

    private fun movie(id: Int) = MediaItem(id = id, title = "t$id", mediaType = MediaType.MOVIE)

    /** A 20-title page; [allowed] titles pass "up to 10", all others are rated 17. */
    private fun page(allowed: Set<Int>): Pair<List<MediaItem>, KidsContentFilter.State> {
        val items = (1..20).map(::movie)
        val ages = items.associate { KidsContentFilter.key("US", MediaType.MOVIE, it.id) to if (it.id in allowed) 7 else 17 }
        return items to KidsContentFilter.State(KidsAgeLevel.OLDER_KIDS, "US", ages)
    }

    @Test
    fun `the last visible title of a mostly excluded page reaches the page end`() {
        val (items, kids) = page(allowed = setOf(2, 5))
        // Two titles show; reaching the second must ask for the next page.
        assertEquals(items.lastIndex, KidsPaging.rawIndex(items, kids, displayedIndex = 1))
        // The first one, with another visible title after it, maps to its own position.
        assertEquals(1, KidsPaging.rawIndex(items, kids, displayedIndex = 0))
        assertTrue(KidsPaging.needsMore(items, kids, minVisible = 24))
    }

    @Test
    fun `a page with every title excluded still asks for more`() {
        val (items, kids) = page(allowed = emptySet())
        assertTrue(KidsPaging.needsMore(items, kids, minVisible = 1))
        assertEquals(items.lastIndex, KidsPaging.rawIndex(items, kids, displayedIndex = 0))
    }

    @Test
    fun `titles still waiting for a rating count as visible`() {
        val items = (1..20).map(::movie)
        val kids = KidsContentFilter.State(KidsAgeLevel.OLDER_KIDS, "US", emptyMap())
        assertFalse(KidsPaging.needsMore(items, kids, minVisible = 20))
    }

    @Test
    fun `other profiles page exactly as before`() {
        val items = (1..20).map(::movie)
        val regular = KidsContentFilter.State(null, "US", emptyMap())
        assertEquals(7, KidsPaging.rawIndex(items, regular, displayedIndex = 7))
        assertFalse(KidsPaging.needsMore(items, regular, minVisible = 100))
    }
}
