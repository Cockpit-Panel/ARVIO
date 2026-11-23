package com.arflix.tv.ui.screens.home

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeWatchlistDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mobileWatchlistCardOpensWithTouch() {
        val rows = withHomeWatchlistRow(
            listOf(Category("continue_watching", "Continue Watching", listOf(MediaItem(1, "Continue title")))),
            listOf(MediaItem(101, "Saved mobile title")), true
        )
        var opened = -1
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.PHONE) {
                Box(Modifier.width(412.dp).fillMaxHeight()) {
                    HomeInputLayer(
                        categories = rows, cardLogoUrls = emptyMap(), focusState = HomeFocusState(),
                        limitRowsDuringStartup = false, suppressSelectUntilMs = 0L,
                        contentStartPadding = 16.dp, fastScrollThresholdMs = 80L,
                        usePosterCards = false, isContextMenuOpen = false, isMobile = true, currentProfile = null,
                        onNavigateToDetails = { _, id, _, _ -> opened = id },
                        onNavigateToCollection = {}, onNavigateToSearch = {}, onNavigateToWatchlist = {},
                        onNavigateToTv = { _, _ -> }, onNavigateToSettings = {}, onSwitchProfile = {},
                        onExitApp = {}, onOpenContextMenu = { _, _ -> }
                    )
                }
            }
        }
        compose.onNode(
            hasScrollToIndexAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        ).performScrollToNode(hasText("Watchlist"))
        val heading = compose.onNodeWithText("Watchlist").fetchSemanticsNode().boundsInRoot
        compose.onNode(hasText("Saved mobile title") and hasClickAction() and SemanticsMatcher("card below watchlist heading") {
            it.boundsInRoot.top >= heading.bottom
        }).performClick()
        compose.runOnIdle { assertEquals(101, opened) }
    }

    @Test fun remoteOpensWatchlistTitlesAndSurvivesRemovingTheRow() {
        val enabled = mutableStateOf(true)
        val saved = (1..20).map { MediaItem(100 + it, "Saved title $it") }
        val base = listOf(
            Category("continue_watching", "Continue Watching", listOf(MediaItem(1, "Continue title"))),
            Category("trending_movies", "Trending Movies", listOf(MediaItem(2, "Trending title")))
        )
        val focus = HomeFocusState().apply { userHasNavigated = true }
        var opened = -1
        var openedLibrary = false
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                HomeInputLayer(
                    categories = withHomeWatchlistRow(base, saved, enabled.value),
                    cardLogoUrls = emptyMap(), focusState = focus,
                    limitRowsDuringStartup = false, suppressSelectUntilMs = 0L,
                    contentStartPadding = 24.dp, fastScrollThresholdMs = 80L,
                    usePosterCards = false, isContextMenuOpen = false, currentProfile = null,
                    onNavigateToDetails = { _, id, _, _ -> opened = id },
                    onNavigateToCategory = { assertEquals(HOME_WATCHLIST_ROW_ID, it); openedLibrary = true },
                    onNavigateToCollection = {}, onNavigateToSearch = {}, onNavigateToWatchlist = {},
                    onNavigateToTv = { _, _ -> }, onNavigateToSettings = {}, onSwitchProfile = {},
                    onExitApp = {}, onOpenContextMenu = { _, _ -> }
                )
            }
        }
        compose.waitForIdle()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) }
        compose.runOnIdle { assertEquals(1, focus.currentRowIndex) }
        compose.onNodeWithTag("home_row_$HOME_WATCHLIST_ROW_ID", useUnmergedTree = true).assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(101, opened) }
        repeat(saved.size) { compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) } }
        compose.runOnIdle { assertEquals(saved.size, focus.currentItemIndex) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertTrue(openedLibrary); enabled.value = false }
        compose.waitForIdle()
        compose.onNodeWithTag("home_row_$HOME_WATCHLIST_ROW_ID", useUnmergedTree = true).assertDoesNotExist()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertTrue(opened == 1 || opened == 2) }
    }
}
