package com.arflix.tv.ui.screens.tv.live

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import com.arflix.tv.ui.skin.arvioFocusable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvProgram
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class LiveFocusAppearanceDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharedFocusAnimationDoesNotRecomposeEveryFrame() {
        val focused = mutableStateOf(false)
        var compositions = 0
        compose.setContent {
            val modifier = Modifier.arvioFocusable(
                enableSystemFocus = false, useSystemFocusForVisuals = false,
                isFocusedOverride = focused.value, shape = RoundedCornerShape(6.dp),
                focusedScale = 1.04f, pressedScale = 1f, showRestBorder = true,
                outlineWidth = 2.dp, glowWidth = 0.dp, glowAlpha = 0f, outlineColor = Color.White,
            )
            androidx.compose.runtime.SideEffect { compositions++ }
            Box(Modifier.size(160.dp, 90.dp).then(modifier).background(Color.Black))
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { focused.value = true }
        compose.mainClock.advanceTimeByFrame()
        val atFocus = compositions
        repeat(12) { compose.mainClock.advanceTimeByFrame() }
        assertEquals("Animation must update drawing, not composition", atFocus, compositions)
        compose.mainClock.autoAdvance = true
    }

    @Test fun sharedCardClearsHighlightOnFirstUnfocusedFrame() {
        assertFocusClearsImmediately(false)
    }

    @Test fun liveOutlineClearsHighlightOnFirstUnfocusedFrame() {
        assertFocusClearsImmediately(true)
    }

    private fun assertFocusClearsImmediately(liveOutline: Boolean) {
        val focused = mutableStateOf(false)
        compose.setContent {
            val focusModifier = if (liveOutline) Modifier.liveFocusOutline(focused.value, 6.dp)
            else Modifier.arvioFocusable(
                enableSystemFocus = false, useSystemFocusForVisuals = false,
                isFocusedOverride = focused.value, shape = RoundedCornerShape(6.dp),
                focusedScale = 1f, pressedScale = 1f, outlineWidth = 2.dp,
                glowWidth = 0.dp, glowAlpha = 0f, outlineColor = Color.White,
            )
            Box(Modifier.size(160.dp, 90.dp).testTag("focus-reset").then(focusModifier).background(Color.Black))
        }
        val baseline = compose.onNodeWithTag("focus-reset").captureToImage().toPixelMap()
        compose.runOnIdle { focused.value = true }
        compose.mainClock.advanceTimeBy(200)
        val highlighted = compose.onNodeWithTag("focus-reset").captureToImage().toPixelMap()
        var highlightedPixels = 0
        for (y in 0 until highlighted.height) for (x in 0 until highlighted.width) {
            if (highlighted[x, y].red > baseline[x, y].red + .5f) highlightedPixels++
        }
        assertTrue("Focused ring is visible", highlightedPixels > 20)
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { focused.value = false }
        compose.mainClock.advanceTimeByFrame()
        val cleared = compose.onNodeWithTag("focus-reset").captureToImage().toPixelMap()
        var differing = 0
        for (y in 0 until cleared.height) for (x in 0 until cleared.width) {
            if (kotlin.math.abs(cleared[x, y].red - baseline[x, y].red) > .05f) differing++
        }
        assertEquals("No departing highlight may remain", 0, differing)
        compose.mainClock.autoAdvance = true
    }

    @Test fun focusedChannelHasWhiteFillDarkTextAndStableSize() {
        val focused = mutableStateOf(true)
        val channel = IptvChannel("focus:test", "Example channel HD", "https://example.invalid/live", "News")
            .enrichForFastStartup(1)
        compose.setContent {
            Box(Modifier.width(300.dp)) {
                ChannelRow(channel, 0L, null, false, true, onClick = {},
                    forceFocused = focused.value, rowHeight = 56.dp,
                    modifier = Modifier.testTag("channel"))
            }
        }
        val first = compose.onNodeWithTag("channel").captureToImage()
        assertWhiteWithDarkContent("channel")
        compose.runOnIdle { focused.value = false }
        val second = compose.onNodeWithTag("channel").captureToImage()
        assertEquals(first.width, second.width)
        assertEquals(first.height, second.height)
        val pixels = second.toPixelMap()
        assertTrue("Unfocused background stays dark", pixels[pixels.width / 2, pixels.height / 6].red < .4f)
    }

    @Test fun focusedProgrammeHasWhiteFillAndDarkText() {
        val requester = FocusRequester()
        compose.setContent {
            ProgramCell(IptvProgram("Example programme", startUtcMillis = 0, endUtcMillis = 3600000),
                60000, 300.dp, true, false, true, onClick = {},
                focusRequester = requester, rowHeight = 56.dp, modifier = Modifier.testTag("programme"))
        }
        compose.runOnIdle { requester.requestFocus() }
        assertWhiteWithDarkContent("programme")
    }

    private fun assertWhiteWithDarkContent(tag: String) {
        val pixels = compose.onNodeWithTag(tag).captureToImage().toPixelMap()
        var white = 0
        var dark = 0
        var total = 0
        for (y in pixels.height / 20 until pixels.height * 19 / 20) {
            for (x in pixels.width / 20 until pixels.width * 19 / 20) {
                val color = pixels[x, y]
                if (color.red > .95f && color.green > .95f && color.blue > .95f) white++
                if (color.red < .2f && color.green < .2f && color.blue < .2f) dark++
                total++
            }
        }
        assertTrue("Focused $tag has solid white background", white > total * .65)
        assertTrue("Focused $tag has dark readable content", dark > total * .005)
    }
}
