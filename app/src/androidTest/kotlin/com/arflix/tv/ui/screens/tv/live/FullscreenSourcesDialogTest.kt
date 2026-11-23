package com.arflix.tv.ui.screens.tv.live

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.pressKey
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.arflix.tv.data.model.IptvChannel
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class FullscreenSourcesDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val current = IptvChannel(id = "p:hd", name = "News HD", group = "News", streamUrl = "https://example.invalid/hd")
        .enrichForFastStartup(1)
    private val backup = IptvChannel(id = "p:sd", name = "News SD", group = "News", streamUrl = "https://example.invalid/sd")
        .enrichForFastStartup(2)

    @Test fun phoneCanChooseSourceAfterLoading() = touchCanChooseSource(DeviceType.PHONE)

    @Test fun tabletCanChooseSourceAfterLoading() = touchCanChooseSource(DeviceType.TABLET)

    private fun touchCanChooseSource(deviceType: DeviceType) {
        val loading = mutableStateOf(true)
        val failed = mutableStateOf(false)
        val variants = mutableStateOf(emptyList<EnrichedChannel>())
        val open = mutableStateOf(true)
        var selected: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides deviceType) {
                if (open.value) SourcesPanel(current, variants.value, loading.value, failed.value,
                    { open.value = false }, { selected = it.id; open.value = false })
            }
        }
        compose.waitForIdle()
        compose.runOnIdle { loading.value = false; failed.value = true }
        compose.onNodeWithText(compose.activity.getString(android.R.string.cancel)).performClick()
        compose.runOnIdle { failed.value = false; loading.value = true; open.value = true }
        compose.waitForIdle()
        compose.runOnIdle { variants.value = listOf(current, backup); loading.value = false }
        compose.onNodeWithText("News SD").performClick()
        compose.runOnIdle { assertEquals(backup.id, selected) }
    }

    @Test fun remoteCanSelectBackupAfterLoading() {
        val loading = mutableStateOf(true)
        var selected: String? = null
        compose.setContent {
            SourcesPanel(current, listOf(current, backup), loading.value, false, {}, { selected = it.id })
        }
        compose.onNodeWithText(compose.activity.getString(android.R.string.cancel)).assertIsFocused()
        compose.runOnIdle { loading.value = false }
        compose.onNode(hasText("News HD") and hasClickAction()).assertIsFocused()
        compose.onNode(hasText("News HD") and hasClickAction()).performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("News SD").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(backup.id, selected) }
    }

    @Test fun backDismissesAnEmptySelector() {
        var dismissals = 0
        compose.setContent {
            SourcesPanel(current, listOf(current), false, false, { dismissals++ }, {})
        }
        compose.onNodeWithText(compose.activity.getString(android.R.string.cancel)).assertIsFocused()
        Espresso.pressBack()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun failedLookupCanBeClosedWithTheRemote() {
        var dismissals = 0
        compose.setContent {
            SourcesPanel(current, emptyList(), false, true, { dismissals++ }, {})
        }
        compose.onNodeWithText(compose.activity.getString(com.arflix.tv.R.string.live_sources_failed)).assertIsDisplayed()
        compose.onNodeWithText(compose.activity.getString(android.R.string.cancel))
            .assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun selectedSourceOffScreenIsScrolledIntoViewAndFocused() {
        val alternatives = (1..20).map { index ->
            IptvChannel(id = "p:$index", name = "Source $index", group = "News", streamUrl = "https://example.invalid/$index")
                .enrichForFastStartup(index)
        }
        var selected: String? = null
        compose.setContent {
            SourcesPanel(alternatives.last(), alternatives, false, false, {}, { selected = it.id })
        }
        compose.onNode(hasText("Source 20") and hasClickAction()).assertIsDisplayed().assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("Source 19").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle { assertEquals("p:19", selected) }
    }

    @Test fun sourceNavigationDoesNotReachUnderlyingPlayerHandlers() {
        var playerKeys = 0
        var selected: String? = null
        compose.setContent {
            Box(Modifier.onPreviewKeyEvent { playerKeys++; true }) {
                SourcesPanel(current, listOf(current, backup), false, false, {}, { selected = it.id })
            }
        }
        compose.onNode(hasText("News HD") and hasClickAction()).assertIsFocused()
            .performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("News SD").assertIsFocused().performKeyInput { pressKey(Key.DirectionCenter) }
        compose.runOnIdle {
            assertEquals(backup.id, selected)
            assertEquals(0, playerKeys)
        }
    }

    @Test fun touchShowsSelectedSourceInMiddleOfLongListAndItsCategory() {
        val alternatives = (1..40).map { index ->
            IptvChannel(id = "p:$index", name = "Source $index", group = "Group $index",
                streamUrl = "https://example.invalid/$index").enrichForFastStartup(index)
        }
        var selected: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.PHONE) {
                SourcesPanel(alternatives[19], alternatives, false, false, {}, { selected = it.id })
            }
        }
        compose.onNode(hasText("Source 20") and hasClickAction()).assertIsDisplayed().performClick()
        compose.onNodeWithText("Group 20").assertIsDisplayed()
        compose.runOnIdle { assertEquals("p:20", selected) }
    }
}

@Composable
private fun SourcesPanel(
    channel: EnrichedChannel,
    variants: List<EnrichedChannel>,
    loading: Boolean,
    failed: Boolean,
    onDismiss: () -> Unit,
    onPick: (EnrichedChannel) -> Unit,
) = FullscreenSourcesOverlay(
    visible = true,
    isLoading = loading,
    failed = failed,
    currentChannel = channel,
    variants = variants,
    onPick = onPick,
    onDismiss = onDismiss,
)
