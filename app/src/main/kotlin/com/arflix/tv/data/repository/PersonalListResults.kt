package com.arflix.tv.data.repository

import com.arflix.tv.data.api.MdbCounts
import com.arflix.tv.data.api.MdbListModifyResponse
import com.arflix.tv.data.api.TraktSyncCounts
import com.arflix.tv.data.api.TraktSyncResponse
import com.arflix.tv.data.model.MediaType

/** MDBList returns at most this many list titles per request. */
internal const val LIST_ITEMS_PAGE = 1000

/** A guard against a service that never says it is done; 50,000 titles is far past any list. */
internal const val LIST_ITEMS_MAX_PAGES = 50

/**
 * Reads every page of [pageSize] items, from offset 0 until a short page. Null when any page
 * fails: a list with a page missing would look complete while titles are left out.
 */
internal suspend fun <T> fetchAllPages(
    pageSize: Int,
    maxPages: Int,
    fetch: suspend (offset: Int) -> List<T>?
): List<T>? {
    val all = mutableListOf<T>()
    repeat(maxPages) {
        val page = fetch(all.size) ?: return null
        all += page
        if (page.size < pageSize) return all
    }
    return all
}

/*
 * A list edit is answered per title, and HTTP 200 alone does not mean the title changed: an
 * unknown id is reported under `not_found`. An addition already in the list (`existing`) is
 * what the user asked for, so it counts as done; a removal counts only when a title was removed.
 */

internal fun listChangeAccepted(response: MdbListModifyResponse?, mediaType: MediaType, add: Boolean): Boolean {
    response ?: return false
    fun MdbCounts?.count() = when (mediaType) {
        MediaType.MOVIE -> this?.movies ?: 0
        else -> this?.shows ?: 0
    }
    return if (add) response.added.count() + response.existing.count() > 0 else response.removed.count() > 0
}

internal fun listChangeAccepted(response: TraktSyncResponse?, mediaType: MediaType, add: Boolean): Boolean {
    response ?: return false
    fun TraktSyncCounts?.count() = when (mediaType) {
        MediaType.MOVIE -> this?.movies ?: 0
        else -> this?.shows ?: 0
    }
    return if (add) response.added.count() + response.existing.count() > 0 else response.deleted.count() > 0
}
