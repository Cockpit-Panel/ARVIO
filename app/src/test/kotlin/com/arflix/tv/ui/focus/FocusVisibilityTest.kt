package com.arflix.tv.ui.focus

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusVisibilityTest {
    @Test fun onlyScrollsTheClippedPortion() {
        assertEquals(0f, focusRevealDelta(30f, 90f, 100f), 0f)
        assertEquals(25f, focusRevealDelta(65f, 125f, 100f), 0f)
        assertEquals(-20f, focusRevealDelta(-20f, 40f, 100f), 0f)
    }
    @Test fun oversizedRowsAlignAtTopAndUnmeasuredViewportsDoNotScroll() {
        assertEquals(15f, focusRevealDelta(15f, 200f, 100f), 0f)
        assertEquals(0f, focusRevealDelta(15f, 200f, 0f), 0f)
    }
}
