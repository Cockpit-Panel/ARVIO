package com.arflix.tv.ui.screens.settings

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import io.mockk.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class WatchlistOnHomeSettingsDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun mobileSettingsCanEnableTheWatchlist() {
        val state = MutableStateFlow(SettingsUiState())
        val model = mockk<SettingsViewModel>(relaxed = true)
        every { model.uiState } returns state
        every { model.setWatchlistOnHome(any()) } answers {
            state.value = state.value.copy(watchlistOnHome = firstArg())
        }
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.PHONE) {
                SettingsScreen(viewModel = model)
            }
        }
        compose.onNodeWithText("Interface").performScrollTo().performClick()
        compose.onNodeWithText("Show watchlist on home").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(state.value.watchlistOnHome) }
        verify(exactly = 1) { model.setWatchlistOnHome(true) }
    }

    @Test fun firstRemoteClickEnablesWatchlistAndSecondDisablesIt() {
        val state = MutableStateFlow(SettingsUiState())
        val model = mockk<SettingsViewModel>(relaxed = true)
        every { model.uiState } returns state
        every { model.setWatchlistOnHome(any()) } answers {
            state.value = state.value.copy(watchlistOnHome = firstArg())
        }
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                SettingsScreen(viewModel = model, initialSection = "appearance")
            }
        }
        compose.waitForIdle()
        repeat(2) { compose.onRoot().performKeyInput { pressKey(Key.DirectionDown) } }
        compose.onNodeWithText("Show watchlist on home").assertIsDisplayed()
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertTrue(state.value.watchlistOnHome) }
        verify(exactly = 1) { model.setWatchlistOnHome(true) }
        compose.onRoot().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertFalse(state.value.watchlistOnHome) }
        verify(exactly = 1) { model.setWatchlistOnHome(false) }
    }
}
