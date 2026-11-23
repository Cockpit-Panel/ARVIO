package com.arflix.tv.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrailerPlaybackLifecycleTest {
    @Test fun readyWhileBackgroundedCuesUntilReturning() {
        val state = TrailerPlaybackLifecycle()
        state.onForeground()
        state.onBackground()
        assertFalse(state.requestStart())
        assertTrue(state.onForeground())
    }

    @Test fun initialResumeDoesNotStartAnUnreadyPlayer() {
        assertFalse(TrailerPlaybackLifecycle().onForeground())
    }

    @Test fun explicitPauseSurvivesLeavingAndReturning() {
        val state = TrailerPlaybackLifecycle()
        state.onForeground()
        state.onPlaying()
        state.onPaused()
        state.onBackground()
        state.onPaused()
        assertFalse(state.onForeground())
    }

    @Test fun lifecyclePauseResumesOnlyOnce() {
        val state = TrailerPlaybackLifecycle()
        state.onForeground()
        state.onPlaying()
        state.onBackground()
        state.onPaused()
        state.onBackground()
        assertTrue(state.onForeground())
        assertFalse(state.onForeground())
    }

    @Test fun finishedVideoDoesNotRestartWhenReturning() {
        val state = TrailerPlaybackLifecycle()
        state.onForeground()
        state.onPlaying()
        state.onEnded()
        state.onBackground()
        assertFalse(state.onForeground())
    }
}
