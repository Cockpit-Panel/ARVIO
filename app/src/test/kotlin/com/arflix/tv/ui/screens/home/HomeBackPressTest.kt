package com.arflix.tv.ui.screens.home

import org.junit.Assert.*
import org.junit.Test

class HomeBackPressTest {
    @Test fun shortPressIsOneNormalBackOnRelease() {
        val press = HomeBackPress()
        assertTrue(press.down(10))
        assertFalse(press.hold(100))
        assertEquals(HomeBackAction.BACK, press.up(100))
        assertEquals(HomeBackAction.NONE, press.up(101))
    }

    @Test fun heldBackResetsOnlyOnceDespiteRepeatsAndRelease() {
        val press = HomeBackPress()
        press.down(10)
        assertFalse(press.down(300))
        assertTrue(press.hold(610))
        assertFalse(press.hold(900))
        assertFalse(press.down(1_000))
        assertEquals(HomeBackAction.NONE, press.up(1_100))
    }

    @Test fun longReleaseWorksWithoutTimerOrRepeatEvents() {
        val press = HomeBackPress()
        press.down(10)
        assertEquals(HomeBackAction.RETURN_TO_START, press.up(710))
    }

    @Test fun cancellationCannotExitOrResetHome() {
        val press = HomeBackPress()
        press.down(10)
        press.cancel()
        assertFalse(press.hold(900))
        assertEquals(HomeBackAction.NONE, press.up(1_000))
        assertTrue(press.down(1_200))
        assertEquals(HomeBackAction.BACK, press.up(1_250))
    }
}
