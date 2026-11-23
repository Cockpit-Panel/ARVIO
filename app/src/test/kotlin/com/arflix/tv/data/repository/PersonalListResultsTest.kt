package com.arflix.tv.data.repository

import com.arflix.tv.data.api.MdbCounts
import com.arflix.tv.data.api.MdbListModifyResponse
import com.arflix.tv.data.api.TraktSyncCounts
import com.arflix.tv.data.api.TraktSyncNotFound
import com.arflix.tv.data.api.TraktSyncResponse
import com.arflix.tv.data.model.MediaType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalListResultsTest {

    @Test
    fun `a list longer than one page is read to the end`() = runBlocking {
        val list = (1..2_500).toList()
        val offsets = mutableListOf<Int>()
        val all = fetchAllPages(LIST_ITEMS_PAGE, LIST_ITEMS_MAX_PAGES) { offset ->
            offsets += offset
            list.drop(offset).take(LIST_ITEMS_PAGE)
        }
        assertEquals(list, all)
        assertEquals(listOf(0, 1_000, 2_000), offsets)
    }

    @Test
    fun `a list of exactly one full page asks once more and stops on the empty page`() = runBlocking {
        val list = (1..1_000).toList()
        var calls = 0
        val all = fetchAllPages(LIST_ITEMS_PAGE, LIST_ITEMS_MAX_PAGES) { offset ->
            calls++
            list.drop(offset).take(LIST_ITEMS_PAGE)
        }
        assertEquals(list, all)
        assertEquals(2, calls)
    }

    @Test
    fun `a failed page fails the whole read instead of returning part of the list`() = runBlocking {
        val all = fetchAllPages(LIST_ITEMS_PAGE, LIST_ITEMS_MAX_PAGES) { offset ->
            if (offset == 0) List(LIST_ITEMS_PAGE) { it } else null
        }
        assertNull(all)
    }

    @Test
    fun `mdblist - an unknown title is not reported as added`() {
        val response = MdbListModifyResponse(added = MdbCounts(), existing = MdbCounts(), notFound = MdbCounts(movies = 1))
        assertFalse(listChangeAccepted(response, MediaType.MOVIE, add = true))
    }

    @Test
    fun `mdblist - adding a title already in the list counts as done`() {
        val response = MdbListModifyResponse(added = MdbCounts(), existing = MdbCounts(shows = 1), notFound = MdbCounts())
        assertTrue(listChangeAccepted(response, MediaType.TV, add = true))
        assertTrue(listChangeAccepted(MdbListModifyResponse(added = MdbCounts(movies = 1)), MediaType.MOVIE, add = true))
    }

    @Test
    fun `mdblist - a removal counts only when the title was removed`() {
        assertTrue(listChangeAccepted(MdbListModifyResponse(removed = MdbCounts(movies = 1)), MediaType.MOVIE, add = false))
        val notFound = MdbListModifyResponse(removed = MdbCounts(), notFound = MdbCounts(movies = 1))
        assertFalse(listChangeAccepted(notFound, MediaType.MOVIE, add = false))
        // A count for the other media type is not this title.
        assertFalse(listChangeAccepted(MdbListModifyResponse(removed = MdbCounts(shows = 1)), MediaType.MOVIE, add = false))
        assertFalse(listChangeAccepted(null as MdbListModifyResponse?, MediaType.MOVIE, add = false))
    }

    @Test
    fun `trakt - not_found is a failure, existing is a success`() {
        val notFound = TraktSyncResponse(
            added = TraktSyncCounts(),
            deleted = null,
            existing = TraktSyncCounts(),
            notFound = TraktSyncNotFound(movies = emptyList(), shows = null, episodes = null)
        )
        assertFalse(listChangeAccepted(notFound, MediaType.MOVIE, add = true))
        val existing = TraktSyncResponse(added = TraktSyncCounts(), deleted = null, existing = TraktSyncCounts(movies = 1), notFound = null)
        assertTrue(listChangeAccepted(existing, MediaType.MOVIE, add = true))
        val removed = TraktSyncResponse(added = null, deleted = TraktSyncCounts(shows = 1), existing = null, notFound = null)
        assertTrue(listChangeAccepted(removed, MediaType.TV, add = false))
        val nothingRemoved = TraktSyncResponse(added = null, deleted = TraktSyncCounts(), existing = null, notFound = null)
        assertFalse(listChangeAccepted(nothingRemoved, MediaType.TV, add = false))
    }
}
