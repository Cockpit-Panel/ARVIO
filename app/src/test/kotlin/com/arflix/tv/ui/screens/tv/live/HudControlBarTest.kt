package com.arflix.tv.ui.screens.tv.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HudControlBarTest {
    private fun place(width: Int, start: Int, center: Int, end: Int, endHeight: Int = 40) = hudControlBarPlacement(
        width = width, minHeight = 56,
        startWidth = start, startHeight = 20,
        centerWidth = center, centerHeight = 48,
        endWidth = end, endHeight = endHeight,
        gap = 20, lineGap = 8,
    )

    @Test fun controlsStayExactlyCentredWhenThereIsRoom() {
        val placement = place(width = 1000, start = 100, center = 300, end = 200)
        assertTrue(placement.oneLine)
        assertEquals(350, placement.centerX)
        assertEquals(56, placement.height)
    }

    @Test fun wideActionsPushTheControlsAsideOnTheSameLine() {
        val placement = place(width = 1000, start = 100, center = 300, end = 450)
        assertTrue(placement.oneLine)
        assertEquals(230, placement.centerX)
        assertTrue(placement.centerX + 300 + 20 <= 1000 - 450)
    }

    @Test fun aWideStartPushesTheControlsTheOtherWay() {
        assertEquals(470, place(width = 1000, start = 450, center = 300, end = 100).centerX)
    }

    @Test fun actionsThatCannotShareTheLineMoveAboveTheControls() {
        // Narrow screen with Audio, Subtitles, Choose source, Guide and catch-up LIVE all shown.
        val placement = place(width = 700, start = 60, center = 320, end = 520)
        assertFalse(placement.oneLine)
        assertEquals(0, placement.endY)
        // The lower line starts below the actions, so their rectangles cannot intersect.
        assertTrue(placement.centerY >= 40 + 8)
        assertTrue(placement.startY >= 40 + 8)
        assertEquals(40 + 8 + 48, placement.height)
        // Time and controls still do not overlap each other.
        assertTrue(placement.centerX >= 60 + 20)
        assertTrue(placement.centerX + 320 <= 700)
    }

    @Test fun wrappedActionLinesPushTheControlsFurtherDown() {
        val placement = place(width = 400, start = 60, center = 300, end = 400, endHeight = 88)
        assertFalse(placement.oneLine)
        assertTrue(placement.centerY >= 88 + 8)
    }
}
