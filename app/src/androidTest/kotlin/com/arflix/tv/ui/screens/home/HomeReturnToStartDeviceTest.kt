package com.arflix.tv.ui.screens.home

import android.os.SystemClock
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
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

@OptIn(ExperimentalTestApi::class)
class HomeReturnToStartDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var focus: HomeFocusState
    private var exits = 0

    @Test fun heldBackResetsClassicHomeAndEveryVisitedRail() {
        show(false)
        holdBack()
        assertReset(false)
        for (row in 1..6) {
            compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
            compose.runOnIdle {
                assertEquals(row, focus.currentRowIndex)
                assertEquals(0, focus.currentItemIndex)
            }
            assertRailAtStart(row)
        }
    }

    @Test fun heldBackResetsNetflixHomeToItsBillboard() {
        show(true)
        holdBack()
        assertReset(true)
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle {
            assertEquals(0, focus.currentRowIndex)
            assertEquals(0, focus.currentItemIndex)
            assertFalse(focus.isHeroFocused)
        }
        assertRailAtStart(0)
    }

    @Test fun shortBackPreservesTheCurrentRowAndHorizontalPosition() {
        show(false)
        val down = SystemClock.uptimeMillis()
        dispatch(down, KeyEvent.ACTION_DOWN)
        dispatch(down, KeyEvent.ACTION_UP)
        compose.runOnIdle {
            assertTrue(focus.isSidebarFocused)
            assertEquals(5, focus.currentRowIndex)
            assertEquals(7, focus.currentItemIndex)
            assertEquals(0, focus.scrollResetRequest)
            assertEquals(0, exits)
        }
    }

    private fun holdBack() {
        val down = SystemClock.uptimeMillis()
        dispatch(down, KeyEvent.ACTION_DOWN)
        SystemClock.sleep(HOME_BACK_HOLD_MS + 200)
        // Compose controls coroutine delays; native key timestamps still use the device clock.
        compose.mainClock.advanceTimeBy(HOME_BACK_HOLD_MS + 32)
        compose.runOnIdle { assertEquals(1, focus.scrollResetRequest) }
        dispatch(down, KeyEvent.ACTION_DOWN, repeat = 3)
        dispatch(down, KeyEvent.ACTION_UP)
        compose.waitForIdle()
    }

    private fun dispatch(down: Long, action: Int, repeat: Int = 0) {
        compose.runOnUiThread {
            compose.activity.dispatchKeyEvent(KeyEvent(down, SystemClock.uptimeMillis(), action, KeyEvent.KEYCODE_BACK, repeat))
        }
    }

    private fun assertReset(billboard: Boolean) = compose.runOnIdle {
        assertEquals(0, focus.currentRowIndex)
        assertEquals(0, focus.currentItemIndex)
        assertFalse(focus.isSidebarFocused)
        assertEquals(billboard, focus.isHeroFocused)
        assertTrue(focus.rowItemIndicesByCategoryId.values.all { it == 0 })
        assertEquals(1, focus.scrollResetRequest)
        assertEquals(0, exits)
    }

    private fun assertRailAtStart(row: Int) {
        val rail = compose.onNodeWithTag("home_rail_row_$row", useUnmergedTree = true).fetchSemanticsNode()
        assertEquals(0f, rail.config[SemanticsProperties.HorizontalScrollAxisRange].value(), 0.1f)
    }

    private fun show(netflix: Boolean) {
        val rows = (0..7).map { row -> Category("row_$row", "Movies $row", (0..23).map { MediaItem(row * 100 + it + 1, "Title $row-$it") }) }
        focus = HomeFocusState(5, 7).apply {
            userHasNavigated = true
            rows.forEach { rowItemIndicesByCategoryId[it.id] = 7 }
        }
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                HomeInputLayer(
                    categories = rows, cardLogoUrls = emptyMap(), focusState = focus,
                    limitRowsDuringStartup = false, suppressSelectUntilMs = 0L,
                    contentStartPadding = 24.dp, fastScrollThresholdMs = 80L,
                    usePosterCards = false, isContextMenuOpen = false, currentProfile = null,
                    onNavigateToDetails = { _, _, _, _ -> }, onNavigateToCollection = {},
                    onNavigateToSearch = {}, onNavigateToWatchlist = {}, onNavigateToTv = { _, _ -> },
                    onNavigateToSettings = {}, onSwitchProfile = {}, onExitApp = { exits++ },
                    onOpenContextMenu = { _, _ -> },
                    netflix = if (netflix) NetflixHomeContent(MediaItem(99, "Featured"), null, null, null, null, 0, 0f) else null
                )
            }
        }
        compose.waitForIdle()
    }
}
