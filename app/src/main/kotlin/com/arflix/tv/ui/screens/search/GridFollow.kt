package com.arflix.tv.ui.screens.search

import com.arflix.tv.ui.screens.watchlist.libraryRevealScrollDelta
import kotlin.math.abs

/** What the discover grid has to do so the focused card ends up fully in view. */
internal sealed class GridFollow {
    /** Already fully in view — move nothing. */
    object Stay : GridFollow()

    /** Reveal only the clipped part and leave the other cards where they are. */
    data class ScrollBy(val delta: Int) : GridFollow()

    /** The card is outside the window: jump to it, animated for short distances. */
    data class ScrollTo(val index: Int, val animate: Boolean) : GridFollow()
}

/**
 * The decision behind `FollowFocusedGridItem`, kept as a plain function on purpose.
 *
 * The case that matters is the middle one: a card that is merely clipped at an edge is still
 * listed in `visibleItemsInfo`, so a follower that only asks "is it visible?" never scrolls and
 * the selection sits on a card the user can barely see. That is invisible to any test that has
 * no screen — unless the decision itself can be called directly.
 *
 * [targetOffsetY] is `null` when the grid does not currently list the card as visible;
 * [viewportStart]/[viewportEnd] are the viewport edges with content padding already taken off.
 */
internal fun gridFollowFor(
    targetIndex: Int,
    firstVisibleIndex: Int,
    targetOffsetY: Int?,
    targetHeight: Int,
    viewportStart: Int,
    viewportEnd: Int,
    jumpThreshold: Int = GRID_FOLLOW_JUMP_THRESHOLD
): GridFollow {
    if (targetOffsetY == null) {
        return GridFollow.ScrollTo(targetIndex, animate = abs(targetIndex - firstVisibleIndex) <= jumpThreshold)
    }
    val delta = libraryRevealScrollDelta(targetOffsetY, targetHeight, viewportStart, viewportEnd)
    return if (delta == 0) GridFollow.Stay else GridFollow.ScrollBy(delta)
}

/** Beyond this many cards a jump is no longer animated — it would only blur past everything. */
internal const val GRID_FOLLOW_JUMP_THRESHOLD = 24
