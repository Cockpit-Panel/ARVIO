package com.arflix.tv.data.repository

import com.arflix.tv.data.api.StremioCatalogResponse
import com.arflix.tv.data.api.StremioMetaPreview
import com.arflix.tv.data.model.CatalogConfig
import com.arflix.tv.data.model.CatalogSourceType
import com.arflix.tv.data.model.MediaItem
import io.mockk.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AddonCatalogPagingTest {
    private val streams = mockk<StreamRepository>()
    private val media = spyk(MediaRepository(mockk(relaxed = true), mockk(), mockk(), mockk(), mockk(), streams, mockk()))
    private val catalog = CatalogConfig("addon-test", "Test", CatalogSourceType.ADDON,
        addonId = "test", addonCatalogType = "movie", addonCatalogId = "popular")

    private fun entries(range: IntRange) = range.map { StremioMetaPreview(id = "tmdb:$it", name = "Movie $it") }

    private fun details() {
        coEvery { media.getMovieDetails(any()) } answers { MediaItem(firstArg(), "Movie ${firstArg<Int>()}") }
    }

    @Test fun `large provider pages only hydrate requested slice and retain cursor`() = runBlocking {
        details()
        coEvery { streams.getAddonCatalogPage("test", "movie", "popular", any()) } answers {
            val offset = arg<Int>(3)
            StremioCatalogResponse(metas = entries((offset + 1)..100))
        }
        val first = media.loadCustomCatalogPage(catalog, 0, 8)
        assertEquals((1..8).toList(), first.items.map { it.id })
        assertEquals(8, first.nextOffset)
        assertTrue(first.hasMore)
        coVerify(exactly = 8) { media.getMovieDetails(any()) }
        val next = media.loadCustomCatalogPage(catalog, first.nextOffset!!, 20)
        assertEquals((9..28).toList(), next.items.map { it.id })
        assertEquals(28, next.nextOffset)
    }

    @Test fun `provider pages smaller than requested limit do not end browsing`() = runBlocking {
        details()
        coEvery { streams.getAddonCatalogPage("test", "movie", "popular", any()) } answers {
            val offset = arg<Int>(3)
            StremioCatalogResponse(metas = if (offset >= 30) emptyList() else entries((offset + 1)..minOf(offset + 5, 30)))
        }
        val page = media.loadCustomCatalogPage(catalog, 0, 20)
        assertEquals(15, page.items.size) // Bounded to three requests, not unbounded probing.
        assertEquals(15, page.nextOffset)
        assertTrue(page.hasMore)
        assertFalse(media.loadCustomCatalogPage(catalog, 30, 20).hasMore)
    }

    @Test fun `failed metadata does not rewind provider cursor`() = runBlocking {
        details()
        coEvery { streams.getAddonCatalogPage("test", "movie", "popular", 0) } returns StremioCatalogResponse(metas = entries(1..8))
        coEvery { media.getMovieDetails(3) } throws IOException("unavailable")
        val page = media.loadCustomCatalogPage(catalog, 0, 8)
        assertEquals(7, page.items.size)
        assertEquals(8, page.nextOffset)
        assertTrue(page.hasMore)
    }

    @Test fun `network failure is not an empty terminal page`() = runBlocking {
        coEvery { streams.getAddonCatalogPage(any(), any(), any(), any()) } throws IOException("offline")
        try {
            media.loadCustomCatalogPage(catalog, 0, 8)
            fail("expected retryable failure")
        } catch (_: IOException) { }
    }

    @Test fun `cancelled catalogue load stays cancelled`() = runBlocking {
        coEvery { streams.getAddonCatalogPage(any(), any(), any(), any()) } throws CancellationException("refresh")
        try {
            media.loadCustomCatalogPage(catalog, 0, 8)
            fail("expected cancellation")
        } catch (_: CancellationException) { }
    }

    @Test fun `standard path pagination precedes query fallback and retains configuration`() {
        val urls = buildCatalogRequestUrls("https://example.invalid/config", "series", "popular", 10, "lang=en")
        assertEquals("https://example.invalid/config/catalog/series/popular/skip=10.json?lang=en", urls.first())
        assertEquals("https://example.invalid/config/catalog/series/popular.json?lang=en&skip=10", urls.last())
    }
}
