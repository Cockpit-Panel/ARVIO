package com.arflix.tv.ui.screens.search

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Card width and column count of the filtered discover grid, kept as plain functions so the
 * numbers can be checked without a screen.
 *
 * The grid used to copy `CollectionDetailsScreen` literally — 172 dp cards, five across a
 * 960 dp TV — which made it look like the rest of the app but contradicted the design that was
 * shown and approved: 105 dp poster cards, seven across. Twice as many titles per screen is the
 * whole reason to show a grid instead of rows, so the approved design wins here.
 *
 * The column count is derived from the available width rather than hard-coded per breakpoint.
 * Two hard-coded lists (one for widths, one for counts) drift apart the moment somebody edits
 * only one of them, and the Library screen already derives its columns the same way.
 */

/** Gap between two cards, and the padding on either side of the grid — both from the design. */
internal val DISCOVER_GRID_GAP: Dp = 18.dp
internal val DISCOVER_GRID_SIDE_PADDING: Dp = 24.dp

/**
 * Poster width on a TV or desktop screen [screenWidth] wide.
 *
 * 105 dp is the approved design at the 960 dp a 1080p and a 4K Android TV both report. The two
 * wider steps keep their old relation to each other, scaled by the same 105/172 the TV step
 * moved by, so a tablet or the web build gets the same denser grid rather than a second look.
 */
internal fun discoverGridCardWidth(screenWidth: Dp): Dp = when {
    screenWidth >= 2200.dp -> 120.dp
    screenWidth >= 1600.dp -> 112.dp
    else -> 105.dp
}

/**
 * How many cards of [cardWidth] fit across [screenWidth].
 *
 * n cards need n widths plus n-1 gaps plus the padding on both sides, so the count is
 * `(available + gap) / (cardWidth + gap)`. At least one column is always returned — a screen
 * narrower than a single card would otherwise produce a grid with no columns at all, which
 * `GridCells.Fixed` rejects.
 */
internal fun discoverGridColumns(
    screenWidth: Dp,
    cardWidth: Dp,
    gap: Dp = DISCOVER_GRID_GAP,
    sidePadding: Dp = DISCOVER_GRID_SIDE_PADDING
): Int {
    val available = screenWidth - sidePadding * 2
    if (available <= 0.dp) return 1
    val perCard = cardWidth + gap
    if (perCard <= 0.dp) return 1
    return (((available + gap) / perCard).toInt()).coerceAtLeast(1)
}
