package com.arflix.tv.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.arflix.tv.ui.components.SettingsRow
import com.arflix.tv.ui.components.SettingsToggleRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsFocusAppearanceDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun outlineMovesImmediatelyWithoutChangingRowGeometry() {
        val focused = mutableIntStateOf(-1)
        compose.setContent {
            Column(
                Modifier.background(Color.Black).width(600.dp).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                repeat(2) { index ->
                    SettingsRow(
                        title = "Audio language",
                        subtitle = "Preferred language for playback",
                        value = "English",
                        isFocused = focused.intValue == index,
                        onClick = {},
                        modifier = Modifier.testTag("setting-$index")
                    )
                }
            }
        }
        val first = compose.onNodeWithTag("setting-0")
        val bounds = first.fetchSemanticsNode().boundsInRoot
        // Compare each row in place; fractional coordinates change text rasterization.
        val resting = List(2) { compose.onNodeWithTag("setting-$it").captureToImage().asAndroidBitmap() }
        compose.runOnIdle { focused.intValue = 0 }
        assertFocusedSurface("setting-0")
        compose.mainClock.autoAdvance = false
        repeat(8) { index ->
            val target = (index + 1) % 2
            compose.runOnIdle { focused.intValue = target }
            compose.mainClock.advanceTimeByFrame()
            compose.waitForIdle()
            assertFocusedSurface("setting-$target")
            val old = compose.onNodeWithTag("setting-${1 - target}").captureToImage().asAndroidBitmap()
            assertTrue("The previous row retained its focus halo", resting[1 - target].sameAs(old))
            assertEquals(bounds, first.fetchSemanticsNode().boundsInRoot)
        }
    }

    @Test fun focusedToggleKeepsItsEnabledColorAndDarkSurface() {
        compose.setContent {
            Column(Modifier.background(Color.Black).width(600.dp).padding(12.dp)) {
                SettingsToggleRow(
                    title = "Match frame rate",
                    subtitle = "Match the display to the video",
                    isEnabled = true,
                    isFocused = true,
                    onToggle = {},
                    modifier = Modifier.testTag("toggle")
                )
            }
        }
        assertFocusedSurface("toggle")
        val bitmap = compose.onNodeWithTag("toggle").captureToImage().asAndroidBitmap()
        val greenPixels = (0 until bitmap.height).sumOf { y ->
            (bitmap.width / 2 until bitmap.width).count { x ->
                val pixel = bitmap.getPixel(x, y)
                android.graphics.Color.green(pixel) > android.graphics.Color.red(pixel) + 30 &&
                    android.graphics.Color.green(pixel) > android.graphics.Color.blue(pixel) + 15
            }
        }
        assertTrue("Enabled state must remain visible independently of focus", greenPixels > 20)
    }

    private fun assertFocusedSurface(tag: String) {
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val outlineY = with(compose.density) { 0.75.dp.toPx().toInt() }
        val edge = bitmap.getPixel(bitmap.width / 2, outlineY)
        assertTrue("Focused row needs a crisp white outline", android.graphics.Color.red(edge) > 180)
        val interiorX = with(compose.density) { 6.dp.toPx().toInt() }
        val interior = bitmap.getPixel(interiorX, bitmap.height / 2)
        assertTrue("Focus must not turn the row into a white block", android.graphics.Color.red(interior) < 60)
    }
}
