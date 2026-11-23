package com.arflix.tv.ui.screens.watchlist

import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Test

class LibraryPreviewTest {
    @Test fun nativePreviewIdentityIsNotJustItsTmdbLogoIdentity() {
        val item = MediaItem(101, "Server title", isHomeServer = true,
            homeServerSourceRef = "emby", homeServerItemId = "native-1")
        assertNotEquals(libraryPreviewKey(item), libraryPreviewKey(item.copy(homeServerSourceRef = "jellyfin")))
        val addon = MediaItem(-1, "Native", addonNativeId = "native", addonNativeAddonId = "addon-a")
        assertNotEquals(libraryPreviewKey(addon), libraryPreviewKey(addon.copy(addonNativeAddonId = "addon-b")))
    }

    @Test fun enrichmentPreservesSourceIdentityProgressAndArtwork() {
        val item = MediaItem(101, "Server title", overview = "Original", progress = 45,
            image = "provider-poster", backdrop = "provider-art", addedAt = 123L,
            isHomeServer = true, homeServerSourceRef = "emby", homeServerItemId = "native-1")
        val details = MediaItem(101, "Metadata title", overview = "Full description", duration = "2h",
            backdrop = "tmdb-art", imdbRating = "8.1", budget = 10_000_000L)
        val result = mergeLibraryPreview(item, details)
        assertEquals(item.id, result.id)
        assertEquals(item.title, result.title)
        assertEquals(item.homeServerItemId, result.homeServerItemId)
        assertEquals(item.homeServerSourceRef, result.homeServerSourceRef)
        assertEquals(45, result.progress)
        assertEquals(123L, result.addedAt)
        assertEquals("provider-art", result.backdrop)
        assertEquals("Full description", result.overview)
        assertEquals("8.1", result.imdbRating)
    }

    @Test fun missingAndWrongIdentityMetadataCannotEraseTheOriginal() {
        val item = MediaItem(-12, "Native item", overview = "Provider description")
        assertEquals(item, mergeLibraryPreview(item, null))
        assertEquals(item, mergeLibraryPreview(item, MediaItem(12, "Wrong title")))
        assertEquals(item, mergeLibraryPreview(item, item.copy(mediaType = MediaType.TV)))
        assertEquals(item.overview, mergeLibraryPreview(item, item.copy(overview = "")).overview)
    }
}
