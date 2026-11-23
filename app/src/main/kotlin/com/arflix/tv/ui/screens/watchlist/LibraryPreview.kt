package com.arflix.tv.ui.screens.watchlist

import com.arflix.tv.data.model.MediaItem

internal data class LibraryPreview(val item: MediaItem, val providerLogoUrl: String? = item.primaryNetworkLogo)

internal fun libraryPreviewKey(item: MediaItem): String = when {
    item.isHomeServer -> "home:${item.homeServerSourceRef}:${item.homeServerItemId}:${item.mediaType}"
    item.isAddonNative -> "addon:${item.addonNativeAddonId}:${item.addonNativeId}:${item.mediaType}"
    else -> watchlistLogoKey(item)
}

/** Enrichment must not replace server/addon identity, saved progress or ordering. */
internal fun mergeLibraryPreview(item: MediaItem, details: MediaItem?): MediaItem {
    if (details == null || details.id != item.id || details.mediaType != item.mediaType) return item
    return item.copy(
        overview = details.overview.ifBlank { item.overview },
        backdrop = item.backdrop?.takeIf(String::isNotBlank) ?: details.backdrop,
        image = item.image.ifBlank { details.image },
        year = details.year.ifBlank { item.year },
        duration = details.duration.ifBlank { item.duration },
        contentRating = details.contentRating ?: item.contentRating,
        imdbRating = details.imdbRating.ifBlank { item.imdbRating },
        tmdbRating = details.tmdbRating.ifBlank { item.tmdbRating },
        budget = details.budget ?: item.budget
    )
}
