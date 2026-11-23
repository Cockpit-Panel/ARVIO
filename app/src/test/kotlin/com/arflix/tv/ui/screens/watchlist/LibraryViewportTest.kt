package com.arflix.tv.ui.screens.watchlist

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryViewportTest {
    @Test fun visibleRowsDoNotMove() {
        assertEquals(0, libraryRevealScrollDelta(60, 44, 0, 300))
        assertEquals(0, libraryRevealScrollDelta(256, 44, 0, 300))
    }
    @Test fun clippedBottomMovesOnlyEnoughToRevealTheRow() {
        assertEquals(24, libraryRevealScrollDelta(280, 44, 0, 300))
    }
    @Test fun clippedTopMovesBackWithoutAligningEverythingToTheTop() {
        assertEquals(-20, libraryRevealScrollDelta(-20, 44, 0, 300))
    }
    @Test fun contentPaddingIsRespected() {
        assertEquals(-2, libraryRevealScrollDelta(0, 44, 2, 298))
        assertEquals(2, libraryRevealScrollDelta(256, 44, 2, 298))
    }
    @Test fun oversizedItemUsesItsLeadingEdgeAndEmptyViewportDoesNotScroll() {
        assertEquals(40, libraryRevealScrollDelta(40, 320, 0, 300))
        assertEquals(0, libraryRevealScrollDelta(40, 44, 0, 0))
    }
    @Test fun tvLibraryRowsAlignToThePreviewEvenWhenAlreadyVisible() {
        assertEquals(60, libraryRowAnchorScrollDelta(60, 44, 0, 300))
        assertEquals(256, libraryRowAnchorScrollDelta(256, 44, 0, 300))
        assertEquals(0, libraryRowAnchorScrollDelta(0, 44, 0, 300))
    }
    @Test fun tvLibraryScrollsWholeRowsInBothDirections() {
        assertEquals(280, libraryRowAnchorScrollDelta(280, 44, 0, 300))
        assertEquals(-20, libraryRowAnchorScrollDelta(-20, 44, 0, 300))
        assertEquals(254, libraryRowAnchorScrollDelta(256, 44, 2, 298))
    }
    @Test fun tvLibraryHandlesOversizedCardsAndUnmeasuredViewport() {
        assertEquals(40, libraryRowAnchorScrollDelta(40, 320, 0, 300))
        assertEquals(0, libraryRowAnchorScrollDelta(40, 44, 0, 0))
        assertEquals(0, libraryRowAnchorScrollDelta(40, 0, 0, 300))
    }
    @Test fun lastRowHasEnoughSpaceToReachTheSameAnchor() {
        assertEquals(112, libraryGridBottomPadding(300, 180, 8, 16))
        assertEquals(16, libraryGridBottomPadding(180, 180, 8, 16))
        assertEquals(16, libraryGridBottomPadding(0, 180, 8, 16))
        assertEquals(16, libraryGridBottomPadding(300, 0, 8, 16))
    }
}
