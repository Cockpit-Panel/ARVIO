package com.arflix.tv.ui.screens.search

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device report 13.09.2026, TCL C7K: "the posters are far too big, that contradicts the picture
 * I sent" — the grid showed five 172 dp cards where the approved design shows seven 105 dp ones.
 *
 * These are the numbers behind that fix. A screen has no say in them, so they are checked here
 * rather than left to the next device round.
 */
class DiscoverGridLayoutTest {

    /** 960 dp is what a 1080p and a 4K Android TV both report — the screen the design was drawn for. */
    private val tvWidth = 960.dp

    @Test fun aTvScreenGetsTheSevenColumnsOfTheApprovedDesign() {
        val cardWidth = discoverGridCardWidth(tvWidth)
        assertEquals(105.dp, cardWidth)
        assertEquals(7, discoverGridColumns(tvWidth, cardWidth))
    }

    @Test fun sevenColumnsActuallyFitOnTheScreenTheyAreMeantFor() {
        val cardWidth = discoverGridCardWidth(tvWidth)
        val columns = discoverGridColumns(tvWidth, cardWidth)
        val used = cardWidth * columns +
            DISCOVER_GRID_GAP * (columns - 1) +
            DISCOVER_GRID_SIDE_PADDING * 2
        assertTrue("a row of $columns cards is $used wide and must not exceed $tvWidth", used <= tvWidth)
    }

    /** The old hard-coded pair overflowed by 20 dp: 5 * 172 + 4 * 18 + 48 = 980 on a 960 dp screen. */
    @Test fun theGridIsDenserThanTheCollectionScreenItWasCopiedFrom() {
        val columns = discoverGridColumns(tvWidth, discoverGridCardWidth(tvWidth))
        assertTrue("the point of the grid is more titles per screen, got $columns", columns > 5)
    }

    @Test fun widerScreensGrowTheCardButKeepTheDenserGrid() {
        assertEquals(112.dp, discoverGridCardWidth(1600.dp))
        assertEquals(120.dp, discoverGridCardWidth(2200.dp))
        assertTrue(discoverGridColumns(1600.dp, discoverGridCardWidth(1600.dp)) > 7)
        assertTrue(discoverGridColumns(2200.dp, discoverGridCardWidth(2200.dp)) > 8)
    }

    @Test fun theColumnCountNeverDropsBelowOne() {
        assertEquals(1, discoverGridColumns(40.dp, 105.dp))
        assertEquals(1, discoverGridColumns(0.dp, 105.dp))
    }

    /** Every step has to stay inside its own screen, not just the TV one. */
    @Test fun noBreakpointOverflowsItsScreen() {
        listOf(600.dp, 960.dp, 1600.dp, 2200.dp, 3840.dp).forEach { width ->
            val cardWidth = discoverGridCardWidth(width)
            val columns = discoverGridColumns(width, cardWidth)
            val used = cardWidth * columns +
                DISCOVER_GRID_GAP * (columns - 1) +
                DISCOVER_GRID_SIDE_PADDING * 2
            assertTrue("$columns cards of $cardWidth need $used but the screen is $width", used <= width)
        }
    }
}
