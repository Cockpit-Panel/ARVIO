package com.arflix.tv.ui.screens.tv.live

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.data.model.IptvNowNext
import com.arflix.tv.data.model.IptvProgram
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.io.File

class GuideHeaderDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun categoryHeadingFollowsSidebarWithoutMovingGuideRows() {
        val sidebarOpen = mutableStateOf(true)
        val now = 1_789_660_800_000L
        val channels = (1..12).map {
            IptvChannel(id = "header:$it", name = "Channel $it", group = "News",
                streamUrl = "https://example.test/live").enrichForFastStartup(it)
        }
        val guide = channels.associate { channel -> channel.id to IptvNowNext(
            now = IptvProgram("Current programme", startUtcMillis = now - 600_000,
                endUtcMillis = now + 3_600_000)) }
        compose.setContent {
            Box(Modifier.size(900.dp, 450.dp)) {
                EpgGrid(channels = channels, clockTickMillis = now, nowNext = guide,
                    selectedChannelId = null, focusSelectedChannelSignal = 0,
                    onChannelSelect = {}, favorites = emptySet(), categoryTitle = "Recently Watched",
                    sidebarOpen = sidebarOpen.value, onMoveLeftFromChannels = { sidebarOpen.value = true })
            }
        }
        compose.onNodeWithTag("iptv-guide-category-title").assertDoesNotExist()
        compose.onNodeWithText("Now").assertIsDisplayed()
        val firstRowTop = compose.onNodeWithTag("iptv-channel:header:1").fetchSemanticsNode().boundsInRoot.top
        screenshot("guide-header-sidebar-open.png")
        compose.runOnIdle { sidebarOpen.value = false }
        compose.onNodeWithTag("iptv-guide-category-title").assertTextEquals("Recently Watched").assertIsDisplayed()
        assertEquals(firstRowTop, compose.onNodeWithTag("iptv-channel:header:1").fetchSemanticsNode().boundsInRoot.top, 0.1f)
        screenshot("guide-header-sidebar-closed.png")
        compose.onNodeWithContentDescription("Groups").assertDoesNotExist()
        compose.runOnIdle { sidebarOpen.value = true }
        compose.onNodeWithTag("iptv-guide-category-title").assertDoesNotExist()
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
