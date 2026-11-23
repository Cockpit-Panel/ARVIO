package com.arflix.tv.ui.screens.home

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

@OptIn(ExperimentalTestApi::class)
class NetflixVerticalNavigationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun downAndUpKeepTheIncomingRowAnchoredWhileTheRowsMorph() {
        val focus = fixture()
        fun top(index: Int) = compose.onNodeWithTag("netflix_row_vertical_$index", useUnmergedTree = true)
            .fetchSemanticsNode().positionInRoot.y
        val anchor = top(0)
        compose.mainClock.autoAdvance = false
        try {
            fun move(key: Key, index: Int) {
                compose.onRoot().performKeyInput { pressKey(key) }
                compose.mainClock.advanceTimeByFrame()
                val start = top(index)
                assertTrue("Changing rows must animate, not snap", abs(start - anchor) > 10f)
                val samples = mutableListOf(start)
                repeat(24) {
                    compose.mainClock.advanceTimeByFrame()
                    samples += top(index)
                }
                android.util.Log.i("NetflixVerticalTest", "Row $index: $samples")
                samples.zipWithNext().forEach { (before, after) ->
                    val delta = after - before
                    assertTrue("Vertical movement reversed: $samples", if (start > anchor) delta <= 3f else delta >= -3f)
                    assertTrue("Vertical movement jumped: $samples", abs(delta) <= abs(start - anchor) * 0.27f + 5f)
                }
                assertEquals("The focused row must settle on the same anchor", anchor, samples.last(), 3f)
                compose.runOnIdle { assertEquals(index, focus.currentRowIndex) }
            }
            (1..4).forEach { move(Key.DirectionDown, it) }
            (3 downTo 0).forEach { move(Key.DirectionUp, it) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    @Test fun rapidVerticalReversalAndReturnToBillboardStayAnimated() {
        val focus = fixture()
        val anchor = rowTop(0)
        compose.mainClock.autoAdvance = false
        try {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.mainClock.advanceTimeBy(80)
            val before = rowTop(0)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            compose.mainClock.advanceTimeByFrame()
            assertEquals("Rapid reversal must not snap to the endpoint", before, rowTop(0), abs(before - anchor) * 0.3f + 5f)
            compose.mainClock.advanceTimeBy(500)
            assertEquals(anchor, rowTop(0), 3f)
            compose.onRoot().performKeyInput { pressKey(Key.DirectionUp) }
            compose.mainClock.advanceTimeBy(80)
            val middle = compose.onNodeWithTag("netflix_billboard", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
            compose.mainClock.advanceTimeBy(500)
            val end = compose.onNodeWithTag("netflix_billboard", useUnmergedTree = true).fetchSemanticsNode().positionInRoot.y
            assertTrue("Returning to the billboard must scroll, not teleport", middle < end - 10f)
            compose.runOnIdle { assertTrue(focus.isHeroFocused) }
        } finally {
            compose.mainClock.autoAdvance = true
        }
    }

    private fun rowTop(index: Int) = compose.onNodeWithTag("netflix_row_vertical_$index", useUnmergedTree = true)
        .fetchSemanticsNode().positionInRoot.y

    private fun fixture(): HomeFocusState {
        val focus = HomeFocusState(0, 0).apply { userHasNavigated = true }
        val rows = (0..6).map { row -> Category("vertical_$row", "Movies $row",
            (1..8).map { MediaItem(row * 100 + it, "Title $row-$it", overview = "A two-line synopsis to keep row dimensions realistic during navigation.") }) }
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                HomeInputLayer(
                    categories = rows, cardLogoUrls = emptyMap(), focusState = focus,
                    limitRowsDuringStartup = false, suppressSelectUntilMs = 0L,
                    contentStartPadding = 24.dp, fastScrollThresholdMs = 80L,
                    usePosterCards = true, isContextMenuOpen = false, currentProfile = null,
                    onNavigateToDetails = { _, _, _, _ -> }, onNavigateToCollection = {},
                    onNavigateToSearch = {}, onNavigateToWatchlist = {}, onNavigateToTv = { _, _ -> },
                    onNavigateToSettings = {}, onSwitchProfile = {}, onExitApp = {},
                    onOpenContextMenu = { _, _ -> },
                    netflix = NetflixHomeContent(MediaItem(9001, "Billboard"), null, null, null, null, 0, 0f)
                )
            }
        }
        compose.waitForIdle()
        return focus
    }
}
