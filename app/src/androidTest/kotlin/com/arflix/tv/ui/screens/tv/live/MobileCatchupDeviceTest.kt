package com.arflix.tv.ui.screens.tv.live

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MobileCatchupDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val now = 10_000_000L
    private val past = IptvProgram(title = "Past programme", startUtcMillis = now - 3_600_000L,
        endUtcMillis = now - 1_800_000L, catchupAvailable = true)

    @Test fun pastProgrammeRespondsToRealTouchWithoutRemoteFocus() {
        var selected: IptvProgram? = null
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.PHONE) {
                ProgramCell(program = past, clockTickMillis = now, width = 240.dp,
                    isNow = false, isPast = true, isFocusTarget = false, focusable = false,
                    isCatchupSupported = true, rowHeight = 60.dp,
                    onClick = { selected = epgProgramActionTarget(past, true, false, true) })
            }
        }
        compose.onNodeWithText("Past programme").performTouchInput { click() }
        compose.runOnIdle { assertEquals(past, selected) }
    }

    @Test fun fullscreenMobileGuideCanSelectAiredProgramme() {
        var selected: IptvProgram? = null
        val channel = IptvChannel(id = "fixture", name = "Archive channel", group = "Test",
            streamUrl = "https://example.invalid/live.ts", catchupDays = 3).enrich(1)
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.PHONE) {
                FullscreenGuideOverlay(visible = true, channel = channel,
                    guide = IptvNowNext(recent = listOf(past)), selectedProgram = null,
                    clockTickMillis = now, isTouchDevice = true, onDismiss = {},
                    onProgramSelect = { selected = it })
            }
        }
        compose.onNodeWithText("Past programme").performScrollTo().performTouchInput { click() }
        compose.runOnIdle { assertEquals(past, selected) }
    }

    @Test fun touchScrubberCommitsOneSeekWhenFingerIsReleased() {
        val seeks = mutableListOf<Long>()
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.PHONE) {
                Box(Modifier.width(320.dp)) {
                    HudSeekBar(progress = .2f, positionMs = 20_000L, durationMs = 100_000L,
                        onSeekToPosition = { seeks.add(it) }, onOpenQuickZap = null,
                        modifier = Modifier.testTag("scrubber"))
                }
            }
        }
        compose.onNodeWithTag("scrubber").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * .2f, height / 2f),
                androidx.compose.ui.geometry.Offset(width * .8f, height / 2f), 600)
        }
        compose.runOnIdle {
            assertEquals(1, seeks.size)
            assertTrue(seeks.single() in 70_000L..90_000L)
        }
    }

    @Test fun portraitCatchupControlsDoNotOverlapEachOther() {
        val clicks = mutableListOf<String>()
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.PHONE) {
                Box(Modifier.fillMaxSize()) {
                    FullscreenHud(channel = null, nowNext = IptvNowNext(now = past), pokeSignal = 0,
                        isCatchupMode = true, playbackPositionMs = 120_000L, playbackDurationMs = 1_800_000L,
                        onBackClick = {}, onRewindClick = { clicks.add("rewind") },
                        onFastForwardClick = { clicks.add("forward") }, onReplayClick = { clicks.add("restart") },
                        onGoLiveClick = {}, onGuideClick = {}, onSeekToPosition = {})
                }
            }
        }
        listOf(com.arflix.tv.R.string.live_cd_rewind, com.arflix.tv.R.string.live_cd_fast_forward,
            com.arflix.tv.R.string.live_cd_replay).forEach { label ->
            compose.onNodeWithContentDescription(context.getString(label)).performTouchInput { click() }
        }
        compose.runOnIdle { assertEquals(listOf("rewind", "forward", "restart"), clicks) }
    }

    @Test fun arrivingArchiveHistoryDoesNotResetMobileBrowsingPosition() {
        fun programme(index: Int) = past.copy(title = "Archive $index",
            startUtcMillis = now - (40 - index) * 1_800_000L,
            endUtcMillis = now - (39 - index) * 1_800_000L)
        val guide = mutableStateOf(IptvNowNext(recent = (20..38).map(::programme)))
        val channel = IptvChannel(id = "fixture", name = "Archive channel", group = "Test",
            streamUrl = "https://example.invalid/live.ts", catchupDays = 3).enrich(1)
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.PHONE) {
                FullscreenGuideOverlay(visible = true, channel = channel, guide = guide.value,
                    selectedProgram = null, clockTickMillis = now, isTouchDevice = true,
                    onDismiss = {}, onProgramSelect = {})
            }
        }
        val item = compose.onNodeWithText("Archive 25")
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Archive 25"))
        item.assertIsDisplayed()
        val before = item.fetchSemanticsNode().boundsInRoot.top
        compose.runOnIdle { guide.value = IptvNowNext(recent = (0..38).map(::programme)) }
        item.assertIsDisplayed()
        assertEquals(before, item.fetchSemanticsNode().boundsInRoot.top, 1f)
    }
}
