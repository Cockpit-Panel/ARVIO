package com.arflix.tv.ui.screens.watchlist

/** Shared with Search, where a fully visible selection must stay still. */
internal fun libraryRevealScrollDelta(itemStart: Int, itemSize: Int, viewportStart: Int, viewportEnd: Int): Int {
    val viewportSize = viewportEnd - viewportStart
    return when {
        viewportSize <= 0 -> 0
        itemSize > viewportSize -> itemStart - viewportStart
        itemStart < viewportStart -> itemStart - viewportStart
        itemStart + itemSize > viewportEnd -> itemStart + itemSize - viewportEnd
        else -> 0
    }
}

/** Anchor TV library rows without leaving a cropped previous row beneath the preview. */
internal fun libraryRowAnchorScrollDelta(itemStart: Int, itemSize: Int, viewportStart: Int, viewportEnd: Int): Int {
    if (viewportEnd <= viewportStart || itemSize <= 0) return 0
    return itemStart - viewportStart
}

internal fun libraryGridBottomPadding(viewportSize: Int, rowSize: Int, topPadding: Int, minimum: Int): Int {
    if (viewportSize <= 0 || rowSize <= 0) return minimum
    return maxOf(minimum, viewportSize - rowSize - topPadding)
}
