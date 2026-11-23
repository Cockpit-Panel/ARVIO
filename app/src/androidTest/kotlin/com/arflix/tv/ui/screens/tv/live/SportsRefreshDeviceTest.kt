package com.arflix.tv.ui.screens.tv.live

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvProgram
import org.junit.Rule
import org.junit.Test
import org.junit.After
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.testTag
import org.junit.Assert.assertTrue

class SportsRefreshDeviceTest {
    @get:Rule val compose = createComposeRule()

    @OptIn(ExperimentalTestApi::class)
    @Test fun upcomingChannelsScrollWithRemoteWithoutStartingPlayback() {
        val now = System.currentTimeMillis()
        val channels = (0 until 52).map { index ->
            IptvChannel("future:$index", "Scheduled channel $index", "https://example.invalid/not-played", "Sports")
        }
        val programme = IptvProgram("Future fixture", startUtcMillis = now + 3600000, endUtcMillis = now + 7200000)
        val event = SportsGuideEvent("future-scroll", programme.title, GuideSport.FOOTBALL, programme, channels)
        var playCount = 0
        compose.setContent {
            SportsGuidePane(listOf(event), now, false, 0, {}, {}, { playCount++ }, Modifier.fillMaxSize())
        }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("sports-event-card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("sports-event-card").performClick()
        compose.waitUntil(5000) {
            compose.onAllNodes(isFocused() and hasText("Scheduled channel 0")).fetchSemanticsNodes().isNotEmpty()
        }
        repeat(20) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
        }
        compose.onNode(isFocused()).assert(hasText("Scheduled channel 20")).assertIsDisplayed()
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertTrue("Upcoming channels must not start playback", playCount == 0) }
        repeat(20) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
            compose.waitForIdle()
        }
        compose.onNode(isFocused()).assert(hasText("Scheduled channel 0")).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun closingSidebarDoesNotResetAnAlreadyFocusedCard() {
        val now = System.currentTimeMillis()
        val channel = IptvChannel("test:sidebar", "Sports", "https://example.invalid/not-played", "Sports")
        val events = (0..8).map { index ->
            val programme = IptvProgram("Fixture $index", startUtcMillis = now - 60000, endUtcMillis = now + 3600000)
            SportsGuideEvent("sidebar:$index", programme.title, GuideSport.FOOTBALL, programme, listOf(channel))
        }
        val sidebar = mutableStateOf(false)
        compose.setContent {
            SportsGuidePane(events, now, false, 1, { sidebar.value = false }, { sidebar.value = true }, {},
                Modifier.fillMaxSize(), sidebarOpen = sidebar.value)
        }
        compose.waitUntil(5000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionRight) }
        val before = compose.onNode(isFocused()).fetchSemanticsNode().id
        compose.runOnIdle { sidebar.value = true }
        compose.waitForIdle()
        compose.runOnIdle { sidebar.value = false }
        compose.waitForIdle()
        assertTrue("Drawer transitions retain the selected event", compose.onNode(isFocused()).fetchSemanticsNode().id == before)
    }

    @Test fun focusRemainsVisibleOnWhiteArtwork() {
        compose.setContent {
            Box(Modifier.size(160.dp, 90.dp).testTag("white-art")
                .liveFocusOutline(true, 4.dp).background(Color.White))
        }
        compose.waitForIdle()
        val pixels = compose.onNodeWithTag("white-art").captureToImage().toPixelMap()
        val y = pixels.height / 2
        assertTrue("White outer focus line", pixels[(pixels.width / 160f).toInt(), y].red > .9f)
        assertTrue("Dark contrasting inner line", pixels[(pixels.width * 4 / 160f).toInt(), y].red < .25f)
        assertTrue("Artwork is unchanged inside the border", pixels[(pixels.width * 12 / 160f).toInt(), y].red > .9f)
    }

    @Test fun upcomingEventShowsChannelCountAndScheduledChannelNames() {
        val now = System.currentTimeMillis()
        val channel = IptvChannel("test:future", "Broadcaster HD", "https://example.invalid/not-played", "Sports")
        val programme = IptvProgram("Upcoming fixture", startUtcMillis = now + 3600000, endUtcMillis = now + 7200000)
        val event = SportsGuideEvent("future", programme.title, GuideSport.BOXING, programme, listOf(channel))
        compose.setContent { SportsGuidePane(listOf(event), now, false, 0, {}, {}, {}, Modifier.fillMaxSize()) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("sports-event-card").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("1 channel").assertIsDisplayed()
        compose.onNodeWithTag("sports-event-card").performClick()
        compose.onNodeWithText("Scheduled channels").assertIsDisplayed()
        compose.onNodeWithText("Broadcaster HD").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    @Test fun remoteMovesAcrossOffscreenRowsDuringMetadataRefresh() {
        val now = System.currentTimeMillis()
        val channel = IptvChannel("test:remote", "Sports", "https://example.invalid/not-played", "Sports")
        val schedule = GuideSport.entries.mapIndexed { index, sport ->
            val programme = IptvProgram("${sport.title} event", startUtcMillis = now - 60000, endUtcMillis = now + 3600000)
            SportsGuideEvent("remote:$index", programme.title, sport, programme, listOf(channel))
        }
        val events = mutableStateOf(schedule)
        compose.setContent { SportsGuidePane(events.value, now, false, 1, {}, {}, {}, Modifier.fillMaxSize()) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("sports-event-card").fetchSemanticsNodes().isNotEmpty() }
        compose.waitUntil(5000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        repeat(GuideSport.entries.size - 1) { index ->
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionDown) }
            compose.waitForIdle()
            if (index % 3 == 0) {
                compose.runOnIdle { events.value = events.value.map { it.copy(prominence = index) } }
                compose.waitForIdle()
            }
        }
        compose.onNodeWithTag("sports-guide-list").assertIsDisplayed()
        compose.onNode(isFocused()).assert(hasText("Other sports event"))
        repeat(GuideSport.entries.size - 1) {
            compose.onNode(isFocused()).performKeyInput { pressKey(Key.DirectionUp) }
            compose.waitForIdle()
        }
        compose.onNode(isFocused()).assertExists()
    }

    @After fun captureUiState() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val folder = File(instrumentation.targetContext.getExternalFilesDir(null), "tv-overhaul").apply { mkdirs() }
        File(folder, "sports-refresh-test.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        compose.onAllNodes(isRoot(), useUnmergedTree = true).printToLog("SportsRefreshTest")
    }

    @Test fun completeScheduleRefreshAndOpenPickerSurviveEventRemoval() {
        val now = System.currentTimeMillis()
        val channel = IptvChannel("fixture:1", "Test channel", "https://example.invalid/not-played", "Sports")
        val schedule = (1..500).map { i ->
            val programme = IptvProgram("North $i vs South $i", startUtcMillis = now - 60000, endUtcMillis = now + 3600000)
            SportsGuideEvent("event:$i", programme.title, GuideSport.FOOTBALL, programme, listOf(channel))
        }
        val events = mutableStateOf(schedule)
        compose.setContent { SportsGuidePane(events.value, now, false, 0, {}, {}, {}, Modifier.fillMaxSize()) }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("sports-event-card").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithTag("sports-event-card")[0].performClick()
        compose.waitUntil(5000) { compose.onNodeWithContentDescription("Close").isDisplayed() }
        compose.onNodeWithText("Test channel").assertIsDisplayed()
        compose.runOnIdle { events.value = emptyList() }
        compose.waitForIdle()
        compose.onNodeWithText("This event is no longer in the available guide.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.runOnIdle { events.value = schedule.reversed() }
        compose.waitUntil(5000) { compose.onAllNodesWithTag("sports-event-card").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun scrollingManySportRowsDoesNotCrashOrLoseTheList() {
        val now = System.currentTimeMillis()
        val channel = IptvChannel("fixture:scroll", "Test sports channel", "https://example.invalid/not-played", "Sports")
        val events = GuideSport.entries.flatMapIndexed { sportIndex, sport ->
            (0 until 14).map { eventIndex ->
                val programme = IptvProgram(
                    title = "${sport.title} event $eventIndex",
                    startUtcMillis = now - 60_000,
                    endUtcMillis = now + 3_600_000,
                )
                // Deliberately repeat a provider-style ID to exercise the exact
                // duplicate-key case while the lazy list is being scrolled.
                SportsGuideEvent("scroll:$sportIndex:${eventIndex % 7}", programme.title, sport, programme, listOf(channel))
            }
        }
        compose.setContent { SportsGuidePane(events, now, false, 0, {}, {}, {}, Modifier.fillMaxSize()) }
        compose.waitUntil(5000) {
            compose.onAllNodesWithTag("sports-guide-list").fetchSemanticsNodes().isNotEmpty()
        }
        val list = compose.onNodeWithTag("sports-guide-list")
        repeat(20) {
            list.performTouchInput { swipeUp() }
            compose.waitForIdle()
        }
        list.assertIsDisplayed()
    }
}
