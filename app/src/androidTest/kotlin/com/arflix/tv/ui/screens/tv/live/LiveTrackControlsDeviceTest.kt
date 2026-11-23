package com.arflix.tv.ui.screens.tv.live

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.arflix.tv.R
import com.arflix.tv.ui.theme.ArflixTvTheme
import com.arflix.tv.util.DeviceType
import com.arflix.tv.util.LocalDeviceType
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveTrackControlsDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val strings get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun show(content: @Composable () -> Unit) {
        compose.setContent {
            CompositionLocalProvider(LocalDeviceType provides DeviceType.TV) {
                ArflixTvTheme { Box(Modifier.fillMaxSize().background(Color.Black)) { content() } }
            }
        }
    }

    @Test fun aSelectedTrackBelowTheFirstScreenIsScrolledToAndFocused() {
        val options = (0..24).map { LiveTrackOption("lang$it", "Language $it", selected = it == 20) }
        show {
            LiveTrackPickerOverlay(
                title = "Subtitles", icon = Icons.Filled.Subtitles, channelName = "Channel",
                options = options, onDismiss = {}, onPick = {},
            )
        }
        // Without scrolling first, the request for the unplaced row failed and nothing was focused.
        compose.waitUntil(5_000) { compose.onAllNodes(isFocused()).fetchSemanticsNodes().isNotEmpty() }
        val focused = compose.onAllNodes(isFocused()).fetchSemanticsNodes().single().boundsInRoot
        val selectedLabel = compose.onNodeWithText("Language 20").assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        val overlap = focused.intersect(selectedLabel)
        assertFalse("Focus is not on the selected row", overlap.width <= 0f || overlap.height <= 0f)
    }

    @Test fun controlsDoNotOverlapOnANarrowScreenWithEveryButtonShown() =
        assertNoOverlappingControls(widthDp = 480)

    @Test fun controlsDoNotOverlapOnATvScreenWithEveryButtonShown() =
        assertNoOverlappingControls(widthDp = 960)

    private fun assertNoOverlappingControls(widthDp: Int) {
        show {
            Box(Modifier.width(widthDp.dp).fillMaxHeight()) {
                FullscreenHud(
                    channel = null, nowNext = null, pokeSignal = 0,
                    isCatchupMode = true, playbackDurationMs = 3_600_000L,
                    onGuideClick = {}, onOpenVariants = {}, onSubtitlesClick = {}, onAudioClick = {},
                    onPlayPauseClick = {}, onRewindClick = {}, onFastForwardClick = {},
                    onPreviousCatchupClick = {}, onNextCatchupClick = {}, onReplayClick = {}, onGoLiveClick = {},
                    showControls = true,
                )
            }
        }
        compose.waitForIdle()
        val controls: List<Pair<String, SemanticsNodeInteraction>> =
            listOf(R.string.live_audio, R.string.live_subtitles, R.string.live_label_choose_source,
                R.string.live_btn_guide, R.string.live_badge_live)
                .map { strings.getString(it) }
                .map { it to compose.onNodeWithText(it) } +
            listOf(R.string.live_cd_previous_channel, R.string.live_cd_rewind, R.string.live_cd_pause,
                R.string.live_cd_fast_forward, R.string.live_cd_next_channel, R.string.live_cd_replay)
                .map { strings.getString(it) }
                .map { it to compose.onNodeWithContentDescription(it) }
        val bounds: List<Pair<String, Rect>> = controls.map { (name, node) -> name to node.fetchSemanticsNode().boundsInRoot }
        for (i in bounds.indices) {
            for (j in i + 1 until bounds.size) {
                val (a, ra) = bounds[i]
                val (b, rb) = bounds[j]
                val overlap = ra.intersect(rb)
                assertFalse("\"$a\" $ra overlaps \"$b\" $rb at ${widthDp}dp", overlap.width > 0f && overlap.height > 0f)
            }
        }
    }
}
