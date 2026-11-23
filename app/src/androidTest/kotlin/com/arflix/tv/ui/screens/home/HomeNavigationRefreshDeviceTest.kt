package com.arflix.tv.ui.screens.home

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.arflix.tv.data.model.Category
import com.arflix.tv.data.model.MediaItem
import com.arflix.tv.data.model.MediaType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class HomeNavigationRefreshDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remoteSelectionSurvivesBackgroundReordering() {
        val items = (1..40).map { MediaItem(it, "Title $it", mediaType = MediaType.MOVIE) }
        val categories = mutableStateOf(listOf(Category("trending_movies", "Movies", items)))
        val focus = HomeFocusState().apply { userHasNavigated = true }
        var opened = -1
        compose.setContent {
            HomeInputLayer(
                categories = categories.value, cardLogoUrls = emptyMap(), focusState = focus,
                limitRowsDuringStartup = false, suppressSelectUntilMs = 0L,
                contentStartPadding = 24.dp, fastScrollThresholdMs = 80L,
                usePosterCards = false, isContextMenuOpen = false, currentProfile = null,
                onNavigateToDetails = { _, id, _, _ -> opened = id },
                onNavigateToCollection = {}, onNavigateToSearch = {}, onNavigateToWatchlist = {},
                onNavigateToTv = { _, _ -> }, onNavigateToSettings = {}, onSwitchProfile = {},
                onExitApp = {}, onOpenContextMenu = { _, _ -> },
            )
        }
        compose.waitForIdle()
        repeat(12) { compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) } }
        compose.runOnIdle {
            assertEquals(12, focus.currentItemIndex)
            categories.value = listOf(Category("trending_movies", "Movies", items.reversed()))
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(27, focus.currentItemIndex) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(13, opened) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionRight) }
        compose.runOnIdle { categories.value = listOf(Category("trending_movies", "Movies", items)) }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(11, focus.currentItemIndex) }
    }
}
