package com.arflix.tv.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Device report 13.09.2026: with two rows on screen and the focus at the bottom, pressing up a
 * second time moved the selection but not the grid — "it does nothing" — and only the third
 * press revealed the row above. Cause: the row above was clipped, not gone, so it was still
 * listed as visible and the follower stood still.
 *
 * The numbers below are one TV screen: 1080 px tall, 63 px content padding top and bottom
 * (24 dp at 2.625), card rows 420 px high. Viewport edges are handed in with the padding
 * already taken off, exactly as `FollowFocusedGridItem` does it.
 */
class GridFollowTest {
    private val viewportStart = 0
    private val viewportEnd = 954
    private val rowHeight = 420

    private fun follow(offsetY: Int?, firstVisible: Int = 0, target: Int = 5) = gridFollowFor(
        targetIndex = target,
        firstVisibleIndex = firstVisible,
        targetOffsetY = offsetY,
        targetHeight = rowHeight,
        viewportStart = viewportStart,
        viewportEnd = viewportEnd
    )

    @Test fun aFullyVisibleCardMovesNothing() {
        assertEquals(GridFollow.Stay, follow(offsetY = 100))
    }

    @Test fun aCardClippedAtTheTopScrollsUpByTheMissingPart() {
        // The row above is half cut off: 180 px of it sit above the viewport start.
        assertEquals(GridFollow.ScrollBy(-180), follow(offsetY = -180))
    }

    @Test fun aCardTouchingTheTopEdgeIsAlreadyEnough() {
        assertEquals(GridFollow.Stay, follow(offsetY = 0))
    }

    @Test fun aCardClippedAtTheBottomScrollsDownByTheMissingPart() {
        // 640 + 420 = 1060, that is 106 px past the lower viewport edge.
        assertEquals(GridFollow.ScrollBy(106), follow(offsetY = 640))
    }

    @Test fun aCardOutsideTheWindowJumpsToTheIndex() {
        assertEquals(GridFollow.ScrollTo(30, animate = true), follow(offsetY = null, firstVisible = 25, target = 30))
    }

    @Test fun aLongJumpIsNotAnimated() {
        assertEquals(GridFollow.ScrollTo(400, animate = false), follow(offsetY = null, firstVisible = 0, target = 400))
    }

    @Test fun aRowTallerThanTheViewportIsAlignedToItsTop() {
        val follow = gridFollowFor(
            targetIndex = 2, firstVisibleIndex = 0, targetOffsetY = -40,
            targetHeight = 1200, viewportStart = viewportStart, viewportEnd = viewportEnd
        )
        assertEquals(GridFollow.ScrollBy(-40), follow)
    }
}
