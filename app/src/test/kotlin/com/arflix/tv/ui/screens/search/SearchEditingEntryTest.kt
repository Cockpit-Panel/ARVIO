package com.arflix.tv.ui.screens.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Device report 15.09.2026, seen twice and reproduced by the user: opening the search screen on
 * a TV left the ring in the search bar with no keyboard on screen, every direction key dead and
 * only BACK getting out. The screen had come up in typing mode, switched on by the very press
 * that opened it and handed on to the new screen.
 *
 * The guard against it was half built — the window was opened on entry and never asked about.
 * These tests ask it: the doors into typing mode are modelled here exactly as `SearchScreen`
 * uses them, so what is checked is the real decision, not a copy of it.
 */
class SearchEditingEntryTest {
    private val entryMs = 10_000L
    private val windowEndsMs = entryMs + SEARCH_SELECT_SUPPRESS_MS

    /** The screen's three doors into typing mode, with the state they set. */
    private class TypingMode(private val suppressUntilMs: Long) {
        var editing = false
            private set
        var focusRequests = 0
            private set

        fun select(nowMs: Long) {
            if (!startsSearchEditing(nowMs, suppressUntilMs)) return
            editing = true
            focusRequests++
        }
    }

    private fun screenEntered() = TypingMode(windowEndsMs)

    @Test fun aSelectHandedOnFromTheOpeningPressDoesNotStartTyping() {
        val screen = screenEntered()
        screen.select(entryMs + 1)
        assertFalse("the press that opened the screen must not open the keyboard", screen.editing)
        assertEquals(0, screen.focusRequests)
    }

    @Test fun theScreenIsNotInTypingModeRightAfterItIsEntered() {
        // What this stands for on screen: with typing mode off, the screen's own D-pad handler
        // owns the direction keys again — down reaches the filter row instead of a keyboard
        // that is not there.
        val screen = screenEntered()
        screen.select(entryMs)
        assertFalse(screen.editing)
    }

    @Test fun aSelectAfterTheWindowStartsTypingAsBefore() {
        val screen = screenEntered()
        screen.select(windowEndsMs)
        assertTrue("the user's own press must still open the keyboard", screen.editing)
        assertEquals(1, screen.focusRequests)
    }

    @Test fun aSecondSelectStillAsksForTheKeyboardAgain() {
        // The nonce exists so a second select re-requests focus and keyboard even though
        // `editing` is already true — that must survive the guard.
        val screen = screenEntered()
        screen.select(windowEndsMs + 500)
        screen.select(windowEndsMs + 900)
        assertEquals(2, screen.focusRequests)
    }

    @Test fun theWindowIsShortEnoughToBeUnnoticeable() {
        assertTrue("a guard longer than a fifth of a second swallows deliberate presses",
            SEARCH_SELECT_SUPPRESS_MS <= 200L)
    }

    @Test fun theLastMillisecondOfTheWindowIsStillSuppressed() {
        assertFalse(startsSearchEditing(windowEndsMs - 1, windowEndsMs))
        assertTrue(startsSearchEditing(windowEndsMs, windowEndsMs))
    }

    @Test fun heldOpeningSelectRemainsSuppressedAfterTheTimeWindow() {
        assertFalse(startsSearchEditing(windowEndsMs + 1000, windowEndsMs, repeatCount = 1))
        assertFalse(startsSearchEditing(windowEndsMs + 5000, windowEndsMs, repeatCount = 20))
    }

    @Test fun releasingAndPressingSelectAgainCanStartEditing() {
        assertFalse(startsSearchEditing(windowEndsMs + 1000, windowEndsMs, repeatCount = 3))
        assertTrue(startsSearchEditing(windowEndsMs + 1100, windowEndsMs, repeatCount = 0))
    }
}

/**
 * Device report 16.09.2026, TCL and phone alike: select opened the keyboard, dismissing the
 * keyboard left the ring on the search bar — and from there up and down were dead again, until
 * a SECOND back press. The keyboard had swallowed the first one to close itself, so the screen
 * still believed it was being typed into.
 *
 * The rule has to survive two devices that behave differently, which is what these pin down:
 * it must not end typing mode while the keyboard is still on its way in, and it must not end
 * it at all on a device that never reports a keyboard.
 */
class SearchEditingKeyboardTest {
    @Test fun typingSurvivesWhileTheKeyboardIsStillComingUp() {
        // Between the select press and the keyboard appearing there is no keyboard to see.
        // Reading that as "gone" would cancel typing mode before it ever started.
        assertTrue(searchEditingSurvivesKeyboard(imeVisible = false, keyboardWasSeen = false))
    }

    @Test fun typingSurvivesWhileTheKeyboardIsUp() {
        assertTrue(searchEditingSurvivesKeyboard(imeVisible = true, keyboardWasSeen = true))
    }

    @Test fun typingEndsOnceTheKeyboardThatWasThereIsGone() {
        assertFalse(searchEditingSurvivesKeyboard(imeVisible = false, keyboardWasSeen = true))
    }

    @Test fun aDeviceThatNeverReportsItsKeyboardIsLeftAlone() {
        // Android TV keyboards do not all report themselves. On such a device this must degrade
        // to the old behaviour rather than drop the user out of typing mode mid-word.
        repeat(5) { assertTrue(searchEditingSurvivesKeyboard(imeVisible = false, keyboardWasSeen = false)) }
    }

    @Test fun theWholeRoundTripEndsExactlyOnce() {
        var editing = true
        var seen = false
        // select pressed, keyboard on its way, keyboard up, keyboard dismissed
        for (visible in listOf(false, false, true, true, false)) {
            if (visible) seen = true
            if (!searchEditingSurvivesKeyboard(visible, seen)) editing = false
        }
        assertFalse("typing mode must be off once the keyboard is gone", editing)
    }
}
